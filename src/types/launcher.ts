export type SlotId =
  | "plate_top_left"
  | "plate_top_right"
  | "plate_bottom_left"
  | "plate_bottom_right"
  | "dock_1"
  | "dock_2"
  | "dock_4"
  | "dock_5";

export type IconPackId =
  | "nosso-universo"
  | "monochrome-hud"
  | "orbital-glow"
  | "system-default";

export type WallpaperId =
  | "ref-horizon"
  | "hilm-void"
  | "obsidian-grid"
  | "solar-nebula"
  | "custom";

export interface InstalledAppItem {
  packageName: string;
  activityName: string;
  label: string;
  category: string;
  accentHex: string;
  iconKey: string;
}

export interface ShortcutSlotConfig {
  slotId: SlotId;
  packageName: string | null;
  customLabel?: string;
  customIconDataUrl?: string;
}

export interface LauncherPersistedState {
  slots: Record<SlotId, ShortcutSlotConfig>;
  iconPack: IconPackId;
  wallpaperId: WallpaperId;
  customWallpaperUrl?: string;
  panelOpacity: number;
  recentPackages: string[];
}

export interface AndroidProjectFile {
  path: string;
  size: number;
  isBinary: boolean;
}

export interface AndroidProjectTreeResponse {
  projectName: string;
  gradleVersion: string;
  agpVersion: string;
  compileSdk: number;
  minSdk: number;
  jdkVersion: number;
  files: AndroidProjectFile[];
}

export interface LauncherUpdateCheckResponse {
  status: "up_to_date" | "update_available" | "no_apk_asset";
  installedVersion: string;
  latestVersion: string;
  releaseName: string;
  publishedAt: string | null;
  releaseNotes: string | null;
  assetId: number | null;
  assetName: string | null;
  assetSizeBytes: number | null;
  downloadPath: string | null;
}
