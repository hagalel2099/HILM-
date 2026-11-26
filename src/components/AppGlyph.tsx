import { useId } from "react";
import {
  Calendar,
  Camera,
  Code2,
  Folder,
  Globe,
  Image as ImageIcon,
  Instagram,
  MapPin,
  MessageCircle,
  MessageSquare,
  Music,
  Phone,
  Play,
  Settings,
  StickyNote,
  Terminal,
} from "lucide-react";
import type { IconPackId, InstalledAppItem } from "../types/launcher";
import {
  isArtifactIconPack,
  isIllustratedIconPack,
  isLiquidGoldObsidianIconPack,
  isLunaHolographicIconPack,
  resolveIllustratedIconUrl,
} from "../data/iconPackRegistry";
import { HilmArtifactGlyph } from "./HilmArtifactGlyph";

interface AppGlyphProps {
  app: InstalledAppItem;
  iconPack: IconPackId;
  customIconDataUrl?: string;
  sizeClass?: string;
}

type LunaWarmMode = "copper-amber" | "copper-rim" | "amber-glint" | "cool";

const LUNA_WARM_AFFINITY_KEYS = new Set([
  "camera",
  "music",
  "settings",
  "notes",
  "terminal",
  "bank",
]);

function resolveLunaWarmProfile(packageName: string, iconKey: string): {
  mode: LunaWarmMode;
  angleDeg: number;
} {
  let hash = 0;
  for (let i = 0; i < packageName.length; i++) {
    hash = (hash * 31 + packageName.charCodeAt(i)) | 0;
  }
  const absHash = Math.abs(hash);
  const angles = [-42, 36, 138, -144];
  const angleDeg = angles[absHash % angles.length];

  if (LUNA_WARM_AFFINITY_KEYS.has(iconKey)) {
    return {
      mode: absHash % 3 === 0 ? "copper-rim" : "copper-amber",
      angleDeg,
    };
  }

  const bucket = absHash % 5;
  const mode: LunaWarmMode =
    bucket === 0
      ? "copper-amber"
      : bucket <= 2
        ? "copper-rim"
        : bucket === 3
          ? "amber-glint"
          : "cool";

  return { mode, angleDeg };
}

export function AppGlyph({
  app,
  iconPack,
  customIconDataUrl,
  sizeClass = "w-5 h-5",
}: AppGlyphProps) {
  const rawId = useId();
  const safeId = rawId.replace(/[^a-zA-Z0-9_-]/g, "");
  const goldGradId = `hilm-gold-${safeId}`;
  const lunaGradId = `hilm-luna-${safeId}`;
  if (customIconDataUrl) {
    return (
      <img
        src={customIconDataUrl}
        alt={app.label}
        referrerPolicy="no-referrer"
        className={`${sizeClass} rounded-md object-cover`}
      />
    );
  }

  const illustratedUrl = resolveIllustratedIconUrl(iconPack, app);
  if (illustratedUrl) {
    return (
      <img
        src={illustratedUrl}
        alt={app.label}
        referrerPolicy="no-referrer"
        className={`${sizeClass} object-contain drop-shadow-[0_4px_10px_rgba(2,6,18,0.65)]`}
      />
    );
  }

  if (isArtifactIconPack(iconPack)) {
    return <HilmArtifactGlyph app={app} sizeClass={sizeClass} />;
  }

  const isLunaHolo = isLunaHolographicIconPack(iconPack);
  const lunaProfile = isLunaHolo ? resolveLunaWarmProfile(app.packageName, app.iconKey) : null;
  const isLiquidGold = isLiquidGoldObsidianIconPack(iconPack);
  const illustratedFallback = isIllustratedIconPack(iconPack);
  const isObsidianFallback = illustratedFallback && iconPack === "obsidian-core";
  const isTronFallback = illustratedFallback && iconPack === "tron-neon";
  const isCyberpunkFallback = illustratedFallback && iconPack === "cyberpunk-holo";

  const colorStyle = (() => {
    if (isLunaHolo) {
      return { color: "#00E5FF" };
    }
    if (isLiquidGold) {
      return { color: `url(#${goldGradId})` };
    }
    if (isObsidianFallback) {
      return { color: "#FFB020" };
    }
    if (isCyberpunkFallback) {
      return { color: "#F472B6" };
    }
    if (isTronFallback) {
      return { color: "#67E8F9" };
    }
    if (illustratedFallback) {
      return { color: "#E2E8F0" };
    }
    switch (iconPack) {
      case "nosso-universo":
        return { color: "#22D3EE" };
      case "monochrome-hud":
        return { color: "#CBD5E1" };
      case "orbital-glow":
        return { color: "#F59E0B" };
      case "system-default":
      default:
        return { color: app.accentHex };
    }
  })();

  const lunaDropShadowClass =
    "relative z-10 w-[86%] h-[86%] drop-shadow-[0_0_4px_rgba(0,229,255,0.88)] drop-shadow-[0_0_10px_rgba(57,139,255,0.45)]";

  const props = {
    className: isLunaHolo
      ? lunaDropShadowClass
      : isLiquidGold
        ? "relative z-10 w-[62%] h-[62%]"
        : illustratedFallback
          ? isObsidianFallback
            ? "relative z-10 w-[54%] h-[54%] drop-shadow-[0_0_6px_rgba(255,140,26,0.75)]"
            : isCyberpunkFallback
              ? "w-[56%] h-[56%] drop-shadow-[0_0_7px_rgba(236,72,153,0.9)]"
              : isTronFallback
                ? "w-[56%] h-[56%] drop-shadow-[0_0_6px_rgba(34,211,238,0.85)]"
                : "w-[58%] h-[58%] drop-shadow-[0_1px_2px_rgba(34,211,238,0.45)]"
          : sizeClass,
    style: isLiquidGold
      ? { filter: `url(#${goldGradId}-relief)` }
      : colorStyle,
    fill: isLiquidGold ? `url(#${goldGradId})` : undefined,
    fillOpacity: isLiquidGold ? 0.92 : undefined,
    stroke: isLiquidGold ? `url(#${goldGradId})` : undefined,
    strokeWidth: isLunaHolo
      ? 1.24
      : isLiquidGold
        ? 2.8
        : illustratedFallback || iconPack === "nosso-universo"
          ? 1.85
          : 2,
  };

  const renderLunaProjectionGlyph = () => {
    const strokeGrad = `url(#${lunaGradId}-stroke)`;
    const warmGrad = `url(#${lunaGradId}-warm)`;
    const wispCoolGrad = `url(#${lunaGradId}-wisp-cool)`;
    const wispWarmGrad = `url(#${lunaGradId}-wisp-warm)`;
    const wispTilt = lunaProfile ? (lunaProfile.angleDeg > 0 ? -24 : 22) : -24;

    const renderLunaSymbolPaths = () => {
      switch (app.iconKey) {
        case "phone":
          return (
            <>
              <path
                d="M6.4 4.2h2.5c.45 0 .82.31.93.75l.65 2.6c.09.38-.04.78-.34 1.03l-1.32 1.1a10.4 10.4 0 0 0 4.86 4.86l1.1-1.32c.25-.3.65-.43 1.03-.34l2.6.65c.44.11.75.48.75.93v2.5a1.38 1.38 0 0 1-1.5 1.36C10.22 17.85 5.55 13.18 5.1 5.66A1.38 1.38 0 0 1 6.4 4.2z"
                stroke={strokeGrad}
                strokeWidth={1.32}
              />
              <path d="M14 5.4a4.2 4.2 0 0 1 4.2 4.2" stroke={strokeGrad} strokeWidth={1.12} />
              <path d="M14 8a1.7 1.7 0 0 1 1.6 1.6" stroke={warmGrad} strokeWidth={1.18} />
            </>
          );
        case "message":
          return (
            <>
              <path
                d="M5.4 5.2h13.2a2.1 2.1 0 0 1 2.1 2.1v7.6a2.1 2.1 0 0 1-2.1 2.1H9.5L5.2 20v-3H5.4a2.1 2.1 0 0 1-2.1-2.1V7.3a2.1 2.1 0 0 1 2.1-2.1z"
                stroke={strokeGrad}
                strokeWidth={1.3}
              />
              <path d="M7.8 9.8h8.4" stroke="#E4FAFF" strokeWidth={1.15} />
              <path d="M7.8 12.8h5.2" stroke={warmGrad} strokeWidth={1.15} />
            </>
          );
        case "chat":
          return (
            <>
              <path
                d="M12 4.1a7.9 7.9 0 0 0-6.85 11.88L4.1 19.9l4.04-1.06A7.9 7.9 0 1 0 12 4.1z"
                stroke={strokeGrad}
                strokeWidth={1.32}
              />
              <path
                d="M9.6 9.1c-.18-.35-.35-.35-.53-.35h-.36c-.18 0-.36.09-.53.27-.18.25-.62.71-.62 1.62s.71 1.96.88 2.14c.11.18 1.42 2.23 3.57 3.04 1.69.62 2.13.53 2.58.44.44-.09 1.15-.46 1.33-.89.18-.44.18-.8.09-.89-.09-.09-.2-.16-.44-.27l-1.15-.53c-.18-.09-.35-.09-.53.09l-.44.53c-.18.18-.27.18-.53.09-.27-.09-.98-.36-1.78-1.07-.62-.53-1.07-1.24-1.16-1.51-.09-.25 0-.35.09-.44l.35-.35c.09-.18.18-.27.18-.36.09-.09 0-.25 0-.35l-.53-1.24z"
                stroke="#E4FAFF"
                strokeWidth={1.1}
              />
            </>
          );
        case "instagram":
          return (
            <>
              <rect x="4.3" y="4.3" width="15.4" height="15.4" rx="4.4" stroke={strokeGrad} strokeWidth={1.32} />
              <circle cx="12" cy="12" r="3.6" stroke={strokeGrad} strokeWidth={1.28} />
              <circle cx="16.4" cy="7.6" r="0.88" fill="#FFE2A8" stroke="#FFB45C" strokeWidth={0.45} />
            </>
          );
        case "youtube":
          return (
            <>
              <rect x="3.5" y="6.2" width="17" height="11.6" rx="3.8" stroke={strokeGrad} strokeWidth={1.32} />
              <polygon points="10.4,9.4 15.5,12 10.4,14.6" stroke="#E4FAFF" strokeWidth={1.25} />
            </>
          );
        case "music":
          if (app.packageName === "com.spotify.music") {
            return (
              <>
                <circle cx="12" cy="12" r="8.2" stroke={strokeGrad} strokeWidth={1.28} />
                <path d="M7.9 9.8c2.7-.8 5.9-.6 8.4.7" stroke="#E4FAFF" strokeWidth={1.22} />
                <path d="M8.6 12.5c2.2-.6 4.9-.4 7 .6" stroke={strokeGrad} strokeWidth={1.18} />
                <path d="M9.4 15c1.7-.4 3.7-.2 5.2.5" stroke={warmGrad} strokeWidth={1.15} />
              </>
            );
          }
          return (
            <>
              <path d="M9.6 16V6.2l8.2-1.4v9.6" stroke={strokeGrad} strokeWidth={1.28} />
              <circle cx="7.6" cy="16.6" r="2.1" stroke="#E4FAFF" strokeWidth={1.22} />
              <circle cx="15.7" cy="15" r="2.1" stroke={warmGrad} strokeWidth={1.22} />
              <path d="M9.6 9.3l8.2-1.4" stroke={strokeGrad} strokeWidth={1.1} />
            </>
          );
        case "globe":
          if (app.packageName === "com.android.chrome") {
            return (
              <>
                <circle cx="12" cy="12" r="8.2" stroke={strokeGrad} strokeWidth={1.3} />
                <circle cx="12" cy="12" r="3.2" stroke="#E4FAFF" strokeWidth={1.25} />
                <path d="M12 8.8h7.5" stroke={strokeGrad} strokeWidth={1.18} />
                <path d="M9.2 13.6L5.5 7.2" stroke="#00E5FF" strokeWidth={1.18} />
                <path d="M14.8 13.6l-3.7 6.4" stroke={warmGrad} strokeWidth={1.18} />
              </>
            );
          }
          return (
            <>
              <circle cx="12" cy="12" r="8.2" stroke={strokeGrad} strokeWidth={1.28} />
              <ellipse cx="12" cy="12" rx="3.6" ry="8.2" stroke={strokeGrad} strokeWidth={1.12} />
              <path d="M4 12h16" stroke="#E4FAFF" strokeWidth={1.12} />
              <path d="M5.6 8.2h12.8" stroke={strokeGrad} strokeWidth={0.95} />
              <path d="M5.6 15.8h12.8" stroke={warmGrad} strokeWidth={0.95} />
            </>
          );
        case "camera":
          return (
            <>
              <path
                d="M4.8 8.6h2.6l1.2-1.9h6.8l1.2 1.9h2.6a1.6 1.6 0 0 1 1.6 1.6v7.2a1.6 1.6 0 0 1-1.6 1.6H4.8a1.6 1.6 0 0 1-1.6-1.6v-7.2a1.6 1.6 0 0 1 1.6-1.6z"
                stroke={strokeGrad}
                strokeWidth={1.3}
              />
              <circle cx="12" cy="13.6" r="3.2" stroke="#E4FAFF" strokeWidth={1.25} />
              <circle cx="17.5" cy="10.8" r="0.72" fill="#FFE2A8" stroke="#FFB45C" strokeWidth={0.4} />
            </>
          );
        case "image":
          return (
            <>
              <rect x="4" y="5" width="16" height="14" rx="2.6" stroke={strokeGrad} strokeWidth={1.3} />
              <path d="M4.8 16.2l4.2-4.3 3 3 2.5-2.3 4.5 3.8" stroke="#E4FAFF" strokeWidth={1.2} />
              <circle cx="15.2" cy="8.8" r="1.35" stroke="#FFB45C" strokeWidth={1.25} />
            </>
          );
        case "map":
          return (
            <>
              <path
                d="M12 19.6s5.6-4.9 5.6-9.4a5.6 5.6 0 1 0-11.2 0c0 4.5 5.6 9.4 5.6 9.4z"
                stroke={strokeGrad}
                strokeWidth={1.3}
              />
              <circle cx="12" cy="10.2" r="2.1" stroke="#E4FAFF" strokeWidth={1.22} />
            </>
          );
        case "calendar":
          return (
            <>
              <rect x="4.2" y="5.6" width="15.6" height="13.8" rx="2.4" stroke={strokeGrad} strokeWidth={1.3} />
              <path d="M4.2 9.6h15.6" stroke="#E4FAFF" strokeWidth={1.15} />
              <path d="M8.5 4v2.8M15.5 4v2.8" stroke="#E4FAFF" strokeWidth={1.25} />
              <rect x="7.4" y="12" width="1.6" height="1.4" rx="0.3" stroke="#00E5FF" strokeWidth={0.95} />
              <rect x="11.2" y="12" width="1.6" height="1.4" rx="0.3" stroke="#E4FAFF" strokeWidth={0.95} />
              <rect x="15" y="12" width="1.6" height="1.4" rx="0.3" stroke="#FFB45C" strokeWidth={0.95} />
              <rect x="7.4" y="15.3" width="1.6" height="1.4" rx="0.3" stroke="#398BFF" strokeWidth={0.95} />
              <rect x="11.2" y="15.3" width="1.6" height="1.4" rx="0.3" stroke="#FFB45C" strokeWidth={0.95} />
              <rect x="15" y="15.3" width="1.6" height="1.4" rx="0.3" stroke="#C47A48" strokeWidth={0.95} />
            </>
          );
        case "notes":
          return (
            <>
              <path
                d="M14.2 4.8H6.8a2 2 0 0 0-2 2v10.4a2 2 0 0 0 2 2h9.2a2 2 0 0 0 2-2V12.8"
                stroke={strokeGrad}
                strokeWidth={1.28}
              />
              <path d="M7.6 9.5h5.2M7.6 12.6h4.2M7.6 15.6h3" stroke="#E4FAFF" strokeWidth={1.12} />
              <path d="M13.5 13.8l5.6-5.6a1.1 1.1 0 0 1 1.6 1.6l-5.6 5.6-2.2.6.6-2.2z" stroke={warmGrad} strokeWidth={1.22} />
            </>
          );
        case "folder":
          return (
            <>
              <path
                d="M4 7.4a1.8 1.8 0 0 1 1.8-1.8h3.8l1.8 2h6.8a1.8 1.8 0 0 1 1.8 1.8v7.2a1.8 1.8 0 0 1-1.8 1.8H5.8A1.8 1.8 0 0 1 4 16.6V7.4z"
                stroke={strokeGrad}
                strokeWidth={1.3}
              />
              <path d="M4.2 10.4h15.6" stroke="#E4FAFF" strokeWidth={1.08} />
            </>
          );
        case "code":
          return (
            <>
              <path d="M8.6 8.4L4.6 12l4 3.6" stroke="#00E5FF" strokeWidth={1.3} />
              <path d="M15.4 8.4L19.4 12l-4 3.6" stroke={warmGrad} strokeWidth={1.3} />
              <path d="M13.4 6.6L10.6 17.4" stroke="#E4FAFF" strokeWidth={1.22} />
            </>
          );
        case "terminal":
          return (
            <>
              <rect x="3.8" y="5.2" width="16.4" height="13.6" rx="2.4" stroke={strokeGrad} strokeWidth={1.28} />
              <path d="M7.2 9.6l3 2.5-3 2.5" stroke="#E4FAFF" strokeWidth={1.28} />
              <path d="M12.2 14.6h4.4" stroke="#FFB45C" strokeWidth={1.28} />
            </>
          );
        case "discord":
          return (
            <>
              <path
                stroke={strokeGrad}
                strokeWidth={1.3}
                d="M18.6 6.8A12.8 12.8 0 0 0 15 5.7l-.45.9a11.8 11.8 0 0 0-5.1 0l-.45-.9a12.8 12.8 0 0 0-3.6 1.1C3.2 10.1 2.7 13.3 3 16.5a13.2 13.2 0 0 0 4 2l.8-1.4a8.4 8.4 0 0 1-1.4-.6l.35-.28a9.5 9.5 0 0 0 10.5 0l.35.28c-.45.26-.9.46-1.4.6l.8 1.4a13.2 13.2 0 0 0 4-2c.36-3.6-.64-6.8-2.4-9.7z"
              />
              <circle cx="9.4" cy="12.4" r="1.3" stroke="#E4FAFF" strokeWidth={1.2} />
              <circle cx="14.6" cy="12.4" r="1.3" stroke="#E4FAFF" strokeWidth={1.2} />
            </>
          );
        case "bank":
          return (
            <>
              <path
                stroke={strokeGrad}
                strokeWidth={1.32}
                d="M5.2 17.8V6.8c0-.85 1-1.3 1.55-.65l6 7.5V6.2"
              />
              <path
                stroke={warmGrad}
                strokeWidth={1.32}
                d="M18.8 6.2v11c0 .85-1 1.3-1.55.65l-6-7.5v7.5"
              />
            </>
          );
        case "settings":
        default:
          return (
            <>
              <circle cx="12" cy="12" r="2.7" stroke="#E4FAFF" strokeWidth={1.25} />
              <circle cx="12" cy="12" r="5.6" stroke={strokeGrad} strokeWidth={1.15} />
              <path
                stroke={strokeGrad}
                strokeWidth={1.25}
                d="M12 4.1v1.8M12 18.1v1.8M4.1 12h1.8M18.1 12h1.8M6.4 6.4l1.3 1.3M16.3 16.3l1.3 1.3M17.6 6.4l-1.3 1.3M7.7 16.3l-1.3 1.3"
              />
            </>
          );
      }
    };

    return (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        className={lunaDropShadowClass}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <defs>
          <linearGradient id={`${lunaGradId}-stroke`} x1="12%" y1="10%" x2="88%" y2="90%">
            <stop offset="0%" stopColor="#398BFF" />
            <stop offset="42%" stopColor="#00E5FF" />
            <stop offset="68%" stopColor="#E4FAFF" />
            <stop offset="86%" stopColor="#FFB45C" />
            <stop offset="100%" stopColor="#C47A48" />
          </linearGradient>
          <linearGradient id={`${lunaGradId}-warm`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#FFB45C" />
            <stop offset="100%" stopColor="#C47A48" />
          </linearGradient>
          <linearGradient id={`${lunaGradId}-wisp-cool`} x1="0%" y1="50%" x2="100%" y2="50%">
            <stop offset="0%" stopColor="#398BFF" stopOpacity="0" />
            <stop offset="28%" stopColor="#00E5FF" stopOpacity="0.95" />
            <stop offset="56%" stopColor="#E4FAFF" stopOpacity="0.9" />
            <stop offset="82%" stopColor="#FFB45C" stopOpacity="0.85" />
            <stop offset="100%" stopColor="#C47A48" stopOpacity="0" />
          </linearGradient>
          <linearGradient id={`${lunaGradId}-wisp-warm`} x1="100%" y1="50%" x2="0%" y2="50%">
            <stop offset="0%" stopColor="#C47A48" stopOpacity="0" />
            <stop offset="32%" stopColor="#FFB45C" stopOpacity="0.85" />
            <stop offset="68%" stopColor="#00E5FF" stopOpacity="0.55" />
            <stop offset="100%" stopColor="#398BFF" stopOpacity="0" />
          </linearGradient>
          <filter id={`${lunaGradId}-halo`} x="-35%" y="-35%" width="170%" height="170%">
            <feGaussianBlur stdDeviation="0.95" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {/* Gossamer elliptical light trails orbiting the suspended symbol */}
        <g transform={`rotate(${wispTilt} 12 12)`} opacity="0.88">
          <ellipse
            cx="12"
            cy="12"
            rx="10.4"
            ry="4.2"
            stroke={wispCoolGrad}
            strokeWidth={0.55}
            strokeDasharray="24 10"
          />
          <ellipse
            cx="12"
            cy="12"
            rx="9.4"
            ry="3.5"
            stroke={wispWarmGrad}
            strokeWidth={0.42}
            strokeDasharray="16 14"
            strokeDashoffset="6"
          />
        </g>

        {/* Luminous thin-line symbol projection */}
        <g filter={`url(#${lunaGradId}-halo)`}>{renderLunaSymbolPaths()}</g>

        {/* Pinpoint starlight sparks along the orbital trail */}
        <g transform={`rotate(${wispTilt} 12 12)`}>
          <circle cx="21.4" cy="11.2" r="0.55" fill="#FFE6B8" />
          <circle cx="2.7" cy="12.7" r="0.52" fill="#E4FAFF" />
          <circle cx="17.8" cy="15.3" r="0.38" fill="#FFB45C" />
          <circle cx="6.2" cy="8.7" r="0.38" fill="#00E5FF" />
        </g>
      </svg>
    );
  };

  const glyphNode = (() => {
    if (isLunaHolo) {
      return renderLunaProjectionGlyph();
    }

    switch (app.iconKey) {
      case "phone":
        return <Phone {...props} />;
      case "message":
        return <MessageSquare {...props} />;
      case "chat":
        return <MessageCircle {...props} />;
      case "instagram":
        return <Instagram {...props} />;
      case "youtube":
        return <Play {...props} />;
      case "music":
        return <Music {...props} />;
      case "globe":
        return <Globe {...props} />;
      case "camera":
        return <Camera {...props} />;
      case "image":
        return <ImageIcon {...props} />;
      case "map":
        return <MapPin {...props} />;
      case "calendar":
        return <Calendar {...props} />;
      case "notes":
        return <StickyNote {...props} />;
      case "folder":
        return <Folder {...props} />;
      case "code":
        return <Code2 {...props} />;
      case "terminal":
        return <Terminal {...props} />;
      case "discord":
        return (
          <svg
            viewBox="0 0 24 24"
            className={props.className}
            style={props.style}
            aria-hidden="true"
          >
            <path
              fill={isLiquidGold ? `url(#${goldGradId})` : colorStyle.color}
              d="M19.27 5.33C17.94 4.71 16.5 4.26 15 4a.09.09 0 0 0-.07.03c-.18.33-.39.76-.53 1.09a16.09 16.09 0 0 0-4.8 0c-.14-.34-.35-.76-.54-1.09-.01-.02-.04-.03-.07-.03-1.5.26-2.93.71-4.27 1.33-.01 0-.02.01-.03.02-2.72 4.07-3.47 8.03-3.1 11.95 0 .02.01.04.03.05 1.8 1.32 3.53 2.12 5.24 2.65.03.01.06 0 .07-.02.4-.55.76-1.13 1.07-1.74.02-.04 0-.08-.04-.09-.57-.22-1.11-.48-1.64-.78-.04-.02-.04-.08-.01-.11.11-.08.22-.17.33-.25.02-.02.05-.02.07-.01 3.44 1.57 7.15 1.57 10.55 0 .02-.01.05-.01.07.01.11.09.22.17.33.26.04.03.04.09-.01.11-.52.31-1.07.56-1.64.78-.04.01-.05.06-.04.09.32.61.68 1.19 1.07 1.74.03.01.06.02.09.01 1.72-.53 3.45-1.33 5.25-2.65.02-.01.03-.03.03-.05.44-4.53-.73-8.46-3.1-11.95-.01-.01-.02-.02-.04-.02zM8.52 14.91c-1.03 0-1.89-.95-1.89-2.12s.84-2.12 1.89-2.12c1.06 0 1.9.96 1.89 2.12 0 1.17-.84 2.12-1.89 2.12zm6.97 0c-1.03 0-1.89-.95-1.89-2.12s.84-2.12 1.89-2.12c1.06 0 1.9.96 1.89 2.12 0 1.17-.83 2.12-1.89 2.12z"
            />
          </svg>
        );
      case "bank":
        return (
          <svg
            viewBox="0 0 24 24"
            className={props.className}
            style={props.style}
            aria-hidden="true"
          >
            <path
              fill={isLiquidGold ? `url(#${goldGradId})` : colorStyle.color}
              d="M4 5.5C4 4.67 4.67 4 5.5 4h2.2c.83 0 1.5.67 1.5 1.5v7.1l5.6-7.7A2 2 0 0 1 16.42 4H18.5C19.33 4 20 4.67 20 5.5v13c0 .83-.67 1.5-1.5 1.5h-2.2c-.83 0-1.5-.67-1.5-1.5v-7.1l-5.6 7.7A2 2 0 0 1 7.58 20H5.5C4.67 20 4 19.33 4 18.5v-13z"
            />
          </svg>
        );
      case "settings":
      default:
        return <Settings {...props} />;
    }
  })();

  if (isLunaHolo) {
    return (
      <span
        className={`${sizeClass} relative flex items-center justify-center shrink-0 select-none`}
      >
        {glyphNode}
      </span>
    );
  }

  if (isLiquidGold) {
    return (
      <span
        className={`${sizeClass} relative rounded-[24%] bg-[#06070A] shadow-[0_7px_16px_rgba(0,0,0,0.92),0_0_12px_rgba(212,175,55,0.28)] flex items-center justify-center shrink-0 overflow-hidden select-none`}
      >
        {/* Smoked dark obsidian glass interior + concave 3D vignette */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_35%_28%,rgba(120,85,19,0.22)_0%,rgba(6,7,10,0.94)_58%,#030406_100%)]"
        />
        {/* Molded double-bevel refractive liquid gold frame + 3D specular filter for sculpted glyph */}
        <svg
          viewBox="0 0 40 40"
          className="pointer-events-none absolute inset-0 w-full h-full"
          aria-hidden="true"
        >
          <defs>
            <linearGradient id={goldGradId} x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stopColor="#FFF8D2" />
              <stop offset="20%" stopColor="#FFF3B0" />
              <stop offset="44%" stopColor="#D4AF37" />
              <stop offset="68%" stopColor="#785513" />
              <stop offset="86%" stopColor="#EAC65C" />
              <stop offset="100%" stopColor="#785513" />
            </linearGradient>
            <linearGradient id={`${goldGradId}-rim2`} x1="100%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stopColor="#FFF3B0" stopOpacity="0.95" />
              <stop offset="35%" stopColor="#D4AF37" stopOpacity="0.45" />
              <stop offset="70%" stopColor="#785513" stopOpacity="0.85" />
              <stop offset="100%" stopColor="#FFF3B0" stopOpacity="0.9" />
            </linearGradient>
            <filter id={`${goldGradId}-relief`} x="-25%" y="-25%" width="150%" height="150%">
              <feGaussianBlur in="SourceAlpha" stdDeviation="1.1" result="blur" />
              <feSpecularLighting
                in="blur"
                surfaceScale="3.2"
                specularConstant="1.15"
                specularExponent="22"
                lightingColor="#FFF3B0"
                result="spec"
              >
                <fePointLight x="-22" y="-24" z="42" />
              </feSpecularLighting>
              <feComposite in="spec" in2="SourceAlpha" operator="in" result="specClip" />
              <feMerge>
                <feMergeNode in="SourceGraphic" />
                <feMergeNode in="specClip" />
              </feMerge>
            </filter>
          </defs>
          <rect
            x="1.4"
            y="1.4"
            width="37.2"
            height="37.2"
            rx="9.2"
            fill="none"
            stroke={`url(#${goldGradId})`}
            strokeWidth="2.9"
          />
          <rect
            x="3.3"
            y="3.3"
            width="33.4"
            height="33.4"
            rx="7.4"
            fill="none"
            stroke={`url(#${goldGradId}-rim2)`}
            strokeWidth="1.05"
          />
        </svg>
        {/* Curved liquid glass dome reflection + sharp top-left & bottom-right optical flares */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute -top-[34%] -left-[12%] -right-[12%] h-[80%] rounded-[50%] bg-[linear-gradient(180deg,rgba(255,243,176,0.22)_0%,transparent_84%)]"
        />
        <span
          aria-hidden="true"
          className="pointer-events-none absolute top-[8%] left-[10%] w-2.5 h-2.5 rounded-full bg-[radial-gradient(circle,rgba(255,252,226,0.95)_0%,rgba(212,175,55,0.4)_45%,transparent_75%)]"
        />
        <span
          aria-hidden="true"
          className="pointer-events-none absolute bottom-[10%] right-[11%] w-2 h-2 rounded-full bg-[radial-gradient(circle,rgba(255,243,176,0.85)_0%,transparent_75%)]"
        />
        {glyphNode}
      </span>
    );
  }

  if (isObsidianFallback) {
    return (
      <span
        className={`${sizeClass} relative rounded-[20%] bg-gradient-to-br from-[#16181F] via-[#07080B] to-[#030406] border border-[#FF9E1B]/55 shadow-[inset_0_1px_2px_rgba(255,176,32,0.28),inset_0_-2px_6px_rgba(234,88,12,0.32),0_4px_12px_rgba(0,0,0,0.85)] flex items-center justify-center shrink-0 overflow-hidden`}
      >
        <span className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_50%_55%,rgba(255,140,26,0.22),transparent_68%)]" />
        <svg
          viewBox="0 0 40 40"
          className="pointer-events-none absolute inset-0 w-full h-full opacity-85"
          aria-hidden="true"
        >
          <path
            d="M3 10 L11 14 L14 19 M37 29 L29 25 L26 20 M12 37 L15 30 L19 28"
            fill="none"
            stroke="#FF9E1B"
            strokeWidth="0.9"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        <span className="pointer-events-none absolute bottom-1.5 right-1.5 w-1.5 h-1.5 rounded-full bg-amber-300 shadow-[0_0_8px_2px_rgba(255,138,0,0.95)]" />
        {glyphNode}
      </span>
    );
  }

  if (isCyberpunkFallback) {
    return (
      <span
        className={`${sizeClass} relative rounded-[24%] bg-gradient-to-br from-[#0E243B]/90 via-[#080F22]/90 to-[#1D0F2E]/90 border-[1.5px] border-cyan-400/80 shadow-[inset_0_1px_4px_rgba(244,114,182,0.5),0_0_11px_rgba(34,211,238,0.45)] flex items-center justify-center shrink-0 overflow-hidden`}
      >
        <span className="pointer-events-none absolute inset-[3px] rounded-[20%] border border-pink-400/45" />
        {glyphNode}
      </span>
    );
  }

  if (isTronFallback) {
    return (
      <span
        className={`${sizeClass} relative rounded-[24%] bg-gradient-to-br from-[#0B2D3A] via-[#041219] to-[#02090D] border-[1.5px] border-cyan-400/90 shadow-[inset_0_2px_4px_rgba(103,232,249,0.55),0_0_10px_rgba(34,211,238,0.48)] flex items-center justify-center shrink-0 overflow-hidden`}
      >
        <span className="pointer-events-none absolute top-1.5 right-1.5 w-2 h-1 rounded-full bg-amber-400 shadow-[0_0_6px_rgba(245,158,11,0.9)]" />
        {glyphNode}
      </span>
    );
  }

  if (illustratedFallback) {
    return (
      <span
        className={`${sizeClass} rounded-[24%] bg-gradient-to-br from-[#141B2A] via-[#05080F] to-[#0B1120] border border-cyan-300/60 shadow-[inset_0_1px_2px_rgba(255,255,255,0.45),0_0_8px_rgba(34,211,238,0.28)] flex items-center justify-center shrink-0`}
      >
        {glyphNode}
      </span>
    );
  }

  return glyphNode;
}
