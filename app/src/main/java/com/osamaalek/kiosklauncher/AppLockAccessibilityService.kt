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

        // If our own app opened:
        if (pkg == packageName) {
            val cls = event.className?.toString() ?: ""
            // When returning to MainActivity (Home screen), re-lock any temporarily unlocked app
            if (cls.contains("MainActivity")) {
                KioskPrefs.clearTemporaryUnlock()
            }
            return
        }

        // If user switched to a different app than the temporarily unlocked one, re-lock
        val unlockedPkg = KioskPrefs.getTemporarilyUnlockedPackage()
        if (unlockedPkg != null && pkg != unlockedPkg && !pkg.contains("permissioncontroller")) {
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
