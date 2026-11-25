package com.nossouniverso.launcher.ui.settings

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nossouniverso.launcher.NossoUniversoApp
import com.nossouniverso.launcher.R
import com.nossouniverso.launcher.model.IconPackInfo
import com.nossouniverso.launcher.model.IconPackKind
import com.nossouniverso.launcher.model.IconPackPreviewItem
import com.nossouniverso.launcher.ui.views.HilmTransitionAnimator
import java.util.concurrent.Executors

class IconPackManagerActivity : AppCompatActivity() {

    private lateinit var app: NossoUniversoApp
    private lateinit var txtActivePackSummary: TextView
    private lateinit var btnImportZipPack: Button
    private lateinit var btnRestoreOriginalIcons: Button
    private lateinit var recyclerIconPacks: RecyclerView
    private lateinit var adapter: IconPackCardAdapter

    private data class SampleApp(
        val packageName: String,
        val activityName: String,
        val label: String,
        val drawable: Drawable
    )

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var sampleNativeApps: List<SampleApp> = emptyList()

    private val pickZipPackLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importZipFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_pack_manager)
        app = application as NossoUniversoApp

        txtActivePackSummary = findViewById(R.id.txtActivePackSummary)
        btnImportZipPack = findViewById(R.id.btnImportZipPack)
        btnRestoreOriginalIcons = findViewById(R.id.btnRestoreOriginalIcons)
        recyclerIconPacks = findViewById(R.id.recyclerIconPacks)

        findViewById<Button>(R.id.btnBackFromPacks).setOnClickListener {
            finish()
        }

        btnImportZipPack.setOnClickListener {
            pickZipPackLauncher.launch(
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "*/*"
                )
            )
        }

        btnRestoreOriginalIcons.setOnClickListener {
            applyIconPack(
                IconPackInfo(
                    id = IconPackInfo.STYLE_SYSTEM_DEFAULT,
                    title = "Ícones Originais do Android",
                    kind = IconPackKind.SYSTEM_DEFAULT
                )
            )
        }

        sampleNativeApps = loadSampleNativeApps(limit = 16)

        adapter = IconPackCardAdapter(
            onPreview = { pack -> openPackPreviewDialog(pack) },
            onApply = { pack -> applyIconPack(pack) },
            onDelete = { pack -> confirmDeleteImportedPack(pack) }
        )
        recyclerIconPacks.layoutManager = LinearLayoutManager(this)
        recyclerIconPacks.adapter = adapter

        refreshPacks()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        HilmTransitionAnimator.applyActivityExitTransition(
            this,
            app.shortcutStorage.getTransitionConfig()
        )
    }

    private fun importZipFromUri(uri: Uri) {
        btnImportZipPack.isEnabled = false
        btnImportZipPack.text = getString(R.string.importing_zip_pack)

        ioExecutor.execute {
            val result = app.iconPackManager.importIconPackZip(uri)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                btnImportZipPack.isEnabled = true
                btnImportZipPack.text = getString(R.string.import_zip_pack)

                result.onSuccess { importedPack ->
                    refreshPacks()
                    AlertDialog.Builder(this)
                        .setTitle("Pacote importado")
                        .setMessage("${importedPack.title} (${importedPack.iconCount} ícones) foi adicionado. Deseja aplicar agora?")
                        .setPositiveButton(R.string.apply_icon_pack) { _, _ ->
                            applyIconPack(importedPack)
                        }
                        .setNegativeButton(R.string.close_label, null)
                        .show()
                }.onFailure { err ->
                    Toast.makeText(
                        this,
                        err.message ?: "Não foi possível importar o pacote .zip.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun confirmDeleteImportedPack(pack: IconPackInfo) {
        AlertDialog.Builder(this)
            .setTitle("Remover pacote importado")
            .setMessage("Deseja excluir '${pack.title}' do armazenamento interno?")
            .setPositiveButton(R.string.delete_pack) { _, _ ->
                if (app.iconPackManager.deleteImportedPack(pack.id)) {
                    refreshPacks()
                    Toast.makeText(this, "Pacote removido.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.close_label, null)
            .show()
    }

    private fun refreshPacks() {
        val packs = app.iconPackManager.getAvailableIconPacks()
        val selectedId = app.shortcutStorage.getSelectedIconPackId()
        val activePack = packs.firstOrNull { it.id == selectedId }
        val isLunaTheme = app.shortcutStorage.getLauncherTheme() == ShortcutStorage.THEME_LUNA
        txtActivePackSummary.text = if (isLunaTheme) {
            "Tema Luna ativo (ícones holográficos exclusivos) · Padrão salvo: ${activePack?.title ?: "HILM Artefatos 3D (Universal)"}"
        } else {
            getString(
                R.string.active_icon_pack_format,
                activePack?.title ?: "HILM Artefatos 3D (Universal)"
            )
        }
        btnRestoreOriginalIcons.isEnabled = selectedId != IconPackInfo.STYLE_SYSTEM_DEFAULT
        adapter.submit(packs, selectedId)
    }

    private fun applyIconPack(pack: IconPackInfo) {
        app.shortcutStorage.setSelectedIconPackId(pack.id)
        app.iconPackManager.clearCache()
        refreshPacks()
        val message = if (pack.id == IconPackInfo.STYLE_SYSTEM_DEFAULT) {
            getString(R.string.original_icons_restored)
        } else {
            getString(R.string.icon_pack_applied_format, pack.title)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun openPackPreviewDialog(pack: IconPackInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_icon_pack_preview, null)
        val txtTitle = dialogView.findViewById<TextView>(R.id.txtDialogPackTitle)
        val txtSub = dialogView.findViewById<TextView>(R.id.txtDialogPackSubtitle)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerPreviewIcons)

        txtTitle.text = pack.title
        txtSub.text = pack.subtitle.ifBlank { "Prévia dos ícones desta coleção" }
        recycler.layoutManager = GridLayoutManager(this, 4)

        val previewEntries: List<Pair<String, Drawable?>> = when (pack.kind) {
            IconPackKind.ILLUSTRATED_ASSET, IconPackKind.IMPORTED_PACK -> {
                val items: List<IconPackPreviewItem> =
                    app.iconPackManager.getPreviewItemsForPack(pack.id)
                val packEntries = items.map { item ->
                    item.label to app.iconPackManager.loadPreviewDrawable(item, 160)
                }
                val adaptedInstalled = sampleNativeApps
                    .take(12)
                    .map { sample ->
                        sample.label to app.iconPackManager.resolveForPack(
                            packId = pack.id,
                            packageName = sample.packageName,
                            activityName = sample.activityName,
                            fallback = sample.drawable
                        )
                    }
                packEntries + adaptedInstalled
            }
            else -> {
                sampleNativeApps.map { sample ->
                    sample.label to app.iconPackManager.resolveForPack(
                        packId = pack.id,
                        packageName = sample.packageName,
                        activityName = sample.activityName,
                        fallback = sample.drawable
                    )
                }
            }
        }

        recycler.adapter = PreviewGridAdapter(previewEntries)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.apply_icon_pack) { _, _ ->
                applyIconPack(pack)
            }
            .setNegativeButton(R.string.close_label, null)
            .show()
    }

    private fun loadSampleNativeApps(limit: Int): List<SampleApp> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return list
            .filter { it.activityInfo.packageName != packageName }
            .sortedWith(
                compareBy<android.content.pm.ResolveInfo> {
                    (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                }.thenBy {
                    it.loadLabel(packageManager)?.toString()?.lowercase().orEmpty()
                }
            )
            .take(limit)
            .mapNotNull { info ->
                try {
                    val pkg = info.activityInfo.packageName
                    val act = info.activityInfo.name
                    val label = info.loadLabel(packageManager)?.toString().orEmpty()
                        .ifBlank { pkg }
                    val drawable = info.loadIcon(packageManager)
                    SampleApp(
                        packageName = pkg,
                        activityName = act,
                        label = label,
                        drawable = drawable
                    )
                } catch (_: Exception) {
                    null
                }
            }
    }

    private inner class IconPackCardAdapter(
        private val onPreview: (IconPackInfo) -> Unit,
        private val onApply: (IconPackInfo) -> Unit,
        private val onDelete: (IconPackInfo) -> Unit
    ) : RecyclerView.Adapter<IconPackCardAdapter.PackViewHolder>() {

        private var items: List<IconPackInfo> = emptyList()
        private var selectedId: String = ""

        fun submit(packs: List<IconPackInfo>, activeId: String) {
            items = packs
            selectedId = activeId
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_icon_pack_card, parent, false)
            return PackViewHolder(view)
        }

        override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
            holder.bind(items[position], items[position].id == selectedId)
        }

        override fun getItemCount(): Int = items.size

        inner class PackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cardRoot: LinearLayout = itemView.findViewById(R.id.cardRoot)
            private val txtTitle: TextView = itemView.findViewById(R.id.txtPackTitle)
            private val txtMeta: TextView = itemView.findViewById(R.id.txtPackMeta)
            private val txtBadge: TextView = itemView.findViewById(R.id.txtPackStatusBadge)
            private val txtSubtitle: TextView = itemView.findViewById(R.id.txtPackSubtitle)
            private val strip: LinearLayout = itemView.findViewById(R.id.previewStripContainer)
            private val btnPreview: Button = itemView.findViewById(R.id.btnPreviewPack)
            private val btnApply: Button = itemView.findViewById(R.id.btnApplyPack)
            private val btnDelete: Button = itemView.findViewById(R.id.btnDeleteImportedPack)

            fun bind(pack: IconPackInfo, isActive: Boolean) {
                cardRoot.setBackgroundResource(
                    if (isActive) R.drawable.bg_icon_pack_card_active else R.drawable.bg_icon_pack_card
                )
                txtTitle.text = pack.title
                txtMeta.text = when (pack.kind) {
                    IconPackKind.ILLUSTRATED_ASSET ->
                        "Coleção Ilustrada · ${pack.iconCount} ícones PNG · v${pack.version}"
                    IconPackKind.IMPORTED_PACK ->
                        "Importado (.zip) · ${pack.iconCount} ícones · v${pack.version}"
                    IconPackKind.BUILT_IN_STYLE -> "Estilo HUD Integrado"
                    IconPackKind.SYSTEM_DEFAULT -> "Padrão Original do Sistema"
                    IconPackKind.EXTERNAL_APK -> "Pacote Externo (${pack.packageName ?: ""})"
                }
                txtSubtitle.text = pack.subtitle.ifBlank { "Estilização visual aplicada aos atalhos e à gaveta." }
                txtBadge.visibility = if (isActive) View.VISIBLE else View.GONE
                txtBadge.text = getString(R.string.active_badge)

                btnApply.isEnabled = !isActive
                btnApply.text = if (isActive) {
                    getString(R.string.active_badge)
                } else {
                    getString(R.string.apply_icon_pack)
                }
                btnApply.setOnClickListener { onApply(pack) }
                btnPreview.setOnClickListener { onPreview(pack) }

                if (pack.kind == IconPackKind.IMPORTED_PACK) {
                    btnDelete.visibility = View.VISIBLE
                    btnDelete.setOnClickListener { onDelete(pack) }
                } else {
                    btnDelete.visibility = View.GONE
                    btnDelete.setOnClickListener(null)
                }

                strip.removeAllViews()
                val density = itemView.resources.displayMetrics.density
                val iconSize = (38 * density).toInt()
                val marginEnd = (10 * density).toInt()

                val stripDrawables: List<Drawable?> = when (pack.kind) {
                    IconPackKind.ILLUSTRATED_ASSET, IconPackKind.IMPORTED_PACK -> {
                        val assetThumbs = pack.previewFiles.take(4).map { item ->
                            app.iconPackManager.loadPreviewDrawable(item, 128)
                        }
                        val adaptedThumbs = sampleNativeApps.take(2).map { sample ->
                            app.iconPackManager.resolveForPack(
                                packId = pack.id,
                                packageName = sample.packageName,
                                activityName = sample.activityName,
                                fallback = sample.drawable
                            )
                        }
                        assetThumbs + adaptedThumbs
                    }
                    else ->
                        sampleNativeApps.take(5).map { sample ->
                            app.iconPackManager.resolveForPack(
                                packId = pack.id,
                                packageName = sample.packageName,
                                activityName = sample.activityName,
                                fallback = sample.drawable
                            )
                        }
                }

                stripDrawables.forEach { drawable ->
                    val iv = ImageView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                            this.marginEnd = marginEnd
                        }
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        if (drawable != null) setImageDrawable(drawable)
                    }
                    strip.addView(iv)
                }
            }
        }
    }

    private class PreviewGridAdapter(
        private val items: List<Pair<String, Drawable?>>
    ) : RecyclerView.Adapter<PreviewGridAdapter.TileHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_icon_pack_preview_tile, parent, false)
            return TileHolder(view)
        }

        override fun onBindViewHolder(holder: TileHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class TileHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val img: ImageView = itemView.findViewById(R.id.imgPreviewTileIcon)
            private val txt: TextView = itemView.findViewById(R.id.txtPreviewTileLabel)

            fun bind(entry: Pair<String, Drawable?>) {
                txt.text = entry.first
                img.setImageDrawable(entry.second)
            }
        }
    }
}
