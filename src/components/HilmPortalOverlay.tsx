import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import type {
  IconPackId,
  InstalledAppItem,
  LauncherThemeId,
  TransitionEffectId,
} from "../types/launcher";
import { usesExpandedGlyphScale } from "../data/iconPackRegistry";
import { AppGlyph } from "./AppGlyph";

export interface HilmPortalOrigin {
  x: number;
  y: number;
  size: number;
}

export interface HilmPortalLaunchState {
  key: number;
  app: InstalledAppItem;
  origin: HilmPortalOrigin;
  customIconDataUrl?: string;
  effect: TransitionEffectId;
  durationMs: number;
}

interface HilmPortalOverlayProps {
  launch: HilmPortalLaunchState | null;
  iconPack: IconPackId;
  themeId?: LauncherThemeId;
}

const DIGITAL_PARTICLES = [
  { x: -34, y: -28, color: "#22D3EE", size: 4.5 },
  { x: 32, y: -30, color: "#FB923C", size: 4 },
  { x: -46, y: 6, color: "#E2E8F0", size: 3.5 },
  { x: 44, y: 12, color: "#22D3EE", size: 4.5 },
  { x: -20, y: -44, color: "#F59E0B", size: 4 },
  { x: 22, y: 38, color: "#22D3EE", size: 4 },
  { x: -30, y: 34, color: "#FB923C", size: 3.5 },
  { x: 18, y: -42, color: "#E2E8F0", size: 3.5 },
  { x: -50, y: -14, color: "#22D3EE", size: 3.5 },
  { x: 48, y: -12, color: "#F59E0B", size: 4 },
  { x: 0, y: 46, color: "#FB923C", size: 4 },
  { x: 0, y: -48, color: "#22D3EE", size: 4.5 },
];

const LUNA_DIGITAL_PARTICLES = [
  { x: -34, y: -28, color: "#00E5FF", size: 4.5 },
  { x: 32, y: -30, color: "#398BFF", size: 4 },
  { x: -46, y: 6, color: "#D9F7FF", size: 3.5 },
  { x: 44, y: 12, color: "#00E5FF", size: 4.5 },
  { x: -20, y: -44, color: "#FFB45C", size: 3.5 },
  { x: 22, y: 38, color: "#398BFF", size: 4 },
  { x: -30, y: 34, color: "#00E5FF", size: 3.5 },
  { x: 18, y: -42, color: "#D9F7FF", size: 3.5 },
  { x: -50, y: -14, color: "#00E5FF", size: 3.5 },
  { x: 48, y: -12, color: "#398BFF", size: 4 },
  { x: 0, y: 46, color: "#00E5FF", size: 4 },
  { x: 0, y: -48, color: "#D9F7FF", size: 4 },
];

function getIconSurgeMotion(effect: TransitionEffectId, durSec: number) {
  const iconDur = Math.max(0.16, durSec * 0.78);
  switch (effect) {
    case "portal":
      return {
        initial: { scale: 0.96, opacity: 1, rotate: 0 },
        animate: { scale: 1.72, opacity: 0, rotate: 24 },
        transition: { duration: iconDur, ease: [0.16, 1, 0.3, 1] as const },
      };

    case "digital-disintegration":
      return {
        initial: {
          scale: 1,
          opacity: 1,
          filter: "blur(0px) brightness(1)",
          clipPath: "inset(0% 0% 0% 0%)",
        },
        animate: {
          scale: 1.24,
          opacity: 0,
          filter: "blur(6px) brightness(1.6)",
          clipPath: "inset(18% 12% 18% 12%)",
        },
        transition: { duration: iconDur, ease: [0.22, 1, 0.36, 1] as const },
      };

    case "light-sweep":
      return {
        initial: {
          scale: 1,
          x: 0,
          opacity: 1,
          clipPath: "inset(0% 0% 0% 0%)",
        },
        animate: {
          scale: 1.12,
          x: 16,
          opacity: 0,
          clipPath: "inset(0% 0% 0% 100%)",
        },
        transition: { duration: iconDur, ease: [0.2, 0.9, 0.25, 1] as const },
      };

    case "dimensional-slide":
      return {
        initial: { scale: 1, rotateY: 0, rotateX: 0, y: 0, opacity: 1 },
        animate: { scale: 1.44, rotateY: -44, rotateX: 18, y: -16, opacity: 0 },
        transition: { duration: iconDur, ease: [0.18, 0.88, 0.28, 1] as const },
      };

    case "energy-dissolve":
      return {
        initial: { scale: 1, opacity: 1, filter: "brightness(1) blur(0px)" },
        animate: { scale: 1.46, opacity: 0, filter: "brightness(2) blur(5px)" },
        transition: { duration: iconDur, ease: [0.4, 0, 0.2, 1] as const },
      };

    case "classic":
    default:
      return {
        initial: { scale: 1, opacity: 1 },
        animate: { scale: 1.22, opacity: 0 },
        transition: { duration: Math.min(0.18, iconDur), ease: "easeOut" as const },
      };
  }
}

export function HilmPortalOverlay({
  launch,
  iconPack,
  themeId = "default",
}: HilmPortalOverlayProps) {
  const prefersReducedMotion = useReducedMotion();

  if (prefersReducedMotion) {
    return null;
  }

  const isLunaTheme = themeId === "luna-entre-luz-e-sombra";
  const particles = isLunaTheme ? LUNA_DIGITAL_PARTICLES : DIGITAL_PARTICLES;

  const durSec = launch
    ? Math.max(0.18, Math.min(0.6, launch.durationMs / 1000))
    : 0.28;
  const iconMotion = launch ? getIconSurgeMotion(launch.effect, durSec) : null;

  return (
    <AnimatePresence>
      {launch && iconMotion && (
        <div
          key={launch.key}
          className="pointer-events-none absolute inset-0 z-40 overflow-hidden"
          aria-hidden="true"
        >
          {launch.effect === "light-sweep" && (
            <motion.div
              initial={{ x: "-130%", opacity: 0.92 }}
              animate={{ x: "145%", opacity: 0 }}
              transition={{
                duration: durSec,
                ease: [0.2, 0.9, 0.25, 1],
              }}
              className="absolute inset-y-0 w-24 -skew-x-12"
              style={{
                background:
                  "linear-gradient(90deg, rgba(34,211,238,0) 0%, rgba(34,211,238,0.5) 44%, rgba(251,146,60,0.4) 68%, rgba(251,146,60,0) 100%)",
              }}
            />
          )}

          {/* Effect Anchor positioned strictly at tapped icon center */}
          <div
            className="absolute"
            style={{
              left: `${launch.origin.x}px`,
              top: `${launch.origin.y}px`,
              width: "160px",
              height: "160px",
              transform: "translate(-50%, -50%)",
              perspective: "420px",
            }}
          >
            {launch.effect === "portal" && (
              <>
                <motion.div
                  initial={{ scale: 0.22, opacity: 0.0 }}
                  animate={{ scale: [0.22, 1.35, 3.3], opacity: [0.15, 0.85, 0] }}
                  transition={{ duration: durSec, times: [0, 0.34, 1], ease: [0.16, 1, 0.3, 1] }}
                  className="absolute inset-0 rounded-full"
                  style={{
                    background: isLunaTheme
                      ? "radial-gradient(circle, rgba(5, 7, 11, 0.90) 0%, rgba(10, 20, 36, 0.72) 42%, rgba(0, 229, 255, 0.28) 64%, rgba(57, 139, 255, 0.18) 78%, rgba(0, 229, 255, 0) 94%)"
                      : "radial-gradient(circle, rgba(3, 9, 20, 0.90) 0%, rgba(7, 22, 38, 0.72) 42%, rgba(34, 211, 238, 0.24) 64%, rgba(251, 146, 60, 0.16) 78%, rgba(34, 211, 238, 0) 94%)",
                  }}
                />

                <motion.div
                  initial={{ scale: 0.28, opacity: 0.85 }}
                  animate={{ scale: 3.1, opacity: 0 }}
                  transition={{ duration: durSec * 0.95, ease: [0.18, 0.88, 0.28, 1] }}
                  className="absolute inset-3 rounded-full"
                  style={{
                    boxShadow: isLunaTheme
                      ? "inset 0 0 18px rgba(0, 229, 255, 0.45), 0 0 16px rgba(57, 139, 255, 0.32)"
                      : "inset 0 0 18px rgba(34, 211, 238, 0.42), 0 0 16px rgba(251, 146, 60, 0.24)",
                    border: isLunaTheme
                      ? "1px solid rgba(0, 229, 255, 0.56)"
                      : "1px solid rgba(34, 211, 238, 0.48)",
                  }}
                />

                <motion.svg
                  viewBox="0 0 160 160"
                  initial={{ scale: 0.32, rotate: -18, opacity: 0.92 }}
                  animate={{ scale: 2.85, rotate: 68, opacity: 0 }}
                  transition={{ duration: durSec, ease: [0.16, 1, 0.3, 1] }}
                  className="absolute inset-0 w-full h-full"
                >
                  <circle
                    cx="80"
                    cy="80"
                    r="66"
                    fill="none"
                    stroke={isLunaTheme ? "rgba(0, 229, 255, 0.84)" : "rgba(34, 211, 238, 0.78)"}
                    strokeWidth="1.6"
                    strokeDasharray="34 14 14 14"
                  />
                  <circle
                    cx="80"
                    cy="80"
                    r="57"
                    fill="none"
                    stroke={isLunaTheme ? "rgba(217, 247, 255, 0.5)" : "rgba(165, 243, 252, 0.4)"}
                    strokeWidth="0.9"
                    strokeDasharray="10 20"
                  />
                </motion.svg>

                <motion.svg
                  viewBox="0 0 160 160"
                  initial={{ scale: 0.28, rotate: 14, opacity: 0.88 }}
                  animate={{ scale: 2.35, rotate: -86, opacity: 0 }}
                  transition={{ duration: durSec * 0.92, ease: [0.16, 1, 0.3, 1] }}
                  className="absolute inset-0 w-full h-full"
                >
                  <circle
                    cx="80"
                    cy="80"
                    r="46"
                    fill="none"
                    stroke={isLunaTheme ? "rgba(57, 139, 255, 0.82)" : "rgba(251, 146, 60, 0.78)"}
                    strokeWidth="1.6"
                    strokeDasharray="24 18 8 18"
                  />
                  <circle
                    cx="80"
                    cy="80"
                    r="38"
                    fill="none"
                    stroke={isLunaTheme ? "rgba(255, 180, 92, 0.45)" : "rgba(245, 158, 11, 0.48)"}
                    strokeWidth="1"
                    strokeDasharray="4 10"
                  />
                </motion.svg>

                <motion.div
                  initial={{ scaleX: 0.25, scaleY: 0.8, opacity: 0.65 }}
                  animate={{ scaleX: 1.9, scaleY: 0.3, opacity: 0 }}
                  transition={{ duration: durSec * 0.72, ease: "easeOut" }}
                  className="absolute left-2 right-2 top-1/2 -translate-y-1/2 h-[2px]"
                  style={{
                    background: isLunaTheme
                      ? "linear-gradient(90deg, rgba(0,229,255,0) 0%, rgba(0,229,255,0.7) 35%, rgba(57,139,255,0.7) 65%, rgba(57,139,255,0) 100%)"
                      : "linear-gradient(90deg, rgba(34,211,238,0) 0%, rgba(34,211,238,0.65) 35%, rgba(251,146,60,0.65) 65%, rgba(251,146,60,0) 100%)",
                  }}
                />
              </>
            )}

            {launch.effect === "digital-disintegration" && (
              <div className="absolute inset-0 flex items-center justify-center">
                {particles.map((p, idx) => (
                  <motion.span
                    key={idx}
                    initial={{ x: 0, y: 0, scale: 1.1, opacity: 0.95 }}
                    animate={{ x: p.x * 1.75, y: p.y * 1.75, scale: 0.25, opacity: 0 }}
                    transition={{ duration: durSec, ease: [0.16, 1, 0.3, 1] }}
                    className="absolute rounded-[1.5px]"
                    style={{
                      width: `${p.size}px`,
                      height: `${p.size}px`,
                      backgroundColor: p.color,
                    }}
                  />
                ))}
              </div>
            )}

            {launch.effect === "light-sweep" && (
              <motion.div
                initial={{ scaleX: 0.2, opacity: 0.9 }}
                animate={{ scaleX: 2.2, opacity: 0 }}
                transition={{ duration: durSec * 0.85, ease: "easeOut" }}
                className="absolute left-0 right-0 top-1/2 -translate-y-1/2 h-[3px]"
                style={{
                  background:
                    "linear-gradient(90deg, transparent, rgba(34,211,238,0.9), rgba(251,146,60,0.8), transparent)",
                }}
              />
            )}

            {launch.effect === "energy-dissolve" && (
              <motion.div
                initial={{ scale: 0.35, opacity: 0.86 }}
                animate={{ scale: 2.6, opacity: 0 }}
                transition={{ duration: durSec, ease: "easeOut" }}
                className="absolute inset-0 rounded-full"
                style={{
                  background:
                    "radial-gradient(circle, rgba(34, 211, 238, 0.44) 0%, rgba(168, 85, 247, 0.32) 45%, rgba(251, 146, 60, 0.18) 70%, transparent 92%)",
                }}
              />
            )}

            {launch.effect === "dimensional-slide" && (
              <motion.div
                initial={{ scale: 0.55, opacity: 0.85, rotateX: 32, rotateY: -22 }}
                animate={{ scale: 2.4, opacity: 0, rotateX: 0, rotateY: 0 }}
                transition={{ duration: durSec, ease: [0.18, 0.88, 0.28, 1] }}
                className="absolute inset-4 rounded-2xl border border-cyan-400/65"
                style={{ boxShadow: "0 0 18px rgba(34, 211, 238, 0.34)" }}
              />
            )}

            {/* Tapped icon surging with per-effect transition style */}
            <motion.div
              initial={iconMotion.initial}
              animate={iconMotion.animate}
              transition={iconMotion.transition}
              className="absolute inset-0 flex items-center justify-center"
            >
              <div
                className="flex items-center justify-center"
                style={{
                  width: `${Math.max(26, launch.origin.size)}px`,
                  height: `${Math.max(26, launch.origin.size)}px`,
                  filter: "drop-shadow(0 0 8px rgba(34, 211, 238, 0.42))",
                }}
              >
                <AppGlyph
                  app={launch.app}
                  iconPack={iconPack}
                  customIconDataUrl={launch.customIconDataUrl}
                  sizeClass={
                    usesExpandedGlyphScale(iconPack) ? "w-full h-full" : "w-[82%] h-[82%]"
                  }
                />
              </div>
            </motion.div>
          </div>
        </div>
      )}
    </AnimatePresence>
  );
}
