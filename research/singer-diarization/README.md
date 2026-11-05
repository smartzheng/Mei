# 歌词行演唱者识别研究

## 目标

输入一首歌的音频和已同步歌词，输出每行的演唱者集合。产品可据此决定靠左、靠右或保持普通排版。第一阶段限定双人歌曲，标签为 `A`、`B`、`A+B`、`unknown`；`A/B` 仅在一首歌内区分歌手，不代表真实姓名或固定左右位置。

先测量**逐行分配正确率**和**可自动排版的覆盖率**。歌词文本识别、逐字同步和歌手真实姓名识别暂不属于第一阶段。

## 第一阶段：20 首歌曲的小规模数据集

1. 先用 2–3 首歌曲打通导入与人工核对，再扩到 20 首包含明显轮唱的双人歌曲。音频和歌词必须是同一版本；现场版、剪辑版、伴奏版另算一首。
2. 每首保留完整音频、同步歌词和来源记录。先人工核对时间偏移，再标注每行 `A`、`B`、`A+B` 或 `unknown`。一句中途换人且无法准确拆分时标为 `unknown`。
3. 留出 5 首作为测试集，从一开始就不依据它们调算法。训练集与测试集按**歌曲**划分；若要评估陌生歌手，还需按歌手组合划分。
4. 先做无需训练的声音特征基线，再决定是否增加歌曲和训练小模型。记录每类标签的数量，避免只有独唱样本。

`data/` 存放本地音频、歌词和标签，已被本目录的 `.gitignore` 排除。不要把受版权保护的原始音频或歌词提交到仓库。

## 数据格式

每首歌有一个稳定的 `song_id`。建议本地使用：

```text
data/
  audio/<song_id>.wav
  lyrics/<song_id>.ttml
  labels/<song_id>.jsonl
  songs.jsonl
```

`songs.jsonl` 每行是一首歌，记录 `song_id`、`audio_path`、`lyric_path`、`audio_version`、`lyric_source`、`split`（`train`/`test`）以及时间偏移 `offset_ms`。路径相对于本研究目录。`offset_ms` 是“音频时间减歌词时间”，核对后填写。

`labels/<song_id>.jsonl` 每行是一条歌词：

```json
{"song_id":"demo-duet","line_index":0,"start_ms":1000,"end_ms":3000,"text":"示例歌词","agent_ids":["A"],"label":"A","label_source":"manual","reviewed":true}
```

- 时间使用毫秒，且 `start_ms < end_ms`。保存原始歌词时间；分析音频时再加 `offset_ms`。
- `agent_ids` 保存来源中的原始歌手标识；`agent_type` 保存 TTML 声明的 agent 类型。`label` 是人工核对后的研究标签。`A+B` 表示两人同时唱这一行；无法判断填 `unknown`，不猜测。
- `label_source` 可为 `manual`、`ttml` 或 `lrc_role`。从现有歌词导入的标记都先设 `reviewed=false`，听音频核对后再改为 `true`。只用已核对的行计算测试指标。

## 从 TTML 导入候选标签

```bash
python3 tools/import_ttml.py --song-id demo-duet --input data/lyrics/demo-duet.ttml --output data/labels/demo-duet.jsonl
```

脚本提取主歌词行时间、文本和 `ttm:agent`，跳过翻译、音译及 `x-bg` 背景人声，**不自动把 agent 映射为 A/B**。TTML 中的 agent 可能表示合唱组，也可能缺失；背景人声还可能影响 `A+B` 标签，必须结合音频核对。

## 从 NCM 歌词库筛选对唱候选

### 格式里的对唱信息

同一份歌词在 `ncm-lyrics/` 下会生成多种格式，但只有一部分格式保留“谁在唱”的信息：

| 格式 | 是否保留对唱信息 | 具体位置 |
|---|---|---|
| `.ttml` | 是，主来源 | 每个 `<p>` 的 `ttm:agent`，以及文件头部的 `<ttm:agent type="person/other/group" ...>` |
| `.lys` | 是，派生确认 | 每行开头 `[property]`；1/2/4/5/7/8 表示对唱视图左/右，6/7/8 表示背景人声 |
| `.eslrc` | 否 | 只有逐字时间戳和文本，没有歌手字段 |
| `.lrc` / `.yrc` / `.qrc` | 否 | 只有整行或逐字时间戳和文本 |

LYS 的属性表：

| property | 背景人声 | 对唱视图 |
|---|---|---|
| 0 | 未设置 | 未设置 |
| 1 | 未设置 | 左 |
| 2 | 未设置 | 右 |
| 3 | 否 | 未设置 |
| 4 | 否 | 左 |
| 5 | 否 | 右 |
| 6 | 是 | 未设置 |
| 7 | 是 | 左 |
| 8 | 是 | 右 |

LYS 属性定义参考 [Lyricify 歌词指南](https://docs.lyricify.app/lyrics/guide/)；ESLRC 的格式只描述逐字时间，参考 [amll-lyric 的 eslrc 说明](https://docs.rs/amll-lyric/0.2.4/amll_lyric/eslrc/index.html)。

例如 `108468`（小酒窝）的 TTML 声明了 `v1=person`、`v2=other`，LYS 前四行是 `[0]`，到第二遍主歌变成 `[2]`；同目录的 `.eslrc` 只有 `[00:30.751]幸[00:31.037]福...`，无法区分这是谁唱的。`101818`（一念执着，胡歌/阿兰）也是同样的结构。

因此：**在目前这个歌词库里，能用于自动筛选的是 TTML 和 LYS；ESLRC 只能用于取逐字时间轴。** LYS 的 `[2]` 与 TTML 的第二个 agent 在抽样文件里逐行对齐（时间差为 0）。这个库中 LYS 只有 0/2/6/8 四种属性。

### 筛选脚本

```bash
python3 tools/filter_ncm_duets.py --no-write  # 只看统计
python3 tools/filter_ncm_duets.py             # 写 ID、JSONL 和预览表
```

默认扫描 `/home/ljyh/code/temp/amll-ttml-db-main/ncm-lyrics`，输出到 `data/candidates/ncm_duets/`：

- `ids_broad.txt` / `ids_confirmed.txt` / `ids_balanced.txt` / `ids_strict.txt`
- `ids_confirmed_lys.txt`（TTML + LYS 双重证据） / `ids_confirmed_ttml_only.txt`（未生成 LYS，仅 TTML）
- `ids_broad_unique.txt` / `ids_balanced_unique.txt` / `ids_strict_unique.txt`（按歌词内容去重后的代表 ID）
- `report.jsonl`（全部 broad 候选）
- `confirmed_report.jsonl`、`balanced_report.jsonl`、`strict_report.jsonl`
- `preview.tsv`（适合直接打开人工扫一眼）
- `summary.json`
- `lys_unmatched.jsonl`（LYS 有对唱但未进入 broad 的异常项及原因）

脚本不复制歌词文件，只在报告里保留绝对路径。可用 `--input-dir`、`--output-dir` 和 `--max-extra-ratio` 调整。

### 口径

- **broad**：TTML 中至少有两位 `person`/`other` 歌手，取行数最多的两位；每人至少 1 行，两位合计至少占全部演唱行的 80%，且至少切换 1 次。
- **confirmed**：broad 且有 LYS 对唱属性；未生成 LYS 的 TTML 项也保留，但标记 `confidence=ttml_only`。只想取双重证据时用 `ids_confirmed_lys.txt`。
- **balanced**：confirmed，且两位各至少 5 行、切换至少 5 次、两位合计至少 80%。
- **strict**：confirmed，且两位各至少 8 行、切换至少 5 次、两位合计至少 90%，TTML 中没有 `group` agent 行。

`type="other"` 在本库中经常表示第二演唱者，不能像旧脚本那样直接排除；`group` 和 `organization` 不算独立歌手。`musicName`/`artists` 元数据只作展示，不作为对唱判据（它可能列别名，也可能漏掉客串歌手）。

### 当前快照结果（2026-11-05）

```text
TTML 文件                      3,585
LYS / ESLRC 文件                3,356 / 3,356
仅 TTML、没有 LYS                  229
broad                          1,684（去重 1,122）
confirmed                      1,684（去重 1,122）
balanced                       1,103（去重 719）
strict                         1,000（去重 639）
```

- broad/confirmed 中有 `1,591` 个同时有 TTML 和 LYS 证据，`93` 个只有 TTML（未生成 LYS）；没有出现“有 LYS、但 TTML 两位歌手不一致”的 broad 项。
- 同一份歌词可能对应多个网易云 ID；报告里的 `content_hash` 和 `same_content_ids` 可用于合并。去重后 broad 从 1,684 降到 1,122，strict 从 1,000 降到 639。
- LYS 有对唱属性的文件有 `1,605` 个：`1,591` 个进入 confirmed；`8` 个是 RAD DOGS、Bad Apple!! 等多人/合唱歌曲，主两位合计不足 80%；`5` 个是《经过》《昔涟》等单人伴奏/和声伴奏版本；另有 1 个 ID 0 无效。后两类和无效项都排除，详情见 `lys_unmatched.jsonl`。
- 旧脚本 `find_ncm_duets.py` 只数 `type="person"`，会漏掉 `other` 表示的第二演唱者；它给出的 71 个候选不能当作全量对唱集合。之前的 20 首人工听音结果仍然有效，但候选池应改为使用本节的输出。
- 文件 `0.ttml` 的 ID 不是有效的网易云歌曲 ID，已排除。

### 抽样核对

| ID | 歌曲 | 歌手 | 前两位行数 | 切换 | 证据 | 结果 |
|---|---|---|---|---:|---|---|
| `101818` | 一念执着 | 胡歌 / 阿兰 | 38 / 8 | 4 | TTML + LYS `[0]/[2]` | 清晰对唱 |
| `108468` | 小酒窝 | 林俊杰 / 蔡卓妍 | 30 / 12 | 8 | TTML + LYS `[0]/[2]` | strict |
| `112678` | 铁血丹心 | 罗文 / 甄妮 | 20 / 14 | 24 | TTML + LYS `[0]/[2]` | strict |
| `108406` | Go! | MC HotDog / 林俊杰 | 70 / 16 | 4 | TTML + LYS `[0]/[2]/[6]` | 有 1 行背景人声 |
| `37155` | Ring Ring Ring | S.H.E | 65 / 4 | 2 | TTML + LYS `[0]/[2]` | confirmed，第二人太少 |
| `101126` | 第三十八年夏至 | 河图 | 45 / 5 | 1 | TTML + LYS `[0]/[2]` | confirmed，未达 balanced |
| `307081` | 表白 | 萧亚轩 | 54 / 4（另 1 行 other） | 6 | 仅 TTML | confirmed，无 LYS |
| `852226` | 現夢 -genmu- | みぃ / yukina / TUMENECO | 29 / 22（另 8 行 other） | 29 | 仅 TTML | confirmed，多一个歌手/声部 |
| `2631336275` | 经过（伴奏 男调中文伴唱） | HOYO-MiX | 9（仅一人） | 0 | 仅 LYS `[2]` | 排除：不是双人对唱 |

这些只是抽样，用于说明筛选口径；是否真的是两个可区分的人类歌手，仍需要听音频和人工核对。`balanced`/`strict` 只是按歌词标注结构提高置信度，不能代替听感。

### 取回音频

取回音频前，把只含 cookie 值的 `MUSIC_U` 放进环境变量，再给脚本 api-enhanced 的本地地址。脚本默认仅处理前 20 个 ID，使用 `exhigh`，不请求解锁；签名音频 URL 不写入文件，也不会把 cookie 转发给音乐 CDN。

```bash
export MUSIC_U='你的MUSIC_U值'
python3 tools/fetch_ncm_audio.py --api-base http://127.0.0.1:3000 --limit 20
```

也可用 `--level standard` 降低文件体积，或显式添加 `--unblock`。音频保存到 `data/audio/`，歌曲详情和下载状态记录在 `data/candidates/downloads.jsonl`。无可用音频 URL 的歌曲会保留在清单中并标为 `no_audio_url`。

接口可能返回 30/45 秒试听片段，下载成功不等于拿到整首歌。用下面的命令将音频时长与 TTML 最后一行时间比较，只把覆盖率达到 90% 的样本先列为完整音频。该检查需要安装 FFmpeg（含 `ffprobe`）。

```bash
python3 tools/check_audio_coverage.py
```

### 当前第一批结果

20 个下载请求里有 19 个返回了音频文件，但完整度检查发现只有 6 个文件覆盖了整首歌，13 个是 30/45 秒试听或不匹配版本，另有 1 个没有音频 URL。`downloads.jsonl` 里的 `downloaded` 只表示拿到了文件，不表示它是完整歌曲。完整候选 ID：`307081`、`852226`、`494865824`、`524148352`、`545728377`、`1300598277`。

人工试听确认后，首批 6 首完整音频中有 4 首可用：`494865824`（Havana）、`852226`（現夢 -genmu-）、`307081`（表白）和 `545728377`（Chinatown Blues）；`524148352`（一口）几乎是同一人演唱，`1300598277`（The Mob）音色不适合当前研究，已排除。决定记录在 `data/candidates/manual_audio_review.jsonl`。

这 4 首目前共导入 234 行歌词，其中 225 行为已核对的 `A/B` 标签、9 行保留为 `unknown`：Havana 为 A 42 / B 28；現夢为 A 22 / B 29，另有 8 行 `type="other"`；表白为 A 54 / B 4，另有 1 行无可用 person agent；Chinatown Blues 为 A 26 / B 20。表白的网易云详情只列一位歌手，但 TTML 标有两个 person agent；因此保留用户试听确认的歌词角色，歌手身份只用歌曲内部 A/B 表示。每首的映射不跨歌曲复用。

**与旧候选池的关系：** 上述 20 首来自早期只统计 `type="person"` 的列表，因此仍有参考价值，但不再代表全量候选。全量池请使用 `ncm_duets` 输出；下一步可以优先从 `balanced`/`strict` 中按音频完整度和听感复核。

## 可参考的数据来源

- [Suda 等人 2022 年论文的数据页](https://www.gavo.t.u-tokyo.ac.jp/~hitoshi/diarization/)有歌手时间段标注，但没有可下载的歌曲音频，因此不能单独构成可训练样本。
- [FruitsMusic](https://huggingface.co/datasets/fruits-music/fruits-music)有“谁在何时唱什么”的标注。它有专门的[许可条款](https://huggingface.co/datasets/fruits-music/fruits-music/blob/main/LICENSE.md)及下载确认流程；使用前先核对条款、音频取得方式和与目标歌曲的差异。它以多人日语偶像歌曲为主，可用于研究参考，不能替代双人歌曲测试集。
- [IdolSongsJp](https://huggingface.co/datasets/imprt/idol-songs-jp)含多轨音频；其[许可条款](https://huggingface.co/datasets/imprt/idol-songs-jp/blob/main/LICENSE.md)区分研究与产品使用，并限制器乐信号用于模型训练。暂不自动下载或并入本项目数据。

目前从本机 TTML 库筛出的歌词仍由其原始库提供；研究目录中的音频、导入标签和试听筛选记录被 `.gitignore` 排除，不应提交到仓库。后续优先处理剩余候选中按歌词内容去重后的歌曲，并按完整音频和实际听感筛选。
