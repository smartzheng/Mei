import {
  formatDuration,
  formatTime,
  parseLyrics,
  parseSongInput,
} from "./lyrics.mjs";

const LABELS = {
  male: { name: "男声", short: "男", key: "1" },
  female: { name: "女声", short: "女", key: "2" },
  both: { name: "合唱", short: "合", key: "3" },
  skip: { name: "跳过", short: "跳", key: "0" },
};
const LABEL_ORDER = ["male", "female", "both", "skip"];
const FILTERS = [
  { id: "all", name: "全部" },
  { id: "unlabeled", name: "未标注" },
  { id: "male", name: "男声" },
  { id: "female", name: "女声" },
  { id: "both", name: "合唱" },
];
const LABELS_STORAGE_KEY = "duet-annotator:labels:v1";
const RECENT_STORAGE_KEY = "duet-annotator:recent:v1";
const MAX_RECENT = 8;

const state = {
  song: null,
  lines: [],
  labels: new Map(),
  labelStore: {},
  recent: [],
  searchResults: [],
  filter: "all",
  activeKey: null,
  showCredits: false,
  undoHistory: [],
  currentSongId: null,
  loadingId: null,
  loadToken: 0,
  searchToken: 0,
  searching: false,
  storageOK: true,
  rowByKey: new Map(),
  filterButtons: new Map(),
  restoredCount: 0,
  lyricStatus: "ok",
  lyricError: "",
  focusSearchOnHome: false,
  toastTimer: 0,
};

const refs = {
  homeView: document.querySelector("#homeView"),
  workspaceView: document.querySelector("#workspaceView"),
  loadingView: document.querySelector("#loadingView"),
  workspaceError: document.querySelector("#workspaceError"),
  workspaceContent: document.querySelector("#workspaceContent"),
  workspaceErrorTitle: document.querySelector("#workspaceErrorTitle"),
  workspaceErrorMessage: document.querySelector("#workspaceErrorMessage"),
  retryLoadButton: document.querySelector("#retryLoadButton"),
  errorBackButton: document.querySelector("#errorBackButton"),

  brandLink: document.querySelector("#brandLink"),
  helpButton: document.querySelector("#helpButton"),
  saveState: document.querySelector("#saveState"),

  searchForm: document.querySelector("#searchForm"),
  searchInput: document.querySelector("#searchInput"),
  searchButton: document.querySelector("#searchButton"),
  searchError: document.querySelector("#searchError"),
  searchResults: document.querySelector("#searchResults"),
  searchStatus: document.querySelector("#searchStatus"),
  resultCount: document.querySelector("#resultCount"),
  resultList: document.querySelector("#resultList"),
  recentSection: document.querySelector("#recentSection"),
  recentList: document.querySelector("#recentList"),

  songCover: document.querySelector("#songCover"),
  songId: document.querySelector("#songId"),
  songTitle: document.querySelector("#songTitle"),
  songArtists: document.querySelector("#songArtists"),
  songAlbum: document.querySelector("#songAlbum"),
  backButton: document.querySelector("#backButton"),
  exportButton: document.querySelector("#exportButton"),
  sidebarExportButton: document.querySelector("#sidebarExportButton"),

  mobileReviewed: document.querySelector("#mobileReviewed"),
  mobileTotal: document.querySelector("#mobileTotal"),
  mobileMale: document.querySelector("#mobileMale"),
  mobileFemale: document.querySelector("#mobileFemale"),
  mobileBoth: document.querySelector("#mobileBoth"),
  mobileSkip: document.querySelector("#mobileSkip"),
  mobileSplitBar: document.querySelector("#mobileSplitBar"),

  sheetToolbar: document.querySelector("#sheetToolbar"),
  filters: document.querySelector("#filters"),
  reviewCount: document.querySelector("#reviewCount"),
  undoButton: document.querySelector("#undoButton"),
  creditNote: document.querySelector("#creditNote"),
  creditNoteText: document.querySelector("#creditNoteText"),
  creditToggle: document.querySelector("#creditToggle"),
  lyricEmpty: document.querySelector("#lyricEmpty"),
  lyricEmptyTitle: document.querySelector("#lyricEmpty h2"),
  lyricEmptyMessage: document.querySelector("#lyricEmptyMessage"),
  retryLyricsButton: document.querySelector("#retryLyricsButton"),
  emptyBackButton: document.querySelector("#emptyBackButton"),
  lyricList: document.querySelector("#lyricList"),
  filteredEmpty: document.querySelector("#filteredEmpty"),
  showAllButton: document.querySelector("#showAllButton"),

  reviewedCount: document.querySelector("#reviewedCount"),
  totalCount: document.querySelector("#totalCount"),
  splitBar: document.querySelector("#splitBar"),
  remainingCopy: document.querySelector("#remainingCopy"),
  countMale: document.querySelector("#countMale"),
  countFemale: document.querySelector("#countFemale"),
  countBoth: document.querySelector("#countBoth"),
  countSkip: document.querySelector("#countSkip"),
  countUnlabeled: document.querySelector("#countUnlabeled"),
  storageNote: document.querySelector("#storageNote"),

  helpDialog: document.querySelector("#helpDialog"),
  exportDialog: document.querySelector("#exportDialog"),
  exportSummary: document.querySelector("#exportSummary"),
  exportJsonButton: document.querySelector("#exportJsonButton"),
  exportJsonlButton: document.querySelector("#exportJsonlButton"),
  toast: document.querySelector("#toast"),
};

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

function createElement(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text != null) element.textContent = text;
  return element;
}

function makeIcon(name, className = "icon") {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("class", className);
  svg.setAttribute("aria-hidden", "true");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", `#icon-${name}`);
  svg.append(use);
  return svg;
}

function readStorage(key, fallback) {
  try {
    const value = localStorage.getItem(key);
    if (!value) return fallback;
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

function writeStorage(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    handleStorageFailure();
    return false;
  }
}

function initStorage() {
  try {
    const probe = "duet-annotator:probe";
    localStorage.setItem(probe, "1");
    localStorage.removeItem(probe);
  } catch {
    state.storageOK = false;
  }

  const storedLabels = readStorage(LABELS_STORAGE_KEY, {});
  state.labelStore =
    storedLabels && typeof storedLabels === "object" && !Array.isArray(storedLabels)
      ? storedLabels
      : {};

  const storedRecent = readStorage(RECENT_STORAGE_KEY, []);
  state.recent = Array.isArray(storedRecent)
    ? storedRecent
        .filter((item) => item && /^\d{1,12}$/.test(String(item.id)) && item.name)
        .slice(0, MAX_RECENT)
    : [];

  updateStorageCopy();
}

function handleStorageFailure() {
  if (!state.storageOK) return;
  state.storageOK = false;
  updateStorageCopy();
  showToast("浏览器无法写入本机存储，本次标注不会保留。", { error: true });
}

function updateStorageCopy() {
  if (state.storageOK) {
    refs.storageNote.textContent = "标注自动保存在当前浏览器。";
    refs.storageNote.classList.remove("is-error");
  } else {
    refs.storageNote.textContent = "浏览器禁止本机存储，关闭页面后标注会丢失。";
    refs.storageNote.classList.add("is-error");
    refs.saveState.textContent = "不会保存";
    refs.saveState.classList.add("is-error");
    refs.saveState.hidden = refs.workspaceContent.hidden;
  }
}

function showToast(message, { error = false } = {}) {
  window.clearTimeout(state.toastTimer);
  refs.toast.textContent = message;
  refs.toast.hidden = false;
  refs.toast.style.color = error ? "#fff4f1" : "";
  refs.toast.style.background = error ? "var(--danger)" : "";
  state.toastTimer = window.setTimeout(() => {
    refs.toast.hidden = true;
  }, 2800);
}

function markSaved() {
  if (!state.storageOK) {
    refs.saveState.textContent = "不会保存";
    refs.saveState.classList.add("is-error");
    refs.saveState.hidden = false;
    return;
  }

  const time = new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date());
  refs.saveState.textContent = `已保存 ${time}`;
  refs.saveState.classList.remove("is-error");
  refs.saveState.hidden = false;
}

async function getJson(url) {
  let response;
  try {
    response = await fetch(url, {
      headers: { Accept: "application/json" },
    });
  } catch {
    throw new ApiError("无法连接本地服务，请确认启动脚本仍在运行。", 0);
  }

  let data = null;
  try {
    data = await response.json();
  } catch {
    // The status below produces a useful error even if the body is empty.
  }

  if (!response.ok) {
    throw new ApiError(
      data?.error?.message ?? `请求失败（${response.status}）。`,
      response.status,
    );
  }
  return data;
}

function setDocumentTitle(title) {
  document.title = title ? `${title} · 对唱标注` : "对唱标注 · Duet Sheet";
}

function setView(name) {
  refs.homeView.hidden = name !== "home";
  refs.workspaceView.hidden = name !== "workspace";
}

function showHome({ focusSearch = false } = {}) {
  state.loadToken += 1;
  state.loadingId = null;
  state.currentSongId = null;
  state.song = null;
  state.lines = [];
  state.labels = new Map();
  state.undoHistory = [];
  state.activeKey = null;
  state.showCredits = false;
  state.filter = "all";
  state.rowByKey.clear();

  closeDialogs();
  setView("home");
  setDocumentTitle("");
  refs.helpButton.hidden = true;
  refs.saveState.hidden = true;
  refs.workspaceView.removeAttribute("aria-busy");
  renderRecent();
  window.scrollTo({ top: 0, behavior: "auto" });

  if (focusSearch || state.focusSearchOnHome) {
    state.focusSearchOnHome = false;
    window.setTimeout(() => refs.searchInput.focus(), 30);
  }
}

function showWorkspaceLoading() {
  refs.searchInput.blur();
  setView("workspace");
  refs.loadingView.hidden = false;
  refs.workspaceError.hidden = true;
  refs.workspaceContent.hidden = true;
  refs.helpButton.hidden = false;
  refs.saveState.hidden = true;
  refs.workspaceView.setAttribute("aria-busy", "true");
  window.scrollTo({ top: 0, behavior: "auto" });
}

function showWorkspaceContent() {
  refs.loadingView.hidden = true;
  refs.workspaceError.hidden = true;
  refs.workspaceContent.hidden = false;
  refs.helpButton.hidden = false;
  refs.saveState.hidden = false;
  refs.workspaceView.removeAttribute("aria-busy");
}

function showWorkspaceError(title, message, { retry = true } = {}) {
  refs.loadingView.hidden = true;
  refs.workspaceContent.hidden = true;
  refs.workspaceError.hidden = false;
  refs.workspaceErrorTitle.textContent = title;
  refs.workspaceErrorMessage.textContent = message;
  refs.retryLoadButton.hidden = !retry;
  refs.helpButton.hidden = false;
  refs.saveState.hidden = true;
  refs.workspaceView.removeAttribute("aria-busy");
  setDocumentTitle("加载失败");
}

function goHome({ focusSearch = true } = {}) {
  if (location.hash) {
    state.focusSearchOnHome = focusSearch;
    location.hash = "";
    return;
  }
  showHome({ focusSearch });
}

function routeFromHash() {
  const match = location.hash.match(/^#song=(\d{1,12})$/);
  if (match) {
    loadSong(match[1]);
  } else {
    showHome();
  }
}

function setSearching(searching) {
  state.searching = searching;
  refs.searchButton.disabled = searching;
  refs.searchButton.setAttribute("aria-busy", String(searching));
  const label = refs.searchButton.querySelector("span");
  if (label) label.textContent = searching ? "搜索中" : "获取歌词";
}

function showSearchError(message) {
  refs.searchError.textContent = message;
  refs.searchError.hidden = !message;
}

function clearSearchFeedback() {
  showSearchError("");
  refs.searchStatus.hidden = true;
  refs.searchStatus.classList.remove("is-message", "is-error");
}

function renderSearchStatus(message, { error = false } = {}) {
  refs.searchStatus.textContent = message;
  refs.searchStatus.hidden = false;
  refs.searchStatus.classList.add("is-message");
  refs.searchStatus.classList.toggle("is-error", error);
}

function normalizeSong(song) {
  return {
    id: String(song.id),
    name: song.name || "未命名歌曲",
    artists: Array.isArray(song.artists) ? song.artists : [],
    album: song.album || "",
    durationMs: Number(song.durationMs) || 0,
    cover: song.cover || null,
  };
}

function renderSearchResults(songs) {
  state.searchResults = songs;
  refs.resultList.replaceChildren();
  refs.searchResults.hidden = false;

  if (!songs.length) {
    refs.resultCount.textContent = "0 个结果";
    refs.searchStatus.hidden = true;
    renderSearchStatus("没有搜到歌曲。试试“歌手 + 歌名”，或直接输入网易云 ID。");
    return;
  }

  refs.searchStatus.hidden = true;
  refs.resultCount.textContent = `${songs.length} 个结果`;

  const fragment = document.createDocumentFragment();
  for (const raw of songs) {
    const song = normalizeSong(raw);
    const item = createElement("li", "result-item");
    const button = createElement("button", "result-button");
    button.type = "button";
    button.setAttribute(
      "aria-label",
      `获取 ${song.name}，${song.artists.join("、") || "未知歌手"} 的歌词`,
    );

    if (song.cover) {
      const image = createElement("img", "result-cover");
      image.src = song.cover;
      image.alt = "";
      image.loading = "lazy";
      image.decoding = "async";
      image.referrerPolicy = "no-referrer";
      image.addEventListener("error", () => {
        const fallback = createElement("span", "result-cover-fallback");
        fallback.append(makeIcon("note"));
        image.replaceWith(fallback);
      });
      button.append(image);
    } else {
      const fallback = createElement("span", "result-cover-fallback");
      fallback.append(makeIcon("note"));
      button.append(fallback);
    }

    const copy = createElement("span", "result-copy");
    const title = createElement("span", "result-title", song.name);
    title.title = song.name;
    copy.append(title);
    const metaParts = [];
    if (song.artists.length) metaParts.push(song.artists.join(" / "));
    if (song.album) metaParts.push(song.album);
    copy.append(createElement("span", "result-meta", metaParts.join(" · ")));
    button.append(copy);

    button.append(
      createElement(
        "span",
        "result-duration",
        song.durationMs ? formatDuration(song.durationMs) : "--:--",
      ),
    );
    button.append(makeIcon("arrow", "result-arrow icon"));

    button.addEventListener("click", () => {
      goToSong(song.id);
    });

    item.append(button);
    fragment.append(item);
  }
  refs.resultList.append(fragment);
}

function renderRecent() {
  refs.recentList.replaceChildren();
  if (!state.recent.length) {
    refs.recentSection.hidden = true;
    return;
  }

  refs.recentSection.hidden = false;
  const fragment = document.createDocumentFragment();

  state.recent.forEach((recent, index) => {
    const item = createElement("li", "recent-item");
    const button = createElement("button", "recent-button");
    button.type = "button";
    button.setAttribute(
      "aria-label",
      `继续标注 ${recent.name}，已标注 ${recent.reviewed} / ${recent.total} 行`,
    );

    button.append(
      createElement("span", "recent-index", String(index + 1).padStart(2, "0")),
    );

    const copy = createElement("span", "recent-copy");
    const title = createElement("span", "recent-title", recent.name);
    title.title = recent.name;
    copy.append(title);
    copy.append(
      createElement(
        "span",
        "recent-meta",
        recent.artists?.length ? recent.artists.join(" / ") : "未知歌手",
      ),
    );
    button.append(copy);

    button.append(
      createElement(
        "span",
        "recent-progress",
        `${recent.reviewed ?? 0}/${recent.total ?? 0}`,
      ),
    );

    button.addEventListener("click", () => goToSong(stringId(recent.id)));
    item.append(button);
    fragment.append(item);
  });

  refs.recentList.append(fragment);
}

function stringId(value) {
  return String(value ?? "").replace(/\D/g, "").slice(0, 12);
}

function goToSong(id) {
  const songId = stringId(id);
  if (!songId) return;
  state.searchToken += 1;
  setSearching(false);
  clearSearchFeedback();
  if (location.hash === `#song=${songId}`) {
    loadSong(songId);
  } else {
    location.hash = `song=${songId}`;
  }
}

async function runSearch(query) {
  const token = ++state.searchToken;
  setSearching(true);
  showSearchError("");
  state.searchResults = [];
  refs.searchResults.hidden = false;
  refs.resultList.replaceChildren();
  refs.resultCount.textContent = "";
  renderSearchStatus("正在搜索网易云音乐…");

  try {
    const data = await getJson(`/api/search?q=${encodeURIComponent(query)}`);
    if (token !== state.searchToken) return;
    renderSearchResults(Array.isArray(data.songs) ? data.songs : []);
  } catch (error) {
    if (token !== state.searchToken) return;
    refs.resultList.replaceChildren();
    refs.resultCount.textContent = "搜索失败";
    renderSearchStatus(error.message || "搜索失败，请稍后重试。", { error: true });
  } finally {
    if (token === state.searchToken) setSearching(false);
  }
}

function handleSearchSubmit(event) {
  event.preventDefault();
  const parsed = parseSongInput(refs.searchInput.value);

  if (parsed.type === "empty") {
    showSearchError("请输入歌曲名或网易云 ID。");
    refs.searchInput.focus();
    return;
  }

  showSearchError("");
  state.searchToken += 1;

  if (parsed.type === "id") {
    setSearching(false);
    goToSong(parsed.id);
    return;
  }

  runSearch(parsed.query);
}

async function loadSong(id) {
  const songId = stringId(id);
  if (!songId || state.loadingId === songId) return;

  state.loadingId = songId;
  state.currentSongId = songId;
  const token = ++state.loadToken;
  showWorkspaceLoading();

  try {
    const [songResult, lyricResult] = await Promise.allSettled([
      getJson(`/api/song/${songId}`),
      getJson(`/api/lyrics/${songId}`),
    ]);

    if (token !== state.loadToken) return;

    if (songResult.status === "rejected") {
      const error = songResult.reason;
      const notFound = error?.status === 404;
      showWorkspaceError(
        notFound ? "没有找到这首歌" : "无法加载歌曲",
        notFound
          ? "请检查网易云 ID 是否正确，或回到搜索换一首。"
          : error?.message || "歌曲信息获取失败，请稍后重试。",
        { retry: !notFound },
      );
      return;
    }

    state.song = normalizeSong(songResult.value.song);
    state.lyricStatus = "ok";
    state.lyricError = "";

    if (lyricResult.status === "rejected") {
      state.lines = [];
      state.lyricStatus = "error";
      state.lyricError =
        lyricResult.reason?.message || "歌词接口请求失败，请稍后重试。";
    } else {
      const lyrics = lyricResult.value.lyrics ?? {};
      try {
        state.lines = parseLyrics(lyrics.lrc ?? "", lyrics.tlyric ?? "");
        markHeaderCredits(state.lines, state.song);
      } catch {
        state.lines = [];
        state.lyricStatus = "error";
        state.lyricError = "歌词格式无法解析，请尝试换一首歌。";
      }

      if (state.lyricStatus === "ok" && state.lines.length === 0) {
        state.lyricStatus = "empty";
        state.lyricError = lyrics.nolyric
          ? "这首歌在网易云被标记为纯音乐。"
          : lyrics.uncollected
            ? "网易云没有返回这首歌的歌词，可能需要登录或版权受限。"
            : "这首歌没有可用的 LRC 歌词。";
      } else if (
        state.lyricStatus === "ok" &&
        state.lines.every((line) => line.isCredit)
      ) {
        state.lyricStatus = "empty";
        state.lyricError = "歌词里只有制作信息，没有可标注的演唱行。";
      }
    }

    const restored = restoreLabels(songId, state.lines);
    state.labels = restored.labels;
    state.restoredCount = restored.count;
    state.undoHistory = [];
    state.filter = "all";
    state.showCredits = false;
    state.activeKey = null;

    renderWorkspace();
    showWorkspaceContent();
    recordRecent();

    refs.saveState.textContent = state.storageOK ? "本机保存" : "不会保存";
    refs.saveState.classList.toggle("is-error", !state.storageOK);
    refs.saveState.hidden = false;

    if (state.restoredCount > 0) {
      window.requestAnimationFrame(() => {
        if (token !== state.loadToken) return;
        const row = state.activeKey
          ? state.rowByKey.get(state.activeKey)
          : null;
        if (row) {
          const rect = row.getBoundingClientRect();
          if (rect.top < 72 || rect.bottom > window.innerHeight - 80) {
            row.scrollIntoView({ block: "center", behavior: "auto" });
          }
        }
        showToast(`已恢复 ${state.restoredCount} 行本机标注`);
      });
    }
  } catch (error) {
    if (token !== state.loadToken) return;
    showWorkspaceError("无法加载歌曲", error?.message || "请稍后重试。");
  } finally {
    if (token === state.loadToken) state.loadingId = null;
  }
}

function restoreLabels(songId, lines) {
  const entry = state.labelStore?.[songId];
  const labels = new Map();
  if (!entry || typeof entry.labels !== "object" || entry.labels == null) {
    return { labels, count: 0 };
  }

  const validKeys = new Set(
    lines.filter((line) => !line.isCredit).map((line) => line.key),
  );

  for (const [key, label] of Object.entries(entry.labels)) {
    if (validKeys.has(key) && Object.hasOwn(LABELS, label)) {
      labels.set(key, label);
    }
  }
  return { labels, count: labels.size };
}

function renderWorkspace() {
  renderSongHeader();
  renderLyricList();
  renderFilters();
  const stats = renderStats();
  renderCreditsNote();
  updateUndoButton();

  const hasAnnotatable = stats.total > 0;
  refs.sheetToolbar.hidden = !hasAnnotatable;
  refs.lyricList.hidden = !hasAnnotatable;
  refs.lyricEmpty.hidden = hasAnnotatable;
  refs.creditNote.hidden = !hasAnnotatable || creditCount() === 0;
  refs.filteredEmpty.hidden = true;

  if (!hasAnnotatable) {
    setLyricEmptyCopy();
    state.activeKey = null;
    updateActiveRow();
    return;
  }

  applyFilter();
  const annotatable = annotatableLines();
  const first =
    annotatable.find((line) => !state.labels.has(line.key)) ?? annotatable[0];
  setActive(first.key);
  updateActiveRow();
}

function renderSongHeader() {
  const song = state.song;
  if (!song) return;

  refs.songId.textContent = `#${song.id}`;
  refs.songTitle.textContent = song.name;
  refs.songTitle.title = song.name;
  refs.songArtists.textContent = song.artists.length
    ? song.artists.join(" / ")
    : "未知歌手";
  refs.songArtists.title = refs.songArtists.textContent;
  refs.songAlbum.textContent = song.album || "未知专辑";
  refs.songAlbum.title = refs.songAlbum.textContent;

  if (song.cover) {
    refs.songCover.src = song.cover;
    refs.songCover.alt = `${song.name} 的封面`;
    refs.songCover.classList.remove("is-broken");
  } else {
    refs.songCover.removeAttribute("src");
    refs.songCover.alt = "";
    refs.songCover.classList.remove("is-broken");
  }

  setDocumentTitle(song.name);
}

function annotatableLines() {
  return state.lines.filter((line) => !line.isCredit);
}

function markHeaderCredits(lines, song) {
  if (!song || !Array.isArray(lines) || lines.length === 0) return;
  const baseTitle = (song.name || "")
    .replace(/\s*[（(][^）)]*[）)]\s*$/, "")
    .trim();
  if (!baseTitle) return;

  for (const line of lines.slice(0, 3)) {
    if (line.startMs > 1_500 || line.isCredit) continue;
    const normalized = line.text.replace(/\s+/g, " ").trim();
    if (!normalized.startsWith(baseTitle)) continue;
    const rest = normalized.slice(baseTitle.length).trimStart();
    if (/^[-–—·|]/.test(rest)) line.isCredit = true;
  }
}

function creditCount() {
  return state.lines.length - annotatableLines().length;
}

function getLabel(line) {
  return state.labels.get(line.key) ?? null;
}

function renderLyricList() {
  refs.lyricList.replaceChildren();
  state.rowByKey.clear();
  if (!state.lines.length) return;

  const fragment = document.createDocumentFragment();
  let lineNumber = 0;

  state.lines.forEach((line, index) => {
    const row = createElement("li", "lyric-row");
    row.dataset.key = line.key;
    row.dataset.index = String(index);
    if (line.isCredit) row.classList.add("is-credit");

    const displayNumber = line.isCredit ? index + 1 : ++lineNumber;
    const label = getLabel(line);
    if (label) row.dataset.label = label;

    if (line.isCredit) {
      row.append(createElement("span", "line-time", formatTime(line.startMs)));
    } else {
      const timeButton = createElement(
        "button",
        "line-time",
        formatTime(line.startMs),
      );
      timeButton.type = "button";
      timeButton.setAttribute(
        "aria-label",
        `设为当前行：第 ${displayNumber} 行，${formatTime(line.startMs)}，${line.text}`,
      );
      row.append(timeButton);
    }

    const body = createElement("div", "line-body");
    body.append(createElement("p", "line-text", line.text));
    if (line.translation) {
      body.append(createElement("p", "line-translation", line.translation));
    }
    row.append(body);

    if (line.isCredit) {
      row.append(createElement("span", "credit-tag", "制作信息"));
    } else {
      const group = createElement("div", "label-group");
      group.setAttribute("role", "group");
      group.setAttribute("aria-label", `第 ${displayNumber} 行的声部`);

      for (const labelId of LABEL_ORDER) {
        const meta = LABELS[labelId];
        const button = createElement("button", "label-button");
        button.type = "button";
        button.dataset.label = labelId;
        button.setAttribute(
          "aria-label",
          `将第 ${displayNumber} 行标记为${meta.name}`,
        );
        button.title = `${meta.name}（${meta.key}）`;
        button.setAttribute("aria-pressed", String(label === labelId));
        button.append(createElement("i", `dot ${labelId}`));
        button.append(createElement("span", "", meta.short));
        button.append(createElement("kbd", "", meta.key));
        group.append(button);
      }
      row.append(group);
    }

    state.rowByKey.set(line.key, row);
    fragment.append(row);
  });

  refs.lyricList.append(fragment);
}

function buildFilters() {
  refs.filters.replaceChildren();
  state.filterButtons.clear();

  for (const filter of FILTERS) {
    const button = createElement("button", "filter-button", filter.name);
    button.type = "button";
    button.dataset.filter = filter.id;
    button.setAttribute("aria-pressed", "false");
    const count = createElement("span", "", "0");
    button.append(count);
    button.addEventListener("click", () => setFilter(filter.id));
    refs.filters.append(button);
    state.filterButtons.set(filter.id, { button, count });
  }
}

function computeStats() {
  const lines = annotatableLines();
  const counts = { male: 0, female: 0, both: 0, skip: 0 };

  for (const line of lines) {
    const label = getLabel(line);
    if (label && Object.hasOwn(counts, label)) counts[label] += 1;
  }

  const reviewed = counts.male + counts.female + counts.both + counts.skip;
  return {
    total: lines.length,
    counts,
    reviewed,
    remaining: Math.max(0, lines.length - reviewed),
  };
}

function renderFilters() {
  const stats = computeStats();
  for (const filter of FILTERS) {
    const entry = state.filterButtons.get(filter.id);
    if (!entry) continue;
    const count =
      filter.id === "all"
        ? stats.total
        : filter.id === "unlabeled"
          ? stats.remaining
          : stats.counts[filter.id] ?? 0;
    entry.count.textContent = String(count);
    entry.button.setAttribute(
      "aria-pressed",
      String(state.filter === filter.id),
    );
  }
}

function renderStats() {
  const stats = computeStats();
  const { total, counts, reviewed, remaining } = stats;

  refs.reviewedCount.textContent = String(reviewed);
  refs.totalCount.textContent = String(total);
  refs.countMale.textContent = String(counts.male);
  refs.countFemale.textContent = String(counts.female);
  refs.countBoth.textContent = String(counts.both);
  refs.countSkip.textContent = String(counts.skip);
  refs.countUnlabeled.textContent = String(remaining);
  refs.remainingCopy.textContent =
    remaining > 0 ? `还剩 ${remaining} 行` : "全部标注完成";
  refs.reviewCount.textContent =
    remaining > 0 ? `剩余 ${remaining} 行` : "已全部标注";

  refs.mobileReviewed.textContent = String(reviewed);
  refs.mobileTotal.textContent = String(total);
  refs.mobileMale.textContent = String(counts.male);
  refs.mobileFemale.textContent = String(counts.female);
  refs.mobileBoth.textContent = String(counts.both);
  refs.mobileSkip.textContent = String(counts.skip);

  const summary =
    `男声 ${counts.male} 行，女声 ${counts.female} 行，` +
    `合唱 ${counts.both} 行，跳过 ${counts.skip} 行，` +
    `未标注 ${remaining} 行`;
  refs.splitBar.setAttribute("aria-label", summary);
  refs.mobileSplitBar.setAttribute("aria-label", summary);
  renderSplitBar(refs.splitBar, counts, total);
  renderSplitBar(refs.mobileSplitBar, counts, total);

  const hasLines = total > 0;
  refs.exportButton.disabled = !hasLines;
  refs.sidebarExportButton.disabled = !hasLines;

  return stats;
}

function renderSplitBar(container, counts, total) {
  container.replaceChildren();
  if (total === 0) {
    container.append(createElement("span", "split-segment unlabeled"));
    return;
  }

  const segments = [
    ["male", counts.male],
    ["female", counts.female],
    ["both", counts.both],
    ["skip", counts.skip],
    ["unlabeled", Math.max(0, total - counts.male - counts.female - counts.both - counts.skip)],
  ];

  for (const [name, count] of segments) {
    if (count <= 0) continue;
    const segment = createElement("span", `split-segment ${name}`);
    segment.style.width = `${(count / total) * 100}%`;
    container.append(segment);
  }
}

function renderCreditsNote() {
  const count = creditCount();
  const total = annotatableLines().length;
  if (count === 0 || total === 0) {
    refs.creditNote.hidden = true;
    return;
  }

  refs.creditNote.hidden = false;
  refs.creditNoteText.textContent = state.showCredits
    ? "制作信息已显示。"
    : `已省略 ${count} 行制作信息。`;
  refs.creditToggle.textContent = state.showCredits ? "隐藏" : "显示";
  refs.creditToggle.setAttribute("aria-expanded", String(state.showCredits));
}

function setLyricEmptyCopy() {
  refs.lyricEmptyTitle.textContent =
    state.lyricStatus === "error" ? "歌词获取失败" : "没有可标注的歌词";
  refs.lyricEmptyMessage.textContent =
    state.lyricError ||
    "这首歌可能是纯音乐，或接口没有返回歌词。";
}

function isLineVisible(line) {
  const row = state.rowByKey.get(line.key);
  return Boolean(row && !row.hidden);
}

function applyFilter() {
  let visible = 0;

  for (const line of state.lines) {
    const row = state.rowByKey.get(line.key);
    if (!row) continue;

    let show;
    if (line.isCredit) {
      show = state.showCredits && state.filter === "all";
    } else if (state.filter === "all") {
      show = true;
    } else if (state.filter === "unlabeled") {
      show = !getLabel(line);
    } else {
      show = getLabel(line) === state.filter;
    }

    row.hidden = !show;
    if (show && !line.isCredit) visible += 1;
  }

  const total = annotatableLines().length;
  refs.filteredEmpty.hidden = !(
    total > 0 &&
    state.filter !== "all" &&
    visible === 0
  );
}

function visibleAnnotatableKeys() {
  return annotatableLines()
    .filter((line) => isLineVisible(line))
    .map((line) => line.key);
}

function setActive(key, { scroll = false, block = "center" } = {}) {
  const line = state.lines.find((item) => item.key === key);
  if (!line || line.isCredit || !isLineVisible(line)) return;
  state.activeKey = key;
  updateActiveRow();
  if (scroll) scrollActiveIntoView(block);
}

function updateActiveRow() {
  for (const [key, row] of state.rowByKey) {
    const active = key === state.activeKey;
    row.classList.toggle("is-active", active);
    if (active) row.setAttribute("aria-current", "true");
    else row.removeAttribute("aria-current");
  }
}

function scrollActiveIntoView(block = "center") {
  if (!state.activeKey) return;
  const row = state.rowByKey.get(state.activeKey);
  if (!row || row.hidden) return;
  row.scrollIntoView({
    block,
    behavior: reduceMotion.matches ? "auto" : "smooth",
  });
}

function ensureActiveVisible() {
  const visible = visibleAnnotatableKeys();
  if (state.activeKey && visible.includes(state.activeKey)) return;
  if (visible.length) {
    state.activeKey = visible[0];
  } else {
    state.activeKey = null;
  }
  updateActiveRow();
}

function setFilter(filterId) {
  state.filter = filterId;
  if (filterId !== "all" && state.showCredits) state.showCredits = false;
  renderFilters();
  renderCreditsNote();
  applyFilter();
  ensureActiveVisible();
  scrollActiveIntoView("nearest");
}

function updateRowLabel(key) {
  const row = state.rowByKey.get(key);
  if (!row) return;
  const label = state.labels.get(key) ?? null;
  if (label) row.dataset.label = label;
  else delete row.dataset.label;

  for (const button of row.querySelectorAll(".label-button")) {
    button.setAttribute(
      "aria-pressed",
      String(button.dataset.label === label),
    );
  }
}

function applyLabel(key, label, { record = true, advance = false } = {}) {
  const line = state.lines.find((item) => item.key === key);
  if (!line || line.isCredit) return false;

  const current = state.labels.get(key) ?? null;
  if (current === label) return false;

  const order = visibleAnnotatableKeys();
  const currentIndex = order.indexOf(key);
  const nextKey = currentIndex >= 0 ? order[currentIndex + 1] ?? null : null;

  if (record) {
    state.undoHistory.push({ key, previous: current });
    if (state.undoHistory.length > 100) state.undoHistory.shift();
  }

  if (label) state.labels.set(key, label);
  else state.labels.delete(key);

  updateRowLabel(key);
  renderFilters();
  renderStats();
  applyFilter();
  updateUndoButton();
  persistLabels();

  if (advance && nextKey && isLineVisibleKey(nextKey)) {
    setActive(nextKey, { scroll: true });
  } else {
    ensureActiveVisible();
  }

  return true;
}

function isLineVisibleKey(key) {
  const line = state.lines.find((item) => item.key === key);
  return Boolean(line && isLineVisible(line));
}

function updateUndoButton() {
  refs.undoButton.disabled = state.undoHistory.length === 0;
}

function undo() {
  const action = state.undoHistory.pop();
  if (!action) return;
  const line = state.lines.find((item) => item.key === action.key);
  if (!line) {
    updateUndoButton();
    return;
  }

  if (action.previous) state.labels.set(action.key, action.previous);
  else state.labels.delete(action.key);

  updateRowLabel(action.key);
  renderFilters();
  renderStats();

  const row = state.rowByKey.get(action.key);
  if (!row || row.hidden) {
    state.filter = "all";
    renderFilters();
    applyFilter();
  }

  updateUndoButton();
  setActive(action.key, { scroll: true });
  persistLabels();
}

function clearActiveLabel() {
  if (!state.activeKey) return;
  applyLabel(state.activeKey, null, { record: true });
}

function moveActive(delta) {
  const order = visibleAnnotatableKeys();
  if (!order.length) return;
  let index = order.indexOf(state.activeKey);
  if (index === -1) index = 0;
  else index = Math.max(0, Math.min(order.length - 1, index + delta));
  setActive(order[index], { scroll: true });
}

function applyLabelToActive(label, { advance = false } = {}) {
  ensureActiveVisible();
  if (!state.activeKey) return;
  applyLabel(state.activeKey, label, { record: true, advance });
}

function persistLabels() {
  if (!state.song) return;

  if (!state.storageOK) {
    refs.saveState.textContent = "不会保存";
    refs.saveState.classList.add("is-error");
    return;
  }

  state.labelStore[state.song.id] = {
    updatedAt: Date.now(),
    labels: Object.fromEntries(state.labels),
  };

  const entries = Object.entries(state.labelStore)
    .filter(([, entry]) => entry && typeof entry === "object")
    .sort((a, b) => (b[1].updatedAt ?? 0) - (a[1].updatedAt ?? 0));

  if (entries.length > 40) {
    state.labelStore = Object.fromEntries(entries.slice(0, 40));
  }

  const saved = writeStorage(LABELS_STORAGE_KEY, state.labelStore);
  if (!saved) return;
  recordRecent();
  if (state.storageOK) markSaved();
}

function recordRecent() {
  if (!state.song) return;
  const stats = computeStats();
  if (stats.total === 0) return;

  const existing = state.recent.some(
    (item) => String(item.id) === state.song.id,
  );
  if (stats.reviewed === 0 && !existing) return;

  state.recent = [
    {
      id: state.song.id,
      name: state.song.name,
      artists: state.song.artists,
      cover: state.song.cover,
      total: stats.total,
      reviewed: stats.reviewed,
      updatedAt: Date.now(),
    },
    ...state.recent.filter((item) => String(item.id) !== state.song.id),
  ].slice(0, MAX_RECENT);

  if (state.storageOK) writeStorage(RECENT_STORAGE_KEY, state.recent);
}

function setCreditsVisible(visible) {
  state.showCredits = visible;
  if (visible) state.filter = "all";
  renderFilters();
  applyFilter();
  renderCreditsNote();
  ensureActiveVisible();
  if (visible) {
    const firstCredit = state.lines.find((line) => line.isCredit);
    if (firstCredit) {
      state.rowByKey.get(firstCredit.key)?.scrollIntoView({
        block: "nearest",
        behavior: reduceMotion.matches ? "auto" : "smooth",
      });
    }
  }
}

function openHelp() {
  if (!refs.helpDialog.open) refs.helpDialog.showModal();
}

function openExport() {
  const stats = computeStats();
  if (stats.total === 0) {
    showToast("还没有可导出的歌词。");
    return;
  }

  refs.exportSummary.textContent =
    `${state.song?.name ?? "当前歌曲"} · 共 ${stats.total} 行，` +
    `已标注 ${stats.reviewed} 行。`;
  if (!refs.exportDialog.open) refs.exportDialog.showModal();
}

function buildExportData() {
  const lines = annotatableLines();
  const exportedLines = lines.map((line, index) => {
    const label = getLabel(line);
    return {
      lineIndex: index,
      startMs: line.startMs,
      endMs: line.endMs,
      text: line.text,
      translation: line.translation ?? null,
      label,
      reviewed: Boolean(label),
    };
  });

  const reviewed = exportedLines.filter((line) => line.reviewed).length;
  const song = state.song;

  return {
    schemaVersion: 1,
    app: "duet-annotator",
    exportedAt: new Date().toISOString(),
    song: {
      id: song.id,
      name: song.name,
      artists: song.artists,
      album: song.album,
      durationMs: song.durationMs,
    },
    labelLegend: Object.fromEntries(
      Object.entries(LABELS).map(([key, value]) => [key, value.name]),
    ),
    reviewed,
    total: exportedLines.length,
    lines: exportedLines,
  };
}

function downloadJson() {
  const data = buildExportData();
  downloadFile(
    exportFilename("json"),
    JSON.stringify(data, null, 2),
    "application/json",
  );
  refs.exportDialog.close();
  showToast(`已导出 ${data.reviewed} 行标注`);
}

function downloadJsonl() {
  const data = buildExportData();
  const lines = data.lines.map((line) => ({
    song_id: data.song.id,
    song_name: data.song.name,
    line_index: line.lineIndex,
    start_ms: line.startMs,
    end_ms: line.endMs,
    text: line.text,
    translation: line.translation,
    label: line.label,
    reviewed: line.reviewed,
    label_source: line.label ? "manual" : null,
  }));
  const content = lines.map((line) => JSON.stringify(line)).join("\n") + "\n";
  downloadFile(exportFilename("jsonl"), content, "application/x-ndjson");
  refs.exportDialog.close();
  showToast(`已导出 ${data.reviewed} 行标注`);
}

function exportFilename(extension) {
  const name = (state.song?.name ?? "song")
    .replace(/[\\/:*?"<>|\n\r]/g, "-")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 60);
  const id = state.song?.id ?? "unknown";
  return `${name || "song"}-${id}-annotations.${extension}`;
}

function downloadFile(filename, content, type) {
  const blob = new Blob([content], { type: `${type};charset=utf-8` });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.style.display = "none";
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 2_000);
}

function closeDialogs() {
  for (const dialog of [refs.helpDialog, refs.exportDialog]) {
    if (dialog?.open) dialog.close();
  }
}

function enableBackdropClose(dialog) {
  dialog.addEventListener("click", (event) => {
    if (event.target !== dialog) return;
    const rect = dialog.getBoundingClientRect();
    const inside =
      event.clientX >= rect.left &&
      event.clientX <= rect.right &&
      event.clientY >= rect.top &&
      event.clientY <= rect.bottom;
    if (!inside) dialog.close();
  });
}

function onKeydown(event) {
  if (document.querySelector("dialog[open]")) return;

  const target = event.target;
  if (
    target instanceof HTMLElement &&
    (target.matches("input, textarea, select") || target.isContentEditable)
  ) {
    return;
  }

  const isUndo =
    (event.metaKey || event.ctrlKey) &&
    !event.shiftKey &&
    event.key.toLowerCase() === "z";
  if (isUndo) {
    if (refs.workspaceContent.hidden) return;
    event.preventDefault();
    undo();
    return;
  }

  if (event.metaKey || event.ctrlKey || event.altKey) return;
  if (refs.workspaceContent.hidden) return;

  if (event.key === "?") {
    event.preventDefault();
    openHelp();
    return;
  }

  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    moveActive(event.key === "ArrowDown" ? 1 : -1);
    return;
  }

  if (event.key === "Backspace" || event.key === "Delete") {
    if (!state.activeKey) return;
    event.preventDefault();
    clearActiveLabel();
    return;
  }

  const labelByKey = {
    "1": "male",
    "2": "female",
    "3": "both",
    "0": "skip",
  };
  const label = labelByKey[event.key];
  if (label) {
    event.preventDefault();
    applyLabelToActive(label, { advance: true });
  }
}

function bindEvents() {
  refs.brandLink.addEventListener("click", (event) => {
    event.preventDefault();
    goHome();
  });

  refs.searchForm.addEventListener("submit", handleSearchSubmit);
  refs.searchInput.addEventListener("input", () => {
    if (!refs.searchError.hidden) showSearchError("");
  });

  refs.backButton.addEventListener("click", () => goHome());
  refs.emptyBackButton.addEventListener("click", () => goHome());
  refs.errorBackButton.addEventListener("click", () => goHome());
  refs.retryLoadButton.addEventListener("click", () => {
    if (state.currentSongId) loadSong(state.currentSongId);
  });
  refs.retryLyricsButton.addEventListener("click", () => {
    if (state.currentSongId) loadSong(state.currentSongId);
  });

  refs.helpButton.addEventListener("click", openHelp);
  refs.exportButton.addEventListener("click", openExport);
  refs.sidebarExportButton.addEventListener("click", openExport);
  refs.exportJsonButton.addEventListener("click", downloadJson);
  refs.exportJsonlButton.addEventListener("click", downloadJsonl);

  refs.undoButton.addEventListener("click", undo);
  refs.creditToggle.addEventListener("click", () => {
    setCreditsVisible(!state.showCredits);
  });
  refs.showAllButton.addEventListener("click", () => setFilter("all"));

  refs.lyricList.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;

    const labelButton = target.closest(".label-button");
    if (labelButton) {
      const row = labelButton.closest(".lyric-row");
      const key = row?.dataset.key;
      const label = labelButton.dataset.label;
      if (key && label) applyLabel(key, label, { record: true });
      return;
    }

    const row = target.closest(".lyric-row");
    if (!row || row.classList.contains("is-credit")) return;
    setActive(row.dataset.key);
  });

  refs.lyricList.addEventListener("focusin", (event) => {
    const row = event.target.closest?.(".lyric-row");
    if (!row || row.classList.contains("is-credit")) return;
    setActive(row.dataset.key);
  });

  refs.songCover.addEventListener("error", () => {
    refs.songCover.classList.add("is-broken");
  });
  refs.songCover.addEventListener("load", () => {
    refs.songCover.classList.remove("is-broken");
  });

  enableBackdropClose(refs.helpDialog);
  enableBackdropClose(refs.exportDialog);
  document.addEventListener("keydown", onKeydown);
  window.addEventListener("hashchange", routeFromHash);
}

function initModifierKeys() {
  const isMac = /Mac|iPhone|iPad|iPod/.test(
    navigator.userAgentData?.platform || navigator.platform || navigator.userAgent,
  );
  const label = isMac ? "⌘" : "Ctrl";
  for (const key of document.querySelectorAll("[data-mod-key]")) {
    key.textContent = label;
  }
  refs.undoButton.title = `撤销 ${label} Z`;
  refs.undoButton.setAttribute(
    "aria-keyshortcuts",
    isMac ? "Meta+Z" : "Control+Z",
  );
}

function init() {
  initStorage();
  initModifierKeys();
  buildFilters();
  bindEvents();
  routeFromHash();
}

init();
