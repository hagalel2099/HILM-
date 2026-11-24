import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import type { IconPackId, InstalledAppItem, TransitionEffectId } from "../types/launcher";
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
}

const DIGITAL_PARTICLES = [
  { x: -34, y: -28, color: "#22D3EE", size: 4 },
  { x: 32, y: -30, color: "#FB923C", size: 3.5 },
  { x: -46, y: 6, color: "#E2E8F0", size: 3 },
  { x: 44, y: 12, color: "#22D3EE", size: 4 },
  { x: -20, y: -44, color: "#F59E0B", size: 3.5 },
  { x: 22, y: 38, color: "#22D3EE", size: 3.5 },
  { x: -30, y: 34, color: "#FB923C", size: 3 },
  { x: 18, y: -42, color: "#E2E8F0", size: 3 },
  { x: -50, y: -14, color: "#22D3EE", size: 3 },
  { x: 48, y: -12, color: "#F59E0B", size: 3.5 },
  { x: 0, y: 46, color: "#FB923C", size: 3.5 },
  { x: 0, y: -48, color: "#22D3EE", size: 4 },
];

export function HilmPortalOverlay({ launch, iconPack }: HilmPortalOverlayProps) {
  const prefersReducedMotion = useReducedMotion();

  if (prefersReducedMotion) {
    return null;
  }

  return (
    <AnimatePresence>
      {launch && launch.effect !== "classic" && (
        <div
          key={launch.key}
          className="pointer-events-none absolute inset-0 z-40 overflow-hidden"
          aria-hidden="true"
        >
          {launch.effect === "light-sweep" && (
            <motion.div
              initial={{ x: "-130%", opacity: 0.85 }}
              animate={{ x: "145%", opacity: 0 }}
              transition={{
                duration: Math.max(0.22, Math.min(0.55, launch.durationMs / 1000)),
                ease: [0.2, 0.9, 0.25, 1],
              }}
              className="absolute inset-y-0 w-24 -skew-x-12"
              style={{
                background:
                  "linear-gradient(90deg, rgba(34,211,238,0) 0%, rgba(34,211,238,0.42) 44%, rgba(251,146,60,0.34) 68%, rgba(251,146,60,0) 100%)",
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
            }}
          >
            {launch.effect === "portal" && (
              <>
                <motion.div
                  initial={{ scale: 0.22, opacity: 0.0 }}
                  animate={{ scale: [0.22, 1.35, 3.3], opacity: [0.15, 0.82, 0] }}
                  transition={{ duration: 0.36, times: [0, 0.34, 1], ease: [0.16, 1, 0.3, 1] }}
                  className="absolute inset-0 rounded-full"
                  style={{
                    background:
                      "radial-gradient(circle, rgba(3, 9, 20, 0.90) 0%, rgba(7, 22, 38, 0.72) 42%, rgba(34, 211, 238, 0.24) 64%, rgba(251, 146, 60, 0.16) 78%, rgba(34, 211, 238, 0) 94%)",
                  }}
                />

                <motion.div
                  initial={{ scale: 0.28, opacity: 0.85 }}
                  animate={{ scale: 3.1, opacity: 0 }}
                  transition={{ duration: 0.34, ease: [0.18, 0.88, 0.28, 1] }}
                  className="absolute inset-3 rounded-full"
                  style={{
                    boxShadow:
                      "inset 0 0 18px rgba(34, 211, 238, 0.38), 0 0 16px rgba(251, 146, 60, 0.22)",
                    border: "1px solid rgba(34, 211, 238, 0.45)",
                  }}
                />

                <motion.svg
                  viewBox="0 0 160 160"
                  initial={{ scale: 0.32, rotate: -18, opacity: 0.92 }}
                  animate={{ scale: 2.85, rotate: 68, opacity: 0 }}
                  transition={{ duration: 0.36, ease: [0.16, 1, 0.3, 1] }}
                  className="absolute inset-0 w-full h-full"
                >
                  <circle
                    cx="80"
                    cy="80"
                    r="66"
                    fill="none"
                    stroke="rgba(34, 211, 238, 0.72)"
                    strokeWidth="1.5"
                    strokeDasharray="34 14 14 14"
                  />
                  <circle
                    cx="80"
                    cy="80"
                    r="57"
                    fill="none"
                    stroke="rgba(165, 243, 252, 0.35)"
                    strokeWidth="0.9"
                    strokeDasharray="10 20"
                  />
                </motion.svg>

                <motion.svg
                  viewBox="0 0 160 160"
                  initial={{ scale: 0.28, rotate: 14, opacity: 0.88 }}
                  animate={{ scale: 2.35, rotate: -86, opacity: 0 }}
                  transition={{ duration: 0.33, ease: [0.16, 1, 0.3, 1] }}
                  className="absolute inset-0 w-full h-full"
                >
                  <circle
                    cx="80"
                    cy="80"
                    r="46"
                    fill="none"
                    stroke="rgba(251, 146, 60, 0.72)"
                    strokeWidth="1.5"
                    strokeDasharray="24 18 8 18"
                  />
                  <circle
                    cx="80"
                    cy="80"
                    r="38"
                    fill="none"
                    stroke="rgba(245, 158, 11, 0.45)"
                    strokeWidth="1"
                    strokeDasharray="4 10"
                  />
                </motion.svg>

                <motion.div
                  initial={{ scaleX: 0.25, scaleY: 0.8, opacity: 0.65 }}
                  animate={{ scaleX: 1.9, scaleY: 0.3, opacity: 0 }}
                  transition={{ duration: 0.24, ease: "easeOut" }}
                  className="absolute left-2 right-2 top-1/2 -translate-y-1/2 h-[2px]"
                  style={{
                    background:
                      "linear-gradient(90deg, rgba(34,211,238,0) 0%, rgba(34,211,238,0.65) 35%, rgba(251,146,60,0.65) 65%, rgba(251,146,60,0) 100%)",
                  }}
                />
              </>
            )}

            {launch.effect === "digital-disintegration" && (
              <div className="absolute inset-0 flex items-center justify-center">
                {DIGITAL_PARTICLES.map((p, idx) => (
                  <motion.span
                    key={idx}
                    initial={{ x: 0, y: 0, scale: 1, opacity: 0.92 }}
                    animate={{ x: p.x * 1.7, y: p.y * 1.7, scale: 0.25, opacity: 0 }}
                    transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
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

            {launch.effect === "energy-dissolve" && (
              <motion.div
                initial={{ scale: 0.35, opacity: 0.78 }}
                animate={{ scale: 2.6, opacity: 0 }}
                transition={{ duration: 0.35, ease: "easeOut" }}
                className="absolute inset-0 rounded-full"
                style={{
                  background:
                    "radial-gradient(circle, rgba(34, 211, 238, 0.36) 0%, rgba(168, 85, 247, 0.24) 45%, rgba(251, 146, 60, 0.14) 70%, transparent 92%)",
                }}
              />
            )}

            {launch.effect === "dimensional-slide" && (
              <motion.div
                initial={{ scale: 0.55, opacity: 0.8, rotateX: 28 }}
                animate={{ scale: 2.4, opacity: 0, rotateX: 0 }}
                transition={{ duration: 0.3, ease: [0.18, 0.88, 0.28, 1] }}
                className="absolute inset-4 rounded-2xl border border-cyan-400/55"
                style={{ boxShadow: "0 0 18px rgba(34, 211, 238, 0.28)" }}
              />
            )}

            {/* Tapped icon surging into the launch transition */}
            <motion.div
              initial={{ scale: 0.96, opacity: 1 }}
              animate={{ scale: 1.48, opacity: 0 }}
              transition={{ duration: 0.16, ease: [0.22, 1, 0.36, 1] }}
              className="absolute inset-0 flex items-center justify-center"
            >
              <div
                className="flex items-center justify-center"
                style={{
                  width: `${Math.max(24, launch.origin.size)}px`,
                  height: `${Math.max(24, launch.origin.size)}px`,
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
