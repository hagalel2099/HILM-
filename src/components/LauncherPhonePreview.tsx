import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  Battery,
  Check,
  CheckCircle2,
  Circle,
  Download,
  GripVertical,
  ImagePlus,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  Signal,
  Square,
  Triangle,
  Wifi,
  X,
} from "lucide-react";
import type {
  InstalledAppItem,
  LauncherPersistedState,
  LauncherUpdateCheckResponse,
  SlotId,
} from "../types/launcher";
import {
  INSTALLED_APPS_CATALOG,
  SLOT_LABELS,
} from "../data/launcherCatalog";
import { AppGlyph } from "./AppGlyph";
import { AppDrawerModal } from "./AppDrawerModal";
import { HilmCore } from "./HilmCore";
import { HudSvgFrame } from "./HudSvgFrame";

interface LauncherPhonePreviewProps {
  state: LauncherPersistedState;
  isEditMode: boolean;
  onToggleEditMode: (active: boolean) => void;
  onAssignSlot: (slotId: SlotId, packageName: string) => void;
  onRemoveSlot: (slotId: SlotId) => void;
  onSwapSlots: (a: SlotId, b: SlotId) => void;
  onSetCustomIcon: (slotId: SlotId, dataUrl: string) => void;
  onLaunchApp: (app: InstalledAppItem) => void;
}

const PLATE_SLOTS: SlotId[] = [
  "plate_top_left",
  "plate_top_right",
  "plate_bottom_left",
  "plate_bottom_right",
];

const SIDE_DOCK_LEFT: SlotId[] = ["dock_1", "dock_2"];
const SIDE_DOCK_RIGHT: SlotId[] = ["dock_4", "dock_5"];

export function LauncherPhonePreview({
  state,
  isEditMode,
  onToggleEditMode,
  onAssignSlot,
  onRemoveSlot,
  onSwapSlots,
  onSetCustomIcon,
  onLaunchApp,
}: LauncherPhonePreviewProps) {
  const [clockText, setClockText] = useState("12:43");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerTargetSlot, setDrawerTargetSlot] = useState<SlotId | null>(null);
  const [selectedSwapSource, setSelectedSwapSource] = useState<SlotId | null>(null);
  const [draggedSlot, setDraggedSlot] = useState<SlotId | null>(null);
  const [activeAppWindow, setActiveAppWindow] = useState<InstalledAppItem | null>(null);
  const [showCoreUpdatePopover, setShowCoreUpdatePopover] = useState(false);
  const [updatePhase, setUpdatePhase] = useState<
    "idle" | "checking" | "up_to_date" | "available" | "downloading" | "downloaded" | "error"
  >("idle");
  const [updateInfo, setUpdateInfo] = useState<LauncherUpdateCheckResponse | null>(null);
  const [updateMessage, setUpdateMessage] = useState("Versão instalada: v1.0.0");
  const [downloadPercent, setDownloadPercent] = useState<number | null>(null);
  const [showRecents, setShowRecents] = useState(false);
  const customIconInputRef = useRef<HTMLInputElement>(null);
  const [customIconSlotTarget, setCustomIconSlotTarget] = useState<SlotId | null>(null);

  const INSTALLED_VERSION = "1.0.0";
  const INSTALLED_VERSION_CODE = 1;

  useEffect(() => {
    const updateClock = () => {
      const now = new Date();
      const hh = String(now.getHours()).padStart(2, "0");
      const mm = String(now.getMinutes()).padStart(2, "0");
      setClockText(`${hh}:${mm}`);
    };
    updateClock();
    const id = window.setInterval(updateClock, 15000);
    return () => window.clearInterval(id);
  }, []);

  const appMap = useMemo(() => {
    const map = new Map<string, InstalledAppItem>();
    INSTALLED_APPS_CATALOG.forEach((a) => map.set(a.packageName, a));
    return map;
  }, []);

  const wallpaperStyle = useMemo(() => {
    if (state.wallpaperId === "custom" && state.customWallpaperUrl) {
      return {
        backgroundImage: `url(${state.customWallpaperUrl})`,
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
      };
    }
    switch (state.wallpaperId) {
      case "hilm-void":
        return {
          backgroundImage:
            "radial-gradient(circle at 20% 25%, rgba(234, 88, 12, 0.22), transparent 45%), radial-gradient(circle at 82% 70%, rgba(139, 92, 246, 0.26), transparent 50%), linear-gradient(180deg, #04060B 0%, #090D18 100%)",
        };
      case "obsidian-grid":
        return {
          backgroundImage:
            "linear-gradient(rgba(34,211,238,0.06) 1px, transparent 1px), linear-gradient(90deg, rgba(34,211,238,0.06) 1px, transparent 1px), linear-gradient(180deg, #030509 0%, #080E18 100%)",
          backgroundSize: "28px 28px, 28px 28px, 100% 100%",
        };
      case "solar-nebula":
        return {
          backgroundImage:
            "radial-gradient(circle at 75% 22%, rgba(245, 158, 11, 0.22), transparent 42%), radial-gradient(circle at 22% 76%, rgba(34, 211, 238, 0.20), transparent 48%), linear-gradient(160deg, #070912 0%, #110B18 100%)",
        };
      case "ref-horizon":
      default:
        return {
          backgroundImage:
            "repeating-linear-gradient(165deg, rgba(56, 189, 248, 0.08) 0px, rgba(56, 189, 248, 0.08) 1px, transparent 1px, transparent 14px), radial-gradient(circle at 12% 35%, rgba(37, 99, 235, 0.24), transparent 42%), radial-gradient(circle at 88% 35%, rgba(37, 99, 235, 0.24), transparent 42%), linear-gradient(180deg, #020408 0%, #060B14 100%)",
        };
    }
  }, [state.wallpaperId, state.customWallpaperUrl]);

  const handleSlotTap = (slotId: SlotId) => {
    const slot = state.slots[slotId];
    const app = slot?.packageName ? appMap.get(slot.packageName) : undefined;

    if (isEditMode) {
      if (!selectedSwapSource) {
        setSelectedSwapSource(slotId);
      } else if (selectedSwapSource === slotId) {
        setSelectedSwapSource(null);
        setDrawerTargetSlot(slotId);
        setDrawerOpen(true);
      } else {
        onSwapSlots(selectedSwapSource, slotId);
        setSelectedSwapSource(null);
      }
      return;
    }

    if (!app) {
      setDrawerTargetSlot(slotId);
      setDrawerOpen(true);
      return;
    }

    onLaunchApp(app);
    setActiveAppWindow(app);
  };

  const handleOpenDrawerForSlot = (slotId: SlotId | null) => {
    setDrawerTargetSlot(slotId);
    setDrawerOpen(true);
    setShowRecents(false);
    setShowCoreUpdatePopover(false);
  };

  const handleToggleCoreUpdatePopover = () => {
    setShowRecents(false);
    if (showCoreUpdatePopover) {
      if (updatePhase !== "checking" && updatePhase !== "downloading") {
        setShowCoreUpdatePopover(false);
      }
      return;
    }
    if (updatePhase === "up_to_date" || updatePhase === "error" || updatePhase === "downloaded") {
      setUpdatePhase("idle");
      setUpdateMessage(`Versão instalada: v${INSTALLED_VERSION}`);
      setDownloadPercent(null);
    }
    setShowCoreUpdatePopover(true);
  };

  const handleCheckForUpdates = async () => {
    setUpdatePhase("checking");
    setUpdateInfo(null);
    setDownloadPercent(null);
    setUpdateMessage("Consultando atualizações…");

    try {
      const res = await fetch(
        `/api/launcher/updates/check?currentVersion=${encodeURIComponent(INSTALLED_VERSION)}&versionCode=${INSTALLED_VERSION_CODE}`
      );
      const data = await res.json().catch(() => null);

      if (!res.ok || !data) {
        setUpdatePhase("error");
        setUpdateMessage(
          data?.error || `Falha ao consultar atualizações (HTTP ${res.status}).`
        );
        return;
      }

      const payload = data as LauncherUpdateCheckResponse;
      setUpdateInfo(payload);

      if (payload.status === "up_to_date") {
        setUpdatePhase("up_to_date");
        setUpdateMessage(`O aplicativo já está atualizado (v${payload.installedVersion}).`);
      } else if (payload.status === "no_apk_asset") {
        setUpdatePhase("error");
        setUpdateMessage(
          `Nova versão v${payload.latestVersion} encontrada, mas sem pacote .apk anexado à release.`
        );
      } else if (payload.status === "update_available") {
        setUpdatePhase("available");
        setUpdateMessage(
          `Nova versão disponível para instalação.`
        );
      } else {
        setUpdatePhase("error");
        setUpdateMessage("Resposta inesperada ao verificar atualizações.");
      }
    } catch {
      setUpdatePhase("error");
      setUpdateMessage("Erro de conexão ao consultar o servidor de atualizações.");
    }
  };

  const handleDownloadApk = async () => {
    if (!updateInfo?.downloadPath) return;
    setUpdatePhase("downloading");
    setDownloadPercent(0);
    setUpdateMessage(`Baixando pacote v${updateInfo.latestVersion}…`);

    try {
      const res = await fetch(updateInfo.downloadPath);
      if (!res.ok) {
        const errData = await res.json().catch(() => null);
        setUpdatePhase("error");
        setDownloadPercent(null);
        setUpdateMessage(
          errData?.error || `Falha ao baixar pacote APK (HTTP ${res.status}).`
        );
        return;
      }

      const contentLengthHeader = res.headers.get("Content-Length");
      const totalBytes =
        contentLengthHeader && Number.parseInt(contentLengthHeader, 10) > 0
          ? Number.parseInt(contentLengthHeader, 10)
          : updateInfo.assetSizeBytes || 0;

      const chunks: BlobPart[] = [];
      let received = 0;

      if (res.body) {
        const reader = res.body.getReader();
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          if (value) {
            chunks.push(value);
            received += value.byteLength;
            if (totalBytes > 0) {
              setDownloadPercent(Math.min(100, Math.round((received * 100) / totalBytes)));
            }
          }
        }
      } else {
        const buf = new Uint8Array(await res.arrayBuffer());
        chunks.push(buf);
        received = buf.byteLength;
      }

      const blob = new Blob(chunks, {
        type: "application/vnd.android.package-archive",
      });
      if (blob.size < 128) {
        setUpdatePhase("error");
        setDownloadPercent(null);
        setUpdateMessage("O pacote baixado é inválido ou está vazio.");
        return;
      }

      const headerBytes = new Uint8Array(await blob.slice(0, 2).arrayBuffer());
      if (headerBytes[0] !== 0x50 || headerBytes[1] !== 0x4b) {
        setUpdatePhase("error");
        setDownloadPercent(null);
        setUpdateMessage("O arquivo recebido não corresponde a um pacote .apk válido.");
        return;
      }

      const filename =
        updateInfo.assetName && updateInfo.assetName.toLowerCase().endsWith(".apk")
          ? updateInfo.assetName
          : `NossoUniverso-v${updateInfo.latestVersion}.apk`;

      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1500);

      setUpdatePhase("downloaded");
      setDownloadPercent(100);
      setUpdateMessage(
        `Pacote ${filename} baixado. No dispositivo Android, o instalador do sistema solicitará autorização e confirmação para concluir a instalação.`
      );
    } catch {
      setUpdatePhase("error");
      setDownloadPercent(null);
      setUpdateMessage("Erro de rede durante o download do pacote .apk.");
    }
  };

  const handleCustomIconPick = (slotId: SlotId) => {
    setCustomIconSlotTarget(slotId);
    customIconInputRef.current?.click();
  };

  const handleCustomIconFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    const target = customIconSlotTarget;
    e.target.value = "";
    if (!file || !target) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === "string") {
        onSetCustomIcon(target, reader.result);
      }
    };
    reader.readAsDataURL(file);
  };

  const renderPlateSlot = (slotId: SlotId) => {
    const slot = state.slots[slotId];
    const app = slot?.packageName ? appMap.get(slot.packageName) : undefined;
    const isSwapSelected = selectedSwapSource === slotId;

    return (
      <div
        key={slotId}
        draggable={isEditMode}
        onDragStart={() => setDraggedSlot(slotId)}
        onDragOver={(e) => {
          if (isEditMode) e.preventDefault();
        }}
        onDrop={() => {
          if (isEditMode && draggedSlot && draggedSlot !== slotId) {
            onSwapSlots(draggedSlot, slotId);
          }
          setDraggedSlot(null);
        }}
        className="relative group"
      >
        <button
          type="button"
          onClick={() => handleSlotTap(slotId)}
          aria-label={app ? `Abrir ${app.label}` : `Adicionar atalho em ${SLOT_LABELS[slotId]}`}
          className={`w-full h-[44px] px-3 flex items-center justify-center gap-2 transition-all duration-150 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 ${
            isSwapSelected
              ? "ring-2 ring-amber-400 scale-[1.02]"
              : isEditMode
                ? "hover:scale-[1.02]"
                : "active:scale-[0.97]"
          }`}
          style={{
            backgroundColor: `rgba(8, 14, 24, ${Math.max(0.45, state.panelOpacity / 100)})`,
            clipPath:
              "polygon(10px 0%, calc(100% - 10px) 0%, 100% 10px, 100% calc(100% - 10px), calc(100% - 10px) 100%, 10px 100%, 0% calc(100% - 10px), 0% 10px)",
            boxShadow: isEditMode
              ? "inset 0 0 0 1.5px rgba(245, 158, 11, 0.85)"
              : "inset 0 0 0 1.3px rgba(34, 211, 238, 0.72)",
          }}
        >
          {isEditMode && <GripVertical className="w-3.5 h-3.5 text-amber-400 shrink-0" />}
          {app ? (
            <>
              <AppGlyph
                app={app}
                iconPack={state.iconPack}
                customIconDataUrl={slot.customIconDataUrl}
                sizeClass="w-4 h-4 shrink-0"
              />
              <span className="font-display text-[11px] font-semibold tracking-wider uppercase text-slate-100 truncate">
                {slot.customLabel || app.label}
              </span>
            </>
          ) : (
            <span className="flex items-center gap-1 font-display text-[11px] tracking-wider uppercase text-cyan-300/75">
              <Plus className="w-3.5 h-3.5" />
              <span>Atalho</span>
            </span>
          )}
        </button>

        {isEditMode && (
          <div className="absolute -top-2.5 right-0 flex items-center gap-1 z-10">
            {app && (
              <>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleCustomIconPick(slotId);
                  }}
                  title="Usar imagem própria como ícone"
                  aria-label="Usar imagem própria como ícone"
                  className="w-6 h-6 rounded-full bg-slate-900 border border-cyan-400/70 text-cyan-300 flex items-center justify-center hover:bg-slate-800"
                >
                  <ImagePlus className="w-3 h-3" />
                </button>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onRemoveSlot(slotId);
                  }}
                  title="Remover atalho da tela inicial"
                  aria-label="Remover atalho da tela inicial"
                  className="w-6 h-6 rounded-full bg-slate-900 border border-rose-400/80 text-rose-300 flex items-center justify-center hover:bg-rose-950"
                >
                  <X className="w-3 h-3" />
                </button>
              </>
            )}
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                handleOpenDrawerForSlot(slotId);
              }}
              title="Trocar aplicativo"
              aria-label="Trocar aplicativo"
              className="w-6 h-6 rounded-full bg-slate-900 border border-amber-400/80 text-amber-300 flex items-center justify-center hover:bg-slate-800"
            >
              <Pencil className="w-3 h-3" />
            </button>
          </div>
        )}
      </div>
    );
  };

  const renderDockSlot = (slotId: SlotId) => {
    const slot = state.slots[slotId];
    const app = slot?.packageName ? appMap.get(slot.packageName) : undefined;
    const isSwapSelected = selectedSwapSource === slotId;

    return (
      <div
        key={slotId}
        draggable={isEditMode}
        onDragStart={() => setDraggedSlot(slotId)}
        onDragOver={(e) => {
          if (isEditMode) e.preventDefault();
        }}
        onDrop={() => {
          if (isEditMode && draggedSlot && draggedSlot !== slotId) {
            onSwapSlots(draggedSlot, slotId);
          }
          setDraggedSlot(null);
        }}
        className="relative"
      >
        <button
          type="button"
          onClick={() => handleSlotTap(slotId)}
          aria-label={app ? `Abrir ${app.label}` : `Adicionar no ${SLOT_LABELS[slotId]}`}
          className={`relative w-[50px] h-[50px] rounded-full flex items-center justify-center transition-transform duration-150 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 ${
            isSwapSelected
              ? "ring-2 ring-amber-400 scale-105"
              : "active:scale-95 hover:scale-105"
          }`}
          style={{
            backgroundColor: `rgba(8, 14, 23, ${Math.max(0.55, state.panelOpacity / 100)})`,
          }}
        >
          <svg viewBox="0 0 56 56" className="absolute inset-0 w-full h-full pointer-events-none">
            <circle
              cx="28"
              cy="28"
              r="25"
              fill="none"
              stroke={isEditMode ? "#F59E0B" : "#22D3EE"}
              strokeWidth="1.7"
              strokeDasharray="24 6"
            />
            <circle
              cx="28"
              cy="28"
              r="21"
              fill="none"
              stroke="#F59E0B"
              strokeWidth="1.2"
              strokeDasharray="10 18"
              opacity="0.85"
            />
          </svg>
          {app ? (
            <AppGlyph
              app={app}
              iconPack={state.iconPack}
              customIconDataUrl={slot.customIconDataUrl}
              sizeClass="w-5 h-5 relative z-10"
            />
          ) : (
            <Plus className="w-4 h-4 text-cyan-300/75 relative z-10" />
          )}
        </button>

        {isEditMode && app && (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onRemoveSlot(slotId);
            }}
            aria-label={`Remover ${app.label} do dock`}
            className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-slate-900 border border-rose-400/80 text-rose-300 flex items-center justify-center z-20"
          >
            <X className="w-3 h-3" />
          </button>
        )}
      </div>
    );
  };

  return (
    <div className="relative mx-auto w-full max-w-[376px] aspect-[376/780] rounded-[34px] p-[9px] bg-[#101622] border border-slate-700/70 shadow-2xl shadow-black/80 select-none">
      <input
        ref={customIconInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={handleCustomIconFileChange}
      />

      {/* Screen Container */}
      <div
        className="relative w-full h-full rounded-[26px] overflow-hidden flex flex-col justify-between"
        style={wallpaperStyle}
      >
        {/* Reference 1 Vector HUD Frame (Clock removed, wallpaper 100% unaltered) */}
        <HudSvgFrame />

        {/* 1. Native Android Status Bar (Preserved!) */}
        <div className="relative z-20 flex items-center justify-between px-4 pt-2 pb-1 text-[11px] font-medium text-slate-100 drop-shadow-[0_1px_2px_rgba(0,0,0,0.75)]">
          <div className="flex items-center gap-1.5 font-mono-tabular">
            <span>{clockText}</span>
            <span className="w-1.5 h-1.5 rounded-full bg-cyan-400/90" aria-hidden="true" />
          </div>
          <div className="flex items-center gap-2 text-slate-200">
            <Wifi className="w-3.5 h-3.5" />
            <Signal className="w-3.5 h-3.5" />
            <Battery className="w-4 h-4" />
          </div>
        </div>

        {/* Edit Mode Contextual Pill-Free Bar */}
        {isEditMode && (
          <div className="relative z-20 mx-5 mt-3 px-3 py-1.5 rounded-lg bg-[#09101B]/95 border border-amber-400/70 flex items-center justify-between gap-2">
            <span className="text-[11px] text-amber-200 leading-tight">
              {selectedSwapSource
                ? "Toque em outro espaço para trocar de lugar"
                : "Arraste ou toque para trocar/editar atalhos"}
            </span>
            <button
              type="button"
              onClick={() => {
                setSelectedSwapSource(null);
                onToggleEditMode(false);
              }}
              className="px-2 py-1 rounded bg-cyan-500 text-slate-950 text-[11px] font-semibold whitespace-nowrap shrink-0 flex items-center gap-1"
            >
              <Check className="w-3 h-3" />
              <span>Concluir</span>
            </button>
          </div>
        )}

        {/* 2. Central Núcleo HILM (Replaces original sphere cleanly) */}
        <div className="relative z-10 flex-1 flex items-center justify-center pt-5">
          <HilmCore onClick={handleToggleCoreUpdatePopover} />
        </div>

        {/* 3. Lower 2x2 HUD Shortcut Plates + Central Hub Edit Toggle */}
        <div className="relative z-20 px-6 pb-4">
          <div className="relative grid grid-cols-2 gap-x-10 gap-y-4 items-center">
            {PLATE_SLOTS.map((slotId) => renderPlateSlot(slotId))}

            {/* Central Hexagonal Hub Node (Edit Mode / Manage Shortcuts) */}
            <button
              type="button"
              onClick={() => {
                setSelectedSwapSource(null);
                onToggleEditMode(!isEditMode);
              }}
              title={isEditMode ? "Concluir edição de atalhos" : "Editar e reorganizar atalhos"}
              aria-label={isEditMode ? "Concluir edição de atalhos" : "Editar e reorganizar atalhos"}
              className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-11 h-11 flex items-center justify-center cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 transition-transform active:scale-95"
            >
              <svg viewBox="0 0 44 44" className="w-full h-full">
                <polygon
                  points="22,3 37,12 37,32 22,41 7,32 7,12"
                  fill="rgba(8, 14, 24, 0.9)"
                  stroke={isEditMode ? "#F59E0B" : "#22D3EE"}
                  strokeWidth="1.6"
                />
                <polygon
                  points="22,9 32,15 32,29 22,35 12,29 12,15"
                  fill="none"
                  stroke="#94A3B8"
                  strokeWidth="1"
                />
                <circle
                  cx="22"
                  cy="22"
                  r="4"
                  fill={isEditMode ? "#F59E0B" : "#22D3EE"}
                />
              </svg>
            </button>
          </div>
        </div>

        {/* 4. Bottom 5-Slot Circular HUD Dock */}
        <div className="relative z-20 px-3 pb-2 flex items-center justify-around">
          {SIDE_DOCK_LEFT.map((slotId) => renderDockSlot(slotId))}

          {/* Center Dock Button — Opens App Drawer */}
          <button
            type="button"
            onClick={() => handleOpenDrawerForSlot(null)}
            aria-label="Abrir gaveta de aplicativos instalados"
            title="Abrir gaveta de aplicativos"
            className="relative w-[62px] h-[62px] rounded-full flex items-center justify-center bg-[#070D17]/90 cursor-pointer transition-transform duration-150 hover:scale-105 active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400"
          >
            <svg viewBox="0 0 68 68" className="w-full h-full">
              <circle
                cx="34"
                cy="34"
                r="30"
                fill="none"
                stroke="#22D3EE"
                strokeWidth="2"
                strokeDasharray="28 8"
              />
              <circle
                cx="34"
                cy="34"
                r="24"
                fill="none"
                stroke="#F59E0B"
                strokeWidth="1.5"
                strokeDasharray="12 18"
              />
              <polygon
                points="34,19 46,27 42,42 26,42 22,27"
                fill="rgba(34, 211, 238, 0.14)"
                stroke="#CBD5E1"
                strokeWidth="1.4"
              />
              <polygon points="34,26 40,36 28,36" fill="#22D3EE" />
            </svg>
          </button>

          {SIDE_DOCK_RIGHT.map((slotId) => renderDockSlot(slotId))}
        </div>

        {/* 5. Android Bottom Navigation Bar (Functional in Preview) */}
        <div className="relative z-20 h-10 flex items-center justify-around px-8 text-slate-200 drop-shadow-[0_1px_2px_rgba(0,0,0,0.75)]">
          <button
            type="button"
            onClick={() => {
              if (activeAppWindow) setActiveAppWindow(null);
              else if (drawerOpen) setDrawerOpen(false);
              else if (showRecents) setShowRecents(false);
              else if (showCoreUpdatePopover && updatePhase !== "checking" && updatePhase !== "downloading") {
                setShowCoreUpdatePopover(false);
              } else if (isEditMode) onToggleEditMode(false);
            }}
            aria-label="Voltar"
            className="min-w-[44px] min-h-[36px] flex items-center justify-center hover:text-cyan-300 transition-colors cursor-pointer"
          >
            <Triangle className="w-3.5 h-3.5 -rotate-90" />
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveAppWindow(null);
              setDrawerOpen(false);
              setShowRecents(false);
              if (updatePhase !== "checking" && updatePhase !== "downloading") {
                setShowCoreUpdatePopover(false);
              }
              setSelectedSwapSource(null);
              onToggleEditMode(false);
            }}
            aria-label="Tela Inicial"
            className="min-w-[44px] min-h-[36px] flex items-center justify-center hover:text-cyan-300 transition-colors cursor-pointer"
          >
            <Circle className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveAppWindow(null);
              setDrawerOpen(false);
              if (updatePhase !== "checking" && updatePhase !== "downloading") {
                setShowCoreUpdatePopover(false);
              }
              setShowRecents((prev) => !prev);
            }}
            aria-label="Aplicativos recentes"
            className="min-w-[44px] min-h-[36px] flex items-center justify-center hover:text-cyan-300 transition-colors cursor-pointer"
          >
            <Square className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* Overlays inside Phone Screen */}
        {drawerOpen && (
          <AppDrawerModal
            apps={INSTALLED_APPS_CATALOG}
            iconPack={state.iconPack}
            targetSlot={drawerTargetSlot}
            onClose={() => {
              setDrawerOpen(false);
              setDrawerTargetSlot(null);
            }}
            onSelectApp={(app) => {
              if (drawerTargetSlot) {
                onAssignSlot(drawerTargetSlot, app.packageName);
                setDrawerOpen(false);
                setDrawerTargetSlot(null);
              } else {
                onLaunchApp(app);
                setDrawerOpen(false);
                setActiveAppWindow(app);
              }
            }}
            onPinToSlot={(app) => {
              const emptySlot = (
                [
                  "plate_top_left",
                  "plate_top_right",
                  "plate_bottom_left",
                  "plate_bottom_right",
                  "dock_1",
                  "dock_2",
                  "dock_4",
                  "dock_5",
                ] as SlotId[]
              ).find((id) => !state.slots[id]?.packageName);
              const target = emptySlot || "plate_top_left";
              onAssignSlot(target, app.packageName);
              setDrawerOpen(false);
            }}
          />
        )}
        {/* Compact Update Popover (exclusively triggered by tapping the central HILM nucleus) */}
        {showCoreUpdatePopover && (
          <div className="absolute inset-x-6 top-[382px] z-30 rounded-xl bg-[#070C15]/95 border border-cyan-400/50 px-3.5 py-2.5 backdrop-blur-md shadow-2xl shadow-black/80">
            <div className="flex items-center justify-between gap-2">
              <span className="font-display text-[11px] font-semibold tracking-wider text-cyan-300 uppercase">
                Central Nosso Universo
              </span>
              <button
                type="button"
                onClick={() => {
                  if (updatePhase !== "checking" && updatePhase !== "downloading") {
                    setShowCoreUpdatePopover(false);
                  }
                }}
                aria-label="Fechar painel de atualização"
                className="w-6 h-6 flex items-center justify-center text-slate-400 hover:text-cyan-300 cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>

            {updatePhase === "available" && updateInfo ? (
              <div className="mt-1.5 text-[11px] text-slate-200 space-y-0.5">
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Versão instalada:</span>
                  <span className="font-mono-tabular text-slate-100">
                    v{updateInfo.installedVersion}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Versão disponível:</span>
                  <span className="font-mono-tabular font-semibold text-cyan-300">
                    v{updateInfo.latestVersion}
                  </span>
                </div>
              </div>
            ) : (
              <div className="mt-1.5 flex items-start gap-1.5 text-[11px] leading-snug text-slate-200">
                {updatePhase === "up_to_date" || updatePhase === "downloaded" ? (
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-0.5" />
                ) : updatePhase === "error" ? (
                  <AlertCircle className="w-3.5 h-3.5 text-amber-400 shrink-0 mt-0.5" />
                ) : null}
                <span>{updateMessage}</span>
              </div>
            )}

            {updatePhase === "downloading" && (
              <div className="mt-2 w-full h-1.5 rounded-full bg-slate-800 overflow-hidden">
                <div
                  className="h-full bg-cyan-400 transition-all duration-150"
                  style={{ width: `${downloadPercent ?? 25}%` }}
                />
              </div>
            )}

            <div className="mt-2.5">
              {updatePhase === "available" ? (
                <button
                  type="button"
                  onClick={handleDownloadApk}
                  className="w-full h-8 rounded-lg bg-cyan-500/20 border border-cyan-400/60 text-xs font-semibold text-cyan-100 hover:bg-cyan-500/30 transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Atualizar agora</span>
                </button>
              ) : updatePhase === "checking" || updatePhase === "downloading" ? (
                <button
                  type="button"
                  disabled
                  className="w-full h-8 rounded-lg bg-slate-800/80 border border-slate-700 text-xs font-medium text-slate-300 flex items-center justify-center gap-1.5 opacity-80"
                >
                  <Loader2 className="w-3.5 h-3.5 animate-spin text-cyan-300" />
                  <span>
                    {updatePhase === "checking"
                      ? "Buscando atualizações…"
                      : downloadPercent !== null
                        ? `Baixando ${downloadPercent}%…`
                        : "Baixando pacote…"}
                  </span>
                </button>
              ) : updatePhase === "downloaded" ? (
                <button
                  type="button"
                  onClick={() => setShowCoreUpdatePopover(false)}
                  className="w-full h-8 rounded-lg bg-emerald-500/20 border border-emerald-400/50 text-xs font-semibold text-emerald-200 hover:bg-emerald-500/30 transition-colors cursor-pointer"
                >
                  Concluído
                </button>
              ) : (
                <button
                  type="button"
                  onClick={handleCheckForUpdates}
                  className="w-full h-8 rounded-lg bg-cyan-500/15 border border-cyan-400/50 text-xs font-semibold text-cyan-200 hover:bg-cyan-500/25 transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  <span>
                    {updatePhase === "idle"
                      ? "Buscar atualizações"
                      : "Buscar atualizações novamente"}
                  </span>
                </button>
              )}
            </div>
          </div>
        )}
        {/* Recent Apps Overlay */}
        {showRecents && (
          <div className="absolute inset-0 bottom-10 z-30 bg-[#05080F]/92 backdrop-blur-md p-4 flex flex-col">
            <div className="flex items-center justify-between pt-6 pb-3 border-b border-slate-800">
              <h4 className="font-display text-sm font-semibold text-cyan-300">
                Aplicativos Recentes
              </h4>
              <button
                type="button"
                onClick={() => setShowRecents(false)}
                className="min-w-[36px] min-h-[36px] flex items-center justify-center text-slate-400 hover:text-cyan-300"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto py-3 space-y-2.5 hud-scroll">
              {state.recentPackages.map((pkg) => {
                const app = appMap.get(pkg);
                if (!app) return null;
                return (
                  <button
                    key={pkg}
                    type="button"
                    onClick={() => {
                      setShowRecents(false);
                      onLaunchApp(app);
                      setActiveAppWindow(app);
                    }}
                    className="w-full p-3 rounded-xl bg-[#0B121E] border border-slate-800 hover:border-cyan-400/50 flex items-center gap-3 text-left transition-colors"
                  >
                    <div className="w-10 h-10 rounded-lg bg-[#06090F] border border-cyan-500/30 flex items-center justify-center shrink-0">
                      <AppGlyph app={app} iconPack={state.iconPack} sizeClass="w-5 h-5" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-xs font-semibold text-slate-100 truncate">
                        {app.label}
                      </div>
                      <div className="text-[10px] font-mono-tabular text-slate-400 truncate">
                        {app.packageName}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* Simulated Launch Intent Feedback Screen inside Phone */}
        {activeAppWindow && (
          <div className="absolute inset-0 bottom-10 z-30 bg-[#060B13]/96 backdrop-blur-md flex flex-col justify-between p-5 pt-10">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-mono-tabular text-cyan-300">
                Intent Nativo Android
              </span>
              <button
                type="button"
                onClick={() => setActiveAppWindow(null)}
                className="min-w-[36px] min-h-[36px] flex items-center justify-center rounded-lg bg-slate-900 border border-slate-700 text-slate-300 hover:text-cyan-300"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex flex-col items-center text-center space-y-3 my-auto">
              <div className="w-16 h-16 rounded-2xl bg-[#0C1422] border border-cyan-400/40 flex items-center justify-center">
                <AppGlyph app={activeAppWindow} iconPack={state.iconPack} sizeClass="w-8 h-8" />
              </div>
              <div>
                <h4 className="font-display text-lg font-semibold text-slate-100">
                  {activeAppWindow.label}
                </h4>
                <p className="text-xs font-mono-tabular text-slate-400 mt-1 break-all">
                  {activeAppWindow.packageName}
                </p>
              </div>
              <div className="w-full rounded-xl bg-[#0A101B] border border-slate-800 p-3 text-left text-[11px] font-mono-tabular text-slate-300 space-y-1">
                <div>ACTION_MAIN · CATEGORY_LAUNCHER</div>
                <div className="text-cyan-300/90 break-all">
                  {activeAppWindow.activityName}
                </div>
              </div>
              <p className="text-xs text-slate-400 leading-relaxed max-w-[260px]">
                No dispositivo Android, o método <code className="text-cyan-300">AppRepository.launchApp()</code> inicia diretamente este aplicativo via <code className="text-cyan-300">PackageManager</code>.
              </p>
            </div>

            <button
              type="button"
              onClick={() => setActiveAppWindow(null)}
              className="w-full h-11 rounded-xl bg-cyan-500 text-slate-950 font-semibold text-xs hover:bg-cyan-400 transition-colors"
            >
              Voltar à Tela Inicial
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
