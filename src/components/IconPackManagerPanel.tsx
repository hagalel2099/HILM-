import {
  Check,
  Download,
  Eye,
  FolderArchive,
  RotateCcw,
  Sparkles,
  Trash2,
  X,
} from "lucide-react";
import { useRef, useState } from "react";
import type { IconPackDescriptor, IconPackId } from "../types/launcher";
import {
  exportIconPackAsZip,
  getAllIconPacks,
  getIconPackDescriptor,
  getPackAllIconEntries,
  getPackPreviewUrls,
  importIconPackFromZipFile,
  isRuntimeImportedPack,
  removeRuntimeImportedPack,
} from "../data/iconPackRegistry";
import { INSTALLED_APPS_CATALOG } from "../data/launcherCatalog";
import { AppGlyph } from "./AppGlyph";

interface IconPackManagerPanelProps {
  activePackId: IconPackId;
  onSelectIconPack: (packId: IconPackId) => void;
}

const SAMPLE_APPS_FOR_HUD = INSTALLED_APPS_CATALOG.slice(0, 6);

export function IconPackManagerPanel({
  activePackId,
  onSelectIconPack,
}: IconPackManagerPanelProps) {
  const [inspectingPack, setInspectingPack] =
    useState<IconPackDescriptor | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const [importingZip, setImportingZip] = useState(false);
  const [exportingPackId, setExportingPackId] = useState<string | null>(null);
  const [, setVersionTick] = useState(0);
  const zipInputRef = useRef<HTMLInputElement>(null);

  const packs = getAllIconPacks();
  const activePack = getIconPackDescriptor(activePackId);
  const isOriginalActive = activePackId === "system-default";

  const handleZipSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    setImportError(null);
    setImportingZip(true);
    try {
      const imported = await importIconPackFromZipFile(file);
      setVersionTick((t) => t + 1);
      onSelectIconPack(imported.id);
    } catch (err) {
      setImportError(
        err instanceof Error
          ? err.message
          : "Não foi possível importar o arquivo .zip."
      );
    } finally {
      setImportingZip(false);
    }
  };

  const handleExportZip = async (pack: IconPackDescriptor) => {
    setExportingPackId(pack.id);
    try {
      await exportIconPackAsZip(pack);
    } finally {
      setExportingPackId(null);
    }
  };

  const handleRemoveImported = (packId: IconPackId) => {
    removeRuntimeImportedPack(packId);
    if (activePackId === packId) {
      onSelectIconPack("nosso-universo");
    }
    setVersionTick((t) => t + 1);
  };

  return (
    <section className="space-y-4 pt-6 border-t border-slate-800/80">
      <input
        ref={zipInputRef}
        type="file"
        accept=".zip,application/zip"
        className="hidden"
        onChange={handleZipSelected}
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-display text-base font-semibold text-slate-100">
            Gerenciador de Pacotes de Ícones
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Visualize, selecione, importe pacotes <code className="text-cyan-300">.zip</code> sem recompilar o APK ou restaure os originais. Ativo:{" "}
            <span className="text-cyan-300 font-medium">{activePack.name}</span>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            disabled={importingZip}
            onClick={() => zipInputRef.current?.click()}
            className="px-3 py-2 rounded-lg bg-[#0D1A2D] border border-cyan-500/40 hover:border-cyan-400 text-xs font-medium text-cyan-200 flex items-center gap-1.5 whitespace-nowrap shrink-0 transition-colors cursor-pointer"
          >
            <FolderArchive className="w-3.5 h-3.5 text-cyan-300" />
            <span>{importingZip ? "Importando…" : "Importar .ZIP"}</span>
          </button>

          <button
            type="button"
            disabled={isOriginalActive}
            onClick={() => onSelectIconPack("system-default")}
            className={`px-3 py-2 rounded-lg border text-xs font-medium flex items-center gap-1.5 whitespace-nowrap shrink-0 transition-colors ${
              isOriginalActive
                ? "bg-slate-900/60 border-slate-800 text-slate-500 cursor-not-allowed"
                : "bg-[#0D1523] border-slate-700 hover:border-amber-400/60 text-amber-300 cursor-pointer"
            }`}
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Restaurar Originais</span>
          </button>
        </div>
      </div>

      {importError && (
        <div className="p-3 rounded-xl bg-rose-950/30 border border-rose-500/40 text-xs text-rose-200">
          {importError}
        </div>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-3.5">
        {packs.map((pack) => {
          const isActive = activePackId === pack.id;
          const illustratedThumbs = getPackPreviewUrls(pack);
          const isImported = isRuntimeImportedPack(pack.id);

          return (
            <div
              key={pack.id}
              className={`rounded-xl border p-4 flex flex-col justify-between gap-3 transition-colors ${
                isActive
                  ? "bg-cyan-500/10 border-cyan-400/70"
                  : "bg-[#0B111C] border-slate-800 hover:border-slate-700"
              }`}
            >
              <div className="space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <h3 className="text-xs font-semibold text-slate-100 truncate">
                        {pack.name}
                      </h3>
                      {pack.kind === "illustrated" && (
                        <Sparkles className="w-3.5 h-3.5 text-cyan-300 shrink-0" />
                      )}
                    </div>
                    <p className="text-[11px] font-mono-tabular text-slate-400 mt-0.5">
                      {pack.kind === "illustrated"
                        ? `${isImported ? "Importado .ZIP" : "Coleção PNG RGBA"} · ${pack.icons?.length ?? 0} ícones · v${pack.version}`
                        : pack.kind === "system-default"
                          ? "Aparência Nativa do Android"
                          : `Estilo HUD · ${pack.author}`}
                    </p>
                  </div>

                  {isActive && (
                    <span className="text-[11px] font-mono-tabular font-semibold text-cyan-300 shrink-0">
                      Ativo
                    </span>
                  )}
                </div>

                <p className="text-xs text-slate-400 leading-relaxed">
                  {pack.description}
                </p>

                {/* Visual Icon Strip Preview */}
                <div className="rounded-lg bg-[#060910] border border-slate-800/90 px-3 py-2.5 flex items-center gap-2.5 overflow-x-auto hud-scroll">
                  {pack.kind === "illustrated" ? (
                    illustratedThumbs.slice(0, 6).map((thumb) => (
                      <img
                        key={thumb.key}
                        src={thumb.url}
                        alt={thumb.label}
                        title={thumb.label}
                        referrerPolicy="no-referrer"
                        className="w-10 h-10 object-contain shrink-0 drop-shadow-[0_4px_10px_rgba(34,211,238,0.2)]"
                      />
                    ))
                  ) : (
                    SAMPLE_APPS_FOR_HUD.map((sampleApp) => (
                      <div
                        key={sampleApp.packageName}
                        title={sampleApp.label}
                        className="w-9 h-9 rounded-lg bg-[#0B121F] border border-slate-800 flex items-center justify-center shrink-0"
                      >
                        <AppGlyph
                          app={sampleApp}
                          iconPack={pack.id}
                          sizeClass="w-4 h-4"
                        />
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="flex items-center gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => setInspectingPack(pack)}
                  className="flex-1 h-9 px-3 rounded-lg bg-[#0F1728] border border-slate-700 hover:border-cyan-400/50 text-xs font-medium text-slate-200 flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
                >
                  <Eye className="w-3.5 h-3.5 text-cyan-300" />
                  <span>Visualizar</span>
                </button>

                {pack.kind === "illustrated" && !isImported && (
                  <button
                    type="button"
                    disabled={exportingPackId === pack.id}
                    onClick={() => handleExportZip(pack)}
                    title="Baixar arquivo .zip pronto para editar ou importar direto no Android"
                    className="h-9 px-2.5 rounded-lg bg-[#0F1728] border border-slate-700 hover:border-cyan-400/50 text-xs text-slate-200 flex items-center justify-center gap-1 transition-colors cursor-pointer shrink-0"
                  >
                    <Download className="w-3.5 h-3.5 text-cyan-300" />
                    <span className="hidden sm:inline">.ZIP</span>
                  </button>
                )}

                {isImported && (
                  <button
                    type="button"
                    onClick={() => handleRemoveImported(pack.id)}
                    aria-label={`Remover ${pack.name}`}
                    className="h-9 px-2.5 rounded-lg bg-rose-950/40 border border-rose-500/40 hover:border-rose-400 text-xs text-rose-300 flex items-center justify-center cursor-pointer shrink-0"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                )}

                <button
                  type="button"
                  disabled={isActive}
                  onClick={() => onSelectIconPack(pack.id)}
                  className={`flex-1 h-9 px-3 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors ${
                    isActive
                      ? "bg-cyan-500/20 border border-cyan-400/50 text-cyan-200 cursor-default"
                      : "bg-cyan-500 hover:bg-cyan-400 text-slate-950 cursor-pointer"
                  }`}
                >
                  {isActive && <Check className="w-3.5 h-3.5" />}
                  <span>{isActive ? "Aplicado" : "Aplicar Pacote"}</span>
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {/* Full Collection Visual Inspector Modal */}
      {inspectingPack && (
        <div
          className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4"
          role="dialog"
          aria-modal="true"
          aria-label={`Prévia de ${inspectingPack.name}`}
        >
          <div className="w-full max-w-3xl max-h-[88vh] rounded-2xl bg-[#080E18] border border-cyan-500/40 shadow-2xl flex flex-col overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-800 flex items-center justify-between gap-4 bg-[#0B1220]">
              <div className="min-w-0">
                <h3 className="font-display text-base font-semibold text-slate-100 truncate">
                  {inspectingPack.name}
                </h3>
                <p className="text-xs text-slate-400 mt-0.5 truncate">
                  {inspectingPack.description}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setInspectingPack(null)}
                aria-label="Fechar visualização"
                className="w-9 h-9 rounded-lg bg-slate-900 border border-slate-700 text-slate-300 hover:text-cyan-300 flex items-center justify-center shrink-0 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-5 hud-scroll">
              {inspectingPack.kind === "illustrated" ? (
                <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-3">
                  {getPackAllIconEntries(inspectingPack).map((entry) => (
                    <div
                      key={entry.key}
                      className="rounded-xl border border-slate-800 bg-[#05080F] p-3 flex flex-col items-center gap-2"
                    >
                      <div
                        className="w-16 h-16 rounded-lg flex items-center justify-center p-1"
                        style={{
                          backgroundImage:
                            "linear-gradient(45deg, rgba(148,163,184,0.07) 25%, transparent 25%), linear-gradient(-45deg, rgba(148,163,184,0.07) 25%, transparent 25%), linear-gradient(45deg, transparent 75%, rgba(148,163,184,0.07) 75%), linear-gradient(-45deg, transparent 75%, rgba(148,163,184,0.07) 75%)",
                          backgroundSize: "14px 14px",
                          backgroundPosition: "0 0, 0 7px, 7px -7px, -7px 0",
                        }}
                      >
                        <img
                          src={entry.url}
                          alt={entry.label}
                          referrerPolicy="no-referrer"
                          className="w-full h-full object-contain drop-shadow-[0_6px_12px_rgba(34,211,238,0.2)]"
                        />
                      </div>
                      <span className="text-[11px] font-medium text-slate-200 text-center truncate w-full">
                        {entry.label}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
                  {INSTALLED_APPS_CATALOG.map((app) => (
                    <div
                      key={app.packageName}
                      className="rounded-xl border border-slate-800 bg-[#060A12] p-3 flex flex-col items-center gap-2"
                    >
                      <div className="w-12 h-12 rounded-xl bg-[#0C1322] border border-slate-800 flex items-center justify-center">
                        <AppGlyph
                          app={app}
                          iconPack={inspectingPack.id}
                          sizeClass="w-6 h-6"
                        />
                      </div>
                      <span className="text-[11px] text-slate-200 text-center truncate w-full">
                        {app.label}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="px-5 py-3.5 border-t border-slate-800 bg-[#0B1220] flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => setInspectingPack(null)}
                className="px-4 py-2 rounded-lg bg-slate-900 border border-slate-700 text-xs font-medium text-slate-300 hover:text-slate-100 cursor-pointer"
              >
                Fechar
              </button>
              <button
                type="button"
                onClick={() => {
                  onSelectIconPack(inspectingPack.id);
                  setInspectingPack(null);
                }}
                className="px-4 py-2 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 text-xs font-semibold cursor-pointer"
              >
                Aplicar Coleção no Launcher
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
