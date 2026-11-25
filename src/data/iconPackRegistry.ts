import JSZip from "jszip";
import type {
  IconPackDescriptor,
  IconPackIconEntry,
  IconPackId,
  InstalledAppItem,
} from "../types/launcher";
import titaniumLiquidManifest from "../../public/assets/icon-packs/titanium-liquid/manifest.json";
import hilmArtifactsManifest from "../../public/assets/icon-packs/hilm-artifacts/manifest.json";
import tronNeonManifest from "../../public/assets/icon-packs/tron-neon/manifest.json";
import cyberpunkHoloManifest from "../../public/assets/icon-packs/cyberpunk-holo/manifest.json";
import obsidianCoreManifest from "../../public/assets/icon-packs/obsidian-core/manifest.json";

interface RawManifest {
  id?: string;
  name?: string;
  title?: string;
  subtitle?: string;
  author?: string;
  version?: string;
  previewIcons?: string[];
  previewFiles?: string[];
  icons?: IconPackIconEntry[];
}

function buildIllustratedDescriptor(
  manifest: RawManifest,
  basePath: string
): IconPackDescriptor {
  return {
    id: manifest.id || "titanium-liquid",
    name: manifest.name || manifest.title || "Coleção Ilustrada",
    description: manifest.subtitle || "Coleção ilustrada em PNG transparente.",
    author: manifest.author || "HILM Studio",
    version: manifest.version || "1.0.0",
    kind: "illustrated",
    basePath,
    previewIcons: manifest.previewIcons || manifest.previewFiles,
    icons: manifest.icons || [],
  };
}

export const ICON_PACK_REGISTRY: IconPackDescriptor[] = [
  buildIllustratedDescriptor(
    hilmArtifactsManifest as RawManifest,
    "/assets/icon-packs/hilm-artifacts"
  ),
  buildIllustratedDescriptor(
    obsidianCoreManifest as RawManifest,
    "/assets/icon-packs/obsidian-core"
  ),
  buildIllustratedDescriptor(
    cyberpunkHoloManifest as RawManifest,
    "/assets/icon-packs/cyberpunk-holo"
  ),
  buildIllustratedDescriptor(
    tronNeonManifest as RawManifest,
    "/assets/icon-packs/tron-neon"
  ),
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

const runtimePacks = new Map<
  string,
  { descriptor: IconPackDescriptor; dataUrls: Map<string, string> }
>();

export function getAllIconPacks(): IconPackDescriptor[] {
  const imported = Array.from(runtimePacks.values()).map((r) => r.descriptor);
  return [...imported, ...ICON_PACK_REGISTRY];
}

export function isRuntimeImportedPack(packId: IconPackId): boolean {
  return runtimePacks.has(packId);
}

export function removeRuntimeImportedPack(packId: IconPackId): void {
  runtimePacks.delete(packId);
}

export function getIconPackDescriptor(packId: IconPackId): IconPackDescriptor {
  const runtime = runtimePacks.get(packId);
  if (runtime) return runtime.descriptor;
  return (
    ICON_PACK_REGISTRY.find((pack) => pack.id === packId) ||
    ICON_PACK_REGISTRY[0]
  );
}

export function isIllustratedIconPack(packId: IconPackId): boolean {
  return getIconPackDescriptor(packId).kind === "illustrated";
}

export function isArtifactIconPack(packId: IconPackId): boolean {
  return packId === "hilm-artifacts";
}

export function isLunaHolographicIconPack(packId: IconPackId): boolean {
  return packId === "luna-holographic";
}

export function isCardlessIconPack(packId: IconPackId): boolean {
  return isArtifactIconPack(packId) || isLunaHolographicIconPack(packId);
}

export function usesExpandedGlyphScale(packId: IconPackId): boolean {
  return isIllustratedIconPack(packId) || isCardlessIconPack(packId);
}

function resolvePackEntryUrl(
  pack: IconPackDescriptor,
  file: string
): string | null {
  const cleanFile = file.split("/").pop() || file;
  const runtime = runtimePacks.get(pack.id);
  if (runtime) {
    return (
      runtime.dataUrls.get(file) ||
      runtime.dataUrls.get(cleanFile) ||
      null
    );
  }
  if (!pack.basePath) return null;
  return `${pack.basePath}/${cleanFile}`;
}

export function resolveIllustratedIconUrl(
  packId: IconPackId,
  app: InstalledAppItem
): string | null {
  const pack = getIconPackDescriptor(packId);
  if (pack.kind !== "illustrated" || !pack.icons?.length) {
    return null;
  }

  const normalizedPkg = app.packageName.toLowerCase();
  const byPackage = pack.icons.find((entry) =>
    entry.packages?.some((pkg) => pkg.toLowerCase() === normalizedPkg)
  );
  if (byPackage) {
    return resolvePackEntryUrl(pack, byPackage.file);
  }

  const byIconKey = pack.icons.find((entry) =>
    entry.iconKeys?.includes(app.iconKey)
  );
  if (byIconKey) {
    return resolvePackEntryUrl(pack, byIconKey.file);
  }

  const byKeyword = pack.icons.find((entry) =>
    entry.keywords?.some((kw) => normalizedPkg.includes(kw.toLowerCase()))
  );
  if (byKeyword) {
    return resolvePackEntryUrl(pack, byKeyword.file);
  }

  return null;
}

export function getPackPreviewUrls(
  pack: IconPackDescriptor
): { key: string; label: string; url: string }[] {
  if (pack.kind !== "illustrated" || !pack.icons?.length) {
    return [];
  }
  const preferredKeys = pack.previewIcons?.length
    ? pack.previewIcons.map((p) => p.replace(/\.(png|webp)$/i, ""))
    : pack.icons.map((i) => i.key);

  const resolved: { key: string; label: string; url: string }[] = [];
  for (const key of preferredKeys) {
    const entry = pack.icons.find((item) => item.key === key);
    if (entry) {
      const url = resolvePackEntryUrl(pack, entry.file);
      if (url) {
        resolved.push({
          key: entry.key,
          label: entry.label,
          url,
        });
      }
    }
  }
  return resolved;
}

export function getPackAllIconEntries(
  pack: IconPackDescriptor
): { key: string; label: string; url: string }[] {
  if (pack.kind !== "illustrated" || !pack.icons?.length) return [];
  const items: { key: string; label: string; url: string }[] = [];
  for (const entry of pack.icons) {
    const url = resolvePackEntryUrl(pack, entry.file);
    if (url) {
      items.push({ key: entry.key, label: entry.label, url });
    }
  }
  return items;
}

const DEFAULT_KEY_TO_ICON_KEY: Record<string, string> = {
  phone: "phone",
  telefone: "phone",
  messages: "message",
  mensagens: "message",
  camera: "camera",
  browser: "globe",
  navegador: "globe",
  chrome: "globe",
  whatsapp: "chat",
  instagram: "instagram",
  youtube: "youtube",
  spotify: "music",
  music: "music",
  musica: "music",
  discord: "discord",
  nubank: "bank",
  bank: "bank",
  gallery: "image",
  galeria: "image",
  maps: "map",
  mapas: "map",
  calendar: "calendar",
  calendario: "calendar",
  notes: "notes",
  notas: "notes",
  files: "folder",
  arquivos: "folder",
  github: "code",
  androidide: "terminal",
  settings: "settings",
  configuracoes: "settings",
  contacts: "contacts",
  contatos: "contacts",
  gmail: "mail",
  playstore: "store",
  calculator: "calculator",
  calculadora: "calculator",
  clock: "clock",
  relogio: "clock",
};

export async function importIconPackFromZipFile(
  file: File
): Promise<IconPackDescriptor> {
  const zip = await JSZip.loadAsync(file);
  const dataUrls = new Map<string, string>();
  let manifestJson: RawManifest | null = null;

  const entries = Object.values(zip.files).filter(
    (z) => !z.dir && !z.name.startsWith("__MACOSX/")
  );

  for (const zEntry of entries) {
    const fileName = zEntry.name.split("/").pop() || "";
    const lower = fileName.toLowerCase();

    if (lower === "manifest.json" && !manifestJson) {
      const rawText = await zEntry.async("text");
      try {
        manifestJson = JSON.parse(rawText) as RawManifest;
      } catch {
        manifestJson = null;
      }
    } else if (lower.endsWith(".png") || lower.endsWith(".webp")) {
      const base64 = await zEntry.async("base64");
      const mime = lower.endsWith(".webp") ? "image/webp" : "image/png";
      dataUrls.set(fileName, `data:${mime};base64,${base64}`);
    }
  }

  if (dataUrls.size === 0) {
    throw new Error("Nenhuma imagem PNG/WEBP encontrada dentro do .zip.");
  }

  const baseName = file.name.replace(/\.zip$/i, "") || "Pacote Importado";
  const rawId =
    manifestJson?.id ||
    baseName
      .toLowerCase()
      .replace(/[^a-z0-9_-]+/g, "_")
      .replace(/^_+|_+$/g, "");
  const packId = `zip:${rawId || Date.now()}`;

  const icons: IconPackIconEntry[] = manifestJson?.icons?.length
    ? manifestJson.icons.map((item) => {
        const cleanFile = item.file.split("/").pop() || item.file;
        return {
          ...item,
          file: cleanFile,
          iconKeys:
            item.iconKeys ||
            (DEFAULT_KEY_TO_ICON_KEY[item.key.toLowerCase()]
              ? [DEFAULT_KEY_TO_ICON_KEY[item.key.toLowerCase()]]
              : undefined),
        };
      })
    : Array.from(dataUrls.keys())
        .sort()
        .map((fileName) => {
          const key = fileName.replace(/\.(png|webp)$/i, "").toLowerCase();
          return {
            key,
            label: key.charAt(0).toUpperCase() + key.slice(1),
            file: fileName,
            iconKeys: DEFAULT_KEY_TO_ICON_KEY[key]
              ? [DEFAULT_KEY_TO_ICON_KEY[key]]
              : undefined,
            keywords: [key],
          };
        });

  const descriptor: IconPackDescriptor = {
    id: packId,
    name: manifestJson?.name || manifestJson?.title || baseName,
    description:
      manifestJson?.subtitle ||
      `Pacote importado via .zip (${icons.length} ícones PNG).`,
    author: manifestJson?.author || "Importado (.zip)",
    version: manifestJson?.version || "1.0.0",
    kind: "illustrated",
    previewIcons: manifestJson?.previewIcons || manifestJson?.previewFiles,
    icons,
  };

  runtimePacks.set(packId, { descriptor, dataUrls });
  return descriptor;
}

export async function exportIconPackAsZip(
  pack: IconPackDescriptor
): Promise<void> {
  if (pack.kind !== "illustrated" || !pack.icons?.length) return;
  const zip = new JSZip();
  const iconsFolder = zip.folder("icons");

  const manifestPayload = {
    id: pack.id.replace(/^zip:/, ""),
    title: pack.name,
    subtitle: pack.description,
    author: pack.author,
    version: pack.version,
    previewFiles: (pack.previewIcons || pack.icons.slice(0, 6).map((i) => i.key)).map(
      (k) => (k.endsWith(".png") ? k : `${k}.png`)
    ),
    icons: pack.icons.map((entry) => ({
      key: entry.key,
      label: entry.label,
      file: entry.file.split("/").pop() || entry.file,
      packages: entry.packages || [],
      keywords: entry.keywords || [entry.key],
    })),
  };

  zip.file("manifest.json", JSON.stringify(manifestPayload, null, 2));

  for (const entry of pack.icons) {
    const url = resolvePackEntryUrl(pack, entry.file);
    if (!url || !iconsFolder) continue;
    const res = await fetch(url);
    if (!res.ok) continue;
    const blob = await res.blob();
    const cleanFile = entry.file.split("/").pop() || entry.file;
    iconsFolder.file(cleanFile, blob);
  }

  const zipBlob = await zip.generateAsync({ type: "blob" });
  const objectUrl = URL.createObjectURL(zipBlob);
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = `${pack.id.replace(/[^a-z0-9_-]+/gi, "-")}-icon-pack.zip`;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1500);
}
