# AstraHomework

班级白板作业助手：把白板 PDF 交给自建的 [AstrBot](https://github.com/AstrBotDevs/AstrBot) 服务分析，由服务端识别作业页并推送总结到班级群。本仓库是 Android 客户端，服务端插件见 [astrbot_plugin_astrahomework](https://github.com/HwlloChen/astrbot_plugin_astrahomework)。

**WARN: 这是一个几乎完全由AI编写的项目，代码质量堪忧，但功能经过完整测试**

## 功能

- 选择一个本地 PDF 文件夹，自动列出其中的白板作业文件并标记新文件。
- 上传后在服务端排队分析，客户端轮询显示真实的分页进度。
- 作业文字、总结、作业图片、原始 PDF 可按群单独开关推送。
- 关于页展示版本信息，并从 GitHub Releases 检查更新、直接下载安装包。
- Material 3 Expressive 组件（宽导航栏、短导航栏、大按钮）+ 动态取色。

## 隐私与安全

- 应用不收集任何个人数据，没有统计、广告或第三方 SDK。
- 只保存你填写的服务器地址、访问令牌和文件夹授权；令牌使用 AndroidX DataStore 保存在应用私有目录。
- 只允许 HTTPS 服务器地址；`http://localhost`、`http://127.0.0.1`、`http://[::1]` 仅用于本机开发。
- 上传使用 `HttpURLConnection` 流式写入，不缓冲整份 PDF，也不跟随重定向，避免把令牌发给其他主机。
- 更新检查只访问 `api.github.com`，携带固定 User-Agent，不发送任何令牌。
- 默认服务器地址不写入仓库：在本地 `local.properties` 中添加 `homework.defaultUrl=https://your-host` 即可，该文件已被忽略。

## 构建

要求 JDK 17+（CI 使用 21）与 Android SDK 37。

```bash
./gradlew :app:testDebugUnitTest   # 单元测试
./gradlew :app:assembleDebug       # 调试包
./gradlew :app:assembleRelease     # 未签名正式包（无密钥时）
```

没有签名密钥时 `assembleRelease` 产出标准的 `app-release-unsigned.apk`，可以验证混淆与优化结果，但不能安装发布。

### 发布签名

签名只在 `Release` 工作流中使用仓库 Secrets 完成，CI 从不持有密钥：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | keystore 文件的 Base64 内容 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

四个变量需全部提供：本地只设置其中一部分会直接构建失败，而不会静默降级为 debug 签名。`release.yml` 在缺少任一 Secret 时会在构建前报错退出。

生成 keystore 并在本地签名构建：

```bash
keytool -genkeypair -v -keystore release.keystore -alias astra \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.keystore   # 填入 KEYSTORE_BASE64

export KEYSTORE_FILE=$PWD/release.keystore KEYSTORE_PASSWORD=... \
       KEY_ALIAS=astra KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

`release.keystore` 已被 `.gitignore` 忽略；请离线备份，密钥丢失后已安装的用户无法升级到新版本。

### 发布流程

1. 更新 `app/build.gradle.kts` 中的 `versionCode` 与 `versionName`。
2. 提交并推送。
3. 打标签并推送：`git tag v1.3 && git push origin v1.3`。
4. `Release` 工作流会校验签名证书、构建 APK，并以 `AstraHomework-<version>.apk` 附加到 GitHub Release。应用内的更新检查读取该 Release 的 `tag_name` 与 APK 资产。

## 工作流

- `ci.yml`（Build）：推送与 PR 时运行 Lint、单元测试，并构建 debug 与未签名 release 两种 APK；两者都作为 Artifacts 可供下载，同时上传测试与 Lint 报告。
- `release.yml`（Release）：在 `v*` 标签或手动触发时使用 Secrets 签名，`apksigner` 验证证书后发布。

## 目录结构

```
app/src/main/java/com/etaris/astrahomework/
├── MainActivity.kt        入口与文件夹选择
├── AppViewModel.kt        状态机、上传、轮询、更新检查
├── HomeworkScreens.kt     首页、设置页与导航
├── AboutScreen.kt         关于页与更新卡片
├── UpdateChecker.kt       GitHub Release 检查与版本比较
├── HomeworkRepository.kt  扫描、上传、任务查询
├── HomeworkStorage.kt     DataStore 设置与本地历史
└── ui/theme/              Material 3 主题与形状
```

## 许可

见 [LICENSE](LICENSE)。
