# Beta 测试包构建

合并到默认分支后，在 GitHub **Actions → Beta Build → Run workflow** 中选择要构建的分支（通常为 `main`），手动启动构建。

完成后，在该次运行的 **Artifacts** 下载 `Mei_beta_<运行序号>_<提交短号>`。压缩包包含以下签名 APK 及 SHA-256 校验文件，保留 14 天：

- `Mei_beta_<运行序号>_<提交短号>_arm64-v8a.apk`：64 位 ARM 手机、平板。

每个 APK 仅包含对应架构的原生 `.so` 库，不生成 32 位或通用 APK。构建后会检查包内的实际原生库架构，防止混包或漏包。

工作流仅构建测试 APK 并上传 Actions 产物，没有发布或创建标签步骤，不会在合并或推送时自动运行。

构建沿用现有 Rust USB 检查、项目 Gradle Wrapper 和签名配置，使用 Secrets：`SIGNING_KEY`、`ALIAS`、`KEY_STORE_PASSWORD`、`KEY_PASSWORD`。APK 使用应用自身版本号和包名。

正式发布工作流同样构建 arm64-v8a APK，直接上传该 APK 到 GitHub Release，使用方式见 [正式发布构建](release-build.md)。

本地构建相同分包时执行：

```bash
./gradlew -PmeiAbiSplits=true :app:assembleRelease
```

Beta Build 与正式 Release 工作流都显式开启该选项；普通本地构建默认仍输出原有通用 APK。两套工作流共用 `tools/ci/prepare-apks.py` 校验签名和实际原生库架构。
