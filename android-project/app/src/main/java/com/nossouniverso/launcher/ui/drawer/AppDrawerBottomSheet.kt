package com.nossouniverso.launcher.ui.drawer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.AppInfo

class AppDrawerBottomSheet(
    private val title: String,
    private val apps: List<AppInfo>,
    private val recentApps: List<AppInfo> = emptyList(),
    private val onSelectApp: (AppInfo) -> Unit,
    private val onPinShortcut: ((AppInfo) -> Unit)? = null
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_app_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleView = view.findViewById<TextView>(R.id.txtDrawerTitle)
        val searchEdit = view.findViewById<EditText>(R.id.editSearchApps)
        val recentHeader = view.findViewById<TextView>(R.id.txtRecentHeader)
        val recentRecycler = view.findViewById<RecyclerView>(R.id.recyclerRecentApps)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerApps)

        titleView.text = title

        val hasRecents = recentApps.isNotEmpty()
        recentHeader.visibility = if (hasRecents) View.VISIBLE else View.GONE
        recentRecycler.visibility = if (hasRecents) View.VISIBLE else View.GONE

        if (hasRecents) {
            val recentAdapter = AppAdapter(
                onAppClick = { app ->
                    onSelectApp(app)
                    dismiss()
                },
                onAppLongClick = { app -> showAppContextMenu(app) },
                itemLayoutResId = R.layout.item_app_drawer_recent
            )
            recentRecycler.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            recentRecycler.adapter = recentAdapter
            recentAdapter.submitList(recentApps)
        }

        val adapter = AppAdapter(
            onAppClick = { app ->
                onSelectApp(app)
                dismiss()
            },
            onAppLongClick = { app -> showAppContextMenu(app) }
        )
        recycler.layoutManager = GridLayoutManager(requireContext(), 4)
        recycler.adapter = adapter
        adapter.submitList(apps)

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                adapter.filter(query)
                val showRecentsNow = hasRecents && query.isBlank()
                recentHeader.visibility = if (showRecentsNow) View.VISIBLE else View.GONE
                recentRecycler.visibility = if (showRecentsNow) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun showAppContextMenu(app: AppInfo) {
        val options = mutableListOf<String>()
        if (onPinShortcut != null) {
            options.add("Fixar na tela inicial")
        }
        options.add("Informações do aplicativo")
        options.add("Desinstalar")

        AlertDialog.Builder(requireContext())
            .setTitle(app.label)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Fixar na tela inicial" -> {
                        onPinShortcut?.invoke(app)
                        dismiss()
                    }
                    "Informações do aplicativo" -> {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${app.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            dismiss()
                        } catch (_: Exception) {
                            Toast.makeText(
                                requireContext(),
                                "Não foi possível abrir as informações do aplicativo.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    "Desinstalar" -> {
                        try {
                            val intent = Intent(Intent.ACTION_DELETE).apply {
                                data = Uri.parse("package:${app.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            dismiss()
                        } catch (_: Exception) {
                            Toast.makeText(
                                requireContext(),
                                "Não foi possível iniciar a desinstalação.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .show()
    }
}
