package com.osamaalek.kiosklauncher.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.InputType
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

    fun showEnableAccessibilityDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Enable Global App Lock")
            .setMessage(
                "To lock all apps across your device (including Settings from the notification bar), enable 'Kiosk Launcher' in Android Accessibility Settings."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                temporarilyUnlock("com.android.settings")
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Later", null)
            .show()
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
     * All apps are restricted (locked) by default unless explicitly added to the unlocked set,
     * except our own app and the device's default Home launcher.
     */
    fun isPackageRestricted(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        if (isHomePackage(context, packageName)) return false
        return !getUnlockedPackages(context).contains(packageName)
    }

    fun togglePackageRestriction(context: Context, packageName: String): Boolean {
        val unlocked = getUnlockedPackages(context).toMutableSet()
        val nowRestricted = if (unlocked.contains(packageName)) {
            // Was unlocked -> now restricted (locked)
            unlocked.remove(packageName)
            true
        } else {
            // Was restricted (default) -> now unlocked
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

    fun showSetPasswordDialog(
        context: Context,
        cancelable: Boolean = true,
        onPasswordSet: (() -> Unit)? = null
    ) {
        val density = context.resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        val inputPassword = EditText(context).apply {
            hint = "Enter new password / PIN"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val inputConfirm = EditText(context).apply {
            hint = "Confirm password / PIN"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
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
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
        }
        dialog.show()
    }

    fun requirePassword(
        context: Context,
        title: String = "Password Required",
        message: String = "Enter admin password to continue:",
        onSuccess: () -> Unit
    ) {
        if (!isPasswordSet(context)) {
            showSetPasswordDialog(context, cancelable = true) {
                onSuccess()
            }
            return
        }

        val density = context.resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val container = FrameLayout(context).apply {
            setPadding(pad, pad / 2, pad, 0)
        }
        val input = EditText(context).apply {
            hint = "Password / PIN"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(input)

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = input.text.toString().trim()
                if (checkPassword(context, entered)) {
                    dialog.dismiss()
                    onSuccess()
                } else {
                    input.error = "Incorrect password"
                }
            }
        }
        dialog.show()
    }
}
