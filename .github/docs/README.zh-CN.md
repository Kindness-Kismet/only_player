<div align="center">

# Only Player

[![English](https://img.shields.io/badge/English-red?style=flat-square)](../../README.md)
&nbsp;
[![简体中文](https://img.shields.io/badge/简体中文-blue?style=flat-square)](README.zh-CN.md)

<br>

[![Android 11+](https://img.shields.io/badge/Android-11+-34A853?logo=android&logoColor=white&style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white&style=flat-square)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?logo=jetpackcompose&logoColor=white&style=flat-square)](https://developer.android.com/compose)
[![Media3](https://img.shields.io/badge/Media3-FF6F00?logo=android&logoColor=white&style=flat-square)](https://developer.android.com/media/media3)

</div>

<br>

Only Player 是 Android 本地视频播放器，基于 Kotlin、Jetpack Compose、Hilt 和 Media3 / ExoPlayer 构建。

支持按文件夹浏览视频、断点续播、手势操控和 ASS 字幕特效，也能在应用内切换语言、备份和恢复设置，换设备时可以继续使用习惯的配置。

<br>

---

## 导航

- [安装](#-安装)
- [快速上手](#-快速上手)
- [常见问题](#-常见问题)
- [开发指南](#-开发指南)
  - [前置依赖](#前置依赖)
  - [架构](#架构)
  - [编码规范](#编码规范)
  - [编译与验证](#编译与验证)
  - [发布通道](#发布通道)
- [贡献规范](#-贡献规范)
- [许可证](#-许可证)
- [致谢](#-致谢)
- [支持项目](#-支持项目)

<br>

---

## 📦 安装

<sub>[↑ 回到导航](#导航)</sub>

### 下载

从 **[发布页面](https://github.com/Kindness-Kismet/only_player/releases/latest)** 下载最新正式版。开发版在 [全部版本](https://github.com/Kindness-Kismet/only_player/releases) 中标记为预发布，正式版历史变更见 [更新日志](../CHANGELOG.md)。

| 设备架构 | 安装包 |
|---|---|
| ARM64 手机、平板 | `Only-Player-arm64-v8a-<version>.apk` |
| x86-64 设备、模拟器 | `Only-Player-x86_64-<version>.apk` |

### 系统要求

- Android **11 及以上**（API 30+）。
- 64 位 ARM 或 x86 处理器，安装包需与设备架构对应。

打开下载的安装包，按系统提示安装。如果系统拦截安装，按提示允许当前浏览器或文件管理器安装应用。

<br>

---

## 🚀 快速上手

<sub>[↑ 回到导航](#导航)</sub>

1. 打开应用，按提示授予媒体访问权限。
2. 浏览媒体库或搜索视频，在快捷设置中调整视图、布局和排序。
3. 点击视频开始播放，也可以在其他应用中选择用 Only Player 打开视频。

### 常用功能

| 分类 | 支持内容 |
|---|---|
| 媒体库 | 文件夹、树状目录、视频视图；列表与网格布局；按标题、时长、大小、日期排序；排除文件夹，按需扫描 `.nomedia` 目录 |
| 播放 | 断点续播、同目录自动连播、画中画、后台播放、倍速、缩放、画面比例，以及单文件音轨和字幕轨记忆 |
| 字幕与音频 | 内嵌与外挂轨道、ASS 特效、首选语言、解码器偏好，以及字幕字体、字号、粗体、背景、延迟、速度和编码设置 |
| 外观 | 亮色与暗色主题、Android 12 及以上动态取色、应用内语言切换、缩略图设置 |
| 设置 | 自定义手势、播放器控件和屏幕方向；导出、导入或重置应用与播放器设置 |

### 播放手势

| 手势 | 操作 |
|---|---|
| 横向滑动 | 快进或快退 |
| 纵向滑动 | 调节亮度或音量 |
| 双击 | 执行已配置的跳转动作 |
| 双指缩放 | 启用缩放手势后调整画面大小 |

播放速度、长按倍速、画中画和后台播放均可在设置中调整。

### 使用字幕

在播放器内打开字幕选择，切换内嵌字幕轨或加载外挂字幕文件。ASS 字幕支持特效渲染，外观、编码和时间偏移可在 **设置 → 字幕** 中调整。

### 备份设置

打开 **设置 → 常规 → 备份设置**，将应用与播放器设置保存为文件。需要时用 **恢复设置** 导入到当前设备或另一台设备，也可以在同一页面重置设置。

<br>

---

## ❓ 常见问题

<sub>[↑ 回到导航](#导航)</sub>

### 媒体库里少了视频？

先检查媒体访问权限和已排除的文件夹，再刷新媒体库。包含 `.nomedia` 文件的目录及其子目录默认不显示；需要显示时，在设置中启用 **忽略 .nomedia 文件**，并按提示授予所有文件访问权限。

### 视频无法播放？

确认文件仍然存在，应用也有访问权限。如果能打开文件但画面或声音异常，再检查播放器设置中的解码器偏好。

### 字幕乱码、不同步或样式不对？

先确认选中了正确的字幕轨或文件。乱码时检查文字编码，不同步时调整字幕延迟或速度，ASS 样式异常时检查字幕样式设置。

<br>

---

## 🛠 开发指南

<sub>[↑ 回到导航](#导航)</sub>

### 前置依赖

| 工具 | 要求 |
|---|---|
| JDK | `26`，`scripts/prebuild.py` 会下载便携版到 `build/jdk/` |
| Android Studio 或 SDK 命令行工具 | Android SDK Platform `37` |
| Python | 构建脚本需要 `3.10+` |
| Gradle | 使用仓库自带的包装脚本 |

```bash
git clone https://github.com/Kindness-Kismet/only_player.git
cd only_player
python scripts/prebuild.py
```

`prebuild.py` 会补齐缺失的 Gradle 包装脚本文件，在没有便携 JDK 时下载 Temurin JDK 26 到 `build/jdk/`，并确认能找到 Android SDK。之后 `scripts/build.py` 会把这个 JDK 传给 Gradle，不需要在系统里安装 JDK。

| 参数 | 作用 |
|---|---|
| `--force` | 替换已有的便携 JDK |
| `--verbose` | 打印下载地址和探测到的 SDK 路径 |

用 Android Studio 打开项目，将 Gradle 使用的 JDK 设为 26，然后同步项目。命令行构建时，通过 `local.properties` 中的 `sdk.dir` 或 `ANDROID_HOME` 配置 Android SDK 路径。

### 架构

采用多模块分层架构，遵循单向数据流。

```text
app/                  应用、Activity、导航与 Manifest
core/common/          日志、调度器与通用工具
core/model/           纯 Kotlin 模型与路径类型
core/database/        Room 数据库、DAO 与表结构
core/datastore/       DataStore 数据源与序列化
core/data/            Repository 接口、实现与数据映射
core/domain/          用例
core/media/           媒体扫描与同步
core/ui/              共享 Compose 组件、字符串与主题
feature/player/       播放器界面、播放服务与播放流程
feature/settings/     设置页面与偏好逻辑
feature/videopicker/  媒体库、搜索与快捷设置
scripts/              构建环境准备与安装包构建脚本
.github/workflows/    检查与发布工作流
```

`app` 负责应用组装，功能模块依赖 `core` 模块，通过回调通信，彼此不直接依赖。`core/model` 不依赖 Android，也不依赖其他核心模块。

建议从 [MainActivity](../../app/src/main/java/one/only/player/MainActivity.kt) 开始阅读，再沿具体功能进入用例和数据层。共享文案位于 `core/ui/src/main/res`。

### 编码规范

- 文件读写等操作放在仓库层或功能内部的辅助类中，业务逻辑放在用例中，页面状态由 ViewModel 管理。
- 使用不可变界面状态和事件、`Flow` / `StateFlow`、Hilt 注入，以及注入的调度器。
- 外部存储的媒体路径使用 `StoragePath`，比较时归一，访问文件和展示时使用 `value` 保留真实写法。
- 使用显式导入、多行列表尾随逗号和卫语句，减少控制流嵌套。
- 所有可交互的 Compose 控件都要提供稳定的 `Modifier.testTag()` 或 `contentDescription`，供调试指令和界面自动化定位。

完整架构和编码要求见 [AGENTS.md](../../AGENTS.md)。

### 编译与验证

**构建调试版安装包：**

```bash
python scripts/build.py build-apk --abi arm64-v8a --build-type debug
```

先执行 `python scripts/prebuild.py`；缺少便携 JDK 时构建脚本会直接报错退出。

| 参数 | 可选值与作用 |
|---|---|
| `--abi` | `arm64-v8a` 或 `x86_64`；省略时构建两种架构 |
| `--build-type` | `debug`、`release` 或 `release-with-debug-signing`；默认 `release` |
| `--verbose` | 显示详细的 Gradle 输出 |

脚本将安装包保存到 `build/apk/`：

- 调试版：`Only-Player-debug-<abi>-<version>.apk`
- 发布版：`Only-Player-<abi>-<version>.apk`

`release` 构建需要配置签名才能生成可安装的包。本地需要验证发布版优化效果时，可用 `--build-type release-with-debug-signing` 生成使用调试密钥签名的安装包。

**代码改动后格式化并检查：**

```bash
./gradlew ktlintFormat ktlintCheck
```

**涉及构建或应用行为时验证：**

```bash
./gradlew ktlintCheck test assembleDebug --warning-mode=fail
```

除了命令结果，还要检查各模块 `build/reports/tests/` 下的测试报告。涉及播放行为时，在真机或模拟器上验证实际播放；需要运行设备测试时，使用 `./gradlew connectedAndroidTest`。

纯文档改动不需要格式化、运行测试或构建安装包。

### 发布通道

| 通道 | 分支 | 工作流 |
|---|---|---|
| 开发版 | `dev` | [检查工作流](../workflows/test.yaml) 检测到产品改动且检查通过后，可触发 [开发版发布](../workflows/publish-dev.yaml)；也支持手动运行开发版发布 |
| 正式版 | `main` | `app/build.gradle.kts` 中的 `versionName` 变化时触发 [正式版发布](../workflows/publish.yaml)，也可在 `main` 上手动运行 |

发布正式版时，先在 `dev` 更新版本号和 [更新日志](../CHANGELOG.md)，再将 `dev` 合并到 `main`。发布工作流会先验证签名配置，再构建已签名安装包；发布失败时，先查看签名验证步骤的输出。

<br>

---

## 📋 贡献规范

<sub>[↑ 回到导航](#导航)</sub>

功能和修复的合并请求以 **`dev`** 为目标分支。只有晋升正式版时，才将 **`dev` 合并到 `main`**。

| 检查项 | 要求 |
|---|---|
| 改动范围 | 每次提交只包含当前改动相关的内容 |
| 架构 | 遵守模块边界和 [AGENTS.md](../../AGENTS.md) 中的规范 |
| 界面控件 | 新增可交互控件需提供稳定的测试标签或内容描述 |
| 验证 | 按改动范围执行检查，如实说明结果 |
| 文档 | 修改共同信息时，同步维护中英文说明文档 |

<br>

---

## 📄 许可证

<sub>[↑ 回到导航](#导航)</sub>

Only Player 使用 [GNU GPL v3 许可证](../../LICENSE)。第三方组件仍遵循各自的许可证。

<br>

---

## 🤝 致谢

<sub>[↑ 回到导航](#导航)</sub>

Only Player 延续自 [Next Player](https://github.com/anilbeesetti/nextplayer)。感谢原项目及所有上游贡献者提供的基础能力与持续维护。

<br>

---

## 💜 支持项目

<sub>[↑ 回到导航](#导航)</sub>

如果 Only Player 对你有帮助，欢迎通过爱发电支持项目开发和日常模型调用费用。捐赠完全自愿，不会影响任何功能使用。

<div align="center">

<a href="https://github.com/KiritoXDone">
  <img src="https://avatars.githubusercontent.com/u/58523182?v=4&amp;s=192" width="96" height="96" alt="KiritoXDone">
</a>

**[KiritoXDone](https://github.com/KiritoXDone)**

**[通过爱发电支持](https://afdian.com/a/KiritoXD)**

</div>
