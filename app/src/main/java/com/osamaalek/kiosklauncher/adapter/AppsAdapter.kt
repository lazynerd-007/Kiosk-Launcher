package com.osamaalek.kiosklauncher.adapter

import android.content.Context
import android.graphics.Color
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

    private val unlockedPackages = KioskPrefs.getUnlockedPackages(context).toMutableSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppsAdapter.ContentHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_app, parent, false)
        return ContentHolder(view)
    }

    override fun onBindViewHolder(holder: ContentHolder, position: Int) {
        val app = list[position]
        val pkg = app.packageName?.toString() ?: ""
        val isRestricted = !unlockedPackages.contains(pkg)

        holder.textView.text = app.label
        holder.imageView.setImageDrawable(app.icon)

        if (isRestricted) {
            holder.itemView.alpha = 0.45f
            holder.statusView.text = "Locked"
            holder.statusView.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            holder.itemView.alpha = 1.0f
            holder.statusView.text = "Unlocked"
            holder.statusView.setTextColor(Color.parseColor("#2E7D32"))
        }

        holder.itemView.setOnClickListener {
            if (pkg.isEmpty() || pkg == context.packageName) return@setOnClickListener
            val nowRestricted = KioskPrefs.togglePackageRestriction(context, pkg)
            if (nowRestricted) {
                unlockedPackages.remove(pkg)
            } else {
                unlockedPackages.add(pkg)
            }
            notifyItemChanged(position)
            val statusMsg = if (nowRestricted) {
                "${app.label} is now password-protected"
            } else {
                "${app.label} is now unlocked"
            }
            Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ContentHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.app_icon)
        val textView: TextView = itemView.findViewById(R.id.app_name)
        val statusView: TextView = itemView.findViewById(R.id.app_status)
    }
}
