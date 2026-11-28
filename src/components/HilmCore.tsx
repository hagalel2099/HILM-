import { useEffect, useRef } from "react";
import type { LauncherThemeId } from "../types/launcher";

export type HilmCoreStatus = "nominal" | "syncing" | "alert" | "resolved";

interface HilmCoreProps {
  status?: HilmCoreStatus;
  themeId?: LauncherThemeId;
  isPaused?: boolean;
  onClick?: () => void;
}

const HILM_CORE_IMAGE = "/assets/hilm_central_core.png";
const MAYA_WALLPAPER_IMAGE = "/assets/themes/maya/wallpaper.jpg";

interface IndependentOrbSpec {
  name: string;
  periodMs: number;
  basePhase: number;
  semiMajor: number;
  eccentricity: number;
  argPeriapsisRad: number;
  inclinationDeg: number;
  yawDeg: number;
  rollDeg: number;
  coreColor: string;
  midColor: string;
  edgeColor: string;
  haloRgba: string;
  trailRgb: string;
  trailCoreRgb: string;
}

const DEFAULT_ORB_SPECS: IndependentOrbSpec[] = [
  {
    name: "Íris",
    periodMs: 4900,
    basePhase: 0.45,
    semiMajor: 53.5,
    eccentricity: 0.12,
    argPeriapsisRad: 1.11,
    inclinationDeg: 61.5,
    yawDeg: 9.9,
    rollDeg: -28.0,
    coreColor: "#FFF3E0",
    midColor: "#FB923C",
    edgeColor: "#C2410C",
    haloRgba: "rgba(234, 88, 12, 0.70)",
    trailRgb: "234, 88, 12",
    trailCoreRgb: "253, 186, 116",
  },
  {
    name: "Luna",
    periodMs: 6300,
    basePhase: 2.55,
    semiMajor: 57.5,
    eccentricity: 0.14,
    argPeriapsisRad: 1.66,
    inclinationDeg: -63.1,
    yawDeg: 3.9,
    rollDeg: 26.0,
    coreColor: "#F5F3FF",
    midColor: "#A855F7",
    edgeColor: "#6D28D9",
    haloRgba: "rgba(139, 92, 246, 0.72)",
    trailRgb: "139, 92, 246",
    trailCoreRgb: "221, 214, 254",
  },
  {
    name: "Maya",
    periodMs: 7900,
    basePhase: 4.65,
    semiMajor: 61.5,
    eccentricity: 0.12,
    argPeriapsisRad: 2.19,
    inclinationDeg: 67.0,
    yawDeg: -2.5,
    rollDeg: 72.0,
    coreColor: "#FFFDF2",
    midColor: "#FBBF24",
    edgeColor: "#B45309",
    haloRgba: "rgba(245, 158, 11, 0.70)",
    trailRgb: "217, 119, 6",
    trailCoreRgb: "253, 224, 71",
  },
];

const MAYA_ORB_SPECS: IndependentOrbSpec[] = [
  {
    name: "Maya Gold",
    periodMs: 5200,
    basePhase: 0.55,
    semiMajor: 56.5,
    eccentricity: 0.11,
    argPeriapsisRad: 1.05,
    inclinationDeg: 62.0,
    yawDeg: 9.0,
    rollDeg: -26.0,
    coreColor: "#FFF8DC",
    midColor: "#F5B942",
    edgeColor: "#B46914",
    haloRgba: "rgba(245, 185, 66, 0.74)",
    trailRgb: "245, 185, 66",
    trailCoreRgb: "255, 228, 158",
  },
  {
    name: "Electric Cyan",
    periodMs: 6500,
    basePhase: 2.65,
    semiMajor: 60.0,
    eccentricity: 0.13,
    argPeriapsisRad: 1.72,
    inclinationDeg: -64.0,
    yawDeg: 4.5,
    rollDeg: 28.0,
    coreColor: "#E6FAFF",
    midColor: "#38BDF8",
    edgeColor: "#1D4ED8",
    haloRgba: "rgba(37, 99, 235, 0.72)",
    trailRgb: "37, 99, 235",
    trailCoreRgb: "125, 211, 252",
  },
  {
    name: "Copper Ember",
    periodMs: 7800,
    basePhase: 4.7,
    semiMajor: 63.0,
    eccentricity: 0.12,
    argPeriapsisRad: 2.25,
    inclinationDeg: 68.0,
    yawDeg: -3.0,
    rollDeg: 70.0,
    coreColor: "#FFEDD5",
    midColor: "#C47A48",
    edgeColor: "#7C3D18",
    haloRgba: "rgba(196, 122, 72, 0.72)",
    trailRgb: "196, 122, 72",
    trailCoreRgb: "253, 186, 116",
  },
];

const VIEW_SIZE = 214;
const CORE_DRAW_SIZE = 108;
const CAMERA_DISTANCE = 240;
const TRAIL_ARC_RAD = 2.65;
const TRAIL_SEGMENTS = 126;
const PULSE_PERIOD_MS = 4600;

interface Vec3 {
  x: number;
  y: number;
  z: number;
}

interface ProjectedPoint {
  x: number;
  y: number;
  z: number;
  scale: number;
}

function clamp01(v: number): number {
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

function hexToRgbTuple(hex: string): [number, number, number] {
  const clean = hex.replace("#", "");
  const num = Number.parseInt(clean, 16);
  return [(num >> 16) & 255, (num >> 8) & 255, num & 255];
}

function parseRgbString(rgb: string): [number, number, number] {
  const parts = rgb.split(",").map((s) => Number.parseInt(s.trim(), 10));
  return [parts[0] || 0, parts[1] || 0, parts[2] || 0];
}

function lerpRgbCss(
  a: [number, number, number],
  b: [number, number, number],
  t: number
): string {
  const f = clamp01(t);
  const r = Math.round(a[0] + (b[0] - a[0]) * f);
  const g = Math.round(a[1] + (b[1] - a[1]) * f);
  const bl = Math.round(a[2] + (b[2] - a[2]) * f);
  return `rgb(${r}, ${g}, ${bl})`;
}

function computeBaseOrb3D(orb: IndependentOrbSpec, elapsedMs: number): Vec3 {
  const meanAnomaly = (elapsedMs / orb.periodMs) * Math.PI * 2 + orb.basePhase;
  const e = orb.eccentricity;
  let E = meanAnomaly;
  for (let i = 0; i < 4; i++) {
    E -= (E - e * Math.sin(E) - meanAnomaly) / (1 - e * Math.cos(E));
  }

  const xOrb = orb.semiMajor * (Math.cos(E) - e);
  const yOrb = orb.semiMajor * Math.sqrt(1 - e * e) * Math.sin(E);

  const cosW = Math.cos(orb.argPeriapsisRad);
  const sinW = Math.sin(orb.argPeriapsisRad);
  const xp = xOrb * cosW - yOrb * sinW;
  const yp = xOrb * sinW + yOrb * cosW;

  const incRad = (orb.inclinationDeg * Math.PI) / 180;
  const x1 = xp;
  const y1 = yp * Math.cos(incRad);
  const z1 = yp * Math.sin(incRad);

  const yawRad = (orb.yawDeg * Math.PI) / 180;
  const cosY = Math.cos(yawRad);
  const sinY = Math.sin(yawRad);
  const x2 = x1 * cosY + z1 * sinY;
  const y2 = y1;
  const z2 = -x1 * sinY + z1 * cosY;

  const rollRad = (orb.rollDeg * Math.PI) / 180;
  const cosR = Math.cos(rollRad);
  const sinR = Math.sin(rollRad);
  return {
    x: x2 * cosR - y2 * sinR,
    y: x2 * sinR + y2 * cosR,
    z: z2,
  };
}

function sampleAllOrbsAtTime(
  orbSpecs: IndependentOrbSpec[],
  elapsedMs: number,
  cx: number,
  cy: number
): ProjectedPoint[] {
  const adj = orbSpecs.map((orb) => computeBaseOrb3D(orb, elapsedMs));

  for (let i = 0; i < adj.length; i++) {
    for (let j = i + 1; j < adj.length; j++) {
      const dx = adj[j].x - adj[i].x;
      const dy = adj[j].y - adj[i].y;
      const dz = adj[j].z - adj[i].z;
      const d3 = Math.sqrt(dx * dx + dy * dy + dz * dz + 1e-4);
      const w3 = Math.exp(-((d3 / 18.0) * (d3 / 18.0)));
      const step3 = 5.5 * w3;
      const ux = dx / d3;
      const uy = dy / d3;
      const uz = dz / d3;
      adj[j].x += ux * step3;
      adj[j].y += uy * step3;
      adj[j].z += uz * step3;
      adj[i].x -= ux * step3;
      adj[i].y -= uy * step3;
      adj[i].z -= uz * step3;
    }
  }

  return adj.map((p) => {
    const perspective = CAMERA_DISTANCE / (CAMERA_DISTANCE - p.z);
    return {
      x: cx + p.x * perspective,
      y: cy + p.y * perspective,
      z: p.z,
      scale: perspective,
    };
  });
}

function sampleSingleOrbTrail(
  orbSpecs: IndependentOrbSpec[],
  orbIndex: number,
  elapsedMs: number,
  cx: number,
  cy: number
): ProjectedPoint[] {
  const orb = orbSpecs[orbIndex];
  const trailWindowMs = (TRAIL_ARC_RAD / (Math.PI * 2)) * orb.periodMs;
  const trail: ProjectedPoint[] = [];
  for (let i = 0; i <= TRAIL_SEGMENTS; i++) {
    const frac = i / TRAIL_SEGMENTS;
    const tSample = elapsedMs - frac * trailWindowMs;
    trail.push(sampleAllOrbsAtTime(orbSpecs, tSample, cx, cy)[orbIndex]);
  }
  return trail;
}

export function HilmCore({
  status = "nominal",
  themeId = "default",
  isPaused = false,
  onClick,
}: HilmCoreProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const coreImgRef = useRef<HTMLImageElement | null>(null);
  const mayaImgRef = useRef<HTMLImageElement | null>(null);
  const isPausedRef = useRef(isPaused);
  const syncAnimationLoopRef = useRef<(() => void) | null>(null);
  const isMayaTheme = themeId === "maya-holographic-presence";
  const isLunaTheme = themeId === "luna-entre-luz-e-sombra";

  useEffect(() => {
    isPausedRef.current = isPaused;
    syncAnimationLoopRef.current?.();
  }, [isPaused]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = VIEW_SIZE * dpr;
    canvas.height = VIEW_SIZE * dpr;

    const orbSpecs = isMayaTheme ? MAYA_ORB_SPECS : DEFAULT_ORB_SPECS;

    let imgLoaded = false;
    let mayaImgLoaded = false;
    let coreGlowCanvas: HTMLCanvasElement | null = null;
    let reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const img = new Image();
    img.src = HILM_CORE_IMAGE;
    img.onload = () => {
      imgLoaded = true;
      coreImgRef.current = img;

      const glowSize = Math.round(CORE_DRAW_SIZE * dpr);
      const glowCanvas = document.createElement("canvas");
      glowCanvas.width = glowSize;
      glowCanvas.height = glowSize;
      const gctx = glowCanvas.getContext("2d");
      if (gctx) {
        if ("filter" in gctx) {
          gctx.filter = `blur(${(3.2 * dpr).toFixed(1)}px)`;
        }
        gctx.drawImage(img, 0, 0, glowSize, glowSize);
        coreGlowCanvas = glowCanvas;
      }

      if (reduceMotion) {
        rafId = window.requestAnimationFrame(renderFrame);
      }
    };

    const mayaImg = new Image();
    if (isMayaTheme) {
      mayaImg.src = MAYA_WALLPAPER_IMAGE;
      mayaImg.onload = () => {
        mayaImgLoaded = true;
        mayaImgRef.current = mayaImg;
        if (reduceMotion) {
          rafId = window.requestAnimationFrame(renderFrame);
        }
      };
    }

    let rafId = 0;
    let lastFrameTimestamp: number | null = null;
    let simulationTimeMs = 0;

    const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    const syncLoop = () => {
      if (reduceMotion || document.hidden || isPausedRef.current) {
        if (rafId !== 0) {
          window.cancelAnimationFrame(rafId);
          rafId = 0;
        }
        lastFrameTimestamp = null;
      } else if (rafId === 0) {
        lastFrameTimestamp = null;
        rafId = window.requestAnimationFrame(renderFrame);
      }
    };
    syncAnimationLoopRef.current = syncLoop;

    const handleMotionChange = (e: MediaQueryListEvent) => {
      reduceMotion = e.matches;
      syncLoop();
    };
    mediaQuery.addEventListener("change", handleMotionChange);

    const handleVisibilityChange = () => {
      syncLoop();
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);

    const puffSprites = orbSpecs.map((orb) => {
      const sprite = document.createElement("canvas");
      sprite.width = 64;
      sprite.height = 64;
      const sctx = sprite.getContext("2d");
      if (sctx) {
        const g = sctx.createRadialGradient(32, 32, 0, 32, 32, 32);
        g.addColorStop(0, `rgba(${orb.trailCoreRgb}, 0.52)`);
        g.addColorStop(0.38, `rgba(${orb.trailRgb}, 0.28)`);
        g.addColorStop(0.75, `rgba(${orb.trailRgb}, 0.08)`);
        g.addColorStop(1, `rgba(${orb.trailRgb}, 0)`);
        sctx.fillStyle = g;
        sctx.fillRect(0, 0, 64, 64);
      }
      return sprite;
    });

    const precomputedTrailColors = orbSpecs.map((orb) => {
      const coreRgb = hexToRgbTuple(orb.coreColor);
      const midRgb = hexToRgbTuple(orb.midColor);
      const tailRgb = parseRgbString(orb.trailRgb);
      const plasmaColors: string[] = [];
      const filamentColors: string[] = [];

      for (let i = 0; i < TRAIL_SEGMENTS; i++) {
        const frac = (i + 0.5) / TRAIL_SEGMENTS;
        if (frac < 0.24) {
          plasmaColors.push(lerpRgbCss(coreRgb, midRgb, frac / 0.24));
        } else if (frac < 0.65) {
          plasmaColors.push(lerpRgbCss(midRgb, tailRgb, (frac - 0.24) / 0.41));
        } else {
          plasmaColors.push(`rgb(${tailRgb[0]}, ${tailRgb[1]}, ${tailRgb[2]})`);
        }

        const innerFrac = clamp01(frac / 0.68);
        filamentColors.push(lerpRgbCss(coreRgb, midRgb, innerFrac));
      }

      return { plasmaColors, filamentColors };
    });

    const drawCurvedTrailPass = (
      _orb: IndependentOrbSpec,
      orbIdx: number,
      points: ProjectedPoint[],
      drawFrontHalf: boolean
    ) => {
      const head = points[0];
      const headDepthMix = clamp01((head.z + 45) / 90);
      const headDepthDim = 0.38 + 0.62 * headDepthMix;
      const puffSprite = puffSprites[orbIdx];
      const { plasmaColors, filamentColors } = precomputedTrailColors[orbIdx];

      ctx.save();

      for (let i = TRAIL_SEGMENTS; i >= 1; i--) {
        const p = points[i];
        const isFront = p.z >= 0;
        if (isFront !== drawFrontHalf) continue;

        const frac = i / TRAIL_SEGMENTS;
        const mistFade = Math.pow(1 - frac, 1.28);
        const depthMix = clamp01((p.z + 45) / 90);
        const depthDim = 0.36 + 0.64 * depthMix;
        const distFromHead = Math.hypot(p.x - head.x, p.y - head.y);
        const comaBoost = 0.72 + 0.28 * Math.exp(-distFromHead / 26);
        const puffAlpha = 0.145 * mistFade * depthDim * comaBoost;
        if (puffAlpha <= 0.003) continue;

        const puffRadius = (10.2 * (1 - 0.54 * frac) + 2.2) * p.scale;
        ctx.globalAlpha = puffAlpha;
        ctx.drawImage(
          puffSprite,
          p.x - puffRadius,
          p.y - puffRadius,
          puffRadius * 2,
          puffRadius * 2
        );
      }

      ctx.lineCap = "round";
      ctx.lineJoin = "round";

      for (let i = TRAIL_SEGMENTS - 1; i >= 0; i--) {
        const p0 = points[i];
        const p1 = points[i + 1];
        const midZ = (p0.z + p1.z) * 0.5;
        const isFront = midZ >= 0;
        if (isFront !== drawFrontHalf) continue;

        const frac = (i + 0.5) / TRAIL_SEGMENTS;
        const taper = Math.pow(1 - frac, 1.15);
        const depthMix = clamp01((midZ + 45) / 90);
        const segAlpha =
          0.35 *
          Math.pow(1 - frac, 1.24) *
          (0.38 + 0.62 * depthMix) *
          (0.68 + 0.32 * headDepthDim);
        if (segAlpha <= 0.004) continue;

        const avgScale = (p0.scale + p1.scale) * 0.5;
        ctx.strokeStyle = plasmaColors[i];
        ctx.globalAlpha = Math.min(1, segAlpha);
        ctx.lineWidth = Math.max(0.45, 5.0 * avgScale * taper);
        ctx.beginPath();
        ctx.moveTo(p0.x, p0.y);
        ctx.lineTo(p1.x, p1.y);
        ctx.stroke();
      }

      const filamentLimit = Math.floor(TRAIL_SEGMENTS * 0.68);
      for (let i = filamentLimit - 1; i >= 0; i--) {
        const p0 = points[i];
        const p1 = points[i + 1];
        const midZ = (p0.z + p1.z) * 0.5;
        const isFront = midZ >= 0;
        if (isFront !== drawFrontHalf) continue;

        const innerFrac = (i + 0.5) / filamentLimit;
        const depthMix = clamp01((midZ + 45) / 90);
        const innerAlpha =
          0.44 *
          Math.pow(1 - innerFrac, 1.38) *
          (0.4 + 0.6 * depthMix) *
          (0.7 + 0.3 * headDepthDim);
        if (innerAlpha <= 0.005) continue;

        const avgScale = (p0.scale + p1.scale) * 0.5;
        const innerTaper = Math.pow(1 - innerFrac, 1.25);
        ctx.strokeStyle = filamentColors[i];
        ctx.globalAlpha = Math.min(1, innerAlpha);
        ctx.lineWidth = Math.max(0.35, 2.2 * avgScale * innerTaper);
        ctx.beginPath();
        ctx.moveTo(p0.x, p0.y);
        ctx.lineTo(p1.x, p1.y);
        ctx.stroke();
      }

      ctx.restore();
    };

    const drawOrbHeadPass = (
      orb: IndependentOrbSpec,
      head: ProjectedPoint,
      drawFrontHalf: boolean
    ) => {
      const isFront = head.z >= 0;
      if (isFront !== drawFrontHalf) return;

      const baseRadius = 6.0 * head.scale;
      const haloRadius = baseRadius * 2.5;
      const depthMix = clamp01((head.z + 45) / 90);
      const depthAlpha = 0.56 + 0.44 * depthMix;

      ctx.save();
      ctx.globalAlpha = depthAlpha;

      const halo = ctx.createRadialGradient(
        head.x,
        head.y,
        baseRadius * 0.2,
        head.x,
        head.y,
        haloRadius
      );
      halo.addColorStop(0, orb.haloRgba);
      halo.addColorStop(1, "rgba(0, 0, 0, 0)");
      ctx.fillStyle = halo;
      ctx.beginPath();
      ctx.arc(head.x, head.y, haloRadius, 0, Math.PI * 2);
      ctx.fill();

      const bodyGrad = ctx.createRadialGradient(
        head.x - baseRadius * 0.28,
        head.y - baseRadius * 0.28,
        baseRadius * 0.1,
        head.x,
        head.y,
        baseRadius
      );
      bodyGrad.addColorStop(0, orb.coreColor);
      bodyGrad.addColorStop(0.42, orb.midColor);
      bodyGrad.addColorStop(1, orb.edgeColor);

      ctx.fillStyle = bodyGrad;
      ctx.beginPath();
      ctx.arc(head.x, head.y, baseRadius, 0, Math.PI * 2);
      ctx.fill();

      ctx.restore();
    };

    const drawMayaNucleusCore = (cx: number, cy: number, pulse: number) => {
      const outerR = CORE_DRAW_SIZE * 0.47;
      const innerR = CORE_DRAW_SIZE * 0.36;

      ctx.save();

      // Subtle holographic backdrop seal for clear contrast without blocking wallpaper behind orbits
      const backdrop = ctx.createRadialGradient(cx, cy, innerR * 0.2, cx, cy, outerR * 1.18);
      backdrop.addColorStop(0, "rgba(13, 17, 24, 0.86)");
      backdrop.addColorStop(0.72, "rgba(16, 22, 32, 0.72)");
      backdrop.addColorStop(1, "rgba(13, 17, 24, 0)");
      ctx.fillStyle = backdrop;
      ctx.beginPath();
      ctx.arc(cx, cy, outerR * 1.18, 0, Math.PI * 2);
      ctx.fill();

      // Outer technological ceramic graphite ring with golden-honey & copper chamfer bezel
      ctx.beginPath();
      ctx.arc(cx, cy, outerR, 0, Math.PI * 2);
      ctx.fillStyle = "rgba(19, 23, 32, 0.90)";
      ctx.fill();

      const goldRing = ctx.createLinearGradient(cx - outerR, cy - outerR, cx + outerR, cy + outerR);
      goldRing.addColorStop(0, "#FFE49E");
      goldRing.addColorStop(0.38, "#F5B942");
      goldRing.addColorStop(0.72, "#C47A48");
      goldRing.addColorStop(1, "#F5B942");
      ctx.strokeStyle = goldRing;
      ctx.lineWidth = 2.4;
      ctx.stroke();

      // Electric-blue holographic inner circuit ring
      ctx.beginPath();
      ctx.arc(cx, cy, innerR + 3.5, 0, Math.PI * 2);
      ctx.strokeStyle = `rgba(56, 189, 248, ${(0.58 + 0.32 * pulse).toFixed(2)})`;
      ctx.lineWidth = 1.25;
      ctx.setLineDash([16, 7, 5, 7]);
      ctx.stroke();
      ctx.setLineDash([]);

      // Circular holographic portrait integration of Maya inside the nucleus
      ctx.save();
      ctx.beginPath();
      ctx.arc(cx, cy, innerR, 0, Math.PI * 2);
      ctx.clip();

      if (mayaImgLoaded && mayaImgRef.current) {
        const srcImg = mayaImgRef.current;
        const sx = srcImg.naturalWidth * 0.25;
        const sy = srcImg.naturalHeight * 0.045;
        const sw = srcImg.naturalWidth * 0.50;
        const sh = sw;
        ctx.globalAlpha = 0.92;
        ctx.drawImage(
          srcImg,
          sx,
          sy,
          sw,
          sh,
          cx - innerR,
          cy - innerR,
          innerR * 2,
          innerR * 2
        );

        // Subtle cyan/blue holographic projection tint & horizontal micro-scanlines
        const holoOverlay = ctx.createRadialGradient(cx, cy - 6, 4, cx, cy, innerR);
        holoOverlay.addColorStop(0, "rgba(0, 229, 255, 0.08)");
        holoOverlay.addColorStop(0.78, "rgba(37, 99, 235, 0.24)");
        holoOverlay.addColorStop(1, "rgba(13, 17, 24, 0.72)");
        ctx.fillStyle = holoOverlay;
        ctx.fillRect(cx - innerR, cy - innerR, innerR * 2, innerR * 2);

        ctx.strokeStyle = "rgba(125, 211, 252, 0.16)";
        ctx.lineWidth = 0.7;
        for (let y = cy - innerR; y <= cy + innerR; y += 3.2) {
          ctx.beginPath();
          ctx.moveTo(cx - innerR, y);
          ctx.lineTo(cx + innerR, y);
          ctx.stroke();
        }
      } else {
        const fallbackGrad = ctx.createRadialGradient(cx, cy, 4, cx, cy, innerR);
        fallbackGrad.addColorStop(0, "rgba(56, 189, 248, 0.65)");
        fallbackGrad.addColorStop(0.6, "rgba(196, 122, 72, 0.35)");
        fallbackGrad.addColorStop(1, "#111622");
        ctx.fillStyle = fallbackGrad;
        ctx.fillRect(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
      }

      // Warm golden-honey internal rim refraction
      ctx.beginPath();
      ctx.arc(cx, cy, innerR - 0.8, 0, Math.PI * 2);
      ctx.strokeStyle = `rgba(245, 185, 66, ${(0.55 + 0.35 * pulse).toFixed(2)})`;
      ctx.lineWidth = 1.4;
      ctx.stroke();
      ctx.restore();

      // Subtle copper & golden-honey 4-point holographic reticle brackets on outer edge
      const tickR1 = outerR + 1.5;
      const tickR2 = outerR + 6.5;
      ctx.strokeStyle = "#FFE49E";
      ctx.lineWidth = 1.5;
      [0, Math.PI * 0.5, Math.PI, Math.PI * 1.5].forEach((angle) => {
        ctx.beginPath();
        ctx.moveTo(cx + Math.cos(angle) * tickR1, cy + Math.sin(angle) * tickR1);
        ctx.lineTo(cx + Math.cos(angle) * tickR2, cy + Math.sin(angle) * tickR2);
        ctx.stroke();
      });

      ctx.restore();
    };

    const renderFrame = (now: number) => {
      if (!reduceMotion) {
        if (lastFrameTimestamp !== null) {
          const deltaMs = Math.min(Math.max(now - lastFrameTimestamp, 0), 100);
          simulationTimeMs += deltaMs;
        }
        lastFrameTimestamp = now;
      } else {
        lastFrameTimestamp = null;
      }

      const elapsed = reduceMotion ? 0 : simulationTimeMs;

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, VIEW_SIZE, VIEW_SIZE);

      const cx = VIEW_SIZE / 2;
      const cy = VIEW_SIZE / 2;

      const sortedByDepth = orbSpecs
        .map((orb, index) => {
          const trail = sampleSingleOrbTrail(orbSpecs, index, elapsed, cx, cy);
          return {
            orb,
            index,
            trail,
            head: trail[0],
          };
        })
        .sort((a, b) => a.head.z - b.head.z);

      for (const item of sortedByDepth) {
        drawCurvedTrailPass(item.orb, item.index, item.trail, false);
        drawOrbHeadPass(item.orb, item.head, false);
      }

      const activePeriodMs = isMayaTheme ? PULSE_PERIOD_MS : 4000;
      const pulseWave =
        0.5 - 0.5 * Math.cos((elapsed / activePeriodMs) * Math.PI * 2);
      const pulse = reduceMotion ? 0 : Math.pow(pulseWave, 1.15);

      if (isMayaTheme) {
        drawMayaNucleusCore(cx, cy, pulse);
      } else if (imgLoaded && coreImgRef.current) {
        const breathScale = 1 + 0.042 * pulse;
        const coreX = cx - CORE_DRAW_SIZE / 2;
        const coreY = cy - CORE_DRAW_SIZE / 2;

        ctx.save();
        ctx.translate(cx, cy);
        ctx.scale(breathScale, breathScale);
        ctx.translate(-cx, -cy);

        ctx.drawImage(
          coreImgRef.current,
          coreX,
          coreY,
          CORE_DRAW_SIZE,
          CORE_DRAW_SIZE
        );

        ctx.save();
        ctx.globalCompositeOperation = "lighter";
        ctx.globalAlpha = 0.03 + 0.38 * pulse;
        ctx.drawImage(
          coreImgRef.current,
          coreX,
          coreY,
          CORE_DRAW_SIZE,
          CORE_DRAW_SIZE
        );
        if (coreGlowCanvas) {
          ctx.globalAlpha = 0.03 + 0.22 * pulse;
          ctx.drawImage(
            coreGlowCanvas,
            coreX,
            coreY,
            CORE_DRAW_SIZE,
            CORE_DRAW_SIZE
          );
        }
        ctx.restore();
        ctx.restore();
      } else {
        ctx.beginPath();
        ctx.arc(cx, cy, 43, 0, Math.PI * 2);
        ctx.fillStyle = "#06080E";
        ctx.fill();
      }

      for (const item of sortedByDepth) {
        drawCurvedTrailPass(item.orb, item.index, item.trail, true);
        drawOrbHeadPass(item.orb, item.head, true);
      }

      if (!reduceMotion && !document.hidden && !isPausedRef.current) {
        rafId = window.requestAnimationFrame(renderFrame);
      } else {
        rafId = 0;
      }
    };

    if (!document.hidden && !isPausedRef.current) {
      rafId = window.requestAnimationFrame(renderFrame);
    }

    return () => {
      syncAnimationLoopRef.current = null;
      window.cancelAnimationFrame(rafId);
      mediaQuery.removeEventListener("change", handleMotionChange);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [isMayaTheme]);

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={
        isMayaTheme
          ? "Núcleo HILM — Maya Holographic Presence"
          : "Núcleo HILM — Íris, Luna e Maya em órbitas 3D independentes"
      }
      data-hilm-status={status}
      className={`relative flex items-center justify-center w-[214px] h-[214px] bg-transparent border-0 outline-none rounded-full cursor-pointer transition-transform duration-150 active:scale-[0.98] ${
        isMayaTheme
          ? "focus-visible:ring-2 focus-visible:ring-[#F5B942]/80"
          : "focus-visible:ring-2 focus-visible:ring-cyan-400/70"
      }`}
    >
      <span
        aria-hidden="true"
        className={`${
          isMayaTheme
            ? "maya-hud-pulse-core"
            : isLunaTheme
              ? "luna-hud-pulse luna-hud-border-glow"
              : "hud-breathe-core"
        }${isPaused ? " hud-breathe-paused" : ""} pointer-events-none absolute w-[132px] h-[132px] rounded-full`}
        style={
          isMayaTheme
            ? {
                background:
                  "radial-gradient(circle, rgba(245, 185, 66, 0.24) 0%, rgba(37, 99, 235, 0.14) 56%, transparent 76%)",
                border: "1px solid rgba(245, 185, 66, 0.34)",
              }
            : {
                background:
                  "radial-gradient(circle, rgba(var(--hilm-breath-rgb, 34, 211, 238), 0.22) 0%, rgba(var(--hilm-breath-rgb, 34, 211, 238), 0.06) 56%, transparent 74%)",
                border: "1px solid rgba(var(--hilm-breath-rgb, 34, 211, 238), 0.24)",
              }
        }
      />
      <canvas
        ref={canvasRef}
        className="relative z-10 w-[214px] h-[214px] pointer-events-none select-none"
      />
    </button>
  );
}
