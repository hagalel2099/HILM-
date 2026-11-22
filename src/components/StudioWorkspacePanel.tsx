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
} from "lucide-react";
import type {
  AndroidProjectTreeResponse,
  IconPackId,
  LauncherPersistedState,
  WallpaperId,
} from "../types/launcher";
import {
  ACCEPTANCE_CRITERIA,
  INSTALLED_APPS_CATALOG,
  SLOT_IDS,
  SLOT_LABELS,
  WALLPAPERS,
} from "../data/launcherCatalog";
import { IconPackManagerPanel } from "./IconPackManagerPanel";

export type WorkspaceTab = "personalizacao" | "androidide" | "criterios";

interface StudioWorkspacePanelProps {
  activeTab: WorkspaceTab;
  onChangeTab: (tab: WorkspaceTab) => void;
  state: LauncherPersistedState;
  isEditMode: boolean;
  onToggleEditMode: (active: boolean) => void;
  onSelectIconPack: (pack: IconPackId) => void;
  onSelectWallpaper: (wallpaperId: WallpaperId, customUrl?: string) => void;
  onChangeOpacity: (opacity: number) => void;
  onResetState: () => void;
}

export function StudioWorkspacePanel({
  activeTab,
  onChangeTab,
  state,
  isEditMode,
  onToggleEditMode,
  onSelectIconPack,
  onSelectWallpaper,
  onChangeOpacity,
  onResetState,
}: StudioWorkspacePanelProps) {
  const [treeData, setTreeData] = useState<AndroidProjectTreeResponse | null>(null);
  const [treeError, setTreeError] = useState<string | null>(null);
  const [selectedFilePath, setSelectedFilePath] = useState<string>(
    "app/src/main/java/com/nossouniverso/launcher/ui/home/HomeActivity.kt"
  );
  const [fileContent, setFileContent] = useState<string>("");
  const [loadingFile, setLoadingFile] = useState(false);

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
              onSelectIconPack={onSelectIconPack}
            />

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
                <button
                  type="button"
                  onClick={onResetState}
                  className="px-3 py-2 rounded-lg bg-[#0D1523] border border-slate-700 hover:border-amber-400/50 text-xs font-medium text-amber-300 flex items-center gap-1.5 cursor-pointer whitespace-nowrap shrink-0"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Restaurar Padrão</span>
                </button>
              </div>

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
