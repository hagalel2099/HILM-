import { useMemo } from "react";

/**
 * Reproduz a arquitetura HUD da Referência 1 em SVG vetorial transparente:
 * - Moldura ornamental superior (sem o relógio grande)
 * - Trilhos laterais esquerdo e direito
 * - Matriz pontilhada inferior atrás do dock
 * O núcleo central HILM e o papel de parede permanecem livres de scrims ou filtros de cor.
 */
export function HudSvgFrame() {
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
