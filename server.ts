import "dotenv/config";
import express from "express";
import fs from "fs";
import path from "path";
import { Readable } from "stream";
import { pipeline } from "stream/promises";
import { fileURLToPath } from "url";
import JSZip from "jszip";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ANDROID_PROJECT_DIR = path.resolve(__dirname, "android-project");

interface GitHubReleaseAsset {
  id: number;
  name: string;
  size: number;
  content_type?: string;
}

interface GitHubRelease {
  tag_name: string;
  name?: string;
  draft?: boolean;
  prerelease?: boolean;
  published_at?: string;
  body?: string;
  assets?: GitHubReleaseAsset[];
}

function readGradleInstalledVersion(): { versionName: string; versionCode: number } {
  const gradlePath = path.join(ANDROID_PROJECT_DIR, "app", "build.gradle.kts");
  try {
    const raw = fs.readFileSync(gradlePath, "utf-8");
    const nameMatch = raw.match(/versionName\s*=\s*"([^"]+)"/);
    const codeMatch = raw.match(/versionCode\s*=\s*(\d+)/);
    return {
      versionName: nameMatch?.[1] || "1.0.0",
      versionCode: codeMatch ? Number.parseInt(codeMatch[1], 10) : 1,
    };
  } catch {
    return { versionName: "1.0.0", versionCode: 1 };
  }
}

function normalizeVersion(raw: string): string {
  return raw.trim().replace(/^v/i, "").split("+")[0];
}

function compareSemver(aRaw: string, bRaw: string): number {
  const parse = (v: string) => {
    const [core, pre] = normalizeVersion(v).split("-");
    const nums = core.split(".").map((part) => {
      const n = Number.parseInt(part.replace(/[^\d]/g, ""), 10);
      return Number.isFinite(n) ? n : 0;
    });
    while (nums.length < 3) nums.push(0);
    return { nums, pre: pre || "" };
  };
  const a = parse(aRaw);
  const b = parse(bRaw);
  const len = Math.max(a.nums.length, b.nums.length);
  for (let i = 0; i < len; i++) {
    const av = a.nums[i] ?? 0;
    const bv = b.nums[i] ?? 0;
    if (av !== bv) return av > bv ? 1 : -1;
  }
  if (!a.pre && b.pre) return 1;
  if (a.pre && !b.pre) return -1;
  return a.pre.localeCompare(b.pre);
}

const WORKER_RELEASES_LATEST_PATH = "/repos/hagalel2099/HILM-/releases/latest";

function readGradleUpdateUrl(): string {
  const propsPath = path.join(ANDROID_PROJECT_DIR, "gradle.properties");
  try {
    const raw = fs.readFileSync(propsPath, "utf-8");
    const match = raw.match(/^\s*NOSSO_UNIVERSO_UPDATE_URL\s*=\s*(.+)$/m);
    return match?.[1]?.trim() || "";
  } catch {
    return "";
  }
}

function resolveCloudflareWorkerConfig(): {
  workerOrigin: string | null;
  reason?: "not_configured" | "invalid";
  assetPattern: string;
} {
  const raw = (
    process.env.CLOUDFLARE_WORKER_URL ||
    process.env.URL_DO_TRABALHADOR_CLOUDFLARE ||
    process.env.CLOUDFLARE_UPDATER_URL ||
    process.env.NOSSO_UNIVERSO_UPDATE_URL ||
    readGradleUpdateUrl() ||
    ""
  ).trim();
  const assetPattern = (
    process.env.NOSSO_UNIVERSO_APK_ASSET_PATTERN ||
    process.env.GITHUB_APK_ASSET_PATTERN ||
    ""
  )
    .trim()
    .toLowerCase();

  if (!raw) {
    return { workerOrigin: null, reason: "not_configured", assetPattern };
  }

  let cleaned = raw.replace(/\/+$/, "");
  if (/SEU-WORKER/i.test(cleaned)) {
    return { workerOrigin: null, reason: "invalid", assetPattern };
  }

  if (
    cleaned
      .toLowerCase()
      .endsWith(WORKER_RELEASES_LATEST_PATH.toLowerCase())
  ) {
    cleaned = cleaned
      .slice(0, cleaned.length - WORKER_RELEASES_LATEST_PATH.length)
      .replace(/\/+$/, "");
  }

  try {
    const parsed = new URL(cleaned);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
      return { workerOrigin: null, reason: "invalid", assetPattern };
    }
    return { workerOrigin: cleaned, assetPattern };
  } catch {
    return { workerOrigin: null, reason: "invalid", assetPattern };
  }
}

function selectApkAsset(
  assets: GitHubReleaseAsset[] | undefined,
  pattern: string
): GitHubReleaseAsset | null {
  const apkAssets = (assets || []).filter((a) =>
    a.name.toLowerCase().endsWith(".apk")
  );
  if (apkAssets.length === 0) return null;
  if (pattern) {
    const matched = apkAssets.find((a) => a.name.toLowerCase().includes(pattern));
    if (matched) return matched;
  }
  const releasePreferred = apkAssets.find((a) => {
    const lower = a.name.toLowerCase();
    return lower.includes("release") || lower.includes("signed");
  });
  return releasePreferred || apkAssets[0];
}

interface ProjectFileEntry {
  path: string;
  size: number;
  isBinary: boolean;
}

function collectFiles(dir: string, baseDir: string): ProjectFileEntry[] {
  if (!fs.existsSync(dir)) return [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const results: ProjectFileEntry[] = [];

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    const relPath = path.relative(baseDir, fullPath).replace(/\\/g, "/");
    if (entry.isDirectory()) {
      results.push(...collectFiles(fullPath, baseDir));
    } else if (entry.isFile()) {
      const stat = fs.statSync(fullPath);
      const ext = path.extname(entry.name).toLowerCase();
      const isBinary = [".png", ".jpg", ".jpeg", ".webp", ".jar"].includes(ext);
      results.push({ path: relPath, size: stat.size, isBinary });
    }
  }
  return results.sort((a, b) => a.path.localeCompare(b.path));
}

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.get("/api/android-project/tree", (_req, res) => {
    try {
      const files = collectFiles(ANDROID_PROJECT_DIR, ANDROID_PROJECT_DIR);
      res.json({
        projectName: "NossoUniversoLauncher",
        gradleVersion: "8.2",
        agpVersion: "8.2.2",
        compileSdk: 33,
        minSdk: 23,
        jdkVersion: 17,
        files,
      });
    } catch (err) {
      res.status(500).json({ error: "Falha ao listar arquivos do projeto Android." });
    }
  });

  app.get("/api/android-project/file", (req, res) => {
    const requestedPath = typeof req.query.path === "string" ? req.query.path : "";
    if (!requestedPath) {
      res.status(400).json({ error: "Parâmetro path obrigatório." });
      return;
    }

    const resolved = path.resolve(ANDROID_PROJECT_DIR, requestedPath);
    if (!resolved.startsWith(ANDROID_PROJECT_DIR + path.sep)) {
      res.status(403).json({ error: "Caminho inválido." });
      return;
    }

    if (!fs.existsSync(resolved) || !fs.statSync(resolved).isFile()) {
      res.status(404).json({ error: "Arquivo não encontrado." });
      return;
    }

    const ext = path.extname(resolved).toLowerCase();
    if ([".png", ".jpg", ".jpeg", ".webp", ".jar"].includes(ext)) {
      res.json({
        path: requestedPath,
        isBinary: true,
        content: null,
        size: fs.statSync(resolved).size,
      });
      return;
    }

    const content = fs.readFileSync(resolved, "utf-8");
    res.json({
      path: requestedPath,
      isBinary: false,
      content,
      size: Buffer.byteLength(content, "utf-8"),
    });
  });

  app.get("/api/launcher/updates/check", async (req, res) => {
    const gradleVer = readGradleInstalledVersion();
    const installedRaw =
      typeof req.query.currentVersion === "string" && req.query.currentVersion.trim()
        ? req.query.currentVersion.trim()
        : gradleVer.versionName;
    const installedVersion = normalizeVersion(installedRaw);

    const cfg = resolveCloudflareWorkerConfig();
    if (!cfg.workerOrigin) {
      res.status(503).json({
        errorCode: "CLOUDFLARE_WORKER_NOT_CONFIGURED",
        error:
          cfg.reason === "invalid"
            ? "CLOUDFLARE_WORKER_URL inválida. Informe a URL real do Worker no Cloudflare (ex.: https://hilm.<subdominio>.workers.dev)."
            : "URL do Worker do Cloudflare não configurada no servidor. Defina CLOUDFLARE_WORKER_URL nas variáveis de ambiente para conectar o preview Web.",
        installedVersion,
      });
      return;
    }

    try {
      const workerEndpoint = `${cfg.workerOrigin}${WORKER_RELEASES_LATEST_PATH}`;
      const workerRes = await fetch(workerEndpoint, {
        headers: {
          Accept: "application/json",
          "User-Agent": "NossoUniversoLauncher-Preview",
        },
        signal: AbortSignal.timeout(12000),
      });

      if (!workerRes.ok) {
        const errBody = await workerRes.text().catch(() => "");
        let workerErr: { error?: string; githubStatus?: number } | null = null;
        try {
          workerErr = errBody ? (JSON.parse(errBody) as { error?: string; githubStatus?: number }) : null;
        } catch {
          workerErr = null;
        }

        if (
          workerRes.status === 404 &&
          workerErr?.error?.toLowerCase().includes("não contém apk")
        ) {
          res.status(404).json({
            errorCode: "NO_APK_ASSET",
            error: "A release mais recente publicada ainda não possui um pacote .apk anexado.",
            installedVersion,
          });
          return;
        }

        res.status(workerRes.status >= 500 ? 502 : workerRes.status).json({
          errorCode: "CLOUDFLARE_WORKER_ERROR",
          error:
            workerErr?.error ||
            `Erro ao consultar o Worker do Cloudflare (HTTP ${workerRes.status}).`,
          installedVersion,
        });
        return;
      }

      const release = (await workerRes.json()) as GitHubRelease;
      if (!release?.tag_name) {
        res.status(502).json({
          errorCode: "CLOUDFLARE_WORKER_INVALID_RESPONSE",
          error: "Resposta do Worker sem identificação de versão (tag_name).",
          installedVersion,
        });
        return;
      }

      const latestVersion = normalizeVersion(release.tag_name);
      const isNewer = compareSemver(latestVersion, installedVersion) > 0;
      const apkAsset = selectApkAsset(release.assets, cfg.assetPattern);

      if (!isNewer) {
        res.json({
          status: "up_to_date",
          installedVersion,
          latestVersion,
          releaseName: release.name || release.tag_name,
          publishedAt: release.published_at || null,
          releaseNotes: release.body || null,
          assetId: apkAsset?.id ?? null,
          assetName: apkAsset?.name ?? null,
          assetSizeBytes: apkAsset?.size ?? null,
          downloadPath: null,
        });
        return;
      }

      if (!apkAsset) {
        res.json({
          status: "no_apk_asset",
          installedVersion,
          latestVersion,
          releaseName: release.name || release.tag_name,
          publishedAt: release.published_at || null,
          releaseNotes: release.body || null,
          assetId: null,
          assetName: null,
          assetSizeBytes: null,
          downloadPath: null,
        });
        return;
      }

      res.json({
        status: "update_available",
        installedVersion,
        latestVersion,
        releaseName: release.name || release.tag_name,
        publishedAt: release.published_at || null,
        releaseNotes: release.body || null,
        assetId: apkAsset.id,
        assetName: apkAsset.name,
        assetSizeBytes: apkAsset.size,
        downloadPath: `/api/launcher/updates/download?assetId=${apkAsset.id}`,
      });
    } catch (err) {
      const isTimeout = err instanceof Error && err.name === "TimeoutError";
      res.status(504).json({
        errorCode: isTimeout ? "CLOUDFLARE_WORKER_TIMEOUT" : "CLOUDFLARE_WORKER_CONNECTION_ERROR",
        error: isTimeout
          ? "Tempo limite excedido ao consultar o Worker do Cloudflare."
          : "Falha de conexão ao consultar atualizações no Worker do Cloudflare.",
        installedVersion,
      });
    }
  });

  app.get("/api/launcher/updates/download", async (req, res) => {
    const assetIdRaw = typeof req.query.assetId === "string" ? req.query.assetId.trim() : "";
    if (!/^\d+$/.test(assetIdRaw)) {
      res.status(400).json({ error: "Identificador de pacote APK inválido." });
      return;
    }

    const cfg = resolveCloudflareWorkerConfig();
    if (!cfg.workerOrigin) {
      res.status(503).json({
        error: "URL do Worker do Cloudflare não configurada no servidor.",
      });
      return;
    }

    try {
      const workerDownloadUrl = `${cfg.workerOrigin}/download/${assetIdRaw}`;
      const downloadRes = await fetch(workerDownloadUrl, {
        headers: {
          "User-Agent": "NossoUniversoLauncher-Preview",
        },
        redirect: "follow",
        signal: AbortSignal.timeout(120000),
      });

      if (!downloadRes.ok || !downloadRes.body) {
        const errBody = await downloadRes.text().catch(() => "");
        let workerErr: string | undefined;
        try {
          workerErr = errBody ? (JSON.parse(errBody) as { error?: string }).error : undefined;
        } catch {
          workerErr = undefined;
        }
        res.status(downloadRes.status >= 400 && downloadRes.status < 500 ? downloadRes.status : 502).json({
          error:
            workerErr ||
            `Falha ao baixar o APK pelo Worker do Cloudflare (HTTP ${downloadRes.status}).`,
        });
        return;
      }

      const disposition = downloadRes.headers.get("content-disposition") || "";
      const filenameMatch = disposition.match(/filename\*?=(?:UTF-8'')?"?([^";\r\n]+)"?/i);
      const rawFilename = filenameMatch?.[1] || `NossoUniverso-${assetIdRaw}.apk`;
      const safeFilename = path.basename(rawFilename).replace(/[^a-zA-Z0-9._-]/g, "_");

      res.setHeader("Content-Type", "application/vnd.android.package-archive");
      res.setHeader(
        "Content-Disposition",
        `attachment; filename="${safeFilename}"`
      );
      const contentLen = downloadRes.headers.get("content-length");
      if (contentLen) {
        res.setHeader("Content-Length", contentLen);
      }

      const nodeStream = Readable.fromWeb(
        downloadRes.body as unknown as import("stream/web").ReadableStream
      );
      await pipeline(nodeStream, res);
    } catch {
      if (!res.headersSent) {
        res.status(502).json({
          error: "Falha de rede durante o download do pacote APK pelo Worker.",
        });
      }
    }
  });

  app.get("/api/android-project/download-zip", async (_req, res) => {
    try {
      const zip = new JSZip();
      const rootFolder = zip.folder("NossoUniversoLauncher");
      if (!rootFolder) {
        res.status(500).json({ error: "Não foi possível inicializar o pacote ZIP." });
        return;
      }

      const workerCfg = resolveCloudflareWorkerConfig();
      const files = collectFiles(ANDROID_PROJECT_DIR, ANDROID_PROJECT_DIR);
      for (const file of files) {
        const fullPath = path.join(ANDROID_PROJECT_DIR, file.path);
        let data: Buffer | string = fs.readFileSync(fullPath);
        if (file.path === "gradle.properties" && workerCfg.workerOrigin) {
          const text = data.toString("utf-8").replace(
            /^NOSSO_UNIVERSO_UPDATE_URL=.*$/m,
            `NOSSO_UNIVERSO_UPDATE_URL=${workerCfg.workerOrigin}`
          );
          data = text.includes("NOSSO_UNIVERSO_UPDATE_URL=")
            ? text
            : `${text.trimEnd()}\nNOSSO_UNIVERSO_UPDATE_URL=${workerCfg.workerOrigin}\n`;
        }
        const isExec = file.path === "gradlew";
        rootFolder.file(file.path, data, isExec ? { unixPermissions: "755" } : undefined);
      }

      const buffer = await zip.generateAsync({
        type: "nodebuffer",
        compression: "DEFLATE",
        compressionOptions: { level: 6 },
      });

      res.setHeader("Content-Type", "application/zip");
      res.setHeader(
        "Content-Disposition",
        'attachment; filename="NossoUniversoLauncher-AndroidIDE.zip"'
      );
      res.setHeader("Content-Length", buffer.length.toString());
      res.send(buffer);
    } catch (err) {
      res.status(500).json({ error: "Erro ao gerar o arquivo ZIP do projeto Android." });
    }
  });

  if (process.env.NODE_ENV !== "production") {
    const { createServer: createViteServer } = await import("vite");
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distDir = path.resolve(__dirname, "dist");
    app.use(express.static(distDir));
    app.get("*", (_req, res) => {
      res.sendFile(path.join(distDir, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Nosso Universo Launcher Studio running on http://localhost:${PORT}`);
  });
}

startServer();
