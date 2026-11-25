import { useEffect, useState } from "react";
import { Download } from "lucide-react";
import { DEFAULT_LAUNCHER_STATE, TRANSITION_EFFECTS } from "./data/launcherCatalog";
import type {
  IconPackId,
  InstalledAppItem,
  LauncherPersistedState,
  LauncherThemeId,
  SlotId,
  TransitionConfig,
  TransitionEffectId,
  WallpaperId,
} from "./types/launcher";
import { LauncherPhonePreview } from "./components/LauncherPhonePreview";
import {
  StudioWorkspacePanel,
  type WorkspaceTab,
} from "./components/StudioWorkspacePanel";

const STORAGE_KEY = "nosso_universo_launcher_state_v1";
const VALID_TRANSITION_EFFECTS = new Set<TransitionEffectId>(
  TRANSITION_EFFECTS.map((item) => item.id)
);

function normalizeTransitionConfig(
  raw?: Partial<TransitionConfig> | null,
  fallback: TransitionConfig = DEFAULT_LAUNCHER_STATE.transition
): TransitionConfig {
  if (!raw || typeof raw !== "object") return fallback;
  const effect =
    raw.effect && VALID_TRANSITION_EFFECTS.has(raw.effect)
      ? raw.effect
      : fallback.effect;
  const durationMs =
    typeof raw.durationMs === "number" && Number.isFinite(raw.durationMs)
      ? Math.max(180, Math.min(600, Math.round(raw.durationMs)))
      : fallback.durationMs;
  const enabled = typeof raw.enabled === "boolean" ? raw.enabled : fallback.enabled;
  return { enabled, effect, durationMs };
}

function loadInitialState(): LauncherPersistedState {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_LAUNCHER_STATE;
    const parsed = JSON.parse(raw) as Partial<LauncherPersistedState>;
    return {
      slots: { ...DEFAULT_LAUNCHER_STATE.slots, ...(parsed.slots || {}) },
      themeId:
        parsed.themeId === "luna-entre-luz-e-sombra"
          ? "luna-entre-luz-e-sombra"
          : "default",
      iconPack: parsed.iconPack || DEFAULT_LAUNCHER_STATE.iconPack,
      wallpaperId: parsed.wallpaperId || DEFAULT_LAUNCHER_STATE.wallpaperId,
      customWallpaperUrl: parsed.customWallpaperUrl,
      panelOpacity:
        typeof parsed.panelOpacity === "number"
          ? parsed.panelOpacity
          : DEFAULT_LAUNCHER_STATE.panelOpacity,
      recentPackages: Array.isArray(parsed.recentPackages)
        ? parsed.recentPackages
        : DEFAULT_LAUNCHER_STATE.recentPackages,
      transition: normalizeTransitionConfig(parsed.transition),
    };
  } catch {
    return DEFAULT_LAUNCHER_STATE;
  }
}

export default function App() {
  const [state, setState] = useState<LauncherPersistedState>(loadInitialState);
  const [isEditMode, setIsEditMode] = useState(false);
  const [workspaceTab, setWorkspaceTab] = useState<WorkspaceTab>("personalizacao");
  const [mobileSection, setMobileSection] = useState<"launcher" | WorkspaceTab>(
    "launcher"
  );

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // Ignore storage quota errors on large custom images
    }
  }, [state]);

  const handleAssignSlot = (slotId: SlotId, packageName: string) => {
    setState((prev) => ({
      ...prev,
      slots: {
        ...prev.slots,
        [slotId]: {
          slotId,
          packageName,
          customIconDataUrl: undefined,
          customLabel: undefined,
        },
      },
    }));
  };

  const handleRemoveSlot = (slotId: SlotId) => {
    setState((prev) => ({
      ...prev,
      slots: {
        ...prev.slots,
        [slotId]: { slotId, packageName: null },
      },
    }));
  };

  const handleSwapSlots = (a: SlotId, b: SlotId) => {
    if (a === b) return;
    setState((prev) => {
      const slotA = prev.slots[a] || { slotId: a, packageName: null };
      const slotB = prev.slots[b] || { slotId: b, packageName: null };
      return {
        ...prev,
        slots: {
          ...prev.slots,
          [a]: { ...slotB, slotId: a },
          [b]: { ...slotA, slotId: b },
        },
      };
    });
  };

  const handleSetCustomIcon = (slotId: SlotId, dataUrl: string) => {
    setState((prev) => ({
      ...prev,
      slots: {
        ...prev.slots,
        [slotId]: {
          ...(prev.slots[slotId] || { slotId, packageName: null }),
          customIconDataUrl: dataUrl,
        },
      },
    }));
  };

  const handleRenameSlot = (slotId: SlotId, customLabel: string) => {
    const trimmed = customLabel.trim();
    setState((prev) => ({
      ...prev,
      slots: {
        ...prev.slots,
        [slotId]: {
          ...(prev.slots[slotId] || { slotId, packageName: null }),
          customLabel: trimmed.length > 0 ? trimmed : undefined,
        },
      },
    }));
  };

  const handleLaunchApp = (app: InstalledAppItem) => {
    setState((prev) => ({
      ...prev,
      recentPackages: [
        app.packageName,
        ...prev.recentPackages.filter((p) => p !== app.packageName),
      ].slice(0, 8),
    }));
  };

  const handleSelectTheme = (themeId: LauncherThemeId) => {
    setState((prev) => {
      if (themeId === "luna-entre-luz-e-sombra") {
        return {
          ...prev,
          themeId,
          wallpaperId: "luna-shadow-light",
          iconPack: "luna-holographic",
          panelOpacity: 40,
        };
      }
      return {
        ...prev,
        themeId: "default",
        wallpaperId:
          prev.wallpaperId === "luna-shadow-light" ? "ref-horizon" : prev.wallpaperId,
        iconPack:
          prev.iconPack === "luna-holographic" ? "hilm-artifacts" : prev.iconPack,
        panelOpacity: prev.panelOpacity <= 45 ? 78 : prev.panelOpacity,
      };
    });
  };

  const handleSelectIconPack = (iconPack: IconPackId) => {
    setState((prev) => ({ ...prev, iconPack }));
  };

  const handleSelectWallpaper = (wallpaperId: WallpaperId, customUrl?: string) => {
    setState((prev) => ({
      ...prev,
      wallpaperId,
      themeId:
        wallpaperId === "luna-shadow-light"
          ? "luna-entre-luz-e-sombra"
          : prev.themeId,
      customWallpaperUrl:
        wallpaperId === "custom" ? customUrl : prev.customWallpaperUrl,
    }));
  };

  const handleChangeOpacity = (panelOpacity: number) => {
    setState((prev) => ({ ...prev, panelOpacity }));
  };

  const handleUpdateTransition = (transition: TransitionConfig) => {
    setState((prev) => ({
      ...prev,
      transition: normalizeTransitionConfig(transition, prev.transition),
    }));
  };

  const handleResetState = () => {
    setState(DEFAULT_LAUNCHER_STATE);
    setIsEditMode(false);
  };

  const handleImportState = (imported: Partial<LauncherPersistedState>) => {
    setState((prev) => ({
      slots: { ...DEFAULT_LAUNCHER_STATE.slots, ...(imported.slots || prev.slots) },
      themeId:
        imported.themeId === "luna-entre-luz-e-sombra"
          ? "luna-entre-luz-e-sombra"
          : imported.themeId === "default"
            ? "default"
            : prev.themeId,
      iconPack: imported.iconPack || prev.iconPack,
      wallpaperId: imported.wallpaperId || prev.wallpaperId,
      customWallpaperUrl: imported.customWallpaperUrl ?? prev.customWallpaperUrl,
      panelOpacity:
        typeof imported.panelOpacity === "number"
          ? Math.max(25, Math.min(95, imported.panelOpacity))
          : prev.panelOpacity,
      recentPackages: Array.isArray(imported.recentPackages)
        ? imported.recentPackages.slice(0, 8)
        : prev.recentPackages,
      transition: normalizeTransitionConfig(imported.transition, prev.transition),
    }));
  };

  const handleNavClick = (target: "launcher" | WorkspaceTab) => {
    setMobileSection(target);
    if (target !== "launcher") {
      setWorkspaceTab(target);
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#05080D] text-slate-100">
      {/* Top Bar Contract: 3 zones separated by gap-8 */}
      <header className="sticky top-0 z-40 border-b border-slate-800/90 bg-[#060A11]/95 backdrop-blur-md">
        <div className="max-w-[1440px] mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-8">
          <a
            href="#top"
            className="font-display text-lg font-bold tracking-wide text-slate-100 whitespace-nowrap shrink-0"
          >
            Nosso Universo
          </a>

          <nav
            aria-label="Navegação principal"
            className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-400"
          >
            <button
              type="button"
              onClick={() => handleNavClick("launcher")}
              className={`hover:text-slate-100 transition-colors whitespace-nowrap shrink-0 cursor-pointer md:hidden ${
                mobileSection === "launcher" ? "text-cyan-300" : ""
              }`}
            >
              Launcher
            </button>
            <button
              type="button"
              onClick={() => handleNavClick("personalizacao")}
              className={`hover:text-slate-100 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                workspaceTab === "personalizacao"
                  ? "text-cyan-300 underline underline-offset-8 decoration-cyan-400/70"
                  : ""
              }`}
            >
              Personalização
            </button>
            <button
              type="button"
              onClick={() => handleNavClick("androidide")}
              className={`hover:text-slate-100 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                workspaceTab === "androidide"
                  ? "text-cyan-300 underline underline-offset-8 decoration-cyan-400/70"
                  : ""
              }`}
            >
              Projeto AndroidIDE
            </button>
            <button
              type="button"
              onClick={() => handleNavClick("criterios")}
              className={`hover:text-slate-100 transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                workspaceTab === "criterios"
                  ? "text-cyan-300 underline underline-offset-8 decoration-cyan-400/70"
                  : ""
              }`}
            >
              Critérios
            </button>
          </nav>

          <div className="flex items-center shrink-0">
            <a
              href="/api/android-project/download-zip"
              download="NossoUniversoLauncher-AndroidIDE.zip"
              className="px-4 py-2 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 text-xs font-semibold flex items-center gap-2 whitespace-nowrap shrink-0 transition-colors"
            >
              <Download className="w-4 h-4" />
              <span>Baixar ZIP</span>
            </a>
          </div>
        </div>

        {/* Mobile Section Switcher */}
        <div className="flex md:hidden items-center gap-1 px-4 pb-2.5 overflow-x-auto">
          {(
            [
              { id: "launcher", label: "Launcher" },
              { id: "personalizacao", label: "Personalização" },
              { id: "androidide", label: "AndroidIDE" },
              { id: "criterios", label: "Critérios" },
            ] as const
          ).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => handleNavClick(tab.id)}
              className={`px-3 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 transition-colors ${
                mobileSection === tab.id
                  ? "bg-cyan-500/15 text-cyan-300 border border-cyan-400/40"
                  : "text-slate-400 bg-slate-900/70"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </header>

      {/* Main Split Studio Layout */}
      <main
        id="top"
        className="flex-1 max-w-[1440px] w-full mx-auto px-4 sm:px-6 py-6 grid grid-cols-1 lg:grid-cols-12 gap-6 items-start"
      >
        {/* Left Stage: Interactive Smartphone Launcher */}
        <section
          aria-label="Preview interativo do Nosso Universo Launcher"
          className={`lg:col-span-5 xl:col-span-4 flex flex-col items-center ${
            mobileSection === "launcher" ? "flex" : "hidden lg:flex"
          }`}
        >
          <LauncherPhonePreview
            state={state}
            isEditMode={isEditMode}
            onToggleEditMode={setIsEditMode}
            onAssignSlot={handleAssignSlot}
            onRemoveSlot={handleRemoveSlot}
            onSwapSlots={handleSwapSlots}
            onSetCustomIcon={handleSetCustomIcon}
            onRenameSlot={handleRenameSlot}
            onLaunchApp={handleLaunchApp}
          />
          <p className="mt-3 text-xs text-slate-400 text-center max-w-[340px]">
            Toque nas abas laterais para abrir as gavetas esquerda/direita sem cobrir o núcleo HILM; deslize para cima para abrir todos os apps.
          </p>
        </section>

        {/* Right Stage: Customization, Real AndroidIDE Project & Acceptance Criteria */}
        <section
          aria-label="Estúdio de configuração e código AndroidIDE"
          className={`lg:col-span-7 xl:col-span-8 lg:h-[780px] ${
            mobileSection !== "launcher" ? "block" : "hidden lg:block"
          }`}
        >
          <StudioWorkspacePanel
            activeTab={workspaceTab}
            onChangeTab={(tab) => {
              setWorkspaceTab(tab);
              setMobileSection(tab);
            }}
            state={state}
            isEditMode={isEditMode}
            onToggleEditMode={setIsEditMode}
            onSelectTheme={handleSelectTheme}
            onSelectIconPack={handleSelectIconPack}
            onSelectWallpaper={handleSelectWallpaper}
            onChangeOpacity={handleChangeOpacity}
            onUpdateTransition={handleUpdateTransition}
            onImportState={handleImportState}
            onResetState={handleResetState}
          />
        </section>
      </main>
    </div>
  );
}
