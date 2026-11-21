const OWNER = "hagalel2099";
const REPO = "HILM-";
const API = "https://api.github.com";

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function githubHeaders(env, accept = "application/vnd.github+json") {
  return {
    "Accept": accept,
    "Authorization": `Bearer ${env.GITHUB_TOKEN}`,
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "Nosso-Universo-Launcher-Updater",
  };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method !== "GET" && request.method !== "HEAD") {
      return json({ error: "Method not allowed" }, 405);
    }

    if (!env.GITHUB_TOKEN) {
      return json({ error: "GITHUB_TOKEN não configurado no Cloudflare" }, 500);
    }

    // Endpoint de verificação: devolve os dados da última release do GitHub,
    // mas troca o link do APK por um endereço deste Worker.
    if (url.pathname === "/repos/hagalel2099/HILM-/releases/latest") {
      const response = await fetch(
        `${API}/repos/${OWNER}/${REPO}/releases/latest`,
        { headers: githubHeaders(env) }
      );

      if (!response.ok) {
        const detail = await response.text();
        return json({
          error: "Não foi possível consultar a release no GitHub",
          githubStatus: response.status,
          detail: detail.slice(0, 500),
        }, response.status === 404 ? 404 : 502);
      }

      const release = await response.json();
      const apkAssets = (release.assets || []).filter(
        asset => asset.name && asset.name.toLowerCase().endsWith(".apk")
      );

      if (apkAssets.length === 0) {
        return json({ error: "A release mais recente não contém APK" }, 404);
      }

      release.assets = apkAssets.map(asset => ({
        ...asset,
        browser_download_url: `${url.origin}/download/${asset.id}`,
      }));

      return json(release);
    }

    // Baixa somente um APK que pertença à última release publicada.
    const match = url.pathname.match(/^\/download\/(\d+)$/);
    if (match) {
      const assetId = match[1];

      const releaseResponse = await fetch(
        `${API}/repos/${OWNER}/${REPO}/releases/latest`,
        { headers: githubHeaders(env) }
      );

      if (!releaseResponse.ok) {
        return json({ error: "Não foi possível validar a release" }, 502);
      }

      const release = await releaseResponse.json();
      const asset = (release.assets || []).find(
        item => String(item.id) === assetId &&
                item.name &&
                item.name.toLowerCase().endsWith(".apk")
      );

      if (!asset) {
        return json({ error: "APK não encontrado na última release" }, 404);
      }

      const downloadResponse = await fetch(
        `${API}/repos/${OWNER}/${REPO}/releases/assets/${assetId}`,
        {
          headers: githubHeaders(env, "application/octet-stream"),
          redirect: "follow",
        }
      );

      if (!downloadResponse.ok) {
        return json({
          error: "Falha ao baixar o APK do GitHub",
          githubStatus: downloadResponse.status,
        }, 502);
      }

      const headers = new Headers(downloadResponse.headers);
      headers.set("content-type", "application/vnd.android.package-archive");
      headers.set("content-disposition", `attachment; filename="${asset.name}"`);
      headers.set("cache-control", "no-store");

      return new Response(downloadResponse.body, {
        status: 200,
        headers,
      });
    }

    if (url.pathname === "/") {
      return new Response("Nosso Universo — serviço de atualização online.", {
        headers: { "content-type": "text/plain; charset=utf-8" },
      });
    }

    return json({ error: "Not found" }, 404);
  },
};
