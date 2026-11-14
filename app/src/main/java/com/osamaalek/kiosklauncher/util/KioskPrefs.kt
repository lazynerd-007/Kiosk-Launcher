package com.osamaalek.kiosklauncher.util

import android.app.Activity
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.security.MessageDigest

object KioskPrefs {
    private const val PREFS_NAME = "kiosk_security_prefs"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_ALLOWED_PACKAGES = "allowed_packages"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    fun getAllowedPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_ALLOWED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun setAllowedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_ALLOWED_PACKAGES, HashSet(packages))
            .apply()
    }

    fun isPackageAllowed(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        return getAllowedPackages(context).contains(packageName)
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
            .setMessage("Set an admin password to protect exiting Kiosk mode and launching restricted apps.")
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
                        Toast.makeText(context, "Kiosk password saved", Toast.LENGTH_SHORT).show()
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

    fun showAllowedAppsDialog(activity: Activity) {
        val allApps = AppsUtil.getAllApps(activity)
            .filter { it.packageName?.toString() != activity.packageName }
            .sortedBy { it.label?.toString()?.lowercase() ?: "" }

        val labels = allApps.map { "${it.label} (${it.packageName})" }.toTypedArray()
        val currentAllowed = getAllowedPackages(activity).toMutableSet()
        val checkedItems = BooleanArray(allApps.size) { index ->
            currentAllowed.contains(allApps[index].packageName.toString())
        }

        AlertDialog.Builder(activity)
            .setTitle("Allowed Kiosk Apps")
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                val pkg = allApps[which].packageName.toString()
                if (isChecked) {
                    currentAllowed.add(pkg)
                } else {
                    currentAllowed.remove(pkg)
                }
            }
            .setPositiveButton("Save") { _, _ ->
                setAllowedPackages(activity, currentAllowed)
                KioskUtil.updateLockTaskPackages(activity)
                Toast.makeText(
                    activity,
                    "Allowed apps updated (${currentAllowed.size} selected)",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showAdminMenu(activity: Activity) {
        val options = arrayOf(
            "Exit Kiosk Mode",
            "Select Allowed Apps (No Password Needed)",
            "Change Kiosk Password",
            "Lock Kiosk Mode Now"
        )
        AlertDialog.Builder(activity)
            .setTitle("Kiosk Admin Menu")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> KioskUtil.stopKioskMode(activity)
                    1 -> showAllowedAppsDialog(activity)
                    2 -> showSetPasswordDialog(activity, cancelable = true)
                    3 -> KioskUtil.startKioskMode(activity)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
