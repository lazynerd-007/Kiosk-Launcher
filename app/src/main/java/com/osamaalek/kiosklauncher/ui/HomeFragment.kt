package com.osamaalek.kiosklauncher.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.adapter.AppsAdapter
import com.osamaalek.kiosklauncher.util.AppsUtil
import com.osamaalek.kiosklauncher.util.KioskPrefs

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var imageButtonAdmin: ImageButton
    private var promptedAccessibilityOnce = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = v.findViewById(R.id.recyclerView_apps)
        imageButtonAdmin = v.findViewById(R.id.imageButton_exit)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4, RecyclerView.VERTICAL, false)
        recyclerView.setHasFixedSize(true)
        loadApps()

        if (!KioskPrefs.isPasswordSet(requireContext())) {
            KioskPrefs.showSetPasswordDialog(requireContext(), cancelable = false) {
                if (!KioskPrefs.isAccessibilityServiceEnabled(requireContext())) {
                    promptedAccessibilityOnce = true
                    KioskPrefs.showEnableAccessibilityDialog(requireContext())
                }
            }
        } else if (!promptedAccessibilityOnce && !KioskPrefs.isAccessibilityServiceEnabled(requireContext())) {
            promptedAccessibilityOnce = true
            KioskPrefs.showEnableAccessibilityDialog(requireContext())
        }

        imageButtonAdmin.setOnClickListener {
            KioskPrefs.requirePassword(
                requireContext(),
                title = "Admin Access",
                message = "Enter password to manage restricted apps or settings:"
            ) {
                KioskPrefs.showAdminMenu(requireActivity()) {
                    loadApps()
                }
            }
        }

        return v
    }

    override fun onResume() {
        super.onResume()
        KioskPrefs.clearTemporaryUnlock()
        if (::recyclerView.isInitialized) {
            loadApps()
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
