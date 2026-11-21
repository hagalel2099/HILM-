import { useEffect, useRef } from "react";

interface HilmCoreProps {
  onClick?: () => void;
}

const HILM_CORE_IMAGE = "/assets/hilm_central_core.png";

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

/**
 * Três presenças luminosas em órbitas elípticas compactas, inclinadas e independentes
 * ao redor do núcleo central HILM:
 * - Períodos, fases e planos 3D próprios e dessincronizados
 * - Cruzamentos ocasionais de trajetórias com separação 3D suave (sem colisões)
 * - Profundidade contínua por perspectiva, escala, luminosidade e oclusão pelo núcleo
 */
const ORB_SPECS: IndependentOrbSpec[] = [
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

const VIEW_SIZE = 214;
const CORE_DRAW_SIZE = 108;
const CAMERA_DISTANCE = 240;
const TRAIL_ARC_RAD = 2.65;
const TRAIL_SEGMENTS = 126;

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

function sampleAllOrbsAtTime(elapsedMs: number, cx: number, cy: number): ProjectedPoint[] {
  const adj = ORB_SPECS.map((orb) => computeBaseOrb3D(orb, elapsedMs));

  // Suave separação 3D contínua quando duas presenças se aproximam em cruzamentos
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
  orbIndex: number,
  elapsedMs: number,
  cx: number,
  cy: number
): ProjectedPoint[] {
  const orb = ORB_SPECS[orbIndex];
  const trailWindowMs = (TRAIL_ARC_RAD / (Math.PI * 2)) * orb.periodMs;
  const trail: ProjectedPoint[] = [];
  for (let i = 0; i <= TRAIL_SEGMENTS; i++) {
    const frac = i / TRAIL_SEGMENTS;
    const tSample = elapsedMs - frac * trailWindowMs;
    trail.push(sampleAllOrbsAtTime(tSample, cx, cy)[orbIndex]);
  }
  return trail;
}

/**
 * Núcleo HILM preservado integralmente sobre o papel de parede, com Íris, Luna e Maya
 * em órbitas elípticas compactas, inclinadas, independentes e com cruzamentos 3D suaves.
 */
export function HilmCore({ onClick }: HilmCoreProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const coreImgRef = useRef<HTMLImageElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = VIEW_SIZE * dpr;
    canvas.height = VIEW_SIZE * dpr;

    let imgLoaded = false;
    let reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const img = new Image();
    img.src = HILM_CORE_IMAGE;
    img.onload = () => {
      imgLoaded = true;
      coreImgRef.current = img;
      if (reduceMotion) {
        rafId = window.requestAnimationFrame(renderFrame);
      }
    };

    let rafId = 0;
    let lastFrameTimestamp: number | null = null;
    let simulationTimeMs = 0;

    const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    const handleMotionChange = (e: MediaQueryListEvent) => {
      reduceMotion = e.matches;
      lastFrameTimestamp = null;
      if (!reduceMotion) {
        rafId = window.requestAnimationFrame(renderFrame);
      }
    };
    mediaQuery.addEventListener("change", handleMotionChange);

    const puffSprites = ORB_SPECS.map((orb) => {
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

    const precomputedTrailColors = ORB_SPECS.map((orb) => {
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

      // Camada 1: Névoa luminosa contínua ao longo de toda a cauda elíptica
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

      // Camada 2: Cauda principal contínua com dissipação progressiva ao longo do arco
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

      // Camada 3: Filamento luminoso interno próximo ao ponto (efeito núcleo de cometa)
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

      const sortedByDepth = ORB_SPECS.map((orb, index) => {
        const trail = sampleSingleOrbTrail(index, elapsed, cx, cy);
        return {
          orb,
          index,
          trail,
          head: trail[0],
        };
      }).sort((a, b) => a.head.z - b.head.z);

      // Pass 1: Hemisfério posterior (z < 0) atrás do núcleo HILM
      for (const item of sortedByDepth) {
        drawCurvedTrailPass(item.orb, item.index, item.trail, false);
        drawOrbHeadPass(item.orb, item.head, false);
      }

      // Pass 2: Núcleo central HILM intacto e fixo
      if (imgLoaded && coreImgRef.current) {
        ctx.drawImage(
          coreImgRef.current,
          cx - CORE_DRAW_SIZE / 2,
          cy - CORE_DRAW_SIZE / 2,
          CORE_DRAW_SIZE,
          CORE_DRAW_SIZE
        );
      } else {
        ctx.beginPath();
        ctx.arc(cx, cy, 43, 0, Math.PI * 2);
        ctx.fillStyle = "#06080E";
        ctx.fill();
      }

      // Pass 3: Hemisfério frontal (z >= 0) em primeiro plano sobre o núcleo
      for (const item of sortedByDepth) {
        drawCurvedTrailPass(item.orb, item.index, item.trail, true);
        drawOrbHeadPass(item.orb, item.head, true);
      }

      if (!reduceMotion) {
        rafId = window.requestAnimationFrame(renderFrame);
      }
    };

    rafId = window.requestAnimationFrame(renderFrame);

    return () => {
      window.cancelAnimationFrame(rafId);
      mediaQuery.removeEventListener("change", handleMotionChange);
    };
  }, []);

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label="Núcleo HILM — Íris, Luna e Maya em órbitas 3D independentes"
      className="relative flex items-center justify-center w-[214px] h-[214px] bg-transparent border-0 outline-none focus-visible:ring-2 focus-visible:ring-cyan-400/70 rounded-full cursor-pointer transition-transform duration-150 active:scale-[0.98]"
    >
      <canvas
        ref={canvasRef}
        className="w-[214px] h-[214px] pointer-events-none select-none"
      />
    </button>
  );
}
