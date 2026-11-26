import { useEffect, useState } from "react";
import {
  CheckCircle2,
  Clock,
  Download,
  FileCode2,
  FolderOpen,
  ImagePlus,
  Layers,
  Pencil,
  RotateCcw,
  Sliders,
  Sparkles,
  Upload,
} from "lucide-react";
import type {
  AndroidProjectTreeResponse,
  IconPackId,
  LauncherPersistedState,
  LauncherThemeId,
  TransitionConfig,
  WallpaperId,
} from "../types/launcher";
import {
  ACCEPTANCE_CRITERIA,
  INSTALLED_APPS_CATALOG,
  LAUNCHER_THEMES,
  SLOT_IDS,
  SLOT_LABELS,
  TRANSITION_EFFECTS,
  WALLPAPERS,
} from "../data/launcherCatalog";
import { IconPackManagerPanel } from "./IconPackManagerPanel";
import { HilmTransitionPreview } from "./HilmTransitionPreview";

export type WorkspaceTab = "personalizacao" | "androidide" | "criterios";

interface StudioWorkspacePanelProps {
  activeTab: WorkspaceTab;
  onChangeTab: (tab: WorkspaceTab) => void;
  state: LauncherPersistedState;
  isEditMode: boolean;
  onToggleEditMode: (active: boolean) => void;
  onSelectTheme: (themeId: LauncherThemeId) => void;
  onSelectIconPack: (pack: IconPackId) => void;
  onSelectWallpaper: (wallpaperId: WallpaperId, customUrl?: string) => void;
  onChangeOpacity: (opacity: number) => void;
  onUpdateTransition: (transition: TransitionConfig) => void;
  onImportState: (imported: Partial<LauncherPersistedState>) => void;
  onResetState: () => void;
}

export function StudioWorkspacePanel({
  activeTab,
  onChangeTab,
  state,
  isEditMode,
  onToggleEditMode,
  onSelectTheme,
  onSelectIconPack,
  onSelectWallpaper,
  onChangeOpacity,
  onUpdateTransition,
  onImportState,
  onResetState,
}: StudioWorkspacePanelProps) {
  const [layoutTransferStatus, setLayoutTransferStatus] = useState<string | null>(null);
  const [draftTransition, setDraftTransition] = useState<TransitionConfig>(state.transition);
  const [previewReplaySeq, setPreviewReplaySeq] = useState(0);
  const [transitionAppliedNotice, setTransitionAppliedNotice] = useState(false);
  const [treeData, setTreeData] = useState<AndroidProjectTreeResponse | null>(null);
  const [treeError, setTreeError] = useState<string | null>(null);
  const [selectedFilePath, setSelectedFilePath] = useState<string>(
    "app/src/main/java/com/nossouniverso/launcher/ui/home/HomeActivity.kt"
  );
  const [fileContent, setFileContent] = useState<string>("");
  const [loadingFile, setLoadingFile] = useState(false);

  useEffect(() => {
    setDraftTransition(state.transition);
  }, [state.transition.enabled, state.transition.effect, state.transition.durationMs]);

  useEffect(() => {
    let active = true;
    fetch("/api/android-project/tree")
      .then((res) => {
        if (!res.ok) throw new Error("Falha ao carregar árvore do projeto Android.");
        return res.json();
      })
      .then((data: AndroidProjectTreeResponse) => {
        if (active) setTreeData(data);
      })
      .catch((err: Error) => {
        if (active) setTreeError(err.message);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (activeTab !== "androidide" || !selectedFilePath) return;
    let active = true;
    setLoadingFile(true);
    fetch(`/api/android-project/file?path=${encodeURIComponent(selectedFilePath)}`)
      .then((res) => res.json())
      .then((data) => {
        if (!active) return;
        if (data.isBinary) {
          setFileContent(
            `Arquivo binário (${data.path}) · ${(data.size / 1024).toFixed(1)} KB`
          );
        } else {
          setFileContent(data.content || "");
        }
      })
      .catch(() => {
        if (active) setFileContent("Não foi possível carregar o conteúdo do arquivo.");
      })
      .finally(() => {
        if (active) setLoadingFile(false);
      });

    return () => {
      active = false;
    };
  }, [activeTab, selectedFilePath]);

  const handleWallpaperUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === "string") {
        onSelectWallpaper("custom", reader.result);
      }
    };
    reader.readAsDataURL(file);
  };

  const handleExportLayoutJson = () => {
    const payload = {
      schema: "nosso_universo_layout_v1",
      exportedAt: new Date().toISOString(),
      themeId: state.themeId || "default",
      iconPack: state.iconPack,
      wallpaperId: state.wallpaperId,
      panelOpacity: state.panelOpacity,
      transition: state.transition,
      slots: state.slots,
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "nosso-universo-backup.json";
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    window.setTimeout(() => URL.revokeObjectURL(url), 1500);
    setLayoutTransferStatus("Backup .json exportado com sucesso.");
  };

  const handleImportLayoutJson = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(String(reader.result || "")) as Partial<LauncherPersistedState>;
        if (!parsed || typeof parsed !== "object" || !parsed.slots) {
          setLayoutTransferStatus("Arquivo JSON inválido para backup do launcher.");
          return;
        }
        onImportState(parsed);
        setLayoutTransferStatus("Layout restaurado a partir do arquivo .json.");
      } catch {
        setLayoutTransferStatus("Não foi possível ler o arquivo .json selecionado.");
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="flex flex-col h-full bg-[#080D15] border border-slate-800/90 rounded-2xl overflow-hidden">
      {/* Sub-tabs Bar */}
      <div className="flex items-center justify-between gap-4 px-5 py-3.5 border-b border-slate-800 bg-[#0A101A]">
        <div className="flex items-center gap-1.5 p-1 rounded-lg bg-[#06090F] border border-slate-800 overflow-x-auto">
          <button
            type="button"
            onClick={() => onChangeTab("personalizacao")}
            className={`px-3 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 flex items-center gap-1.5 transition-colors cursor-pointer ${
              activeTab === "personalizacao"
                ? "bg-cyan-500/15 text-cyan-300 border border-cyan-400/40"
                : "text-slate-400 hover:text-slate-200"
            }`}
          >
            <Sliders className="w-3.5 h-3.5" />
            <span>Personalização</span>
          </button>
          <button
            type="button"
            onClick={() => onChangeTab("androidide")}
            className={`px-3 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 flex items-center gap-1.5 transition-colors cursor-pointer ${
              activeTab === "androidide"
                ? "bg-cyan-500/15 text-cyan-300 border border-cyan-400/40"
                : "text-slate-400 hover:text-slate-200"
            }`}
          >
            <FileCode2 className="w-3.5 h-3.5" />
            <span>Projeto AndroidIDE</span>
          </button>
          <button
            type="button"
            onClick={() => onChangeTab("criterios")}
            className={`px-3 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 flex items-center gap-1.5 transition-colors cursor-pointer ${
              activeTab === "criterios"
                ? "bg-cyan-500/15 text-cyan-300 border border-cyan-400/40"
                : "text-slate-400 hover:text-slate-200"
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            <span>Critérios & Arquitetura</span>
          </button>
        </div>

        <button
          type="button"
          onClick={() => onToggleEditMode(!isEditMode)}
          className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap shrink-0 flex items-center gap-1.5 border transition-colors cursor-pointer ${
            isEditMode
              ? "bg-amber-500/15 border-amber-400/60 text-amber-200"
              : "bg-slate-900 border-slate-700 text-slate-200 hover:border-cyan-400/50"
          }`}
        >
          <Pencil className="w-3.5 h-3.5" />
          <span>{isEditMode ? "Sair da Edição" : "Editar Atalhos"}</span>
        </button>
      </div>

      {/* Tab Content */}
      <div className="flex-1 overflow-y-auto p-6 space-y-8 hud-scroll">
        {activeTab === "personalizacao" && (
          <>
            {/* Section 0: Exclusive Launcher Theme Switcher */}
            <section className="space-y-3.5 pb-6 border-b border-slate-800/80">
              <div>
                <h2 className="font-display text-base font-semibold text-slate-100 flex items-center gap-2">
                  <Sparkles className="w-4 h-4 text-[#00E5FF]" />
                  <span>Tema Exclusivo do Launcher</span>
                </h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Alterne instantaneamente entre a HUD padrão, o Tema Luna — Entre Luz e Sombra e o Tema Maya — Holographic Presence.
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                {LAUNCHER_THEMES.map((theme) => {
                  const activeTheme = state.themeId || "default";
                  const selected = activeTheme === theme.id;
                  const isLunaCard = theme.id === "luna-entre-luz-e-sombra";
                  const isMayaCard = theme.id === "maya-holographic-presence";
                  return (
                    <button
                      key={theme.id}
                      type="button"
                      onClick={() => onSelectTheme(theme.id)}
                      className={`p-3.5 rounded-xl text-left border transition-colors cursor-pointer ${
                        selected
                          ? isMayaCard
                            ? "bg-[#F5B942]/10 border-[#F5B942]/75 shadow-[0_0_20px_rgba(245,185,66,0.14)]"
                            : isLunaCard
                              ? "bg-[#00E5FF]/10 border-[#00E5FF]/70 shadow-[0_0_20px_rgba(0,229,255,0.12)]"
                              : "bg-cyan-500/10 border-cyan-400/60"
                          : "bg-[#0B111C] border-slate-800 hover:border-slate-700"
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-semibold text-slate-100">
                          {theme.name}
                        </span>
                        <span
                          className={`text-[10px] font-mono-tabular shrink-0 ${
                            selected
                              ? isMayaCard
                                ? "text-[#F5B942]"
                                : "text-[#00E5FF]"
                              : "text-slate-400"
                          }`}
                        >
                          {selected ? "Ativo" : theme.badge}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 mt-1 leading-snug">
                        {theme.subtitle}
                      </p>
                    </button>
                  );
                })}
              </div>
            </section>

            {/* Section 1: Wallpaper & Transparency */}
            <section className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="font-display text-base font-semibold text-slate-100">
                    Papel de Parede e Transparência HUD
                  </h2>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Os painéis HUD mantêm transparência para exibir o papel de parede ao fundo.
                  </p>
                </div>
                <label className="px-3 py-2 rounded-lg bg-[#0D1523] border border-slate-700 hover:border-cyan-400/50 text-xs font-medium text-slate-200 flex items-center gap-1.5 cursor-pointer whitespace-nowrap shrink-0">
                  <ImagePlus className="w-3.5 h-3.5 text-cyan-300" />
                  <span>Imagem Própria</span>
                  <input
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={handleWallpaperUpload}
                  />
                </label>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {WALLPAPERS.map((wp) => {
                  const selected = state.wallpaperId === wp.id;
                  return (
                    <button
                      key={wp.id}
                      type="button"
                      onClick={() => onSelectWallpaper(wp.id)}
                      className={`p-3.5 rounded-xl text-left border transition-colors cursor-pointer ${
                        selected
                          ? "bg-cyan-500/10 border-cyan-400/60"
                          : "bg-[#0B111C] border-slate-800 hover:border-slate-700"
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-semibold text-slate-100">
                          {wp.name}
                        </span>
                        {selected && (
                          <span className="text-[11px] font-mono-tabular text-cyan-300">
                            Ativo
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-slate-400 mt-1">{wp.description}</p>
                    </button>
                  );
                })}
              </div>

              <div className="p-4 rounded-xl bg-[#0B111C] border border-slate-800 space-y-2.5">
                <div className="flex items-center justify-between">
                  <label
                    htmlFor="hud-opacity-range"
                    className="text-xs font-medium text-slate-200"
                  >
                    Opacidade dos painéis HUD
                  </label>
                  <span className="text-xs font-mono-tabular text-cyan-300">
                    {state.panelOpacity}%
                  </span>
                </div>
                <input
                  id="hud-opacity-range"
                  type="range"
                  min={25}
                  max={95}
                  value={state.panelOpacity}
                  onChange={(e) => onChangeOpacity(Number(e.target.value))}
                  className="w-full accent-cyan-400 cursor-pointer"
                />
              </div>
            </section>

            {/* Section 2: Icon Pack Manager */}
            <IconPackManagerPanel
              activePackId={state.iconPack}
              activeThemeId={
                state.themeId === "maya-holographic-presence" ||
                state.wallpaperId === "maya-holographic-presence"
                  ? "maya-holographic-presence"
                  : state.themeId === "luna-entre-luz-e-sombra" ||
                      state.wallpaperId === "luna-shadow-light"
                    ? "luna-entre-luz-e-sombra"
                    : "default"
              }
              isLunaThemeActive={
                state.themeId === "luna-entre-luz-e-sombra" ||
                state.wallpaperId === "luna-shadow-light"
              }
              onSelectIconPack={onSelectIconPack}
            />

            {/* Section 2.5: Custom Visual Transitions (Preview before apply) */}
            <section className="space-y-4 pt-6 border-t border-slate-800/80">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="font-display text-base font-semibold text-slate-100 flex items-center gap-2">
                    <Sparkles className="w-4 h-4 text-cyan-300" />
                    <span>Transições Visuais HILM</span>
                  </h2>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Escolha o estilo e a velocidade com prévia ao vivo antes de aplicar.
                  </p>
                </div>

                <label className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-[#0D1523] border border-slate-700 text-xs font-medium text-slate-200 cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={draftTransition.enabled}
                    onChange={(e) => {
                      const next = { ...draftTransition, enabled: e.target.checked };
                      setDraftTransition(next);
                      setTransitionAppliedNotice(false);
                      setPreviewReplaySeq((s) => s + 1);
                    }}
                    className="accent-cyan-400 cursor-pointer"
                  />
                  <span>{draftTransition.enabled ? "Efeitos ativos" : "Efeitos desativados"}</span>
                </label>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {TRANSITION_EFFECTS.map((item) => {
                  const isPreviewSelected = draftTransition.effect === item.id;
                  const isSavedActive =
                    state.transition.enabled && state.transition.effect === item.id;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => {
                        setDraftTransition((prev) => ({
                          ...prev,
                          enabled: true,
                          effect: item.id,
                        }));
                        setTransitionAppliedNotice(false);
                        setPreviewReplaySeq((s) => s + 1);
                      }}
                      className={`p-3 rounded-xl text-left border transition-colors cursor-pointer ${
                        isPreviewSelected && draftTransition.enabled
                          ? "bg-cyan-500/10 border-cyan-400/60"
                          : "bg-[#0B111C] border-slate-800 hover:border-slate-700"
                      } ${!draftTransition.enabled ? "opacity-65" : ""}`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-semibold text-slate-100">
                          {item.name}
                        </span>
                        <span className="text-[10px] font-mono-tabular shrink-0">
                          {isSavedActive ? (
                            <span className="text-emerald-400">Aplicado</span>
                          ) : isPreviewSelected && draftTransition.enabled ? (
                            <span className="text-cyan-300">Em prévia</span>
                          ) : null}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-400 mt-1 leading-snug">
                        {item.description}
                      </p>
                    </button>
                  );
                })}
              </div>

              <div className="p-3.5 rounded-xl bg-[#0B111C] border border-slate-800 space-y-2">
                <div className="flex items-center justify-between">
                  <label
                    htmlFor="hilm-transition-duration"
                    className="text-xs font-medium text-slate-200"
                  >
                    Duração da animação
                  </label>
                  <span className="text-xs font-mono-tabular text-cyan-300">
                    {draftTransition.durationMs} ms
                  </span>
                </div>
                <input
                  id="hilm-transition-duration"
                  type="range"
                  min={180}
                  max={600}
                  step={20}
                  disabled={!draftTransition.enabled}
                  value={draftTransition.durationMs}
                  onChange={(e) => {
                    setDraftTransition((prev) => ({
                      ...prev,
                      durationMs: Number(e.target.value),
                    }));
                    setTransitionAppliedNotice(false);
                    setPreviewReplaySeq((s) => s + 1);
                  }}
                  className="w-full accent-cyan-400 cursor-pointer disabled:opacity-50"
                />
                <div className="flex justify-between text-[10px] text-slate-500">
                  <span>Rápida (180 ms)</span>
                  <span>Equilibrada (280 ms)</span>
                  <span>Suave (600 ms)</span>
                </div>
              </div>

              <HilmTransitionPreview
                config={draftTransition}
                replayTrigger={previewReplaySeq}
              />

              <div className="flex flex-wrap items-center justify-between gap-3">
                <span className="text-[11px] text-slate-400">
                  {transitionAppliedNotice
                    ? "Transição aplicada e salva com sucesso."
                    : draftTransition.enabled !== state.transition.enabled ||
                        draftTransition.effect !== state.transition.effect ||
                        draftTransition.durationMs !== state.transition.durationMs
                      ? "Alterações em prévia — clique em Aplicar para salvar no launcher."
                      : !state.transition.enabled
                        ? "Efeitos visuais desativados no momento."
                        : "Preferência atual salva."}
                </span>

                <div className="flex items-center gap-2">
                  {draftTransition.enabled && (
                    <button
                      type="button"
                      onClick={() => {
                        const disabled = { ...draftTransition, enabled: false };
                        setDraftTransition(disabled);
                        onUpdateTransition(disabled);
                        setTransitionAppliedNotice(true);
                      }}
                      className="px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 hover:border-amber-400/50 text-xs font-medium text-slate-300 cursor-pointer"
                    >
                      Desativar Efeitos
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      onUpdateTransition(draftTransition);
                      setTransitionAppliedNotice(true);
                    }}
                    className="px-4 py-2 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 text-xs font-semibold cursor-pointer transition-colors"
                  >
                    Aplicar Transição
                  </button>
                </div>
              </div>
            </section>

            {/* Section 3: Persistent Shortcut Mapping Overview */}
            <section className="space-y-4 pt-6 border-t border-slate-800/80">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="font-display text-base font-semibold text-slate-100">
                    Organização Atual da Tela Inicial
                  </h2>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Salva automaticamente no navegador e espelhada em <code className="text-cyan-300">ShortcutStorage.kt</code>.
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={handleExportLayoutJson}
                    className="px-3 py-2 rounded-lg bg-[#0D1523] border border-slate-700 hover:border-cyan-400/50 text-xs font-medium text-cyan-200 flex items-center gap-1.5 cursor-pointer whitespace-nowrap shrink-0"
                  >
                    <Download className="w-3.5 h-3.5 text-cyan-300" />
                    <span>Exportar .JSON</span>
                  </button>
                  <label className="px-3 py-2 rounded-lg bg-[#0D1523] border border-slate-700 hover:border-cyan-400/50 text-xs font-medium text-slate-200 flex items-center gap-1.5 cursor-pointer whitespace-nowrap shrink-0">
                    <Upload className="w-3.5 h-3.5 text-cyan-300" />
                    <span>Importar .JSON</span>
                    <input
                      type="file"
                      accept=".json,application/json"
                      className="hidden"
                      onChange={handleImportLayoutJson}
                    />
                  </label>
                  <button
                    type="button"
                    onClick={onResetState}
                    className="px-3 py-2 rounded-lg bg-[#0D1523] border border-slate-700 hover:border-amber-400/50 text-xs font-medium text-amber-300 flex items-center gap-1.5 cursor-pointer whitespace-nowrap shrink-0"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                    <span>Restaurar Padrão</span>
                  </button>
                </div>
              </div>

              {layoutTransferStatus && (
                <div className="px-3.5 py-2 rounded-lg bg-cyan-950/30 border border-cyan-500/30 text-xs text-cyan-200">
                  {layoutTransferStatus}
                </div>
              )}

              <div className="overflow-hidden rounded-xl border border-slate-800 bg-[#0A101A]">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-slate-800 text-slate-400">
                      <th className="py-2.5 px-3.5 font-medium">Posição HUD</th>
                      <th className="py-2.5 px-3.5 font-medium">Aplicativo</th>
                      <th className="py-2.5 px-3.5 font-medium">Pacote Android</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/70">
                    {SLOT_IDS.map((slotId) => {
                      const slot = state.slots[slotId];
                      const app = slot?.packageName
                        ? INSTALLED_APPS_CATALOG.find(
                            (a) => a.packageName === slot.packageName
                          )
                        : undefined;
                      return (
                        <tr key={slotId} className="text-slate-200">
                          <td className="py-2.5 px-3.5 font-medium">{SLOT_LABELS[slotId]}</td>
                          <td className="py-2.5 px-3.5">
                            {app ? app.label : <span className="text-slate-500">Vazio</span>}
                          </td>
                          <td className="py-2.5 px-3.5 font-mono-tabular text-[11px] text-slate-400">
                            {app ? app.packageName : "—"}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </section>
          </>
        )}

        {activeTab === "androidide" && (
          <div className="space-y-6">
            {/* Build Spec Summary */}
            <div className="flex flex-wrap items-center justify-between gap-4 p-4 rounded-xl bg-[#0B111C] border border-slate-800">
              <div className="space-y-1">
                <h2 className="font-display text-base font-semibold text-slate-100">
                  Projeto Android Nativo (Kotlin + ViewBinding)
                </h2>
                <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-mono-tabular text-slate-400">
                  <span>Gradle {treeData?.gradleVersion || "8.2"}</span>
                  <span aria-hidden="true">·</span>
                  <span>AGP {treeData?.agpVersion || "8.1.4"}</span>
                  <span aria-hidden="true">·</span>
                  <span>JDK {treeData?.jdkVersion || 17}</span>
                  <span aria-hidden="true">·</span>
                  <span>Compile/Target SDK {treeData?.compileSdk || 33}</span>
                  <span aria-hidden="true">·</span>
                  <span>Min SDK {treeData?.minSdk || 23}</span>
                </div>
              </div>

              <a
                href="/api/android-project/download-zip"
                download="NossoUniversoLauncher-AndroidIDE.zip"
                className="px-4 py-2.5 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 text-xs font-semibold flex items-center gap-2 whitespace-nowrap shrink-0 transition-colors"
              >
                <Download className="w-4 h-4" />
                <span>Baixar ZIP AndroidIDE</span>
              </a>
            </div>

            {treeError ? (
              <div className="p-4 rounded-xl border border-rose-500/40 bg-rose-950/20 text-xs text-rose-200">
                {treeError}
              </div>
            ) : (
              <div className="grid grid-cols-1 xl:grid-cols-12 gap-4 min-h-[460px]">
                {/* File Tree */}
                <div className="xl:col-span-5 rounded-xl bg-[#0A101A] border border-slate-800 flex flex-col overflow-hidden">
                  <div className="px-3.5 py-2.5 border-b border-slate-800 flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-200 flex items-center gap-1.5">
                      <FolderOpen className="w-3.5 h-3.5 text-cyan-300" />
                      <span>Arquivos do Projeto</span>
                    </span>
                    <span className="text-[11px] font-mono-tabular text-slate-400">
                      {treeData?.files.length ?? 0} itens
                    </span>
                  </div>
                  <div className="flex-1 max-h-[430px] overflow-y-auto p-1.5 space-y-0.5 hud-scroll">
                    {treeData?.files.map((file) => {
                      const active = selectedFilePath === file.path;
                      return (
                        <button
                          key={file.path}
                          type="button"
                          onClick={() => setSelectedFilePath(file.path)}
                          className={`w-full px-2.5 py-1.5 rounded-md text-left text-[11px] font-mono-tabular flex items-center justify-between gap-2 transition-colors cursor-pointer ${
                            active
                              ? "bg-cyan-500/15 text-cyan-200"
                              : "text-slate-300 hover:bg-slate-800/60"
                          }`}
                        >
                          <span className="truncate">{file.path}</span>
                          <span className="text-[10px] text-slate-500 shrink-0">
                            {(file.size / 1024).toFixed(1)}K
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Source Viewer */}
                <div className="xl:col-span-7 rounded-xl bg-[#060A11] border border-slate-800 flex flex-col overflow-hidden">
                  <div className="px-3.5 py-2.5 border-b border-slate-800 bg-[#0A101A] flex items-center justify-between gap-2">
                    <span className="text-[11px] font-mono-tabular text-cyan-300 truncate">
                      {selectedFilePath}
                    </span>
                  </div>
                  <pre className="flex-1 max-h-[430px] overflow-auto p-4 text-[11.5px] leading-relaxed font-mono-tabular text-slate-200 hud-scroll">
                    {loadingFile ? "Carregando arquivo..." : fileContent}
                  </pre>
                </div>
              </div>
            )}
          </div>
        )}

        {activeTab === "criterios" && (
          <div className="space-y-6">
            <div>
              <h2 className="font-display text-base font-semibold text-slate-100">
                Critérios de Aceitação e Escopo Técnico
              </h2>
              <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                Verificação objetiva do cumprimento da especificação do Projeto Nosso Universo e da limitação inerente ao ambiente web de preview.
              </p>
            </div>

            <div className="space-y-2.5">
              {ACCEPTANCE_CRITERIA.map((item) => (
                <div
                  key={item.id}
                  className="p-4 rounded-xl bg-[#0B111C] border border-slate-800 flex items-start gap-3"
                >
                  {item.status === "implemented" ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                  ) : (
                    <Clock className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="text-xs font-semibold text-slate-100">
                        {item.label}
                      </span>
                      <span
                        className={`text-[11px] font-mono-tabular ${
                          item.status === "implemented"
                            ? "text-emerald-400"
                            : "text-amber-300"
                        }`}
                      >
                        {item.status === "implemented"
                          ? "Implementado"
                          : "Requer AndroidIDE / Dispositivo"}
                      </span>
                    </div>
                    <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                      {item.detail}
                    </p>
                  </div>
                </div>
              ))}
            </div>

            <div className="p-4 rounded-xl bg-[#0A101A] border border-slate-800 space-y-2">
              <h3 className="font-display text-sm font-semibold text-cyan-300">
                Instruções para Compilação no AndroidIDE
              </h3>
              <ol className="list-decimal list-inside space-y-1.5 text-xs text-slate-300 leading-relaxed">
                <li>
                  Baixe o arquivo <code className="text-cyan-300">NossoUniversoLauncher-AndroidIDE.zip</code> na aba Projeto AndroidIDE ou pelo botão no topo.
                </li>
                <li>
                  Descompacte o conteúdo e abra a pasta <code className="text-cyan-300">NossoUniversoLauncher</code> no AndroidIDE com JDK 17 configurado.
                </li>
                <li>
                  Confirme que o projeto sincroniza com o Wrapper <code className="text-cyan-300">gradle-8.2-bin.zip</code> e AGP <code className="text-cyan-300">8.1.4</code>.
                </li>
                <li>
                  Compile o APK e instale no smartphone; ao pressionar o botão Início, escolha <strong>Nosso Universo</strong> como launcher padrão.
                </li>
              </ol>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
