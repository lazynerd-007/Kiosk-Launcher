package com.osamaalek.kiosklauncher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.osamaalek.kiosklauncher.util.KioskPrefs

class PasswordLockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private var lockedPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        lockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        buildLockScreenUI()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        lockedPackage = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        buildLockScreenUI()
    }

    private fun buildLockScreenUI() {
        val density = resources.displayMetrics.density
        val pad = (28 * density).toInt()

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(lockedPackage, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            lockedPackage
        }

        val appIcon = try {
            packageManager.getApplicationIcon(lockedPackage)
        } catch (_: Exception) {
            null
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(pad, pad, pad, pad)
        }

        if (appIcon != null) {
            val iconView = ImageView(this).apply {
                setImageDrawable(appIcon)
                val size = (64 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    bottomMargin = (16 * density).toInt()
                }
            }
            root.addView(iconView)
        }

        val titleView = TextView(this).apply {
            text = "$appLabel is Restricted"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }
        root.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = "Enter your Kiosk password to access $appLabel"
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (24 * density).toInt()
            }
        }
        root.addView(subtitleView)

        val passwordInput = EditText(this).apply {
            hint = "Password / PIN"
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (20 * density).toInt()
            }
        }
        root.addView(passwordInput)

        val attemptUnlock = {
            val entered = passwordInput.text.toString().trim()
            if (!KioskPrefs.isPasswordSet(this@PasswordLockActivity) ||
                KioskPrefs.checkPassword(this@PasswordLockActivity, entered)
            ) {
                KioskPrefs.temporarilyUnlock(lockedPackage)
                finish()
            } else {
                passwordInput.error = "Incorrect password"
            }
        }

        passwordInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                attemptUnlock()
                true
            } else {
                false
            }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val cancelBtn = Button(this).apply {
            text = "Cancel"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * density).toInt()
            }
            setOnClickListener {
                goHome()
            }
        }

        val unlockBtn = Button(this).apply {
            text = "Unlock"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * density).toInt()
            }
            setOnClickListener {
                attemptUnlock()
            }
        }

        buttonRow.addView(cancelBtn)
        buttonRow.addView(unlockBtn)
        root.addView(buttonRow)

        setContentView(root)

        passwordInput.requestFocus()
        passwordInput.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(passwordInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
