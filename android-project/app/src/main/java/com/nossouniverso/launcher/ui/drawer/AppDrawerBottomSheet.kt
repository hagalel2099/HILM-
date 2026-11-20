package com.nossouniverso.launcher.ui.drawer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.AppInfo

class AppDrawerBottomSheet(
    private val title: String,
    private val apps: List<AppInfo>,
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
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerApps)

        titleView.text = title
        val adapter = AppAdapter(
            onAppClick = { app ->
                onSelectApp(app)
                dismiss()
            },
            onAppLongClick = { app ->
                onPinShortcut?.invoke(app)
                dismiss()
            }
        )
        recycler.layoutManager = GridLayoutManager(requireContext(), 4)
        recycler.adapter = adapter
        adapter.submitList(apps)

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }
}
