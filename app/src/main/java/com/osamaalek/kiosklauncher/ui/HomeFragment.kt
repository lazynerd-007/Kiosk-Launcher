package com.osamaalek.kiosklauncher.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.adapter.AppsAdapter
import com.osamaalek.kiosklauncher.util.AppsUtil
import com.osamaalek.kiosklauncher.util.KioskPrefs

class HomeFragment : Fragment() {

    private lateinit var topBar: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var buttonChangePassword: Button
    private lateinit var textServiceStatus: TextView
    private var isAdminAuthenticated = false
    private var activeDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_home, container, false)

        topBar = v.findViewById(R.id.topBar)
        recyclerView = v.findViewById(R.id.recyclerView_apps)
        buttonChangePassword = v.findViewById(R.id.button_change_password)
        textServiceStatus = v.findViewById(R.id.text_service_status)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4, RecyclerView.VERTICAL, false)
        recyclerView.setHasFixedSize(true)

        buttonChangePassword.setOnClickListener {
            if (!isAdminAuthenticated) return@setOnClickListener
            KioskPrefs.showSetPasswordDialog(requireContext(), cancelable = true)
        }

        textServiceStatus.setOnClickListener {
            if (!isAdminAuthenticated) return@setOnClickListener
            if (!KioskPrefs.isAccessibilityServiceEnabled(requireContext())) {
                KioskPrefs.temporarilyUnlock("com.android.settings")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        return v
    }

    override fun onResume() {
        super.onResume()
        updateServiceBanner()

        // Avoid stacking duplicate dialogs if one is already open
        if (activeDialog?.isShowing == true) {
            return
        }

        if (!KioskPrefs.isPasswordSet(requireContext())) {
            topBar.visibility = View.INVISIBLE
            recyclerView.visibility = View.INVISIBLE
            activeDialog = KioskPrefs.showSetPasswordDialog(requireContext(), cancelable = false) {
                isAdminAuthenticated = true
                topBar.visibility = View.VISIBLE
                recyclerView.visibility = View.VISIBLE
                loadApps()
                if (!KioskPrefs.isAccessibilityServiceEnabled(requireContext())) {
                    activeDialog = KioskPrefs.showEnableAccessibilityDialog(requireContext())
                }
            }
        } else if (!isAdminAuthenticated) {
            topBar.visibility = View.INVISIBLE
            recyclerView.visibility = View.INVISIBLE
            activeDialog = KioskPrefs.requirePassword(
                requireContext(),
                title = "Admin Password",
                message = "Enter your password to manage locked apps:",
                cancelable = false,
                onCancel = {
                    requireActivity().finish()
                }
            ) {
                isAdminAuthenticated = true
                topBar.visibility = View.VISIBLE
                recyclerView.visibility = View.VISIBLE
                loadApps()
                if (!KioskPrefs.isAccessibilityServiceEnabled(requireContext())) {
                    activeDialog = KioskPrefs.showEnableAccessibilityDialog(requireContext())
                }
            }
        } else {
            topBar.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE
            loadApps()
        }
    }

    override fun onStop() {
        super.onStop()
        activeDialog?.dismiss()
        activeDialog = null
        isAdminAuthenticated = false
    }

    private fun updateServiceBanner() {
        val enabled = KioskPrefs.isAccessibilityServiceEnabled(requireContext())
        if (enabled) {
            textServiceStatus.text = "Global App Lock is ACTIVE. Tap any app below to lock or unlock it."
            textServiceStatus.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            textServiceStatus.text = "App Lock service is OFF. Tap here to enable 'Kiosk Launcher' in Accessibility Settings."
            textServiceStatus.setTextColor(Color.parseColor("#D32F2F"))
        }
    }

    private fun loadApps() {
        val apps = AppsUtil.getAllApps(requireContext())
            .filter {
                val pkg = it.packageName?.toString() ?: ""
                pkg != requireContext().packageName && !KioskPrefs.isHomePackage(requireContext(), pkg)
            }
            .distinctBy { it.packageName?.toString() }
            .sortedBy { it.label?.toString()?.lowercase() ?: "" }
        recyclerView.adapter = AppsAdapter(apps, requireContext())
    }
}
