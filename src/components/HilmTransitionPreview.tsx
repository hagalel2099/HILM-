import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { Play, Sparkles } from "lucide-react";
import type { TransitionConfig, TransitionEffectId } from "../types/launcher";
import { TRANSITION_EFFECTS } from "../data/launcherCatalog";

export type HilmSurfaceKind =
  | "left-drawer"
  | "right-drawer"
  | "app-drawer"
  | "recents"
  | "popover";

export function getHilmSurfaceMotion(
  surface: HilmSurfaceKind,
  config: TransitionConfig,
  prefersReducedMotion: boolean
) {
  if (prefersReducedMotion || !config.enabled) {
    return {
      initial: { opacity: 1 },
      animate: { opacity: 1 },
      exit: { opacity: 0 },
      transition: { duration: 0.01 },
    };
  }

  const dur = Math.max(0.16, Math.min(0.65, config.durationMs / 1000));
  const isLeft = surface === "left-drawer";
  const isRight = surface === "right-drawer";
  const isHorizontal = isLeft || isRight;
  const sideOffset = isLeft ? -74 : isRight ? 74 : 0;
  const verticalOffset = surface === "app-drawer" ? 42 : surface === "recents" ? 30 : -12;

  switch (config.effect) {
    case "portal":
      return {
        initial: isHorizontal
          ? { x: sideOffset * 0.65, scale: 0.72, opacity: 0 }
          : { scale: 0.84, opacity: 0 },
        animate: { x: 0, y: 0, scale: 1, opacity: 1 },
        exit: isHorizontal
          ? { x: sideOffset * 0.55, scale: 0.76, opacity: 0 }
          : { scale: 0.88, opacity: 0 },
        transition: { duration: dur, ease: [0.16, 1, 0.3, 1] as const },
      };

    case "digital-disintegration":
      return {
        initial: isHorizontal
          ? { x: sideOffset * 0.35, scale: 0.92, opacity: 0, filter: "blur(3px)" }
          : { scale: 0.95, opacity: 0, filter: "blur(4px)" },
        animate: { x: 0, y: 0, scale: 1, opacity: 1, filter: "blur(0px)" },
        exit: isHorizontal
          ? { x: sideOffset * 0.4, scale: 1.04, opacity: 0, filter: "blur(4px)" }
          : { scale: 1.03, opacity: 0, filter: "blur(5px)" },
        transition: { duration: dur, ease: [0.22, 1, 0.36, 1] as const },
      };

    case "light-sweep":
      return {
        initial: isHorizontal
          ? {
              clipPath: isLeft ? "inset(0 100% 0 0)" : "inset(0 0 0 100%)",
              opacity: 0.35,
              x: isLeft ? -14 : 14,
            }
          : { clipPath: "inset(0 0 100% 0)", opacity: 0.35, y: -8 },
        animate: { clipPath: "inset(0 0 0 0)", opacity: 1, x: 0, y: 0 },
        exit: isHorizontal
          ? {
              clipPath: isLeft ? "inset(0 100% 0 0)" : "inset(0 0 0 100%)",
              opacity: 0,
            }
          : { clipPath: "inset(100% 0 0 0)", opacity: 0 },
        transition: { duration: dur, ease: [0.2, 0.9, 0.25, 1] as const },
      };

    case "dimensional-slide":
      return {
        initial: isHorizontal
          ? {
              x: sideOffset,
              rotateY: isLeft ? 28 : -28,
              scale: 0.86,
              opacity: 0,
            }
          : {
              y: verticalOffset,
              rotateX: surface === "popover" ? -12 : 10,
              scale: 0.9,
              opacity: 0,
            },
        animate: { x: 0, y: 0, rotateX: 0, rotateY: 0, scale: 1, opacity: 1 },
        exit: isHorizontal
          ? {
              x: sideOffset * 0.9,
              rotateY: isLeft ? 24 : -24,
              scale: 0.86,
              opacity: 0,
            }
          : {
              y: verticalOffset * 0.8,
              rotateX: surface === "popover" ? 8 : -8,
              scale: 0.92,
              opacity: 0,
            },
        transition: { duration: dur, ease: [0.18, 0.88, 0.28, 1] as const },
      };

    case "energy-dissolve":
      return {
        initial: { opacity: 0, scale: 0.965 },
        animate: { opacity: 1, scale: 1 },
        exit: { opacity: 0, scale: 1.02 },
        transition: { duration: dur * 1.05, ease: [0.4, 0, 0.2, 1] as const },
      };

    case "classic":
    default:
      return {
        initial: isHorizontal ? { x: sideOffset * 0.85, opacity: 0 } : { y: verticalOffset * 0.55, opacity: 0 },
        animate: { x: 0, y: 0, opacity: 1 },
        exit: isHorizontal ? { x: sideOffset * 0.85, opacity: 0 } : { y: verticalOffset * 0.55, opacity: 0 },
        transition: { duration: Math.min(dur, 0.28), ease: "easeOut" as const },
      };
  }
}

export function getHilmAppWindowMotion(
  effect: TransitionEffectId,
  enabled: boolean,
  durationMs: number,
  originX: number,
  originY: number,
  prefersReducedMotion: boolean
) {
  if (prefersReducedMotion || !enabled) {
    return {
      initial: { opacity: 1 },
      animate: { opacity: 1 },
      exit: { opacity: 0 },
      transition: { duration: 0.01 },
    };
  }

  const dur = Math.max(0.18, Math.min(0.55, durationMs / 1000));

  switch (effect) {
    case "portal":
      return {
        initial: {
          clipPath: `circle(16px at ${originX}px ${originY}px)`,
          opacity: 0.45,
          scale: 0.92,
        },
        animate: {
          clipPath: `circle(135% at ${originX}px ${originY}px)`,
          opacity: 1,
          scale: 1,
        },
        exit: {
          clipPath: `circle(14px at ${originX}px ${originY}px)`,
          opacity: 0,
          scale: 0.95,
        },
        transition: { duration: dur, ease: [0.16, 1, 0.3, 1] as const },
      };

    case "digital-disintegration":
      return {
        initial: { opacity: 0, scale: 0.94, filter: "blur(5px)" },
        animate: { opacity: 1, scale: 1, filter: "blur(0px)" },
        exit: { opacity: 0, scale: 1.03, filter: "blur(5px)" },
        transition: { duration: dur, ease: [0.22, 1, 0.36, 1] as const },
      };

    case "light-sweep":
      return {
        initial: { clipPath: "inset(0 100% 0 0)", opacity: 0.6 },
        animate: { clipPath: "inset(0 0% 0 0)", opacity: 1 },
        exit: { clipPath: "inset(0 0 0 100%)", opacity: 0 },
        transition: { duration: dur, ease: [0.2, 0.9, 0.25, 1] as const },
      };

    case "dimensional-slide":
      return {
        initial: { y: 36, rotateX: 9, scale: 0.9, opacity: 0 },
        animate: { y: 0, rotateX: 0, scale: 1, opacity: 1 },
        exit: { y: 28, rotateX: -7, scale: 0.92, opacity: 0 },
        transition: { duration: dur, ease: [0.18, 0.88, 0.28, 1] as const },
      };

    case "energy-dissolve":
      return {
        initial: { opacity: 0, scale: 0.97 },
        animate: { opacity: 1, scale: 1 },
        exit: { opacity: 0, scale: 1.02 },
        transition: { duration: dur, ease: [0.4, 0, 0.2, 1] as const },
      };

    case "classic":
    default:
      return {
        initial: { y: 18, opacity: 0 },
        animate: { y: 0, opacity: 1 },
        exit: { y: 14, opacity: 0 },
        transition: { duration: Math.min(dur, 0.24), ease: "easeOut" as const },
      };
  }
}

const PREVIEW_MOTES = [
  { dx: -38, dy: -22, color: "#22D3EE", size: 4 },
  { dx: 34, dy: -26, color: "#FB923C", size: 3.5 },
  { dx: -44, dy: 12, color: "#E2E8F0", size: 3 },
  { dx: 42, dy: 16, color: "#22D3EE", size: 4 },
  { dx: -16, dy: -34, color: "#F59E0B", size: 3.5 },
  { dx: 18, dy: 30, color: "#22D3EE", size: 3 },
  { dx: -24, dy: 28, color: "#FB923C", size: 3.5 },
  { dx: 28, dy: -8, color: "#E2E8F0", size: 3 },
];

interface HilmTransitionPreviewProps {
  config: TransitionConfig;
  replayTrigger: number;
}

export function HilmTransitionPreview({
  config,
  replayTrigger,
}: HilmTransitionPreviewProps) {
  const prefersReducedMotion = useReducedMotion();
  const [stageOpen, setStageOpen] = useState(true);
  const [burstKey, setBurstKey] = useState(0);
  const timerRef = useRef<number | null>(null);

  const effectMeta = TRANSITION_EFFECTS.find((e) => e.id === config.effect);

  const triggerCycle = () => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setStageOpen(false);
    setBurstKey((k) => k + 1);
    const gap = !config.enabled || prefersReducedMotion ? 70 : Math.max(170, Math.round(config.durationMs * 0.65));
    timerRef.current = window.setTimeout(() => {
      setStageOpen(true);
      setBurstKey((k) => k + 1);
      timerRef.current = null;
    }, gap);
  };

  useEffect(() => {
    triggerCycle();
    return () => {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current);
      }
    };
  }, [config.effect, config.enabled, config.durationMs, replayTrigger]);

  const cardMotion = getHilmSurfaceMotion(
    "dimensional-slide" === config.effect ? "app-drawer" : "popover",
    config,
    Boolean(prefersReducedMotion)
  );

  const durSec = Math.max(0.18, Math.min(0.6, config.durationMs / 1000));

  return (
    <div className="rounded-xl border border-slate-800 bg-[#060A12] p-3.5 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <Sparkles className="w-3.5 h-3.5 text-cyan-300 shrink-0" />
          <span className="text-xs font-semibold text-slate-200 truncate">
            Prévia: {config.enabled ? effectMeta?.name : "Efeitos desativados"}
          </span>
        </div>
        <button
          type="button"
          onClick={triggerCycle}
          className="px-2.5 py-1 rounded-md bg-slate-900 border border-slate-700 hover:border-cyan-400/60 text-[11px] font-medium text-cyan-300 flex items-center gap-1 cursor-pointer shrink-0"
        >
          <Play className="w-3 h-3" />
          <span>Testar prévia</span>
        </button>
      </div>

      {/* Simulated Mini HUD Viewport */}
      <div
        className="relative h-32 rounded-lg overflow-hidden border border-cyan-500/20 bg-gradient-to-br from-[#04070D] via-[#091220] to-[#060B15] flex items-center justify-center"
        style={{ perspective: "540px" }}
      >
        {/* Subtle HUD Grid Background */}
        <div
          className="absolute inset-0 opacity-20 pointer-events-none"
          style={{
            backgroundImage:
              "linear-gradient(rgba(34,211,238,0.18) 1px, transparent 1px), linear-gradient(90deg, rgba(34,211,238,0.18) 1px, transparent 1px)",
            backgroundSize: "18px 18px",
          }}
        />

        <AnimatePresence mode="wait">
          {stageOpen && (
            <motion.div
              key={`preview-card-${config.effect}-${config.enabled}`}
              initial={cardMotion.initial}
              animate={cardMotion.animate}
              exit={cardMotion.exit}
              transition={cardMotion.transition}
              className="relative z-10 w-[76%] max-w-[240px] rounded-xl bg-[#0A1322]/90 border border-cyan-400/50 px-3.5 py-2.5 shadow-lg shadow-black/60"
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="w-6 h-6 rounded-md bg-cyan-500/20 border border-cyan-400/60 flex items-center justify-center">
                    <span className="w-2.5 h-2.5 rounded-full bg-cyan-300" />
                  </span>
                  <div>
                    <div className="text-[11px] font-semibold text-slate-100">
                      Painel HILM
                    </div>
                    <div className="text-[10px] font-mono-tabular text-cyan-300/80">
                      {config.enabled ? `${config.durationMs} ms` : "Instantâneo"}
                    </div>
                  </div>
                </div>
                <span className="w-2 h-2 rounded-full bg-amber-400" />
              </div>
              <div className="mt-2 h-1.5 w-full rounded-full bg-slate-800 overflow-hidden">
                <div className="h-full w-2/3 bg-gradient-to-r from-cyan-400 to-amber-400 rounded-full" />
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Single-shot Effect Accent Overlay on Preview */}
        {config.enabled && !prefersReducedMotion && (
          <div
            key={`burst-${burstKey}`}
            className="pointer-events-none absolute inset-0 flex items-center justify-center"
          >
            {config.effect === "portal" && (
              <motion.div
                initial={{ scale: 0.35, opacity: 0.85, rotate: -15 }}
                animate={{ scale: 2.1, opacity: 0, rotate: 55 }}
                transition={{ duration: durSec, ease: [0.16, 1, 0.3, 1] }}
                className="w-20 h-20 rounded-full border border-cyan-400/70"
                style={{
                  boxShadow:
                    "inset 0 0 12px rgba(34,211,238,0.45), 0 0 14px rgba(251,146,60,0.3)",
                }}
              />
            )}

            {config.effect === "digital-disintegration" &&
              PREVIEW_MOTES.map((mote, i) => (
                <motion.span
                  key={i}
                  initial={{ x: 0, y: 0, opacity: 0.9, scale: 1 }}
                  animate={{ x: mote.dx * 1.4, y: mote.dy * 1.3, opacity: 0, scale: 0.35 }}
                  transition={{ duration: durSec, ease: "easeOut" }}
                  className="absolute rounded-[2px]"
                  style={{
                    width: `${mote.size}px`,
                    height: `${mote.size}px`,
                    backgroundColor: mote.color,
                  }}
                />
              ))}

            {config.effect === "light-sweep" && (
              <motion.div
                initial={{ x: "-120%", opacity: 0.8 }}
                animate={{ x: "140%", opacity: 0 }}
                transition={{ duration: durSec, ease: "easeInOut" }}
                className="w-12 h-full -skew-x-12"
                style={{
                  background:
                    "linear-gradient(90deg, transparent, rgba(34,211,238,0.55), rgba(251,146,60,0.35), transparent)",
                }}
              />
            )}

            {config.effect === "energy-dissolve" && (
              <motion.div
                initial={{ scale: 0.7, opacity: 0.65 }}
                animate={{ scale: 1.7, opacity: 0 }}
                transition={{ duration: durSec, ease: "easeOut" }}
                className="w-36 h-20 rounded-2xl"
                style={{
                  background:
                    "radial-gradient(circle, rgba(34,211,238,0.35) 0%, rgba(251,146,60,0.18) 55%, transparent 78%)",
                }}
              />
            )}
          </div>
        )}
      </div>
    </div>
  );
}
