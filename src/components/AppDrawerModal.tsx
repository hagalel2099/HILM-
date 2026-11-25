import { Plus, Search, X } from "lucide-react";
import { useMemo, useState } from "react";
import { motion, useReducedMotion } from "motion/react";
import type {
  IconPackId,
  InstalledAppItem,
  LauncherThemeId,
  SlotId,
  TransitionConfig,
} from "../types/launcher";
import { DEFAULT_LAUNCHER_STATE, SLOT_LABELS } from "../data/launcherCatalog";
import {
  isCardlessIconPack,
  usesExpandedGlyphScale,
} from "../data/iconPackRegistry";
import { AppGlyph } from "./AppGlyph";
import { getHilmIconItemMotion } from "./HilmTransitionPreview";

interface AppDrawerModalProps {
  apps: InstalledAppItem[];
  iconPack: IconPackId;
  themeId?: LauncherThemeId;
  targetSlot: SlotId | null;
  transition?: TransitionConfig;
  onClose: () => void;
  onSelectApp: (app: InstalledAppItem, originElement?: HTMLElement | null) => void;
  onPinToSlot?: (app: InstalledAppItem) => void;
}

export function AppDrawerModal({
  apps,
  iconPack,
  themeId = "default",
  targetSlot,
  transition = DEFAULT_LAUNCHER_STATE.transition,
  onClose,
  onSelectApp,
  onPinToSlot,
}: AppDrawerModalProps) {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState<string>("Todos");
  const prefersReducedMotion = Boolean(useReducedMotion());
  const isLunaTheme = themeId === "luna-entre-luz-e-sombra";
  const isCardless = isCardlessIconPack(iconPack);

  const categories = useMemo(() => {
    const unique = Array.from(new Set(apps.map((a) => a.category)));
    return ["Todos", ...unique];
  }, [apps]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return apps.filter((app) => {
      const matchesCat = category === "Todos" || app.category === category;
      const matchesQuery =
        !q ||
        app.label.toLowerCase().includes(q) ||
        app.packageName.toLowerCase().includes(q);
      return matchesCat && matchesQuery;
    });
  }, [apps, query, category]);

  return (
    <div
      className={`absolute inset-0 z-30 flex flex-col backdrop-blur-md ${
        isLunaTheme
          ? "bg-[#05070B]/90 text-[#D9F7FF]"
          : "bg-[#060A12]/95 text-slate-100"
      }`}
      role="dialog"
      aria-modal="true"
      aria-label="Gaveta de Aplicativos"
    >
      <div
        className={`flex items-center justify-between px-4 pt-9 pb-3 border-b ${
          isLunaTheme ? "border-[#00E5FF]/25" : "border-cyan-500/20"
        }`}
      >
        <div className="min-w-0">
          <h3
            className={`font-display text-sm font-semibold tracking-wide truncate ${
              isLunaTheme ? "text-[#00E5FF]" : "text-cyan-300"
            }`}
          >
            {targetSlot ? `Selecionar para ${SLOT_LABELS[targetSlot]}` : "Aplicativos Instalados"}
          </h3>
          <p className="text-[11px] text-slate-400 mt-0.5 truncate">
            {targetSlot
              ? "Toque em um aplicativo para vincular ao atalho"
              : `${filtered.length} aplicativos disponíveis`}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Fechar gaveta"
          className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-lg text-slate-300 hover:text-cyan-300 hover:bg-slate-800/60 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      <div
        className={`px-4 py-2.5 space-y-2 border-b ${
          isLunaTheme ? "border-[#0A1424]" : "border-slate-800/80"
        }`}
      >
        <div className="relative flex items-center">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 pointer-events-none" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar aplicativo..."
            className={`w-full h-10 pl-9 pr-3 rounded-lg text-xs placeholder:text-slate-500 focus:outline-none ${
              isLunaTheme
                ? "bg-[#0A1424]/75 border border-[#00E5FF]/30 text-[#D9F7FF] focus:border-[#00E5FF]"
                : "bg-[#0D1422] border border-slate-700/80 text-slate-100 focus:border-cyan-400"
            }`}
          />
        </div>

        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 hud-scroll">
          {categories.map((cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setCategory(cat)}
              className={`px-2.5 py-1 text-[11px] font-medium rounded-md whitespace-nowrap shrink-0 transition-colors ${
                category === cat
                  ? isLunaTheme
                    ? "bg-[#00E5FF]/15 text-[#D9F7FF] border border-[#00E5FF]/55"
                    : "bg-cyan-500/20 text-cyan-300 border border-cyan-400/50"
                  : isLunaTheme
                    ? "bg-[#0A1424]/75 text-slate-300 border border-[#398BFF]/20 hover:text-[#D9F7FF]"
                    : "bg-slate-900/80 text-slate-400 border border-slate-800 hover:text-slate-200"
              }`}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3 hud-scroll">
        {filtered.length === 0 ? (
          <div className="h-40 flex items-center justify-center text-xs text-slate-400">
            Nenhum aplicativo encontrado para "{query}".
          </div>
        ) : (
          <div className="grid grid-cols-4 gap-2" style={{ perspective: "620px" }}>
            {filtered.map((app, index) => {
              const itemMotion = getHilmIconItemMotion(
                transition,
                index,
                prefersReducedMotion
              );
              return (
                <motion.div
                  key={`${category}-${transition.effect}-${app.packageName}`}
                  initial={itemMotion.initial}
                  animate={itemMotion.animate}
                  transition={itemMotion.transition}
                  className={`group relative flex flex-col items-center p-2 rounded-xl transition-colors ${
                    isLunaTheme
                      ? "bg-[#0A1424]/30 border border-[#00E5FF]/15 hover:border-[#00E5FF]/55"
                      : "bg-[#0B121E]/70 border border-slate-800/70 hover:border-cyan-400/50"
                  }`}
                >
                  <button
                    type="button"
                    onClick={(e) => {
                      const iconEl = e.currentTarget.querySelector<HTMLElement>("[data-portal-glyph]");
                      onSelectApp(app, iconEl ?? e.currentTarget);
                    }}
                    className="w-full flex flex-col items-center gap-1.5 min-h-[44px] active:scale-95 transition-transform focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 rounded-lg cursor-pointer"
                  >
                    <div
                      data-portal-glyph="true"
                      className={
                        isCardless
                          ? "w-12 h-12 flex items-center justify-center"
                          : "w-11 h-11 rounded-full bg-[#060A12] border border-cyan-500/30 flex items-center justify-center"
                      }
                    >
                      <AppGlyph
                        app={app}
                        iconPack={iconPack}
                        sizeClass={
                          isCardless
                            ? "w-11 h-11"
                            : usesExpandedGlyphScale(iconPack)
                              ? "w-8 h-8"
                              : "w-5 h-5"
                        }
                      />
                    </div>
                    <span
                      className={`text-[11px] truncate w-full text-center ${
                        isLunaTheme ? "text-[#D9F7FF]" : "text-slate-200"
                      }`}
                    >
                      {app.label}
                    </span>
                  </button>
                  {!targetSlot && onPinToSlot && (
                    <button
                      type="button"
                      onClick={() => onPinToSlot(app)}
                      title="Fixar na tela inicial"
                      aria-label={`Fixar ${app.label} na tela inicial`}
                      className={`mt-1 px-1.5 py-0.5 text-[10px] flex items-center gap-0.5 whitespace-nowrap ${
                        isLunaTheme
                          ? "text-[#FFB45C]/90 hover:text-[#FFB45C]"
                          : "text-amber-300/90 hover:text-amber-200"
                      }`}
                    >
                      <Plus className="w-3 h-3" />
                      <span>Fixar</span>
                    </button>
                  )}
                </motion.div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
