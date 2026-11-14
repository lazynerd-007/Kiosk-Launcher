package com.osamaalek.kiosklauncher.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.osamaalek.kiosklauncher.AppLockAccessibilityService
import java.security.MessageDigest

object KioskPrefs {
    private const val PREFS_NAME = "kiosk_security_prefs"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_UNLOCKED_PACKAGES = "unlocked_packages"

    @Volatile
    private var temporarilyUnlockedPkg: String? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun temporarilyUnlock(packageName: String) {
        temporarilyUnlockedPkg = packageName
    }

    fun clearTemporaryUnlock() {
        temporarilyUnlockedPkg = null
    }

    fun getTemporarilyUnlockedPackage(): String? = temporarilyUnlockedPkg

    fun isTemporarilyUnlocked(packageName: String): Boolean {
        return temporarilyUnlockedPkg == packageName
    }

    fun isHomePackage(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
            resolveInfos.any { it.activityInfo?.packageName == packageName }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true only if the package is an actual user-launchable app (appears in the app grid)
     * or Android Settings. Prevents locking hidden system dialogs like Share Sheet, Permission Controller,
     * Package Installer, or In-Call UI that have no launcher icon.
     */
    fun isLaunchableOrSettings(context: Context, packageName: String): Boolean {
        if (packageName == "com.android.settings") return true
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, AppLockAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expectedComponent }
    }

    fun showEnableAccessibilityDialog(context: Context): AlertDialog {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Enable Global App Lock")
            .setMessage(
                "To lock apps across your device (including Settings from the notification bar), enable 'Kiosk Launcher' in Android Accessibility Settings."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                temporarilyUnlock("com.android.settings")
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Later", null)
            .create()
        dialog.show()
        return dialog
    }

    fun isPasswordSet(context: Context): Boolean {
        return !prefs(context).getString(KEY_PASSWORD_HASH, null).isNullOrEmpty()
    }

    fun setPassword(context: Context, plainPassword: String) {
        prefs(context).edit()
            .putString(KEY_PASSWORD_HASH, hashPassword(plainPassword))
            .apply()
    }

    fun checkPassword(context: Context, plainPassword: String): Boolean {
        val savedHash = prefs(context).getString(KEY_PASSWORD_HASH, null) ?: return false
        return savedHash == hashPassword(plainPassword)
    }

    fun getUnlockedPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_UNLOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun setUnlockedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_UNLOCKED_PACKAGES, HashSet(packages))
            .apply()
    }

    /**
     * Fast check without PackageManager IPC for UI lists where items are already filtered.
     */
    fun isPackageUnlocked(context: Context, packageName: String): Boolean {
        return getUnlockedPackages(context).contains(packageName)
    }

    /**
     * All launchable apps (plus Settings) are restricted by default unless added to unlocked_packages.
     * Non-launchable system helpers and the default Home launcher are never restricted.
     */
    fun isPackageRestricted(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        if (getUnlockedPackages(context).contains(packageName)) return false
        if (isHomePackage(context, packageName)) return false
        if (!isLaunchableOrSettings(context, packageName)) return false
        return true
    }

    fun togglePackageRestriction(context: Context, packageName: String): Boolean {
        val unlocked = getUnlockedPackages(context).toMutableSet()
        val nowRestricted = if (unlocked.contains(packageName)) {
            unlocked.remove(packageName)
            true
        } else {
            unlocked.add(packageName)
            false
        }
        setUnlockedPackages(context, unlocked)
        return nowRestricted
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("kiosk_salt_$password".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun focusAndShowKeyboard(dialog: AlertDialog, input: EditText) {
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        input.requestFocus()
        input.post {
            val imm = input.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun showSetPasswordDialog(
        context: Context,
        cancelable: Boolean = true,
        onPasswordSet: (() -> Unit)? = null
    ): AlertDialog {
        val density = context.resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        val inputPassword = EditText(context).apply {
            hint = "Enter new password / PIN"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_NEXT
            isSingleLine = true
        }
        val inputConfirm = EditText(context).apply {
            hint = "Confirm password / PIN"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
        }

        layout.addView(inputPassword)
        layout.addView(inputConfirm)

        val builder = AlertDialog.Builder(context)
            .setTitle("Set Kiosk Password")
            .setMessage("Set an admin password to unlock apps and manage Kiosk settings.")
            .setView(layout)
            .setCancelable(cancelable)
            .setPositiveButton("Save", null)

        if (cancelable) {
            builder.setNegativeButton("Cancel", null)
        }

        val dialog = builder.create()
        val submitAction = {
            val p1 = inputPassword.text.toString().trim()
            val p2 = inputConfirm.text.toString().trim()
            when {
                p1.length < 4 -> {
                    inputPassword.error = "Password must be at least 4 characters"
                }
                p1 != p2 -> {
                    inputConfirm.error = "Passwords do not match"
                }
                else -> {
                    setPassword(context, p1)
                    Toast.makeText(context, "Password saved", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onPasswordSet?.invoke()
                }
            }
        }

        inputConfirm.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                submitAction()
                true
            } else {
                false
            }
        }

        dialog.setOnShowListener {
            focusAndShowKeyboard(dialog, inputPassword)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                submitAction()
            }
        }
        dialog.show()
        return dialog
    }

    fun requirePassword(
        context: Context,
        title: String = "Password Required",
        message: String = "Enter admin password to continue:",
        cancelable: Boolean = true,
        onCancel: (() -> Unit)? = null,
        onSuccess: () -> Unit
    ): AlertDialog {
        if (!isPasswordSet(context)) {
            return showSetPasswordDialog(context, cancelable = false) {
                onSuccess()
            }
        }

        val density = context.resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val container = FrameLayout(context).apply {
            setPadding(pad, pad / 2, pad, 0)
        }
        val input = EditText(context).apply {
            hint = "Password / PIN"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
        }
        container.addView(input)

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setCancelable(cancelable)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
                onCancel?.invoke()
            }
            .setOnCancelListener {
                onCancel?.invoke()
            }
            .create()

        val submitAction = {
            val entered = input.text.toString().trim()
            if (checkPassword(context, entered)) {
                dialog.dismiss()
                onSuccess()
            } else {
                input.error = "Incorrect password"
            }
        }

        input.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                submitAction()
                true
            } else {
                false
            }
        }

        dialog.setOnShowListener {
            focusAndShowKeyboard(dialog, input)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                submitAction()
            }
        }
        dialog.show()
        return dialog
    }
}
