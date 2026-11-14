package com.osamaalek.kiosklauncher.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.adapter.AppsAdapter
import com.osamaalek.kiosklauncher.util.AppsUtil
import com.osamaalek.kiosklauncher.util.KioskPrefs

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var buttonChangePassword: Button
    private lateinit var textServiceStatus: TextView
    private var isAdminAuthenticated = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = v.findViewById(R.id.recyclerView_apps)
        buttonChangePassword = v.findViewById(R.id.button_change_password)
        textServiceStatus = v.findViewById(R.id.text_service_status)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4, RecyclerView.VERTICAL, false)
        recyclerView.setHasFixedSize(true)

        buttonChangePassword.setOnClickListener {
            KioskPrefs.showSetPasswordDialog(requireContext(), cancelable = true)
        }

        textServiceStatus.setOnClickListener {
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

        if (!KioskPrefs.isPasswordSet(requireContext())) {
            KioskPrefs.showSetPasswordDialog(requireContext(), cancelable = false) {
                isAdminAuthenticated = true
                loadApps()
                if (!KioskPrefs.isAccessibilityServiceEnabled(requireContext())) {
                    KioskPrefs.showEnableAccessibilityDialog(requireContext())
                }
            }
        } else if (!isAdminAuthenticated) {
            recyclerView.visibility = View.INVISIBLE
            KioskPrefs.requirePassword(
                requireContext(),
                title = "Admin Password",
                message = "Enter your password to manage locked apps:"
            ) {
                isAdminAuthenticated = true
                recyclerView.visibility = View.VISIBLE
                loadApps()
                if (!KioskPrefs.isAccessibilityServiceEnabled(requireContext())) {
                    KioskPrefs.showEnableAccessibilityDialog(requireContext())
                }
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            loadApps()
        }
    }

    override fun onStop() {
        super.onStop()
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
            .filter { it.packageName?.toString() != requireContext().packageName }
            .distinctBy { it.packageName?.toString() }
            .sortedBy { it.label?.toString()?.lowercase() ?: "" }
        recyclerView.adapter = AppsAdapter(apps, requireContext())
    }
}
