const TIMESTAMP_PATTERN = /\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g;

const CREDIT_PATTERN =
  /^(?:作词|作曲|编曲|词曲|制作(?:人|统筹|助理|公司)?|和声(?:编写)?|合声(?:编写)?|和音|伴唱|原唱|翻唱|吉他|贝斯|鼓|录音(?:工程|助理|室|棚|师)?|混音(?:工程|室|师)?|母带(?:工程|室|师)?|监制|出品(?:人)?|发行|版权|词|曲|OP|SP|统筹|企划|策划|封面(?:设计)?|视觉(?:设计)?|设计|插画|人声(?:编辑|录音)?|配唱(?:制作人|指导)?|弦乐(?:编写)?|钢琴|键盘|口琴|小提琴|大提琴|音乐总监|音乐制作|制作统筹|宣发)\s*[:：]/i;

function timestampToMs(minutes, seconds, fraction) {
  const fractionMs = fraction ? Number(fraction.padEnd(3, "0").slice(0, 3)) : 0;
  return Number(minutes) * 60_000 + Number(seconds) * 1_000 + fractionMs;
}

function normalizeText(value) {
  return String(value ?? "")
    .replace(/^\uFEFF/, "")
    .replace(/\r$/, "")
    .trim();
}

/**
 * Parse an LRC document. Multiple timestamps on one source line expand into
 * separate lyric lines, matching the usual LRC behaviour.
 *
 * @param {string} raw
 * @returns {Array<{
 *   key: string,
 *   startMs: number,
 *   endMs: number,
 *   text: string,
 *   translation: string | null,
 *   isCredit: boolean
 * }>}
 */
export function parseLrc(raw) {
  if (typeof raw !== "string" || !raw.trim()) return [];

  const entries = [];
  let order = 0;

  for (const sourceLine of raw.split("\n")) {
    const line = normalizeText(sourceLine);
    if (!line) continue;

    const tags = [...line.matchAll(TIMESTAMP_PATTERN)];
    if (tags.length === 0) continue;

    const text = normalizeText(line.replace(TIMESTAMP_PATTERN, ""));
    if (!text || text === "//") continue;

    for (const tag of tags) {
      entries.push({
        startMs: timestampToMs(tag[1], tag[2], tag[3]),
        text,
        isCredit: CREDIT_PATTERN.test(text),
        order: order++,
      });
    }
  }

  entries.sort((a, b) => a.startMs - b.startMs || a.order - b.order);

  const occurrences = new Map();
  return entries.map((entry, index) => {
    const baseKey = `${entry.startMs}|${entry.text}`;
    const occurrence = occurrences.get(baseKey) ?? 0;
    occurrences.set(baseKey, occurrence + 1);

    const next = entries[index + 1];
    const endMs =
      next && next.startMs > entry.startMs
        ? next.startMs
        : entry.startMs + (next ? 3_000 : 5_000);

    return {
      key: occurrence === 0 ? baseKey : `${baseKey}|${occurrence}`,
      startMs: entry.startMs,
      endMs,
      text: entry.text,
      translation: null,
      isCredit: entry.isCredit,
    };
  });
}

function attachTranslations(lines, translationLines) {
  const claimed = new Set();

  for (const translation of translationLines) {
    if (!translation.text || translation.isCredit) continue;

    let bestIndex = -1;
    let bestDistance = 600;

    for (let index = 0; index < lines.length; index += 1) {
      if (claimed.has(index)) continue;
      const distance = Math.abs(lines[index].startMs - translation.startMs);
      if (distance <= bestDistance) {
        bestDistance = distance;
        bestIndex = index;
      }
    }

    if (bestIndex !== -1) {
      lines[bestIndex].translation = translation.text;
      claimed.add(bestIndex);
    }
  }
}

/**
 * Parse a main LRC document and attach matching translation lines by time.
 *
 * @param {string} raw
 * @param {string} [translationRaw]
 */
export function parseLyrics(raw, translationRaw = "") {
  const lines = parseLrc(raw);
  if (!lines.length || !translationRaw) return lines;
  attachTranslations(lines, parseLrc(translationRaw));
  return lines;
}

export function formatTime(ms) {
  const totalSeconds = Math.max(0, Math.floor(Number(ms) / 1_000));
  const hours = Math.floor(totalSeconds / 3_600);
  const minutes = Math.floor((totalSeconds % 3_600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  }
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export function formatDuration(ms) {
  const totalSeconds = Math.max(0, Math.round(Number(ms) / 1_000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

/**
 * Accept a bare ID, a NetEase song URL, or a free-text search query.
 *
 * @param {string} raw
 * @returns {{type: "empty"} | {type: "id", id: string} | {type: "query", query: string}}
 */
export function parseSongInput(raw) {
  const value = normalizeText(raw);
  if (!value) return { type: "empty" };

  const url =
    value.match(/music\.163\.com\/[^\s]*?[?&#]id=(\d+)/i) ??
    value.match(/music\.163\.com\/song\/(\d+)/i);

  if (url) return { type: "id", id: url[1] };
  if (/^\d{1,12}$/.test(value)) return { type: "id", id: value };

  return { type: "query", query: value };
}
