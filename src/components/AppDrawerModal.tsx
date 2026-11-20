import { Plus, Search, X } from "lucide-react";
import { useMemo, useState } from "react";
import type { IconPackId, InstalledAppItem, SlotId } from "../types/launcher";
import { SLOT_LABELS } from "../data/launcherCatalog";
import { AppGlyph } from "./AppGlyph";

interface AppDrawerModalProps {
  apps: InstalledAppItem[];
  iconPack: IconPackId;
  targetSlot: SlotId | null;
  onClose: () => void;
  onSelectApp: (app: InstalledAppItem) => void;
  onPinToSlot?: (app: InstalledAppItem) => void;
}

export function AppDrawerModal({
  apps,
  iconPack,
  targetSlot,
  onClose,
  onSelectApp,
  onPinToSlot,
}: AppDrawerModalProps) {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState<string>("Todos");

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
      className="absolute inset-0 z-30 flex flex-col bg-[#060A12]/95 backdrop-blur-md text-slate-100"
      role="dialog"
      aria-modal="true"
      aria-label="Gaveta de Aplicativos"
    >
      <div className="flex items-center justify-between px-4 pt-9 pb-3 border-b border-cyan-500/20">
        <div className="min-w-0">
          <h3 className="font-display text-sm font-semibold tracking-wide text-cyan-300 truncate">
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

      <div className="px-4 py-2.5 space-y-2 border-b border-slate-800/80">
        <div className="relative flex items-center">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 pointer-events-none" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar aplicativo..."
            className="w-full h-10 pl-9 pr-3 rounded-lg bg-[#0D1422] border border-slate-700/80 text-xs text-slate-100 placeholder:text-slate-500 focus:outline-none focus:border-cyan-400"
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
                  ? "bg-cyan-500/20 text-cyan-300 border border-cyan-400/50"
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
          <div className="grid grid-cols-4 gap-2">
            {filtered.map((app) => (
              <div
                key={app.packageName}
                className="group relative flex flex-col items-center p-2 rounded-xl bg-[#0B121E]/70 border border-slate-800/70 hover:border-cyan-400/50 transition-colors"
              >
                <button
                  type="button"
                  onClick={() => onSelectApp(app)}
                  className="w-full flex flex-col items-center gap-1.5 min-h-[44px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 rounded-lg"
                >
                  <div className="w-10 h-10 rounded-full bg-[#060A12] border border-cyan-500/30 flex items-center justify-center">
                    <AppGlyph app={app} iconPack={iconPack} sizeClass="w-5 h-5" />
                  </div>
                  <span className="text-[11px] text-slate-200 truncate w-full text-center">
                    {app.label}
                  </span>
                </button>
                {!targetSlot && onPinToSlot && (
                  <button
                    type="button"
                    onClick={() => onPinToSlot(app)}
                    title="Fixar na tela inicial"
                    aria-label={`Fixar ${app.label} na tela inicial`}
                    className="mt-1 px-1.5 py-0.5 text-[10px] text-amber-300/90 hover:text-amber-200 flex items-center gap-0.5 whitespace-nowrap"
                  >
                    <Plus className="w-3 h-3" />
                    <span>Fixar</span>
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
