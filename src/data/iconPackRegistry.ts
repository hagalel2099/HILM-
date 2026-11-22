import type {
  IconPackDescriptor,
  IconPackIconEntry,
  IconPackId,
  InstalledAppItem,
} from "../types/launcher";
import titaniumLiquidManifest from "../../public/assets/icon-packs/titanium-liquid/manifest.json";

interface RawManifest {
  id: string;
  name: string;
  subtitle: string;
  author: string;
  version: string;
  previewIcons?: string[];
  icons: IconPackIconEntry[];
}

function buildIllustratedDescriptor(
  manifest: RawManifest,
  basePath: string
): IconPackDescriptor {
  return {
    id: manifest.id,
    name: manifest.name,
    description: manifest.subtitle,
    author: manifest.author,
    version: manifest.version,
    kind: "illustrated",
    basePath,
    previewIcons: manifest.previewIcons,
    icons: manifest.icons,
  };
}

export const ICON_PACK_REGISTRY: IconPackDescriptor[] = [
  buildIllustratedDescriptor(
    titaniumLiquidManifest as RawManifest,
    "/assets/icon-packs/titanium-liquid"
  ),
  {
    id: "nosso-universo",
    name: "Nosso Universo Padrão",
    description: "Traço técnico ciano & âmbar alinhado às molduras HUD.",
    author: "HILM",
    version: "1.0.0",
    kind: "hud-style",
  },
  {
    id: "monochrome-hud",
    name: "HUD Monocromático Prata",
    description: "Ícones em escala metálica fria com alto contraste.",
    author: "HILM",
    version: "1.0.0",
    kind: "hud-style",
  },
  {
    id: "orbital-glow",
    name: "Núcleo Orbital HILM",
    description: "Acentos violeta e dourado inspirados nas órbitas do núcleo.",
    author: "HILM",
    version: "1.0.0",
    kind: "hud-style",
  },
  {
    id: "system-default",
    name: "Ícones Originais Android",
    description: "Restaura as cores e aparência nativas de cada aplicativo.",
    author: "Sistema Android",
    version: "1.0.0",
    kind: "system-default",
  },
];

export function getIconPackDescriptor(packId: IconPackId): IconPackDescriptor {
  return (
    ICON_PACK_REGISTRY.find((pack) => pack.id === packId) ||
    ICON_PACK_REGISTRY[1]
  );
}

export function isIllustratedIconPack(packId: IconPackId): boolean {
  return getIconPackDescriptor(packId).kind === "illustrated";
}

export function resolveIllustratedIconUrl(
  packId: IconPackId,
  app: InstalledAppItem
): string | null {
  const pack = getIconPackDescriptor(packId);
  if (pack.kind !== "illustrated" || !pack.basePath || !pack.icons?.length) {
    return null;
  }

  const normalizedPkg = app.packageName.toLowerCase();
  const byPackage = pack.icons.find((entry) =>
    entry.packages?.some((pkg) => pkg.toLowerCase() === normalizedPkg)
  );
  if (byPackage) {
    return `${pack.basePath}/${byPackage.file}`;
  }

  const byIconKey = pack.icons.find((entry) =>
    entry.iconKeys?.includes(app.iconKey)
  );
  if (byIconKey) {
    return `${pack.basePath}/${byIconKey.file}`;
  }

  const byKeyword = pack.icons.find((entry) =>
    entry.keywords?.some((kw) => normalizedPkg.includes(kw.toLowerCase()))
  );
  if (byKeyword) {
    return `${pack.basePath}/${byKeyword.file}`;
  }

  return null;
}

export function getPackPreviewUrls(
  pack: IconPackDescriptor
): { key: string; label: string; url: string }[] {
  if (pack.kind !== "illustrated" || !pack.basePath || !pack.icons?.length) {
    return [];
  }
  const preferredKeys = pack.previewIcons?.length
    ? pack.previewIcons
    : pack.icons.map((i) => i.key);

  const resolved: { key: string; label: string; url: string }[] = [];
  for (const key of preferredKeys) {
    const entry = pack.icons.find((item) => item.key === key);
    if (entry) {
      resolved.push({
        key: entry.key,
        label: entry.label,
        url: `${pack.basePath}/${entry.file}`,
      });
    }
  }
  return resolved;
}
