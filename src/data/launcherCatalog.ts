import type {
  IconPackId,
  InstalledAppItem,
  LauncherPersistedState,
  SlotId,
  TransitionEffectId,
} from "../types/launcher";
import { ICON_PACK_REGISTRY } from "./iconPackRegistry";

export const LEFT_DRAWER_SLOT_IDS: SlotId[] = [
  "left_1",
  "left_2",
  "left_3",
  "left_4",
  "left_5",
];

export const RIGHT_DRAWER_SLOT_IDS: SlotId[] = [
  "right_1",
  "right_2",
  "right_3",
  "right_4",
  "right_5",
];

export const SLOT_IDS: SlotId[] = [
  "plate_top_left",
  "plate_top_right",
  "plate_bottom_left",
  "plate_bottom_right",
  "dock_1",
  "dock_2",
  "dock_4",
  "dock_5",
  ...LEFT_DRAWER_SLOT_IDS,
  ...RIGHT_DRAWER_SLOT_IDS,
];

export const SLOT_LABELS: Record<SlotId, string> = {
  plate_top_left: "Painel Superior Esquerdo",
  plate_top_right: "Painel Superior Direito",
  plate_bottom_left: "Painel Inferior Esquerdo",
  plate_bottom_right: "Painel Inferior Direito",
  dock_1: "Dock Posição 1",
  dock_2: "Dock Posição 2",
  dock_4: "Dock Posição 4",
  dock_5: "Dock Posição 5",
  left_1: "Gaveta Esquerda 1",
  left_2: "Gaveta Esquerda 2",
  left_3: "Gaveta Esquerda 3",
  left_4: "Gaveta Esquerda 4",
  left_5: "Gaveta Esquerda 5",
  right_1: "Gaveta Direita 1",
  right_2: "Gaveta Direita 2",
  right_3: "Gaveta Direita 3",
  right_4: "Gaveta Direita 4",
  right_5: "Gaveta Direita 5",
};

export const INSTALLED_APPS_CATALOG: InstalledAppItem[] = [
  {
    packageName: "com.android.dialer",
    activityName: "com.android.dialer.MainActivity",
    label: "Telefone",
    category: "Comunicação",
    accentHex: "#22D3EE",
    iconKey: "phone",
  },
  {
    packageName: "com.android.messaging",
    activityName: "com.android.messaging.ui.ConversationListActivity",
    label: "Mensagens",
    category: "Comunicação",
    accentHex: "#38BDF8",
    iconKey: "message",
  },
  {
    packageName: "com.whatsapp",
    activityName: "com.whatsapp.HomeActivity",
    label: "WhatsApp",
    category: "Social",
    accentHex: "#10B981",
    iconKey: "chat",
  },
  {
    packageName: "com.instagram.android",
    activityName: "com.instagram.mainactivity.LauncherActivity",
    label: "Instagram",
    category: "Social",
    accentHex: "#F472B6",
    iconKey: "instagram",
  },
  {
    packageName: "com.google.android.youtube",
    activityName: "com.google.android.youtube.HomeActivity",
    label: "YouTube",
    category: "Mídia",
    accentHex: "#EF4444",
    iconKey: "youtube",
  },
  {
    packageName: "com.spotify.music",
    activityName: "com.spotify.music.MainActivity",
    label: "Spotify",
    category: "Música",
    accentHex: "#22C55E",
    iconKey: "music",
  },
  {
    packageName: "com.android.chrome",
    activityName: "com.google.android.apps.chrome.Main",
    label: "Navegador",
    category: "Internet",
    accentHex: "#60A5FA",
    iconKey: "globe",
  },
  {
    packageName: "com.android.camera2",
    activityName: "com.android.camera.CameraActivity",
    label: "Câmera",
    category: "Mídia",
    accentHex: "#F59E0B",
    iconKey: "camera",
  },
  {
    packageName: "com.android.gallery3d",
    activityName: "com.android.gallery3d.app.GalleryActivity",
    label: "Galeria",
    category: "Mídia",
    accentHex: "#A855F7",
    iconKey: "image",
  },
  {
    packageName: "com.google.android.apps.maps",
    activityName: "com.google.android.maps.MapsActivity",
    label: "Mapas",
    category: "Navegação",
    accentHex: "#34D399",
    iconKey: "map",
  },
  {
    packageName: "com.android.calendar",
    activityName: "com.android.calendar.AllInOneActivity",
    label: "Calendário",
    category: "Produtividade",
    accentHex: "#22D3EE",
    iconKey: "calendar",
  },
  {
    packageName: "com.nossouniverso.notes",
    activityName: "com.nossouniverso.notes.MainActivity",
    label: "Notas",
    category: "Produtividade",
    accentHex: "#FBBF24",
    iconKey: "notes",
  },
  {
    packageName: "com.android.documentsui",
    activityName: "com.android.documentsui.files.FilesActivity",
    label: "Arquivos",
    category: "Sistema",
    accentHex: "#94A3B8",
    iconKey: "folder",
  },
  {
    packageName: "com.github.android",
    activityName: "com.github.android.activities.MainActivity",
    label: "GitHub",
    category: "Desenvolvimento",
    accentHex: "#E2E8F0",
    iconKey: "code",
  },
  {
    packageName: "com.itsaky.androidide",
    activityName: "com.itsaky.androidide.activities.MainActivity",
    label: "AndroidIDE",
    category: "Desenvolvimento",
    accentHex: "#38BDF8",
    iconKey: "terminal",
  },
  {
    packageName: "com.discord",
    activityName: "com.discord.main.MainActivity",
    label: "Discord",
    category: "Social",
    accentHex: "#5865F2",
    iconKey: "discord",
  },
  {
    packageName: "com.nu.production",
    activityName: "com.nu.production.MainActivity",
    label: "Nubank",
    category: "Finanças",
    accentHex: "#820AD1",
    iconKey: "bank",
  },
  {
    packageName: "com.android.settings",
    activityName: "com.android.settings.Settings",
    label: "Ajustes",
    category: "Sistema",
    accentHex: "#CBD5E1",
    iconKey: "settings",
  },
];

export const ICON_PACKS: {
  id: IconPackId;
  name: string;
  description: string;
}[] = ICON_PACK_REGISTRY.map((pack) => ({
  id: pack.id,
  name: pack.name,
  description: pack.description,
}));

export const LAUNCHER_THEMES = [
  {
    id: "default" as const,
    name: "Nosso Universo Padrão",
    subtitle: "HUD espacial com núcleo central orbital e placas octogonais.",
    badge: "Original",
  },
  {
    id: "luna-entre-luz-e-sombra" as const,
    name: "Tema Luna — Entre Luz e Sombra",
    subtitle:
      "Projeção holográfica pessoal em ciano (#00E5FF) e azul elétrico (#398BFF) ao redor da personagem.",
    badge: "Exclusivo HILM",
  },
];

export const WALLPAPERS = [
  {
    id: "ref-horizon" as const,
    name: "Horizonte Espacial",
    description: "Linhas perspectivas em azul profundo fiel à Referência 1.",
  },
  {
    id: "luna-shadow-light" as const,
    name: "Luna — Entre Luz e Sombra",
    description: "Imagem original da Luna em projeção holográfica sem recortes.",
  },
  {
    id: "hilm-void" as const,
    name: "Vácuo Estelar HILM",
    description: "Campo escuro com nebulosa violeta e poeira dourada sutil.",
  },
  {
    id: "obsidian-grid" as const,
    name: "Grade Obsidiana",
    description: "Preto técnico com malha geométrica discreta.",
  },
  {
    id: "solar-nebula" as const,
    name: "Nebulosa Solar",
    description: "Gradiente espacial quente em âmbar escuro e índigo.",
  },
];

export const TRANSITION_EFFECTS: {
  id: TransitionEffectId;
  name: string;
  description: string;
}[] = [
  {
    id: "portal",
    name: "Portal holográfico",
    description: "Expansão em camadas concêntricas com profundidade e brilho ciano/laranja.",
  },
  {
    id: "digital-disintegration",
    name: "Desintegração digital",
    description: "Elementos se desfazem e se recompõem em micro-partículas luminosas.",
  },
  {
    id: "light-sweep",
    name: "Varredura de luz",
    description: "Uma faixa luminosa direcional revela ou recolhe a nova superfície.",
  },
  {
    id: "dimensional-slide",
    name: "Deslizamento dimensional",
    description: "A interface entra e recua com inclinação em perspectiva e profundidade.",
  },
  {
    id: "energy-dissolve",
    name: "Dissolução energética",
    description: "A interface se dissolve e reaparece suavemente com onda harmônica sutil.",
  },
  {
    id: "classic",
    name: "Transição clássica",
    description: "Alternativa simples, direta e ultra leve para máxima fluidez.",
  },
];

export const DEFAULT_LAUNCHER_STATE: LauncherPersistedState = {
  slots: {
    plate_top_left: { slotId: "plate_top_left", packageName: "com.whatsapp" },
    plate_top_right: { slotId: "plate_top_right", packageName: "com.google.android.youtube" },
    plate_bottom_left: { slotId: "plate_bottom_left", packageName: "com.instagram.android" },
    plate_bottom_right: { slotId: "plate_bottom_right", packageName: "com.spotify.music" },
    dock_1: { slotId: "dock_1", packageName: "com.android.camera2" },
    dock_2: { slotId: "dock_2", packageName: "com.android.messaging" },
    dock_4: { slotId: "dock_4", packageName: "com.android.dialer" },
    dock_5: { slotId: "dock_5", packageName: "com.android.chrome" },
    left_1: { slotId: "left_1", packageName: "com.google.android.apps.maps" },
    left_2: { slotId: "left_2", packageName: "com.android.calendar" },
    left_3: { slotId: "left_3", packageName: "com.nossouniverso.notes" },
    left_4: { slotId: "left_4", packageName: "com.android.documentsui" },
    left_5: { slotId: "left_5", packageName: null },
    right_1: { slotId: "right_1", packageName: "com.github.android" },
    right_2: { slotId: "right_2", packageName: "com.itsaky.androidide" },
    right_3: { slotId: "right_3", packageName: "com.android.gallery3d" },
    right_4: { slotId: "right_4", packageName: "com.android.settings" },
    right_5: { slotId: "right_5", packageName: null },
  },
  iconPack: "hilm-artifacts",
  wallpaperId: "ref-horizon",
  panelOpacity: 78,
  recentPackages: [
    "com.itsaky.androidide",
    "com.google.android.youtube",
    "com.spotify.music",
  ],
  transition: {
    enabled: true,
    effect: "portal",
    durationMs: 280,
  },
};

export const HILM_NODES = [
  { letter: "H", name: "Hagalel", tone: "#334155", role: "Núcleo Central Fixo" },
  { letter: "I", name: "Íris", tone: "#EA580C", role: "Órbita Laranja Cobre" },
  { letter: "L", name: "Luna", tone: "#8B5CF6", role: "Órbita Violeta Elétrico" },
  { letter: "M", name: "Maya", tone: "#F59E0B", role: "Órbita Âmbar Dourado Mel" },
];

export const ACCEPTANCE_CRITERIA = [
  {
    id: "androidide-sync",
    label: "Estrutura completa compatível com AndroidIDE",
    status: "implemented" as const,
    detail: "Projeto em /android-project pronto para abertura direta e exportação em ZIP.",
  },
  {
    id: "gradle-82",
    label: "Gradle Wrapper 8.2 e AGP 8.1.4 com JDK 17 e SDK 33",
    status: "implemented" as const,
    detail: "Configurados em gradle-wrapper.properties, build.gradle.kts e app/build.gradle.kts.",
  },
  {
    id: "home-category",
    label: "Reconhecimento como launcher HOME nativo no Android",
    status: "implemented" as const,
    detail: "HomeActivity declarada com CATEGORY_HOME, CATEGORY_DEFAULT e RoleManager.",
  },
  {
    id: "no-clock-status-bar",
    label: "Relógio superior grande removido e barra de status preservada",
    status: "implemented" as const,
    detail: "Moldura superior preservada sem o bloco do relógio; status bar nativa intacta.",
  },
  {
    id: "hilm-core",
    label: "Núcleo HILM substituindo a esfera central",
    status: "implemented" as const,
    detail: "Imagem original integrada no centro da composição sem esfera extra ao redor.",
  },
  {
    id: "functional-icons",
    label: "Ícones funcionais com adicionar, remover, reorganizar e persistir",
    status: "implemented" as const,
    detail: "Ativo tanto no protótipo web (localStorage) quanto em Kotlin (SharedPreferences + Drag & Drop).",
  },
  {
    id: "device-compile",
    label: "Compilação APK e teste físico no aparelho Android",
    status: "requires-device" as const,
    detail: "Este ambiente web não possui JDK/Android SDK instalados; compile e teste no AndroidIDE.",
  },
];
