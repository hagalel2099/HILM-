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
  resolveIllustratedIconUrl,
} from "../data/iconPackRegistry";
import { HilmArtifactGlyph } from "./HilmArtifactGlyph";

interface AppGlyphProps {
  app: InstalledAppItem;
  iconPack: IconPackId;
  customIconDataUrl?: string;
  sizeClass?: string;
}

export function AppGlyph({
  app,
  iconPack,
  customIconDataUrl,
  sizeClass = "w-5 h-5",
}: AppGlyphProps) {
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

  const illustratedFallback = isIllustratedIconPack(iconPack);
  const isObsidianFallback = illustratedFallback && iconPack === "obsidian-core";
  const isTronFallback = illustratedFallback && iconPack === "tron-neon";
  const isCyberpunkFallback = illustratedFallback && iconPack === "cyberpunk-holo";

  const colorStyle = (() => {
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
    className: illustratedFallback
      ? isObsidianFallback
        ? "relative z-10 w-[54%] h-[54%] drop-shadow-[0_0_6px_rgba(255,140,26,0.75)]"
        : isCyberpunkFallback
          ? "w-[56%] h-[56%] drop-shadow-[0_0_7px_rgba(236,72,153,0.9)]"
          : isTronFallback
            ? "w-[56%] h-[56%] drop-shadow-[0_0_6px_rgba(34,211,238,0.85)]"
            : "w-[58%] h-[58%] drop-shadow-[0_1px_2px_rgba(34,211,238,0.45)]"
      : sizeClass,
    style: colorStyle,
    strokeWidth: illustratedFallback || iconPack === "nosso-universo" ? 1.85 : 2,
  };

  const glyphNode = (() => {
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
      case "settings":
      default:
        return <Settings {...props} />;
    }
  })();

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
