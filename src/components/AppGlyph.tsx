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

const LUNA_WARM_ACCENT_KEYS = new Set(["camera", "music", "settings", "notes", "terminal"]);

export function AppGlyph({
  app,
  iconPack,
  customIconDataUrl,
  sizeClass = "w-5 h-5",
}: AppGlyphProps) {
  const rawId = useId();
  const goldGradId = `hilm-gold-${rawId.replace(/[^a-zA-Z0-9_-]/g, "")}`;
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

  const props = {
    className: isLunaHolo
      ? "relative z-10 w-[72%] h-[72%] drop-shadow-[0_0_7px_rgba(0,229,255,0.68)]"
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
      ? 1.75
      : isLiquidGold
        ? 2.8
        : illustratedFallback || iconPack === "nosso-universo"
          ? 1.85
          : 2,
  };

  const glyphNode = (() => {
    switch (app.iconKey) {
      case "phone":
        return <Phone {...props} />;
      case "message":
        return <MessageSquare {...props} />;
      case "chat":
        if (isLunaHolo && app.packageName === "com.whatsapp") {
          return (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className={props.className}
              style={props.style}
              stroke="currentColor"
              strokeWidth={1.65}
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M12 3a9 9 0 0 0-7.75 13.56L3 21l4.6-1.2A9 9 0 1 0 12 3z" />
              <path
                d="M9.5 8.8c-.2-.4-.4-.4-.6-.4h-.4c-.2 0-.4.1-.6.3-.2.3-.7.8-.7 1.8s.8 2.2 1 2.4c.1.2 1.6 2.5 4 3.4 1.9.7 2.4.6 2.9.5.5-.1 1.3-.5 1.5-1 .2-.5.2-.9.1-1-.1-.1-.2-.2-.5-.3l-1.3-.6c-.2-.1-.4-.1-.6.1l-.5.6c-.2.2-.3.2-.6.1-.3-.1-1.1-.4-2-1.2-.7-.6-1.2-1.4-1.3-1.7-.1-.3 0-.4.1-.5l.4-.4c.1-.2.2-.3.2-.4.1-.1 0-.3 0-.4l-.6-1.4z"
                stroke="#D9F7FF"
                strokeWidth={1.3}
              />
            </svg>
          );
        }
        return <MessageCircle {...props} />;
      case "instagram":
        return <Instagram {...props} />;
      case "youtube":
        if (isLunaHolo) {
          return (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className={props.className}
              style={props.style}
              stroke="currentColor"
              strokeWidth={1.65}
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <rect x="2.5" y="5.5" width="19" height="13" rx="4.2" stroke="#398BFF" />
              <polygon points="10,9 16,12 10,15" stroke="#D9F7FF" strokeWidth={1.6} />
            </svg>
          );
        }
        return <Play {...props} />;
      case "music":
        if (isLunaHolo && app.packageName === "com.spotify.music") {
          return (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className={props.className}
              style={props.style}
              stroke="currentColor"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <circle cx="12" cy="12" r="9.2" strokeWidth={1.55} stroke="#00E5FF" />
              <path d="M7.4 9.6c3-.9 6.6-.7 9.4.8" stroke="#D9F7FF" strokeWidth={1.7} />
              <path d="M8.2 12.6c2.5-.7 5.5-.5 7.8.7" stroke="#00E5FF" strokeWidth={1.6} />
              <path d="M9.1 15.4c1.9-.5 4.2-.3 5.9.6" stroke="#398BFF" strokeWidth={1.5} />
            </svg>
          );
        }
        return <Music {...props} />;
      case "globe":
        if (isLunaHolo && app.packageName === "com.android.chrome") {
          return (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className={props.className}
              style={props.style}
              stroke="currentColor"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <circle cx="12" cy="12" r="9.2" strokeWidth={1.6} stroke="#00E5FF" />
              <circle cx="12" cy="12" r="3.6" stroke="#D9F7FF" strokeWidth={1.6} />
              <path d="M12 8.4h8.4" stroke="#00E5FF" strokeWidth={1.55} />
              <path d="M8.9 13.8L4.7 6.6" stroke="#398BFF" strokeWidth={1.55} />
              <path d="M15.1 13.8l-4.2 7.2" stroke="#00E5FF" strokeWidth={1.55} />
            </svg>
          );
        }
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
        if (isLunaHolo) {
          return (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className={props.className}
              style={props.style}
              stroke="currentColor"
              strokeWidth={1.6}
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path
                stroke="#00E5FF"
                d="M19.2 6.2A14 14 0 0 0 15.3 5l-.5 1a13 13 0 0 0-5.6 0l-.5-1A14 14 0 0 0 4.8 6.2C2.4 9.8 1.8 13.3 2.1 16.8a14.4 14.4 0 0 0 4.4 2.2l.9-1.5a9.3 9.3 0 0 1-1.5-.7l.4-.3a10.4 10.4 0 0 0 11.4 0l.4.3c-.5.3-1 .5-1.5.7l.9 1.5a14.4 14.4 0 0 0 4.4-2.2c.4-4-.7-7.5-2.7-10.6z"
              />
              <circle cx="9.2" cy="12.4" r="1.4" stroke="#D9F7FF" strokeWidth={1.45} />
              <circle cx="14.8" cy="12.4" r="1.4" stroke="#D9F7FF" strokeWidth={1.45} />
            </svg>
          );
        }
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
        if (isLunaHolo) {
          return (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className={props.className}
              style={props.style}
              stroke="currentColor"
              strokeWidth={1.6}
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path
                stroke="#00E5FF"
                d="M4.5 18.5V6.2c0-.9 1.1-1.4 1.7-.7l6.6 8.3V5.5"
              />
              <path
                stroke="#D9F7FF"
                d="M19.5 5.5v12.3c0 .9-1.1 1.4-1.7.7l-6.6-8.3v8.3"
              />
            </svg>
          );
        }
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
    const hasWarmGlint = LUNA_WARM_ACCENT_KEYS.has(app.iconKey);
    return (
      <span
        className={`${sizeClass} relative flex items-center justify-center shrink-0 select-none`}
      >
        {/* Ultra-discreet dark-navy radial mist behind symbol (no square box) */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-[-12%] rounded-full bg-[radial-gradient(circle,rgba(10,20,36,0.72)_0%,rgba(5,7,11,0.34)_52%,transparent_76%)]"
        />
        {/* Delicate holographic projection contour arc */}
        <svg
          viewBox="0 0 40 40"
          className="pointer-events-none absolute inset-0 w-full h-full opacity-80"
          aria-hidden="true"
        >
          <circle
            cx="20"
            cy="20"
            r="17.5"
            fill="none"
            stroke="#398BFF"
            strokeOpacity="0.34"
            strokeWidth="0.9"
            strokeDasharray="16 10 5 10"
          />
          <path
            d="M 8 11 A 15 15 0 0 1 16 6.5"
            fill="none"
            stroke="#D9F7FF"
            strokeOpacity="0.65"
            strokeWidth="1.05"
            strokeLinecap="round"
          />
        </svg>
        {/* Bluish-white inner glint */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute top-[14%] left-[18%] w-1 h-1 rounded-full bg-[#D9F7FF] opacity-85 shadow-[0_0_5px_#00E5FF]"
        />
        {/* Selective copper/amber micro-glint on curated apps only */}
        {hasWarmGlint && (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute bottom-[15%] right-[16%] w-1.5 h-1.5 rounded-full bg-[#FFB45C] ring-1 ring-[#C47A48]/80 shadow-[0_0_6px_rgba(255,180,92,0.8)]"
          />
        )}
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
