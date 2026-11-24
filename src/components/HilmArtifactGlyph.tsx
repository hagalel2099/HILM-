import { useId } from "react";
import {
  Calendar,
  Camera,
  Clock,
  Code2,
  Folder,
  Globe,
  Image as ImageIcon,
  Landmark,
  Mail,
  MapPin,
  MessageCircle,
  MessageSquare,
  Music,
  Phone,
  Play,
  Settings,
  ShoppingBag,
  StickyNote,
  Terminal,
} from "lucide-react";
import type { InstalledAppItem } from "../types/launcher";

type ArtifactArchetype =
  | "optical_lens"
  | "acoustic_waves"
  | "glass_planes"
  | "communication_pulses"
  | "orbital_glass_orb"
  | "precision_mechanics";

const ARCHETYPE_BASE_PATH: Record<ArtifactArchetype, string> = {
  optical_lens: "/assets/icon-packs/hilm-artifacts/bases/base_optical_lens.png",
  acoustic_waves: "/assets/icon-packs/hilm-artifacts/bases/base_acoustic_waves.png",
  glass_planes: "/assets/icon-packs/hilm-artifacts/bases/base_glass_planes.png",
  communication_pulses:
    "/assets/icon-packs/hilm-artifacts/bases/base_communication_pulses.png",
  orbital_glass_orb:
    "/assets/icon-packs/hilm-artifacts/bases/base_orbital_glass_orb.png",
  precision_mechanics:
    "/assets/icon-packs/hilm-artifacts/bases/base_precision_mechanics.png",
};

const ARCHETYPES: ArtifactArchetype[] = [
  "optical_lens",
  "acoustic_waves",
  "glass_planes",
  "communication_pulses",
  "orbital_glass_orb",
  "precision_mechanics",
];

function hashString(input: string): number {
  let h = 0;
  for (let i = 0; i < input.length; i++) {
    h = (Math.imul(31, h) + input.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

function classifyArchetype(app: InstalledAppItem): ArtifactArchetype {
  const key = app.iconKey.toLowerCase();
  const id = `${app.packageName} ${app.category} ${app.label}`.toLowerCase();

  if (
    key === "camera" ||
    key === "youtube" ||
    /(camera|cam|lens|youtube|netflix|twitch|video|scanner)/.test(id)
  ) {
    return "optical_lens";
  }
  if (
    key === "music" ||
    /(music|spotify|deezer|tidal|podcast|audio|sound)/.test(id)
  ) {
    return "acoustic_waves";
  }
  if (
    key === "image" ||
    key === "notes" ||
    key === "folder" ||
    /(gallery|galeria|photos|notes|notas|files|arquivos|drive|docs)/.test(id)
  ) {
    return "glass_planes";
  }
  if (
    key === "message" ||
    key === "chat" ||
    key === "instagram" ||
    key === "discord" ||
    key === "phone" ||
    key === "mail" ||
    /(message|mensag|whatsapp|instagram|discord|telegram|dialer|phone|telef|social|mail|gmail)/.test(
      id
    )
  ) {
    return "communication_pulses";
  }
  if (
    key === "globe" ||
    key === "map" ||
    /(browser|chrome|firefox|navegador|maps|mapas|waze|uber|weather|globe)/.test(id)
  ) {
    return "orbital_glass_orb";
  }
  if (
    key === "settings" ||
    key === "code" ||
    key === "terminal" ||
    key === "calendar" ||
    key === "bank" ||
    key === "clock" ||
    key === "store" ||
    /(settings|ajustes|github|androidide|calendar|nubank|bank|finan|calc|clock|store|game)/.test(
      id
    )
  ) {
    return "precision_mechanics";
  }

  return ARCHETYPES[hashString(app.packageName) % ARCHETYPES.length];
}

function renderAuthenticColorEmblem(app: InstalledAppItem, uid: string) {
  const pkg = app.packageName.toLowerCase();
  const key = app.iconKey.toLowerCase();

  if (pkg.includes("spotify")) {
    return (
      <svg viewBox="0 0 40 40" className="w-[54%] h-[54%] drop-shadow-[0_2px_6px_rgba(0,0,0,0.9)]">
        <circle cx="20" cy="20" r="15" fill="#1DB954" />
        <path d="M11.5 15.2 C17 13.5 23.5 14 28.5 16.8" fill="none" stroke="#052E16" strokeWidth="2.8" strokeLinecap="round" />
        <path d="M13 20 C17.6 18.6 22.8 19 26.8 21.2" fill="none" stroke="#052E16" strokeWidth="2.4" strokeLinecap="round" />
        <path d="M14.5 24.4 C18.2 23.4 22 23.7 25 25.4" fill="none" stroke="#052E16" strokeWidth="2.1" strokeLinecap="round" />
      </svg>
    );
  }

  if (key === "bank" || pkg.includes("nu.production")) {
    return (
      <svg viewBox="0 0 40 40" className="w-[54%] h-[54%] drop-shadow-[0_2px_6px_rgba(0,0,0,0.9)]">
        <path
          d="M9 30 V13 C9 9 14.5 8.5 17 12.2 L23 24.8 C25.5 28.5 31 28 31 24 V11"
          fill="none"
          stroke="#A855F7"
          strokeWidth="5.4"
          strokeLinecap="round"
        />
        <path
          d="M9 30 V13 C9 9 14.5 8.5 17 12.2 L23 24.8 C25.5 28.5 31 28 31 24 V11"
          fill="none"
          stroke="#F3E8FF"
          strokeWidth="1.8"
          strokeLinecap="round"
        />
      </svg>
    );
  }

  if (key === "discord" || pkg.includes("discord")) {
    return (
      <svg viewBox="0 0 40 40" className="w-[54%] h-[54%] drop-shadow-[0_2px_6px_rgba(0,0,0,0.9)]">
        <path
          d="M9 12 C12.5 9.8 16.2 9.4 20 9.4 C23.8 9.4 27.5 9.8 31 12 C33.8 17.2 34.5 23.2 33.2 29.4 C30.2 31.8 27 33 23.8 33.5 L22.2 30.6 C23.8 30.1 25.1 29.5 26.2 28.7 C24.3 29.7 22.2 30.2 20 30.2 C17.8 30.2 15.7 29.7 13.8 28.7 C14.9 29.5 16.2 30.1 17.8 30.6 L16.2 33.5 C13 33 9.8 31.8 6.8 29.4 C5.5 23.2 6.2 17.2 9 12 Z"
          fill="#5865F2"
        />
        <circle cx="15" cy="21.5" r="2.8" fill="#FFFFFF" />
        <circle cx="25" cy="21.5" r="2.8" fill="#FFFFFF" />
      </svg>
    );
  }

  if (key === "instagram" || pkg.includes("instagram")) {
    return (
      <svg viewBox="0 0 40 40" className="w-[52%] h-[52%] drop-shadow-[0_2px_6px_rgba(0,0,0,0.9)]">
        <defs>
          <linearGradient id={`${uid}-insta`} x1="10%" y1="90%" x2="90%" y2="10%">
            <stop offset="0%" stopColor="#F59E0B" />
            <stop offset="50%" stopColor="#EC4899" />
            <stop offset="100%" stopColor="#6366F1" />
          </linearGradient>
        </defs>
        <rect x="6" y="6" width="28" height="28" rx="8" fill={`url(#${uid}-insta)`} />
        <circle cx="20" cy="20" r="6.4" fill="none" stroke="#FFFFFF" strokeWidth="2.5" />
        <circle cx="28" cy="12" r="1.7" fill="#FFFFFF" />
      </svg>
    );
  }

  const props = {
    className: "w-[50%] h-[50%] drop-shadow-[0_2px_6px_rgba(2,6,18,0.95)]",
    style: { color: app.accentHex || "#38BDF8" },
    strokeWidth: 2.2,
  };

  switch (app.iconKey) {
    case "phone":
      return <Phone {...props} />;
    case "message":
      return <MessageSquare {...props} />;
    case "chat":
      return <MessageCircle {...props} />;
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
    case "bank":
      return <Landmark {...props} />;
    case "mail":
      return <Mail {...props} />;
    case "store":
      return <ShoppingBag {...props} />;
    case "clock":
      return <Clock {...props} />;
    case "settings":
    default:
      return <Settings {...props} />;
  }
}

export function HilmArtifactGlyph({
  app,
  sizeClass = "w-8 h-8",
}: {
  app: InstalledAppItem;
  sizeClass?: string;
}) {
  const rawUid = useId();
  const uid = rawUid.replace(/[^a-zA-Z0-9_-]/g, "");
  const archetype = classifyArchetype(app);
  const baseSrc = ARCHETYPE_BASE_PATH[archetype];

  return (
    <span
      className={`relative inline-flex items-center justify-center shrink-0 select-none ${sizeClass}`}
    >
      <img
        src={baseSrc}
        alt=""
        aria-hidden="true"
        referrerPolicy="no-referrer"
        className="w-full h-full object-contain drop-shadow-[0_5px_12px_rgba(2,6,18,0.75)]"
      />
      {(archetype === "acoustic_waves" || archetype === "communication_pulses") && (
        <span
          aria-hidden="true"
          className="absolute inset-[18%] rounded-full pointer-events-none"
          style={{
            background:
              "radial-gradient(circle, rgba(5,10,18,0.72) 0%, rgba(5,10,18,0.35) 58%, transparent 100%)",
          }}
        />
      )}
      <span
        className={`absolute inset-0 flex items-center justify-center pointer-events-none ${
          archetype === "optical_lens" ? "translate-x-[6%]" : ""
        }`}
      >
        {renderAuthenticColorEmblem(app, uid)}
      </span>
    </span>
  );
}
