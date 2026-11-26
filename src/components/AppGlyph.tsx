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
    "relative z-10 w-[82%] h-[82%] drop-shadow-[0_0_5px_rgba(0,229,255,0.92)] drop-shadow-[0_0_12px_rgba(57,139,255,0.48)]";

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
    const svgBaseProps = {
      viewBox: "0 0 24 24",
      fill: "none",
      className: lunaDropShadowClass,
      strokeLinecap: "round" as const,
      strokeLinejoin: "round" as const,
      "aria-hidden": true as const,
    };

    switch (app.iconKey) {
      case "phone":
        return (
          <svg {...svgBaseProps}>
            <path
              d="M6.4 3.6h2.7c.5 0 .9.34 1.02.82l.72 2.88c.1.42-.04.86-.37 1.14l-1.45 1.2a11.4 11.4 0 0 0 5.34 5.34l1.2-1.45c.28-.33.72-.47 1.14-.37l2.88.72c.48.12.82.52.82 1.02v2.7a1.5 1.5 0 0 1-1.64 1.5C10.56 18.62 5.38 13.44 4.9 5.24A1.5 1.5 0 0 1 6.4 3.6z"
              stroke="#00E5FF"
              strokeWidth={1.25}
            />
            <path d="M14.2 5.2a4.6 4.6 0 0 1 4.6 4.6" stroke="#398BFF" strokeWidth={1.15} />
            <path d="M14.2 8.2a1.8 1.8 0 0 1 1.6 1.6" stroke="#FFB45C" strokeWidth={1.25} />
          </svg>
        );
      case "message":
        return (
          <svg {...svgBaseProps}>
            <path
              d="M5.2 4.6h13.6a2.2 2.2 0 0 1 2.2 2.2v8.4a2.2 2.2 0 0 1-2.2 2.2H9.6L5 20.6v-3.2H5.2A2.2 2.2 0 0 1 3 15.2V6.8a2.2 2.2 0 0 1 2.2-2.2z"
              stroke="#00E5FF"
              strokeWidth={1.25}
            />
            <path d="M7.8 9.6h8.4" stroke="#D9F7FF" strokeWidth={1.2} />
            <path d="M7.8 12.8h5.2" stroke="#398BFF" strokeWidth={1.18} />
            <circle cx="15.8" cy="12.8" r="0.85" fill="#FFB45C" stroke="none" />
          </svg>
        );
      case "chat":
        return (
          <svg {...svgBaseProps}>
            <path
              d="M12 3.2a8.8 8.8 0 0 0-7.6 13.25L3.2 20.8l4.5-1.18A8.8 8.8 0 1 0 12 3.2z"
              stroke="#00E5FF"
              strokeWidth={1.25}
            />
            <path
              d="M9.4 8.9c-.2-.38-.38-.38-.58-.38h-.4c-.2 0-.4.1-.58.3-.2.28-.68.78-.68 1.78s.78 2.15.96 2.34c.12.2 1.56 2.45 3.92 3.34 1.86.68 2.34.58 2.84.48.48-.1 1.26-.5 1.46-.98.2-.48.2-.88.1-.98-.1-.1-.22-.18-.48-.3l-1.26-.58c-.2-.1-.38-.1-.58.1l-.48.58c-.2.2-.3.2-.58.1-.3-.1-1.08-.4-1.96-1.18-.68-.58-1.18-1.36-1.28-1.66-.1-.28 0-.38.1-.48l.38-.38c.1-.2.2-.3.2-.4.1-.1 0-.28 0-.38l-.58-1.36z"
              stroke="#D9F7FF"
              strokeWidth={1.15}
            />
            <path d="M18.6 6.2a8.9 8.9 0 0 1 2 4.1" stroke="#C47A48" strokeWidth={1.1} />
          </svg>
        );
      case "instagram":
        return (
          <svg {...svgBaseProps}>
            <rect x="3.5" y="3.5" width="17" height="17" rx="5" stroke="#00E5FF" strokeWidth={1.25} />
            <circle cx="12" cy="12" r="3.9" stroke="#D9F7FF" strokeWidth={1.25} />
            <circle cx="16.8" cy="7.2" r="0.95" fill="#FFB45C" stroke="none" />
            <path d="M7.2 3.5h5.2" stroke="#398BFF" strokeWidth={1.15} />
          </svg>
        );
      case "youtube":
        return (
          <svg {...svgBaseProps}>
            <rect x="2.6" y="5.4" width="18.8" height="13.2" rx="4.2" stroke="#398BFF" strokeWidth={1.25} />
            <path d="M7.2 5.4h9.6" stroke="#00E5FF" strokeWidth={1.2} />
            <polygon points="10.2,9 15.8,12 10.2,15" stroke="#D9F7FF" strokeWidth={1.25} />
            <circle cx="18.2" cy="8.2" r="0.75" fill="#C47A48" stroke="none" />
          </svg>
        );
      case "music":
        if (app.packageName === "com.spotify.music") {
          return (
            <svg {...svgBaseProps}>
              <circle cx="12" cy="12" r="9.1" stroke="#00E5FF" strokeWidth={1.22} />
              <path d="M7.4 9.6c3-.9 6.6-.7 9.4.8" stroke="#D9F7FF" strokeWidth={1.28} />
              <path d="M8.2 12.6c2.5-.7 5.5-.5 7.8.7" stroke="#00E5FF" strokeWidth={1.22} />
              <path d="M9.1 15.4c1.9-.5 4.2-.3 5.9.6" stroke="#398BFF" strokeWidth={1.18} />
              <circle cx="17.8" cy="6.6" r="0.8" fill="#FFB45C" stroke="none" />
            </svg>
          );
        }
        return (
          <svg {...svgBaseProps}>
            <path d="M9.5 16.5V5.8l9-1.6v10.5" stroke="#00E5FF" strokeWidth={1.25} />
            <circle cx="7.2" cy="17.2" r="2.3" stroke="#D9F7FF" strokeWidth={1.22} />
            <circle cx="16.2" cy="15.4" r="2.3" stroke="#398BFF" strokeWidth={1.22} />
            <path d="M9.5 9.2l9-1.6" stroke="#C47A48" strokeWidth={1.1} />
          </svg>
        );
      case "globe":
        if (app.packageName === "com.android.chrome") {
          return (
            <svg {...svgBaseProps}>
              <circle cx="12" cy="12" r="9.1" stroke="#00E5FF" strokeWidth={1.22} />
              <circle cx="12" cy="12" r="3.5" stroke="#D9F7FF" strokeWidth={1.25} />
              <path d="M12 8.5h8.3" stroke="#00E5FF" strokeWidth={1.18} />
              <path d="M8.9 13.7L4.8 6.7" stroke="#398BFF" strokeWidth={1.18} />
              <path d="M15.1 13.7l-4.1 7.1" stroke="#C47A48" strokeWidth={1.15} />
            </svg>
          );
        }
        return (
          <svg {...svgBaseProps}>
            <circle cx="12" cy="12" r="9.1" stroke="#00E5FF" strokeWidth={1.22} />
            <ellipse cx="12" cy="12" rx="3.9" ry="9.1" stroke="#398BFF" strokeWidth={1.15} />
            <path d="M3.2 12h17.6" stroke="#D9F7FF" strokeWidth={1.15} />
            <circle cx="17.4" cy="7.5" r="0.8" fill="#FFB45C" stroke="none" />
          </svg>
        );
      case "camera":
        return (
          <svg {...svgBaseProps}>
            <path
              d="M4.2 8.2h2.9l1.4-2.1h7l1.4 2.1h2.9a1.8 1.8 0 0 1 1.8 1.8v8.1a1.8 1.8 0 0 1-1.8 1.8H4.2a1.8 1.8 0 0 1-1.8-1.8V10a1.8 1.8 0 0 1 1.8-1.8z"
              stroke="#00E5FF"
              strokeWidth={1.25}
            />
            <circle cx="12" cy="13.8" r="3.6" stroke="#D9F7FF" strokeWidth={1.25} />
            <circle cx="12" cy="13.8" r="1.2" stroke="#398BFF" strokeWidth={1.1} />
            <circle cx="18.1" cy="10.7" r="0.8" fill="#FFB45C" stroke="none" />
          </svg>
        );
      case "image":
        return (
          <svg {...svgBaseProps}>
            <rect x="3.4" y="4.4" width="17.2" height="15.2" rx="2.8" stroke="#00E5FF" strokeWidth={1.25} />
            <path d="M4.2 16.5l4.6-4.7 3.4 3.4 2.7-2.6 4.7 4.2" stroke="#D9F7FF" strokeWidth={1.22} />
            <circle cx="8.6" cy="9.1" r="1.45" stroke="#FFB45C" strokeWidth={1.2} />
            <path d="M13.5 4.4h4.5" stroke="#398BFF" strokeWidth={1.15} />
          </svg>
        );
      case "map":
        return (
          <svg {...svgBaseProps}>
            <path
              d="M12 20.5s6.2-5.4 6.2-10.4a6.2 6.2 0 1 0-12.4 0c0 5 6.2 10.4 6.2 10.4z"
              stroke="#00E5FF"
              strokeWidth={1.25}
            />
            <circle cx="12" cy="10.1" r="2.3" stroke="#D9F7FF" strokeWidth={1.22} />
            <path d="M6.2 19.6c1.9.7 3.8 1 5.8 1s3.9-.3 5.8-1" stroke="#398BFF" strokeWidth={1.12} />
            <circle cx="12" cy="10.1" r="0.65" fill="#FFB45C" stroke="none" />
          </svg>
        );
      case "calendar":
        return (
          <svg {...svgBaseProps}>
            <rect x="3.5" y="5" width="17" height="15.5" rx="2.6" stroke="#00E5FF" strokeWidth={1.25} />
            <path d="M3.5 9.5h17" stroke="#398BFF" strokeWidth={1.18} />
            <path d="M8.2 3.4v3.1M15.8 3.4v3.1" stroke="#D9F7FF" strokeWidth={1.25} />
            <circle cx="8.5" cy="13.4" r="0.85" fill="#D9F7FF" stroke="none" />
            <circle cx="12" cy="13.4" r="0.85" fill="#00E5FF" stroke="none" />
            <circle cx="15.5" cy="13.4" r="0.85" fill="#FFB45C" stroke="none" />
            <path d="M8.2 16.8h4.5" stroke="#C47A48" strokeWidth={1.15} />
          </svg>
        );
      case "notes":
        return (
          <svg {...svgBaseProps}>
            <path
              d="M6.2 3.5h8.6l4.4 4.4v11.1a1.6 1.6 0 0 1-1.6 1.6H6.2a1.6 1.6 0 0 1-1.6-1.6V5.1a1.6 1.6 0 0 1 1.6-1.6z"
              stroke="#00E5FF"
              strokeWidth={1.25}
            />
            <path d="M14.8 3.5v4.4h4.4" stroke="#398BFF" strokeWidth={1.18} />
            <path d="M8 11.4h8" stroke="#D9F7FF" strokeWidth={1.18} />
            <path d="M8 14.6h6" stroke="#C47A48" strokeWidth={1.15} />
            <path d="M8 17.4h3.5" stroke="#FFB45C" strokeWidth={1.15} />
          </svg>
        );
      case "folder":
        return (
          <svg {...svgBaseProps}>
            <path
              d="M3.5 7a2 2 0 0 1 2-2h4.2l2 2.2h6.8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V7z"
              stroke="#00E5FF"
              strokeWidth={1.25}
            />
            <path d="M3.6 10.2h16.8" stroke="#398BFF" strokeWidth={1.15} />
            <path d="M7.5 14.5h4.8" stroke="#FFB45C" strokeWidth={1.2} />
          </svg>
        );
      case "code":
        return (
          <svg {...svgBaseProps}>
            <path d="M8.5 8.2L4.2 12l4.3 3.8" stroke="#00E5FF" strokeWidth={1.28} />
            <path d="M15.5 8.2L19.8 12l-4.3 3.8" stroke="#398BFF" strokeWidth={1.28} />
            <path d="M13.6 6.2L10.4 17.8" stroke="#D9F7FF" strokeWidth={1.22} />
            <circle cx="12" cy="4.2" r="0.8" fill="#FFB45C" stroke="none" />
          </svg>
        );
      case "terminal":
        return (
          <svg {...svgBaseProps}>
            <rect x="3" y="4.6" width="18" height="14.8" rx="2.6" stroke="#398BFF" strokeWidth={1.22} />
            <path d="M6.8 9.4l3.3 2.7-3.3 2.7" stroke="#D9F7FF" strokeWidth={1.28} />
            <path d="M12.2 14.8h4.8" stroke="#FFB45C" strokeWidth={1.28} />
            <path d="M6.5 4.6h6.5" stroke="#00E5FF" strokeWidth={1.22} />
          </svg>
        );
      case "discord":
        return (
          <svg {...svgBaseProps}>
            <path
              stroke="#00E5FF"
              strokeWidth={1.25}
              d="M19.2 6.2A14 14 0 0 0 15.3 5l-.5 1a13 13 0 0 0-5.6 0l-.5-1A14 14 0 0 0 4.8 6.2C2.4 9.8 1.8 13.3 2.1 16.8a14.4 14.4 0 0 0 4.4 2.2l.9-1.5a9.3 9.3 0 0 1-1.5-.7l.4-.3a10.4 10.4 0 0 0 11.4 0l.4.3c-.5.3-1 .5-1.5.7l.9 1.5a14.4 14.4 0 0 0 4.4-2.2c.4-4-.7-7.5-2.7-10.6z"
            />
            <circle cx="9.2" cy="12.4" r="1.4" stroke="#D9F7FF" strokeWidth={1.2} />
            <circle cx="14.8" cy="12.4" r="1.4" stroke="#D9F7FF" strokeWidth={1.2} />
            <path d="M9.6 6.2h4.8" stroke="#C47A48" strokeWidth={1.1} />
          </svg>
        );
      case "bank":
        return (
          <svg {...svgBaseProps}>
            <path
              stroke="#00E5FF"
              strokeWidth={1.25}
              d="M4.5 18.5V6.2c0-.9 1.1-1.4 1.7-.7l6.6 8.3V5.5"
            />
            <path
              stroke="#D9F7FF"
              strokeWidth={1.25}
              d="M19.5 5.5v12.3c0 .9-1.1 1.4-1.7.7l-6.6-8.3v8.3"
            />
            <circle cx="19.2" cy="5.2" r="0.8" fill="#FFB45C" stroke="none" />
          </svg>
        );
      case "settings":
      default:
        return (
          <svg {...svgBaseProps}>
            <circle cx="12" cy="12" r="2.9" stroke="#D9F7FF" strokeWidth={1.25} />
            <path
              stroke="#00E5FF"
              strokeWidth={1.22}
              d="M12 3.6v2.1M12 18.3v2.1M3.6 12h2.1M18.3 12h2.1M6.1 6.1l1.5 1.5M16.4 16.4l1.5 1.5M17.9 6.1l-1.5 1.5M7.6 16.4l-1.5 1.5"
            />
            <circle cx="12" cy="12" r="6.3" stroke="#398BFF" strokeWidth={1.15} strokeDasharray="4.2 2.4" />
            <circle cx="12" cy="12" r="0.75" fill="#C47A48" stroke="none" />
          </svg>
        );
    }
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

  if (isLunaHolo && lunaProfile) {
    const showWarmLight = lunaProfile.mode !== "cool";
    const isCopperLead = lunaProfile.mode === "copper-rim";
    const isDual = lunaProfile.mode === "copper-amber";

    return (
      <span
        className={`${sizeClass} relative flex items-center justify-center shrink-0 select-none`}
      >
        {glyphNode}
        {showWarmLight && (
          <svg
            viewBox="0 0 40 40"
            className="pointer-events-none absolute inset-0 w-full h-full"
            aria-hidden="true"
          >
            <g transform={`rotate(${lunaProfile.angleDeg} 20 20)`}>
              <circle
                cx="35.5"
                cy="20"
                r="1.15"
                fill={isCopperLead ? "#C47A48" : "#FFB45C"}
                fillOpacity="0.9"
                className="drop-shadow-[0_0_4px_rgba(255,180,92,0.85)]"
              />
              {isDual && (
                <circle
                  cx="4.8"
                  cy="20"
                  r="0.9"
                  fill="#C47A48"
                  fillOpacity="0.8"
                />
              )}
            </g>
          </svg>
        )}
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
