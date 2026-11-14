package com.osamaalek.kiosklauncher.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.osamaalek.kiosklauncher.R
import com.osamaalek.kiosklauncher.model.AppInfo
import com.osamaalek.kiosklauncher.util.KioskPrefs

class AppsAdapter(private val list: List<AppInfo>, private val context: Context) :
    RecyclerView.Adapter<AppsAdapter.ContentHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppsAdapter.ContentHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_app, parent, false)
        return ContentHolder(view)
    }

    override fun onBindViewHolder(holder: ContentHolder, position: Int) {
        val app = list[position]
        val pkg = app.packageName?.toString() ?: ""
        val isRestricted = KioskPrefs.isPackageRestricted(context, pkg)

        holder.textView.text = if (isRestricted) "🔒 ${app.label}" else app.label
        holder.imageView.setImageDrawable(app.icon)

        holder.itemView.setOnClickListener {
            if (pkg.isEmpty()) return@setOnClickListener
            val launchIntent: Intent? = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                if (KioskPrefs.isPackageRestricted(context, pkg)) {
                    KioskPrefs.requirePassword(
                        context,
                        title = "Restricted App",
                        message = "Enter admin password to open ${app.label}:"
                    ) {
                        KioskPrefs.temporarilyUnlock(pkg)
                        context.startActivity(launchIntent)
                    }
                } else {
                    context.startActivity(launchIntent)
                }
            }
        }

        holder.itemView.setOnLongClickListener {
            if (pkg.isEmpty() || pkg == context.packageName) return@setOnLongClickListener true
            val currentlyRestricted = KioskPrefs.isPackageRestricted(context, pkg)
            val actionLabel = if (currentlyRestricted) "remove password restriction from" else "require password for"
            KioskPrefs.requirePassword(
                context,
                title = "Manage App Restriction",
                message = "Enter admin password to $actionLabel ${app.label}:"
            ) {
                val nowRestricted = KioskPrefs.togglePackageRestriction(context, pkg)
                notifyItemChanged(position)
                val statusMsg = if (nowRestricted) {
                    "${app.label} is now password-restricted"
                } else {
                    "${app.label} is now unlocked"
                }
                Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ContentHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.app_icon)
        val textView: TextView = itemView.findViewById(R.id.app_name)
    }
}
