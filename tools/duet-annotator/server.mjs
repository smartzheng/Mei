import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = path.join(ROOT, "public");
const UPSTREAM = "https://music.163.com";
const PORT = parsePort(process.env.PORT, 4173);
const HOST = process.env.HOST?.trim() || "127.0.0.1";
const COOKIE = normalizeCookie(process.env.NCM_COOKIE);
const CACHE_TTL_MS = 30 * 60 * 1_000;
const CACHE_LIMIT = 200;

const cache = new Map();

const MIME_TYPES = new Map([
  [".html", "text/html; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"],
  [".mjs", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".svg", "image/svg+xml"],
  [".png", "image/png"],
  [".ico", "image/x-icon"],
]);

function normalizeCookie(value) {
  const cookie = String(value ?? "").trim();
  if (!cookie) return "";
  return cookie.includes("=") ? cookie : `MUSIC_U=${cookie}`;
}

class HttpError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function parsePort(value, fallback) {
  const port = Number(value ?? fallback);
  return Number.isInteger(port) && port > 0 && port < 65_536 ? port : fallback;
}

function sendJson(response, status, payload) {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  });
  response.end(body);
}

function cacheGet(key) {
  const entry = cache.get(key);
  if (!entry) return null;
  if (entry.expiresAt <= Date.now()) {
    cache.delete(key);
    return null;
  }
  return entry.value;
}

function cacheSet(key, value) {
  if (cache.size >= CACHE_LIMIT) {
    cache.delete(cache.keys().next().value);
  }
  cache.set(key, { value, expiresAt: Date.now() + CACHE_TTL_MS });
}

async function fetchUpstream(pathname, params) {
  const url = new URL(pathname, UPSTREAM);
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, String(value));
  }

  const headers = {
    Accept: "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9",
    Referer: "https://music.163.com/",
    "User-Agent":
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
  };
  if (COOKIE) headers.Cookie = COOKIE;

  let response;
  try {
    response = await fetch(url, {
      headers,
      signal: AbortSignal.timeout(12_000),
    });
  } catch (error) {
    const timedOut = error?.name === "TimeoutError" || error?.name === "AbortError";
    throw new HttpError(
      502,
      timedOut ? "upstream_timeout" : "upstream_unreachable",
      timedOut ? "网易云接口响应超时，请稍后重试。" : "无法连接网易云接口，请检查网络。",
    );
  }

  if (!response.ok) {
    throw new HttpError(
      502,
      "upstream_http_error",
      `网易云接口返回 ${response.status}，请稍后重试。`,
    );
  }

  const body = await response.text();
  try {
    return JSON.parse(body);
  } catch {
    throw new HttpError(502, "upstream_bad_json", "网易云接口返回了无法解析的数据。");
  }
}

function normalizeCover(url) {
  if (typeof url !== "string" || !url.trim()) return null;
  return url.trim().replace(/^http:\/\//i, "https://");
}

export function normalizeSong(song) {
  if (!song || typeof song !== "object" || song.id == null) return null;

  const artists = Array.isArray(song.ar)
    ? song.ar
    : Array.isArray(song.artists)
      ? song.artists
      : [];

  const album = song.al ?? song.album ?? {};
  const durationMs = Number(song.dt ?? song.duration ?? 0);

  return {
    id: String(song.id),
    name: String(song.name ?? "").trim() || "未命名歌曲",
    artists: [...new Set(artists.map((artist) => artist?.name).filter(Boolean))],
    album: String(album.name ?? "").trim(),
    durationMs: Number.isFinite(durationMs) && durationMs > 0 ? durationMs : 0,
    cover: normalizeCover(album.picUrl ?? song.picUrl),
  };
}

function normalizeSongList(songs) {
  if (!Array.isArray(songs)) return [];
  const seen = new Set();
  const result = [];

  for (const raw of songs) {
    const song = normalizeSong(raw);
    if (!song || seen.has(song.id)) continue;
    seen.add(song.id);
    result.push(song);
  }
  return result;
}

async function searchSongs(query) {
  const key = `search:${query.toLocaleLowerCase("zh-CN")}`;
  const cached = cacheGet(key);
  if (cached) return cached;

  let songs = [];
  try {
    const data = await fetchUpstream("/api/cloudsearch/pc", {
      s: query,
      type: 1,
      limit: 15,
      offset: 0,
    });
    songs = normalizeSongList(data?.result?.songs ?? data?.songs);
  } catch (error) {
    if (!(error instanceof HttpError) || error.code !== "upstream_http_error") throw error;
  }

  if (songs.length === 0) {
    const data = await fetchUpstream("/api/search/get/web", {
      s: query,
      type: 1,
      offset: 0,
      total: "true",
      limit: 15,
    });
    songs = normalizeSongList(data?.result?.songs ?? data?.songs);
  }

  cacheSet(key, songs);
  return songs;
}

async function getSong(id) {
  const key = `song:${id}`;
  const cached = cacheGet(key);
  if (cached) return cached;

  const data = await fetchUpstream("/api/song/detail", { ids: `[${id}]` });
  const song = normalizeSongList(data?.songs)[0];
  if (!song) {
    throw new HttpError(404, "song_not_found", "没有找到这首歌，请检查网易云 ID。");
  }

  cacheSet(key, song);
  return song;
}

async function getLyrics(id) {
  const key = `lyrics:${id}`;
  const cached = cacheGet(key);
  if (cached) return cached;

  const data = await fetchUpstream("/api/song/lyric", {
    id,
    lv: 1,
    kv: 1,
    tv: -1,
  });

  const payload = {
    lrc: typeof data?.lrc?.lyric === "string" ? data.lrc.lyric : "",
    tlyric: typeof data?.tlyric?.lyric === "string" ? data.tlyric.lyric : "",
    nolyric: Boolean(data?.nolyric),
    uncollected: Boolean(data?.uncollected),
  };

  cacheSet(key, payload);
  return payload;
}

async function handleApi(requestUrl, response) {
  const { pathname } = requestUrl;

  if (pathname === "/api/health") {
    sendJson(response, 200, { ok: true });
    return;
  }

  if (pathname === "/api/search") {
    const query = (requestUrl.searchParams.get("q") ?? "").trim();
    if (!query) {
      throw new HttpError(400, "query_required", "请输入歌曲名或网易云 ID。");
    }
    if (query.length > 80) {
      throw new HttpError(400, "query_too_long", "搜索内容太长，请缩短后重试。");
    }

    const songs = await searchSongs(query);
    sendJson(response, 200, { songs });
    return;
  }

  const songMatch = pathname.match(/^\/api\/song\/(\d{1,12})$/);
  if (songMatch) {
    sendJson(response, 200, { song: await getSong(songMatch[1]) });
    return;
  }

  const lyricMatch = pathname.match(/^\/api\/lyrics\/(\d{1,12})$/);
  if (lyricMatch) {
    sendJson(response, 200, { lyrics: await getLyrics(lyricMatch[1]) });
    return;
  }

  throw new HttpError(404, "not_found", "接口不存在。");
}

async function serveStatic(requestUrl, response) {
  let pathname;
  try {
    pathname = decodeURIComponent(requestUrl.pathname);
  } catch {
    throw new HttpError(400, "bad_path", "请求路径无效。");
  }

  if (pathname === "/") pathname = "/index.html";
  if (pathname.includes("\0")) {
    throw new HttpError(400, "bad_path", "请求路径无效。");
  }

  const filePath = path.resolve(PUBLIC_DIR, `.${pathname}`);
  if (filePath !== PUBLIC_DIR && !filePath.startsWith(`${PUBLIC_DIR}${path.sep}`)) {
    throw new HttpError(403, "forbidden", "无法访问该文件。");
  }

  let fileStat;
  try {
    fileStat = await stat(filePath);
  } catch {
    throw new HttpError(404, "not_found", "页面不存在。");
  }

  if (!fileStat.isFile()) {
    throw new HttpError(404, "not_found", "页面不存在。");
  }

  const body = await readFile(filePath);
  const extension = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES.get(extension) ?? "application/octet-stream";

  response.writeHead(200, {
    "Content-Type": contentType,
    "Content-Length": body.length,
    "Cache-Control": "no-cache",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "strict-origin-when-cross-origin",
    "Content-Security-Policy":
      "default-src 'self'; " +
      "img-src 'self' data: https://*.126.net; " +
      "style-src 'self' 'unsafe-inline'; " +
      "script-src 'self'; connect-src 'self'; " +
      "base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
  });
  response.end(body);
}

const server = createServer(async (request, response) => {
  try {
    if (request.method !== "GET" && request.method !== "HEAD") {
      response.setHeader("Allow", "GET, HEAD");
      throw new HttpError(405, "method_not_allowed", "只支持 GET 请求。");
    }

    const requestUrl = new URL(request.url ?? "/", `http://${request.headers.host ?? HOST}`);
    if (requestUrl.pathname.startsWith("/api/")) {
      await handleApi(requestUrl, response);
      return;
    }

    await serveStatic(requestUrl, response);
  } catch (error) {
    if (response.headersSent) {
      response.destroy();
      return;
    }

    if (error instanceof HttpError) {
      if (request.url?.startsWith("/api/")) {
        sendJson(response, error.status, {
          error: { code: error.code, message: error.message },
        });
      } else {
        response.writeHead(error.status, { "Content-Type": "text/plain; charset=utf-8" });
        response.end(error.message);
      }
      return;
    }

    console.error("[duet-annotator]", error);
    if (request.url?.startsWith("/api/")) {
      sendJson(response, 500, {
        error: { code: "internal_error", message: "服务出现了意外错误，请稍后重试。" },
      });
    } else {
      response.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("服务出现了意外错误。");
    }
  }
});

server.on("error", (error) => {
  if (error.code === "EADDRINUSE") {
    console.error(`端口 ${PORT} 已被占用，请设置 PORT 后重试。`);
  } else {
    console.error("[duet-annotator] 无法启动服务：", error);
  }
  process.exitCode = 1;
});

server.listen(PORT, HOST, () => {
  console.log(`对唱标注已启动：http://${HOST}:${PORT}`);
  if (COOKIE) console.log("已从 NCM_COOKIE 读取网易云登录态。");
});
