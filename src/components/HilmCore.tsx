import { useEffect, useRef } from "react";

interface HilmCoreProps {
  onClick?: () => void;
}

const HILM_CORE_IMAGE = "/assets/hilm_central_core.png";

interface KeplerOrbSpec {
  name: string;
  phase: number;
  semiMajor: number;
  eccentricity: number;
  inclinationDeg: number; // Tilt from face-on (66-74 deg = steep 3D ellipse)
  yawDeg: number;         // Rotation around Y axis (depth asymmetry)
  rollDeg: number;        // Rotation of orbital plane on screen
  periodMs: number;
  coreColor: string;
  midColor: string;
  edgeColor: string;
  haloRgba: string;
  trailRgb: string;
  trailCoreRgb: string;
}

const ORB_SPECS: KeplerOrbSpec[] = [
  {
    name: "Íris",
    phase: 0.2,
    semiMajor: 73,
    eccentricity: 0.23,
    inclinationDeg: 68,
    yawDeg: 18,
    rollDeg: -34,
    periodMs: 9200,
    coreColor: "#FFF3E0",
    midColor: "#FB923C",
    edgeColor: "#C2410C",
    haloRgba: "rgba(234, 88, 12, 0.68)",
    trailRgb: "234, 88, 12",
    trailCoreRgb: "253, 186, 116",
  },
  {
    name: "Luna",
    phase: 2.35,
    semiMajor: 72,
    eccentricity: 0.21,
    inclinationDeg: 72,
    yawDeg: -22,
    rollDeg: 32,
    periodMs: 11300,
    coreColor: "#F5F3FF",
    midColor: "#A855F7",
    edgeColor: "#6D28D9",
    haloRgba: "rgba(139, 92, 246, 0.70)",
    trailRgb: "139, 92, 246",
    trailCoreRgb: "221, 214, 254",
  },
  {
    name: "Maya",
    phase: 4.35,
    semiMajor: 71,
    eccentricity: 0.2,
    inclinationDeg: 66,
    yawDeg: 10,
    rollDeg: 5,
    periodMs: 13400,
    coreColor: "#FFFBEB",
    midColor: "#FBBF24",
    edgeColor: "#D97706",
    haloRgba: "rgba(245, 158, 11, 0.66)",
    trailRgb: "245, 158, 11",
    trailCoreRgb: "254, 240, 138",
  },
];

const VIEW_SIZE = 214;
const CORE_DRAW_SIZE = 104;
const CAMERA_DISTANCE = 195;
const TRAIL_ARC_MEAN_RAD = 1.22;
const TRAIL_SEGMENTS = 42;

interface ProjectedPoint {
  x: number;
  y: number;
  z: number;
  scale: number;
}

/**
 * Solves Kepler's equation (M = E - e * sin(E)) and projects the 3D orbital
 * ellipse (with the fixed HILM nucleus at its focal origin) into screen coordinates
 * using inclination (X-tilt), yaw (Y-rotation), roll (Z-rotation), and camera perspective.
 */
function projectKeplerOrbit(
  meanAnomaly: number,
  orb: KeplerOrbSpec,
  cx: number,
  cy: number
): ProjectedPoint {
  const e = orb.eccentricity;
  let E = meanAnomaly;
  for (let i = 0; i < 4; i++) {
    const f = E - e * Math.sin(E) - meanAnomaly;
    const fp = 1 - e * Math.cos(E);
    E -= f / fp;
  }

  // Elliptical coordinates in orbital plane with nucleus at focal point (0,0,0)
  const xOrb = orb.semiMajor * (Math.cos(E) - e);
  const yOrb = orb.semiMajor * Math.sqrt(1 - e * e) * Math.sin(E);

  // 1. Inclination around X-axis (maps yOrb into vertical + Z depth)
  const incRad = (orb.inclinationDeg * Math.PI) / 180;
  const cosI = Math.cos(incRad);
  const sinI = Math.sin(incRad);
  const x1 = xOrb;
  const y1 = yOrb * cosI;
  const z1 = yOrb * sinI;

  // 2. Yaw around Y-axis (depth asymmetry across left/right sides)
  const yawRad = (orb.yawDeg * Math.PI) / 180;
  const cosY = Math.cos(yawRad);
  const sinY = Math.sin(yawRad);
  const x2 = x1 * cosY + z1 * sinY;
  const y2 = y1;
  const z2 = -x1 * sinY + z1 * cosY;

  // 3. Roll around Z-axis (orbital plane orientation on screen)
  const rollRad = (orb.rollDeg * Math.PI) / 180;
  const cosR = Math.cos(rollRad);
  const sinR = Math.sin(rollRad);
  const x3 = x2 * cosR - y2 * sinR;
  const y3 = x2 * sinR + y2 * cosR;

  const perspective = CAMERA_DISTANCE / (CAMERA_DISTANCE - z2);

  return {
    x: cx + x3 * perspective,
    y: cy + y3 * perspective,
    z: z2,
    scale: perspective,
  };
}

/**
 * Núcleo HILM fixo ao centro com sistema orbital kepleriano 3D para Íris, Luna e Maya,
 * combinando planos inclinados distintos, perspectiva variável, escala por profundidade,
 * sobreposição real à frente/atrás do núcleo e rastros luminosos curvos contínuos.
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

    const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    const handleMotionChange = (e: MediaQueryListEvent) => {
      reduceMotion = e.matches;
      if (!reduceMotion) {
        rafId = window.requestAnimationFrame(renderFrame);
      }
    };
    mediaQuery.addEventListener("change", handleMotionChange);

    let rafId = 0;
    const startTime = performance.now();

    const drawCurvedTrailPass = (
      orb: KeplerOrbSpec,
      meanAnomaly: number,
      cx: number,
      cy: number,
      drawFrontHalf: boolean
    ) => {
      const points: ProjectedPoint[] = [];
      for (let i = 0; i <= TRAIL_SEGMENTS; i++) {
        const frac = i / TRAIL_SEGMENTS;
        points.push(
          projectKeplerOrbit(meanAnomaly - frac * TRAIL_ARC_MEAN_RAD, orb, cx, cy)
        );
      }

      ctx.save();
      ctx.lineCap = "round";
      ctx.lineJoin = "round";

      for (let i = TRAIL_SEGMENTS - 1; i >= 0; i--) {
        const p0 = points[i];
        const p1 = points[i + 1];
        const midZ = (p0.z + p1.z) * 0.5;
        const isFront = midZ >= 0;
        if (isFront !== drawFrontHalf) continue;

        const frac = (i + 0.5) / TRAIL_SEGMENTS;
        const taper = Math.pow(1 - frac, 1.4);
        const alphaFade = Math.pow(1 - frac, 2.0);
        const depthDim = isFront ? 1 : 0.52;
        const avgScale = (p0.scale + p1.scale) * 0.5;

        ctx.beginPath();
        ctx.moveTo(p0.x, p0.y);
        ctx.lineTo(p1.x, p1.y);
        ctx.lineWidth = Math.max(0.8, 9.6 * avgScale * taper);
        ctx.strokeStyle = `rgba(${orb.trailRgb}, ${(0.44 * alphaFade * depthDim).toFixed(3)})`;
        ctx.stroke();

        ctx.beginPath();
        ctx.moveTo(p0.x, p0.y);
        ctx.lineTo(p1.x, p1.y);
        ctx.lineWidth = Math.max(0.5, 3.8 * avgScale * taper);
        ctx.strokeStyle = `rgba(${orb.trailCoreRgb}, ${(0.80 * alphaFade * depthDim).toFixed(3)})`;
        ctx.stroke();
      }

      ctx.restore();
    };

    const drawOrbHeadPass = (
      orb: KeplerOrbSpec,
      meanAnomaly: number,
      cx: number,
      cy: number,
      drawFrontHalf: boolean
    ) => {
      const head = projectKeplerOrbit(meanAnomaly, orb, cx, cy);
      const isFront = head.z >= 0;
      if (isFront !== drawFrontHalf) return;

      const baseRadius = 6.8 * head.scale;
      const haloRadius = baseRadius * 2.55;
      const depthAlpha = isFront ? 1 : 0.72;

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
      const elapsed = reduceMotion ? 0 : now - startTime;

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, VIEW_SIZE, VIEW_SIZE);

      const cx = VIEW_SIZE / 2;
      const cy = VIEW_SIZE / 2;

      const sortedByDepth = ORB_SPECS.map((orb) => {
        const meanAnomaly =
          (reduceMotion ? 0 : (elapsed / orb.periodMs) * Math.PI * 2) + orb.phase;
        return {
          orb,
          meanAnomaly,
          z: projectKeplerOrbit(meanAnomaly, orb, cx, cy).z,
        };
      }).sort((a, b) => a.z - b.z);

      // Pass 1: Background hemisphere (z < 0) occluded behind the central sphere
      for (const item of sortedByDepth) {
        drawCurvedTrailPass(item.orb, item.meanAnomaly, cx, cy, false);
        drawOrbHeadPass(item.orb, item.meanAnomaly, cx, cy, false);
      }

      // Pass 2: Fixed & stable central HILM nucleus (original artwork)
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
        ctx.arc(cx, cy, 42, 0, Math.PI * 2);
        ctx.fillStyle = "#06080E";
        ctx.fill();
      }

      // Pass 3: Foreground hemisphere (z >= 0) sweeping in front of the central sphere
      for (const item of sortedByDepth) {
        drawCurvedTrailPass(item.orb, item.meanAnomaly, cx, cy, true);
        drawOrbHeadPass(item.orb, item.meanAnomaly, cx, cy, true);
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
      aria-label="Núcleo HILM — Íris, Luna e Maya em órbitas espaciais 3D"
      className="relative flex items-center justify-center w-[214px] h-[214px] rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 cursor-pointer transition-transform duration-150 active:scale-[0.98]"
    >
      <canvas
        ref={canvasRef}
        className="w-[214px] h-[214px] pointer-events-none select-none"
      />
    </button>
  );
}
