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

function getGitHubRepoConfig(): {
  token: string;
  owner: string;
  repo: string;
  assetPattern: string;
} | null {
  const token = (process.env.GITHUB_TOKEN || "").trim();
  let owner = (process.env.GITHUB_REPO_OWNER || "").trim();
  let repo = (process.env.GITHUB_REPO_NAME || "").trim();
  const combined = (process.env.GITHUB_REPO || "").trim();
  if ((!owner || !repo) && combined.includes("/")) {
    const [o, r] = combined.split("/");
    owner = owner || o.trim();
    repo = repo || r.trim();
  }
  const assetPattern = (process.env.GITHUB_APK_ASSET_PATTERN || "").trim().toLowerCase();
  if (!token || !owner || !repo) return null;
  return { token, owner, repo, assetPattern };
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

    const cfg = getGitHubRepoConfig();
    if (!cfg) {
      res.status(503).json({
        errorCode: "UPDATER_NOT_CONFIGURED",
        error:
          "Repositório privado do GitHub não configurado no servidor. Defina GITHUB_TOKEN, GITHUB_REPO_OWNER e GITHUB_REPO_NAME nas variáveis de ambiente do servidor.",
        installedVersion,
      });
      return;
    }

    try {
      const ghUrl = `https://api.github.com/repos/${encodeURIComponent(cfg.owner)}/${encodeURIComponent(cfg.repo)}/releases?per_page=10`;
      const ghRes = await fetch(ghUrl, {
        headers: {
          Authorization: `Bearer ${cfg.token}`,
          Accept: "application/vnd.github+json",
          "X-GitHub-Api-Version": "2022-11-28",
          "User-Agent": "NossoUniversoLauncher-Updater",
        },
        signal: AbortSignal.timeout(12000),
      });

      if (ghRes.status === 401 || ghRes.status === 403) {
        res.status(502).json({
          errorCode: "GITHUB_AUTH_FAILED",
          error:
            "Credencial do GitHub recusada ou sem permissão de leitura no repositório privado.",
          installedVersion,
        });
        return;
      }

      if (ghRes.status === 404) {
        res.status(404).json({
          errorCode: "GITHUB_REPO_OR_RELEASE_NOT_FOUND",
          error:
            "Repositório privado ou releases não encontradas no GitHub.",
          installedVersion,
        });
        return;
      }

      if (!ghRes.ok) {
        res.status(502).json({
          errorCode: "GITHUB_UPSTREAM_ERROR",
          error: `Erro ao consultar releases no GitHub (HTTP ${ghRes.status}).`,
          installedVersion,
        });
        return;
      }

      const releases = (await ghRes.json()) as GitHubRelease[];
      const publishedReleases = releases.filter((r) => !r.draft);
      if (publishedReleases.length === 0) {
        res.json({
          status: "up_to_date",
          installedVersion,
          latestVersion: installedVersion,
          releaseName: `v${installedVersion}`,
          publishedAt: null,
          releaseNotes: null,
          assetId: null,
          assetName: null,
          assetSizeBytes: null,
          downloadPath: null,
        });
        return;
      }

      // Seleciona a release publicada de maior versão semântica
      const latestRelease = [...publishedReleases].sort((a, b) =>
        compareSemver(b.tag_name, a.tag_name)
      )[0];
      const latestVersion = normalizeVersion(latestRelease.tag_name);
      const isNewer = compareSemver(latestVersion, installedVersion) > 0;
      const apkAsset = selectApkAsset(latestRelease.assets, cfg.assetPattern);

      if (!isNewer) {
        res.json({
          status: "up_to_date",
          installedVersion,
          latestVersion,
          releaseName: latestRelease.name || latestRelease.tag_name,
          publishedAt: latestRelease.published_at || null,
          releaseNotes: latestRelease.body || null,
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
          releaseName: latestRelease.name || latestRelease.tag_name,
          publishedAt: latestRelease.published_at || null,
          releaseNotes: latestRelease.body || null,
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
        releaseName: latestRelease.name || latestRelease.tag_name,
        publishedAt: latestRelease.published_at || null,
        releaseNotes: latestRelease.body || null,
        assetId: apkAsset.id,
        assetName: apkAsset.name,
        assetSizeBytes: apkAsset.size,
        downloadPath: `/api/launcher/updates/download?assetId=${apkAsset.id}`,
      });
    } catch (err) {
      const isTimeout = err instanceof Error && err.name === "TimeoutError";
      res.status(504).json({
        errorCode: isTimeout ? "GITHUB_TIMEOUT" : "GITHUB_CONNECTION_ERROR",
        error: isTimeout
          ? "Tempo limite excedido ao consultar o GitHub."
          : "Falha de conexão ao consultar atualizações no GitHub.",
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

    const cfg = getGitHubRepoConfig();
    if (!cfg) {
      res.status(503).json({
        error: "Repositório privado do GitHub não configurado no servidor.",
      });
      return;
    }

    try {
      const metaUrl = `https://api.github.com/repos/${encodeURIComponent(cfg.owner)}/${encodeURIComponent(cfg.repo)}/releases/assets/${assetIdRaw}`;
      const metaRes = await fetch(metaUrl, {
        headers: {
          Authorization: `Bearer ${cfg.token}`,
          Accept: "application/vnd.github+json",
          "X-GitHub-Api-Version": "2022-11-28",
          "User-Agent": "NossoUniversoLauncher-Updater",
        },
        signal: AbortSignal.timeout(12000),
      });

      if (!metaRes.ok) {
        res.status(metaRes.status === 404 ? 404 : 502).json({
          error: `Pacote APK não encontrado no repositório privado (HTTP ${metaRes.status}).`,
        });
        return;
      }

      const meta = (await metaRes.json()) as GitHubReleaseAsset;
      if (!meta.name || !meta.name.toLowerCase().endsWith(".apk")) {
        res.status(400).json({
          error: "O arquivo solicitado não é um pacote instalável (.apk).",
        });
        return;
      }

      const downloadRes = await fetch(metaUrl, {
        headers: {
          Authorization: `Bearer ${cfg.token}`,
          Accept: "application/octet-stream",
          "X-GitHub-Api-Version": "2022-11-28",
          "User-Agent": "NossoUniversoLauncher-Updater",
        },
        redirect: "follow",
        signal: AbortSignal.timeout(120000),
      });

      if (!downloadRes.ok || !downloadRes.body) {
        res.status(502).json({
          error: `Falha ao baixar o APK do GitHub (HTTP ${downloadRes.status}).`,
        });
        return;
      }

      const safeFilename = path.basename(meta.name).replace(/[^a-zA-Z0-9._-]/g, "_");
      res.setHeader("Content-Type", "application/vnd.android.package-archive");
      res.setHeader(
        "Content-Disposition",
        `attachment; filename="${safeFilename}"`
      );
      const contentLen = downloadRes.headers.get("content-length") || (meta.size ? String(meta.size) : "");
      if (contentLen) {
        res.setHeader("Content-Length", contentLen);
      }

      const nodeStream = Readable.fromWeb(
        downloadRes.body as unknown as import("stream/web").ReadableStream
      );
      await pipeline(nodeStream, res);
    } catch (err) {
      if (!res.headersSent) {
        res.status(502).json({
          error: "Falha de rede durante o download do pacote APK.",
        });
      }
    }
  });

  app.get("/api/android-project/download-zip", async (req, res) => {
    try {
      const zip = new JSZip();
      const rootFolder = zip.folder("NossoUniversoLauncher");
      if (!rootFolder) {
        res.status(500).json({ error: "Não foi possível inicializar o pacote ZIP." });
        return;
      }

      const forwardedProto = (req.get("x-forwarded-proto") || req.protocol || "https")
        .split(",")[0]
        .trim();
      const reqHost = req.get("host");
      const rawAppUrl = (process.env.APP_URL || "").trim();
      const publicBaseUrl =
        rawAppUrl && !rawAppUrl.includes("MY_APP_URL")
          ? rawAppUrl.replace(/\/+$/, "")
          : reqHost
            ? `${forwardedProto}://${reqHost}`
            : "";

      const files = collectFiles(ANDROID_PROJECT_DIR, ANDROID_PROJECT_DIR);
      for (const file of files) {
        const fullPath = path.join(ANDROID_PROJECT_DIR, file.path);
        let data: Buffer | string = fs.readFileSync(fullPath);
        if (file.path === "gradle.properties" && publicBaseUrl) {
          const text = data.toString("utf-8").replace(
            /^NOSSO_UNIVERSO_UPDATE_URL=.*$/m,
            `NOSSO_UNIVERSO_UPDATE_URL=${publicBaseUrl}`
          );
          data = text.includes("NOSSO_UNIVERSO_UPDATE_URL=")
            ? text
            : `${text.trimEnd()}\nNOSSO_UNIVERSO_UPDATE_URL=${publicBaseUrl}\n`;
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
