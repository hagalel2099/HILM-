import express from "express";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import JSZip from "jszip";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ANDROID_PROJECT_DIR = path.resolve(__dirname, "android-project");

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

  app.get("/api/android-project/download-zip", async (_req, res) => {
    try {
      const zip = new JSZip();
      const rootFolder = zip.folder("NossoUniversoLauncher");
      if (!rootFolder) {
        res.status(500).json({ error: "Não foi possível inicializar o pacote ZIP." });
        return;
      }

      const files = collectFiles(ANDROID_PROJECT_DIR, ANDROID_PROJECT_DIR);
      for (const file of files) {
        const fullPath = path.join(ANDROID_PROJECT_DIR, file.path);
        const data = fs.readFileSync(fullPath);
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
