# 对唱标注台

给一首网易云歌曲，取回 LRC 歌词，逐行标注男声、女声、合唱或跳过。标注自动保存在本机，可导出 JSON / JSONL。

这是一个本地网页工具，不包含音频播放。运行时需要 Node.js 20 或更高版本，不需要安装依赖。

## 启动

```bash
cd tools/duet-annotator
npm start
```

打开 <http://127.0.0.1:4173>。

默认只监听本机。可用环境变量修改：

```bash
PORT=8080 HOST=127.0.0.1 npm start
```

测试：

```bash
npm test
```

## 使用

1. 输入歌曲名、网易云 ID 或网易云歌曲链接。直接输入 ID 或链接最快，也不会受搜索翻唱结果影响。
2. 从搜索结果里选一首歌，等待歌词加载。
3. 点击每行右侧的「男 / 女 / 合 / 跳」，或用键盘给当前行标注：
   - `1` 男声
   - `2` 女声
   - `3` 合唱
   - `0` 跳过
   - `↑` / `↓` 切换当前行
   - `Backspace` 清除当前行标签
   - `Ctrl+Z` / `⌘Z` 撤销
4. 顶部筛选可以只看未标注、男声、女声或合唱；进度和分声部数量在右侧栏（手机上在歌曲标题下方）。
5. 「导出标注」可下载 JSON 或 JSONL。

在当前页面按键盘 `?` 也会打开快捷键说明。

## 歌词处理

- 支持 `[mm:ss.xx]`、`[mm:ss.xxx]`、`[mm:ss:xx]` 以及一行多个时间戳的 LRC。
- 同时请求网易云翻译歌词，并按时间近似配对。
- 自动识别常见制作信息行（作词、作曲、编曲、制作人、录音/混音/母带、和声编写等），默认折叠。作者、标题行也会尽量识别为制作信息。
- 制作信息不参与标注和导出；需要核对时点歌词列表上方的「显示」。
- 没有歌词、纯音乐、版权受限或接口失败时，会显示对应的空状态和重试入口。

标注状态保存在浏览器 `localStorage`：

- `duet-annotator:labels:v1`：按歌曲 ID 保存每行标签。
- `duet-annotator:recent:v1`：最近标注的歌曲。

清除浏览器站点数据会丢失本机标注，重要数据请导出备份。

## 导出格式

标签值：

| 值 | 含义 |
|---|---|
| `male` | 男声 |
| `female` | 女声 |
| `both` | 合唱 |
| `skip` | 跳过（间奏、念白等不参与判断的行） |
| `null` | 未标注 |

JSON 包含歌曲信息、导出时间、标签图例和完整行数据。JSONL 每行一句，字段如下：

```json
{"song_id":"2668397359","song_name":"晴天 (原唱 周杰伦)","line_index":0,"start_ms":29260,"end_ms":32710,"text":"故事的小黄花","translation":null,"label":"male","reviewed":true,"label_source":"manual"}
```

- `line_index` 从 0 开始，按导出歌词行计数；制作信息不计入。
- `start_ms` / `end_ms` 来自 LRC 时间轴。
- `reviewed` 为 `true` 表示已有标签；未标注行为 `false`，`label` 和 `label_source` 为 `null`。
- 导出文件不包含音频，也不包含制作信息行。

## 网易云接口

页面通过同源的本地代理请求网易云，避免浏览器 CORS 限制。代理只开放以下接口：

```text
GET /api/health
GET /api/search?q=...
GET /api/song/:id
GET /api/lyrics/:id
```

上游使用：

- `/api/cloudsearch/pc`（失败时回退 `/api/search/get/web`）
- `/api/song/detail`
- `/api/song/lyric?lv=1&kv=1&tv=-1`

结果在内存中缓存 30 分钟。服务默认绑定 `127.0.0.1`，不要在不信任的网络里改成 `0.0.0.0`，因为代理会带上网易云 Cookie。

部分歌曲需要登录态才能返回歌词。可以只提供 `MUSIC_U` 的值，或提供完整 Cookie：

```bash
NCM_COOKIE='你的 MUSIC_U 值' npm start
# 或
NCM_COOKIE='MUSIC_U=...; 其他Cookie=...' npm start
```

Cookie 只会发送给 `music.163.com`，不会写入源码或导出文件。不要把 Cookie 提交到仓库。

## 已知限制

- 不播放音频。标注时需要另外播放歌曲；工具只负责歌词、时间轴和标签。
- 搜索使用网易云公开搜索结果，可能以翻唱、现场版或钢琴版排在前面；认准歌手、专辑，或直接输入 ID。
- 匿名接口可能对部分歌曲返回空歌词或受限结果；这不是本工具能绕过的。
- 制作信息识别是启发式规则。未识别的信息行会按普通歌词显示，可以直接标为跳过；制作信息行本身不会导出。
- LRC 本身通常不声明每行由谁演唱，声部标签全部来自人工判断。
- 本机保存依赖浏览器 `localStorage`；无痕模式、清理站点数据或换浏览器都会丢失标注。

## 文件

```text
tools/duet-annotator/
├── server.mjs               # 静态文件服务 + 网易云同源代理
├── package.json
├── public/
│   ├── index.html
│   ├── styles.css
│   ├── app.js               # 页面状态、标注、存取、导出
│   ├── lyrics.mjs           # LRC 解析、翻译配对、输入识别
│   └── favicon.svg
└── test/
    └── lyrics.test.mjs
```
