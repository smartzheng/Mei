import test from "node:test";
import assert from "node:assert/strict";

import {
  formatDuration,
  formatTime,
  parseLrc,
  parseLyrics,
  parseSongInput,
} from "../public/lyrics.mjs";

test("parses timestamps, multiple tags, and fills line durations", () => {
  const lines = parseLrc(
    [
      "[ar:测试歌手]",
      "[00:01.50][00:10.005]第一句",
      "[00:04:25]第二句",
      "[00:09.000]",
    ].join("\n"),
  );

  assert.equal(lines.length, 3);
  assert.deepEqual(
    lines.map((line) => [line.startMs, line.text]),
    [
      [1_500, "第一句"],
      [4_250, "第二句"],
      [10_005, "第一句"],
    ],
  );
  assert.equal(lines[0].endMs, 4_250);
  assert.equal(lines[1].endMs, 10_005);
  assert.equal(lines[2].endMs, 15_005);
});

test("marks production credits without confusing lyric text", () => {
  const lines = parseLrc(
    [
      "[00:00.000] 作词 : 周杰伦",
      "[00:01.000] 作曲：周杰伦",
      "[00:02.000] 曲终人散以后",
      "[00:03.000] OP：测试音乐",
      "[00:04.000] 合声编写：周杰伦",
      "[00:05.000] 混音工程：测试录音室",
      "[00:06.000] 母带师：测试母带室",
    ].join("\n"),
  );

  assert.deepEqual(
    lines.map((line) => line.isCredit),
    [true, true, false, true, true, true, true],
  );
});

test("keeps identical lines distinct and stable", () => {
  const lines = parseLrc("[00:01.00]一样\n[00:01.00]一样\n[00:02.00]结束");

  assert.equal(new Set(lines.map((line) => line.key)).size, 3);
  assert.equal(lines[0].key, "1000|一样");
  assert.equal(lines[1].key, "1000|一样|1");
});

test("attaches the nearest translation line", () => {
  const lines = parseLyrics(
    ["[00:10.00]你好", "[00:20.00]再见"].join("\n"),
    ["[00:10.02]Hello", "[00:20.00]Goodbye"].join("\n"),
  );

  assert.equal(lines[0].translation, "Hello");
  assert.equal(lines[1].translation, "Goodbye");
});

test("does not attach translations beyond the matching window", () => {
  const lines = parseLyrics("[00:10.00]你好", "[00:12.00]Hello");
  assert.equal(lines[0].translation, null);
});

test("formats track durations and lyric timestamps", () => {
  assert.equal(formatTime(65_000), "01:05");
  assert.equal(formatTime(3_665_000), "1:01:05");
  assert.equal(formatDuration(269_000), "4:29");
});

test("recognises IDs, song URLs, and titles", () => {
  assert.deepEqual(parseSongInput(" 186016 "), { type: "id", id: "186016" });
  assert.deepEqual(parseSongInput("https://music.163.com/song?id=186016"), {
    type: "id",
    id: "186016",
  });
  assert.deepEqual(parseSongInput("https://music.163.com/#/song?id=186016"), {
    type: "id",
    id: "186016",
  });
  assert.deepEqual(parseSongInput("晴天"), { type: "query", query: "晴天" });
  assert.deepEqual(parseSongInput("   "), { type: "empty" });
});
