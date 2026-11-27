import { useMemo } from "react";
import type { LauncherThemeId } from "../types/launcher";
import type { HilmCoreStatus } from "./HilmCore";

interface HudSvgFrameProps {
  status?: HilmCoreStatus;
  themeId?: LauncherThemeId;
}

/**
 * Reproduz a arquitetura HUD da Referência 1 em SVG vetorial transparente,
 * ou a moldura holográfica mínima e elegante do Tema Luna — Entre Luz e Sombra.
 */
export function HudSvgFrame({ status = "nominal", themeId = "default" }: HudSvgFrameProps) {
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

  const isLunaTheme = themeId === "luna-entre-luz-e-sombra";
  const isMayaTheme = themeId === "maya-holographic-presence";

  const primaryStroke = isMayaTheme
    ? status === "alert"
      ? "#F59E0B"
      : status === "resolved"
        ? "#FFE49E"
        : status === "syncing"
          ? "#38BDF8"
          : "#F5B942"
    : isLunaTheme
      ? status === "alert"
        ? "#FFB45C"
        : status === "resolved"
          ? "#D9F7FF"
          : status === "syncing"
            ? "#398BFF"
            : "#00E5FF"
      : status === "alert"
        ? "#F59E0B"
        : status === "resolved"
          ? "#34D399"
          : status === "syncing"
            ? "#38BDF8"
            : "#22D3EE";

  if (isMayaTheme) {
    return (
      <svg
        viewBox="0 0 360 760"
        preserveAspectRatio="xMidYMid slice"
        data-hilm-status={status}
        className="absolute inset-0 w-full h-full pointer-events-none select-none z-10"
        aria-hidden="true"
      >
        <defs>
          <filter id="mayaHoloGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="1.6" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <linearGradient id="mayaRailGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#38BDF8" stopOpacity="0.18" />
            <stop offset="48%" stopColor="#F5B942" stopOpacity="0.72" />
            <stop offset="100%" stopColor="#C47A48" stopOpacity="0.22" />
          </linearGradient>
        </defs>

        {/* Top Ceramic-Gold Chamfered Horizon Rail, Signature & Top Drawer Indicator */}
        <g className="maya-hud-pulse-rail" opacity="0.86" filter="url(#mayaHoloGlow)">
          <path d="M 16 37 L 30 30 L 98 30" fill="none" stroke={primaryStroke} strokeWidth="1.25" />
          <path d="M 262 30 L 330 30 L 344 37" fill="none" stroke={primaryStroke} strokeWidth="1.25" />
          <line x1="36" y1="34" x2="62" y2="34" stroke="#38BDF8" strokeWidth="1.15" opacity="0.88" />
          <line x1="298" y1="34" x2="324" y2="34" stroke="#C47A48" strokeWidth="1.15" opacity="0.88" />
          <text
            x="180"
            y="33.5"
            textAnchor="middle"
            fill="#FFE49E"
            fontSize="6.7"
            fontWeight="600"
            letterSpacing="2.1"
            opacity="0.92"
          >
            MAYA — HOLOGRAPHIC PRESENCE
          </text>
          <path
            d="M 165 39 L 180 48 L 195 39"
            fill="none"
            stroke="#38BDF8"
            strokeWidth="1.45"
          />
          <circle cx="173" cy="54" r="1.45" fill="#F5B942" />
          <circle cx="180" cy="54" r="1.45" fill="#38BDF8" />
          <circle cx="187" cy="54" r="1.45" fill="#F5B942" />
        </g>

        {/* Sculpted Left & Right Side Drawer Ceramic Rails */}
        <g className="maya-hud-pulse-rail" style={{ animationDelay: "-0.75s" }} opacity="0.78">
          <path
            d="M 5 162 L 12 172 L 12 256 L 6 264 L 6 298 L 12 306 L 12 378 L 5 388"
            fill="none"
            stroke="url(#mayaRailGrad)"
            strokeWidth="1.4"
          />
          <line x1="8" y1="268" x2="8" y2="294" stroke="#38BDF8" strokeWidth="1.8" />
          <line x1="8" y1="242" x2="8" y2="254" stroke="#C47A48" strokeWidth="1.6" />

          <path
            d="M 355 162 L 348 172 L 348 256 L 354 264 L 354 298 L 348 306 L 348 378 L 355 388"
            fill="none"
            stroke="url(#mayaRailGrad)"
            strokeWidth="1.4"
          />
          <line x1="352" y1="268" x2="352" y2="294" stroke="#38BDF8" strokeWidth="1.8" />
          <line x1="352" y1="242" x2="352" y2="254" stroke="#F5B942" strokeWidth="1.6" />
        </g>

        <line
          x1="28"
          y1="714"
          x2="332"
          y2="714"
          className="maya-hud-pulse"
          stroke="#F5B942"
          strokeWidth="0.8"
          strokeOpacity="0.34"
        />
      </svg>
    );
  }

  if (isLunaTheme) {
    return (
      <svg
        viewBox="0 0 360 760"
        preserveAspectRatio="xMidYMid slice"
        data-hilm-status={status}
        className="absolute inset-0 w-full h-full pointer-events-none select-none z-10"
        aria-hidden="true"
      >
        <defs>
          <filter id="lunaHoloGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="1.8" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <linearGradient id="lunaRailGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#00E5FF" stopOpacity="0.1" />
            <stop offset="50%" stopColor="#398BFF" stopOpacity="0.65" />
            <stop offset="100%" stopColor="#00E5FF" stopOpacity="0.1" />
          </linearGradient>
        </defs>

        {/* Subtle Top Holographic Hairline Corners & Top Drawer Indicator */}
        <g className="luna-hud-pulse" opacity="0.76" filter="url(#lunaHoloGlow)">
          <path d="M 16 35 L 28 29 L 96 29" fill="none" stroke={primaryStroke} strokeWidth="1.1" />
          <path d="M 264 29 L 332 29 L 344 35" fill="none" stroke={primaryStroke} strokeWidth="1.1" />
          <line x1="36" y1="32" x2="54" y2="32" stroke="#C47A48" strokeWidth="1.1" opacity="0.85" />
          <path
            d="M 166 35 L 180 43 L 194 35"
            fill="none"
            stroke={primaryStroke}
            strokeWidth="1.25"
          />
          <circle cx="173.5" cy="49" r="1.35" fill={primaryStroke} />
          <circle cx="180" cy="49" r="1.35" fill="#8B5CF6" />
          <circle cx="186.5" cy="49" r="1.35" fill={primaryStroke} />
        </g>

        {/* Minimal Left & Right Holographic Edge Brackets */}
        <g className="luna-hud-pulse" style={{ animationDelay: "-1.3s" }} opacity="0.68">
          <path
            d="M 5 170 L 11 178 L 11 258 L 6 264 L 6 296 L 11 302 L 11 372 L 5 380"
            fill="none"
            stroke="url(#lunaRailGrad)"
            strokeWidth="1.2"
          />
          <line x1="7.5" y1="268" x2="7.5" y2="292" stroke={primaryStroke} strokeWidth="1.6" />
          <circle cx="7.5" cy="250" r="1.1" fill="#FFB45C" opacity="0.85" />

          <path
            d="M 355 170 L 349 178 L 349 258 L 354 264 L 354 296 L 349 302 L 349 372 L 355 380"
            fill="none"
            stroke="url(#lunaRailGrad)"
            strokeWidth="1.2"
          />
          <line x1="352.5" y1="268" x2="352.5" y2="292" stroke={primaryStroke} strokeWidth="1.6" />
          <circle cx="352.5" cy="250" r="1.1" fill="#C47A48" opacity="0.85" />
        </g>

        {/* Discreet Bottom Horizon Line above navigation bar */}
        <line
          x1="28"
          y1="714"
          x2="332"
          y2="714"
          className="luna-hud-pulse"
          style={{ animationDelay: "-2.4s" }}
          stroke={primaryStroke}
          strokeWidth="0.7"
          strokeOpacity="0.28"
        />
      </svg>
    );
  }

  return (
    <svg
      viewBox="0 0 360 760"
      preserveAspectRatio="xMidYMid slice"
      data-hilm-status={status}
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

      {/* Top HUD Ornamental Rail (below Android Status Bar, shifted cleanly upward) */}
      <g className="hud-breathe-rail" style={{ transition: "stroke 300ms ease" }}>
        <path
          d="M 18 39 L 36 32 L 324 32 L 342 39"
          fill="none"
          stroke={primaryStroke}
          strokeWidth="1.5"
          filter="url(#cyanGlow)"
        />
        <line x1="46" y1="36" x2="72" y2="36" stroke="#F59E0B" strokeWidth="1.6" />
        <line x1="288" y1="36" x2="314" y2="36" stroke="#F59E0B" strokeWidth="1.6" />
        <path
          d="M 164 37 L 180 47 L 196 37"
          fill="none"
          stroke={primaryStroke}
          strokeWidth="1.5"
        />
        <circle cx="173" cy="53" r="1.6" fill={primaryStroke} />
        <circle cx="180" cy="53" r="1.6" fill={primaryStroke} />
        <circle cx="187" cy="53" r="1.6" fill={primaryStroke} />
      </g>

      {/* Left Vertical HUD Rail */}
      <g className="hud-breathe-rail" style={{ animationDelay: "-0.65s" }}>
        <path
          d="M 5 145 L 15 157 L 15 244 L 8 252 L 8 296 L 15 304 L 15 391 L 5 403"
          fill="none"
          stroke="#CBD5E1"
          strokeWidth="1.7"
          opacity="0.78"
        />
        <line x1="11" y1="234" x2="11" y2="258" stroke="#F59E0B" strokeWidth="2.2" />
        <line x1="11" y1="290" x2="11" y2="314" stroke={primaryStroke} strokeWidth="2.2" />
        <line x1="8" y1="168" x2="8" y2="210" stroke={primaryStroke} strokeWidth="1.4" opacity="0.7" />
        <line x1="8" y1="338" x2="8" y2="380" stroke={primaryStroke} strokeWidth="1.4" opacity="0.7" />
      </g>

      {/* Right Vertical HUD Rail (mirrored) */}
      <g className="hud-breathe-rail" style={{ animationDelay: "-0.65s" }}>
        <path
          d="M 355 145 L 345 157 L 345 244 L 352 252 L 352 296 L 345 304 L 345 391 L 355 403"
          fill="none"
          stroke="#CBD5E1"
          strokeWidth="1.7"
          opacity="0.78"
        />
        <line x1="349" y1="234" x2="349" y2="258" stroke="#F59E0B" strokeWidth="2.2" />
        <line x1="349" y1="290" x2="349" y2="314" stroke={primaryStroke} strokeWidth="2.2" />
        <line x1="352" y1="168" x2="352" y2="210" stroke={primaryStroke} strokeWidth="1.4" opacity="0.7" />
        <line x1="352" y1="338" x2="352" y2="380" stroke={primaryStroke} strokeWidth="1.4" opacity="0.7" />
      </g>

      {/* Bottom Dotted Equalizer / Spectrum Matrix behind the Dock */}
      <g className="hud-breathe-spectrum" style={{ animationDelay: "-1.3s" }}>
        {spectrumCols.map((col, colIdx) =>
          Array.from({ length: col.rows }).map((_, rowIdx) => (
            <rect
              key={`${colIdx}-${rowIdx}`}
              x={col.x}
              y={704 - rowIdx * 6}
              width="2.6"
              height="2.6"
              fill={rowIdx < 3 ? primaryStroke : "#64748B"}
              opacity={Math.max(0.14, 0.72 - rowIdx * 0.055)}
            />
          ))
        )}
      </g>
    </svg>
  );
}
