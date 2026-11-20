import { useMemo } from "react";

interface HudSvgFrameProps {
  panelOpacity: number;
}

function polarToCartesian(cx: number, cy: number, r: number, angleDeg: number) {
  const rad = (angleDeg * Math.PI) / 180;
  return {
    x: cx + r * Math.cos(rad),
    y: cy + r * Math.sin(rad),
  };
}

function describeArc(
  cx: number,
  cy: number,
  r: number,
  startAngle: number,
  endAngle: number
) {
  const start = polarToCartesian(cx, cy, r, endAngle);
  const end = polarToCartesian(cx, cy, r, startAngle);
  const largeArc = endAngle - startAngle <= 180 ? "0" : "1";
  return `M ${start.x.toFixed(2)} ${start.y.toFixed(2)} A ${r} ${r} 0 ${largeArc} 0 ${end.x.toFixed(2)} ${end.y.toFixed(2)}`;
}

/**
 * Reproduz a arquitetura HUD da Referência 1 em SVG vetorial transparente:
 * - Moldura ornamental superior (sem o relógio grande)
 * - Trilhos laterais esquerdo e direito
 * - Anéis concêntricos segmentados, ticks radiais, suportes de canto e eixos de calibração
 * - Matriz pontilhada inferior atrás do dock
 */
export function HudSvgFrame({ panelOpacity }: HudSvgFrameProps) {
  const ticks = useMemo(() => {
    const items: { x1: number; y1: number; x2: number; y2: number; major: boolean }[] = [];
    const cx = 180;
    const cy = 274;
    const outerR = 132;
    for (let deg = 0; deg < 360; deg += 6) {
      const major = deg % 30 === 0;
      const r1 = major ? outerR - 11 : outerR - 6;
      const p1 = polarToCartesian(cx, cy, r1, deg);
      const p2 = polarToCartesian(cx, cy, outerR, deg);
      items.push({ x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y, major });
    }
    return items;
  }, []);

  const spectrumCols = useMemo(() => {
    const cols: { x: number; rows: number }[] = [];
    const count = 38;
    const spacing = 360 / count;
    for (let c = 0; c < count; c++) {
      const norm = (c / (count - 1)) * 2 - 1;
      const envelope = (0.35 + 0.65 * Math.abs(norm)) * (c % 3 === 0 ? 1 : 0.68);
      const rows = Math.round(3 + envelope * 10);
      cols.push({ x: c * spacing + spacing * 0.35, rows });
    }
    return cols;
  }, []);

  const scrimAlpha = ((panelOpacity / 100) * 0.56).toFixed(2);

  return (
    <svg
      viewBox="0 0 360 760"
      preserveAspectRatio="xMidYMid slice"
      className="absolute inset-0 w-full h-full pointer-events-none select-none"
      aria-hidden="true"
    >
      <defs>
        <filter id="cyanGlow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="2.2" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      {/* Atmospheric HUD scrim letting wallpaper breathe through */}
      <rect x="0" y="0" width="360" height="760" fill={`rgba(5, 8, 13, ${scrimAlpha})`} />

      {/* Top HUD Ornamental Rail (below Android Status Bar, clock removed) */}
      <g opacity="0.92">
        <path
          d="M 18 46 L 36 39 L 324 39 L 342 46"
          fill="none"
          stroke="#22D3EE"
          strokeWidth="1.5"
          filter="url(#cyanGlow)"
        />
        <line x1="46" y1="43" x2="72" y2="43" stroke="#F59E0B" strokeWidth="1.6" />
        <line x1="288" y1="43" x2="314" y2="43" stroke="#F59E0B" strokeWidth="1.6" />
        <path
          d="M 164 44 L 180 54 L 196 44"
          fill="none"
          stroke="#22D3EE"
          strokeWidth="1.5"
        />
        <circle cx="173" cy="61" r="1.6" fill="#22D3EE" />
        <circle cx="180" cy="61" r="1.6" fill="#22D3EE" />
        <circle cx="187" cy="61" r="1.6" fill="#22D3EE" />
      </g>

      {/* Left Vertical HUD Rail */}
      <g>
        <path
          d="M 5 145 L 15 157 L 15 244 L 8 252 L 8 296 L 15 304 L 15 391 L 5 403"
          fill="none"
          stroke="#CBD5E1"
          strokeWidth="1.7"
          opacity="0.78"
        />
        <line x1="11" y1="234" x2="11" y2="258" stroke="#F59E0B" strokeWidth="2.2" />
        <line x1="11" y1="290" x2="11" y2="314" stroke="#22D3EE" strokeWidth="2.2" />
        <line x1="8" y1="168" x2="8" y2="210" stroke="#22D3EE" strokeWidth="1.4" opacity="0.7" />
        <line x1="8" y1="338" x2="8" y2="380" stroke="#22D3EE" strokeWidth="1.4" opacity="0.7" />
      </g>

      {/* Right Vertical HUD Rail (mirrored) */}
      <g>
        <path
          d="M 355 145 L 345 157 L 345 244 L 352 252 L 352 296 L 345 304 L 345 391 L 355 403"
          fill="none"
          stroke="#CBD5E1"
          strokeWidth="1.7"
          opacity="0.78"
        />
        <line x1="349" y1="234" x2="349" y2="258" stroke="#F59E0B" strokeWidth="2.2" />
        <line x1="349" y1="290" x2="349" y2="314" stroke="#22D3EE" strokeWidth="2.2" />
        <line x1="352" y1="168" x2="352" y2="210" stroke="#22D3EE" strokeWidth="1.4" opacity="0.7" />
        <line x1="352" y1="338" x2="352" y2="380" stroke="#22D3EE" strokeWidth="1.4" opacity="0.7" />
      </g>

      {/* Central Concentric HUD Ring Composition around Núcleo HILM (cx=180, cy=274) */}
      <g>
        {/* Outer translucent structural ring track */}
        <circle
          cx="180"
          cy="274"
          r="116"
          fill="none"
          stroke="rgba(7, 11, 18, 0.72)"
          strokeWidth="22"
        />

        {/* Radial Calibration Ticks */}
        {ticks.map((t, idx) => (
          <line
            key={idx}
            x1={t.x1}
            y1={t.y1}
            x2={t.x2}
            y2={t.y2}
            stroke={t.major ? "#CBD5E1" : "#64748B"}
            strokeWidth={t.major ? "1.3" : "0.9"}
            opacity={t.major ? "0.75" : "0.45"}
          />
        ))}

        {/* Outer Cyan Segmented Arcs */}
        <g filter="url(#cyanGlow)">
          <path d={describeArc(180, 274, 134, 200, 252)} fill="none" stroke="#22D3EE" strokeWidth="2.4" />
          <path d={describeArc(180, 274, 134, 290, 355)} fill="none" stroke="#22D3EE" strokeWidth="2.4" />
          <path d={describeArc(180, 274, 134, 22, 80)} fill="none" stroke="#22D3EE" strokeWidth="2.4" />
          <path d={describeArc(180, 274, 134, 112, 164)} fill="none" stroke="#22D3EE" strokeWidth="2.4" />
        </g>

        {/* Amber Accent Arcs */}
        <path d={describeArc(180, 274, 120, 256, 282)} fill="none" stroke="#F59E0B" strokeWidth="2.2" />
        <path d={describeArc(180, 274, 120, 80, 106)} fill="none" stroke="#F59E0B" strokeWidth="2.2" />
        <path d={describeArc(180, 274, 120, 166, 188)} fill="none" stroke="#F59E0B" strokeWidth="2" />
        <path d={describeArc(180, 274, 120, 348, 370)} fill="none" stroke="#F59E0B" strokeWidth="2" />

        {/* Inner Aperture Border framing the Núcleo HILM */}
        <path d={describeArc(180, 274, 98, 15, 145)} fill="none" stroke="#22D3EE" strokeWidth="1.6" opacity="0.75" />
        <path d={describeArc(180, 274, 98, 195, 325)} fill="none" stroke="#22D3EE" strokeWidth="1.6" opacity="0.75" />

        {/* Outer Left & Right Silver Calipers */}
        <path d={describeArc(180, 274, 147, 160, 200)} fill="none" stroke="#CBD5E1" strokeWidth="2.4" opacity="0.88" />
        <path d={describeArc(180, 274, 147, -20, 20)} fill="none" stroke="#CBD5E1" strokeWidth="2.4" opacity="0.88" />

        {/* 4 Chamfered Corner Brackets */}
        <path d="M 60 176 L 60 156 L 80 156" fill="none" stroke="#CBD5E1" strokeWidth="2" opacity="0.82" />
        <path d="M 300 176 L 300 156 L 280 156" fill="none" stroke="#CBD5E1" strokeWidth="2" opacity="0.82" />
        <path d="M 60 372 L 60 392 L 80 392" fill="none" stroke="#CBD5E1" strokeWidth="2" opacity="0.82" />
        <path d="M 300 372 L 300 392 L 280 392" fill="none" stroke="#CBD5E1" strokeWidth="2" opacity="0.82" />

        {/* Thin Amber Radial Calibration Hairlines */}
        <line x1="205" y1="176" x2="216" y2="132" stroke="#F59E0B" strokeWidth="1" opacity="0.7" />
        <line x1="155" y1="372" x2="144" y2="416" stroke="#F59E0B" strokeWidth="1" opacity="0.7" />

        {/* Top & Bottom 3-Dot Ring Indicators */}
        <circle cx="173" cy="122" r="1.8" fill="#22D3EE" opacity="0.85" />
        <circle cx="180" cy="122" r="1.8" fill="#22D3EE" opacity="0.85" />
        <circle cx="187" cy="122" r="1.8" fill="#22D3EE" opacity="0.85" />

        <circle cx="173" cy="426" r="1.8" fill="#22D3EE" opacity="0.85" />
        <circle cx="180" cy="426" r="1.8" fill="#22D3EE" opacity="0.85" />
        <circle cx="187" cy="426" r="1.8" fill="#22D3EE" opacity="0.85" />
      </g>

      {/* Bottom Dotted Equalizer / Spectrum Matrix behind the Dock */}
      <g opacity="0.55">
        {spectrumCols.map((col, colIdx) =>
          Array.from({ length: col.rows }).map((_, rowIdx) => (
            <rect
              key={`${colIdx}-${rowIdx}`}
              x={col.x}
              y={704 - rowIdx * 6}
              width="2.6"
              height="2.6"
              fill={rowIdx < 3 ? "#22D3EE" : "#64748B"}
              opacity={Math.max(0.14, 0.72 - rowIdx * 0.055)}
            />
          ))
        )}
      </g>
    </svg>
  );
}
