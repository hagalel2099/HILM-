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
import { resolveIllustratedIconUrl } from "../data/iconPackRegistry";

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
        className={`${sizeClass} object-contain`}
      />
    );
  }

  const colorStyle = (() => {
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
    className: sizeClass,
    style: colorStyle,
    strokeWidth: iconPack === "nosso-universo" ? 1.85 : 2,
  };

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
}
