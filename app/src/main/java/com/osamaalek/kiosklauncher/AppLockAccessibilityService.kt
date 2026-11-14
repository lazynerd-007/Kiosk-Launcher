package com.osamaalek.kiosklauncher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.osamaalek.kiosklauncher.ui.PasswordLockActivity
import com.osamaalek.kiosklauncher.util.KioskPrefs

class AppLockAccessibilityService : AccessibilityService() {

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                KioskPrefs.clearTemporaryUnlock()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore SystemUI (notification shade, status bar, volume dialog) and keyboards
        if (pkg == "com.android.systemui" || pkg == "android" || isInputMethodPackage(pkg)) {
            return
        }

        // Ignore our own app (PasswordLockActivity and MainActivity handle their own auth)
        if (pkg == packageName) {
            return
        }

        // If user returned to the default device homescreen, clear any temporary app unlock
        if (KioskPrefs.isHomePackage(this, pkg)) {
            KioskPrefs.clearTemporaryUnlock()
            return
        }

        // Ignore non-launchable system helpers/sub-screens (e.g. PermissionController, Share Sheet,
        // Google Play Services sub-screens opened from inside Settings) so they don't clear
        // the temporary unlock of the parent app or get blocked.
        if (!KioskPrefs.isLaunchableOrSettings(this, pkg)) {
            return
        }

        // If user switched to a different launchable app than the temporarily unlocked one, re-lock
        val unlockedPkg = KioskPrefs.getTemporarilyUnlockedPackage()
        if (unlockedPkg != null && pkg != unlockedPkg) {
            KioskPrefs.clearTemporaryUnlock()
        }

        // Check if this package is restricted and not currently unlocked
        if (KioskPrefs.isPackageRestricted(this, pkg) && !KioskPrefs.isTemporarilyUnlocked(pkg)) {
            val lockIntent = Intent(this, PasswordLockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(PasswordLockActivity.EXTRA_PACKAGE_NAME, pkg)
            }
            startActivity(lockIntent)
        }
    }

    private fun isInputMethodPackage(pkg: String): Boolean {
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.any { it.packageName == pkg }
        } catch (_: Exception) {
            false
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
