<div align="center">

# Only Player

[![English](https://img.shields.io/badge/English-red?style=flat-square)](README.md)
&nbsp;
[![简体中文](https://img.shields.io/badge/简体中文-blue?style=flat-square)](.github/docs/README.zh-CN.md)

<br>

[![Android 11+](https://img.shields.io/badge/Android-11+-34A853?logo=android&logoColor=white&style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white&style=flat-square)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?logo=jetpackcompose&logoColor=white&style=flat-square)](https://developer.android.com/compose)
[![Media3](https://img.shields.io/badge/Media3-FF6F00?logo=android&logoColor=white&style=flat-square)](https://developer.android.com/media/media3)

</div>

<br>

Only Player is an Android video player for local media, built with Kotlin, Jetpack Compose, Hilt, and Media3 / ExoPlayer.

Browse videos by folder, resume playback, use gesture controls, and render ASS subtitle effects. In-app language switching and settings backup make it easy to keep your preferred setup across devices.

<br>

---

## Navigation

- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [FAQ](#-faq)
- [Development Guide](#-development-guide)
  - [Prerequisites](#prerequisites)
  - [Architecture](#architecture)
  - [Coding Conventions](#coding-conventions)
  - [Build and Validation](#build-and-validation)
  - [Release Channels](#release-channels)
- [Contribution Guidelines](#-contribution-guidelines)
- [License](#-license)
- [Acknowledgements](#-acknowledgements)

<br>

---

## 📦 Installation

<sub>[↑ Back to Navigation](#navigation)</sub>

### Download

Download the latest stable APK from the **[Releases page](https://github.com/Kindness-Kismet/only_player/releases/latest)**. Development builds are listed as pre-releases under [all releases](https://github.com/Kindness-Kismet/only_player/releases). See the [changelog](.github/CHANGELOG.md) for stable version history.

| Device architecture | APK |
|---|---|
| ARM64 phones and tablets | `Only-Player-arm64-v8a-<version>.apk` |
| x86-64 devices and emulators | `Only-Player-x86_64-<version>.apk` |

### System Requirements

- Android **11 or later** (API 30+).
- A 64-bit ARM or x86 processor matching the APK you download.

Open the downloaded APK and follow Android's installation prompts. Allow installation from your browser or file manager if prompted.

<br>

---

## 🚀 Quick Start

<sub>[↑ Back to Navigation](#navigation)</sub>

1. Open the app and grant media access when prompted.
2. Browse your library or use search to find a video. Quick settings let you change the view, layout, and sorting.
3. Tap a video to start playback. You can also open videos from another app using Only Player.

### Main Features

| Area | Options |
|---|---|
| Media library | Folder, tree, and video views; list or grid layouts; sorting by title, duration, size, or date; folder exclusions and optional `.nomedia` scanning |
| Playback | Resume, autoplay the next video in the folder, picture-in-picture, background playback, speed, zoom, aspect ratio, and per-file track memory |
| Subtitles and audio | Embedded and external tracks, ASS effects, preferred languages, decoder preferences, and subtitle font, size, bold text, background, delay, speed, and encoding |
| Appearance | Light and dark themes, dynamic color on Android 12+, in-app language switching, and thumbnail settings |
| Settings | Configurable gestures, player controls, and orientation; export, import, or reset app and player settings |

### Playback Gestures

| Gesture | Action |
|---|---|
| Swipe horizontally | Seek forward or backward |
| Swipe vertically | Adjust brightness or volume |
| Double tap | Perform the configured skip action |
| Pinch | Zoom when zoom gestures are enabled |

Playback speed, long-press speed, picture-in-picture, and background playback can be adjusted in settings.

### Subtitles

Open subtitle selection in the player to choose an embedded track or load an external subtitle file. ASS subtitles support effect rendering. Adjust subtitle appearance, encoding, and timing under **Settings → Subtitle**.

### Settings Backup

Open **Settings → General → Backup settings** to save app and player settings to a file. Use **Restore settings** to import that file on the same or another device. You can also reset settings from this page.

<br>

---

## ❓ FAQ

<sub>[↑ Back to Navigation](#navigation)</sub>

### Why are some videos missing from the library?

Check media permissions and excluded folders, then refresh the library. Directories covered by a `.nomedia` file are hidden by default. To include them, enable **Ignore .nomedia files** in settings and grant all-files access when prompted.

### Why does a video fail to play?

Confirm that the file still exists and the app has permission to access it. If the file opens but video or audio playback fails, check the decoder preferences in player settings.

### Why are subtitles garbled, out of sync, or styled incorrectly?

Confirm that the correct subtitle track or file is selected. Check text encoding for garbled text, adjust subtitle delay or speed for timing issues, and check subtitle style settings for ASS appearance problems.

<br>

---

## 🛠 Development Guide

<sub>[↑ Back to Navigation](#navigation)</sub>

### Prerequisites

| Tool | Requirement |
|---|---|
| JDK | `26`; `scripts/prebuild.py` downloads a portable one into `build/jdk/` |
| Android Studio or SDK command-line tools | Android SDK Platform `37` |
| Python | `3.10+` for the build scripts |
| Gradle | Use the wrapper included in the repository |

```bash
git clone https://github.com/Kindness-Kismet/only_player.git
cd only_player
python scripts/prebuild.py
```

`prebuild.py` restores any missing Gradle wrapper file, downloads Temurin JDK 26 into `build/jdk/` when no portable JDK is present, and verifies the Android SDK is reachable. `scripts/build.py` then passes that JDK to Gradle, so no system-wide JDK installation is required.

| Option | Behavior |
|---|---|
| `--force` | Replace the existing portable JDK |
| `--verbose` | Print the download URL and detected SDK path |

Open the project in Android Studio, select JDK 26 for Gradle, and sync the project. For command-line builds, configure the Android SDK path through `local.properties` (`sdk.dir`) or `ANDROID_HOME`.

### Architecture

The project uses a layered, multi-module architecture with unidirectional data flow.

```text
app/                  Application, activities, navigation, and manifest
core/common/          Logging, dispatchers, and shared helpers
core/model/           Pure Kotlin models and path types
core/database/        Room database, DAO, and schema
core/datastore/       DataStore sources and serializers
core/data/            Repository interfaces, implementations, and mappers
core/domain/          Use cases
core/media/           Media scanning and synchronization
core/ui/              Shared Compose components, strings, and themes
feature/player/       Player UI, playback service, and playback flow
feature/settings/     Settings screens and preference logic
feature/videopicker/  Media library, search, and quick settings
scripts/              Prebuild environment setup and APK build script
.github/workflows/    Validation and release workflows
```

`app` assembles the application. Features depend on `core` modules and communicate through callbacks; they do not depend on each other. `core/model` has no Android or other core-module dependencies.

Start with [MainActivity](app/src/main/java/one/only/player/MainActivity.kt), then follow the relevant feature into its domain and data layers. Shared strings live in `core/ui/src/main/res`.

### Coding Conventions

- Keep IO in repositories or feature-local helpers, business logic in use cases, and presentation state in ViewModels.
- Use immutable UI state and events, `Flow` / `StateFlow`, Hilt injection, and injected dispatchers.
- Use `StoragePath` for external-storage media paths; comparisons use normalized paths while file access and display use `value`.
- Use explicit imports, trailing commas in multiline lists, and guard clauses to keep control flow flat.
- Give every interactive Compose control a stable `Modifier.testTag()` or `contentDescription` so debug commands and UI automation can locate it.

See [AGENTS.md](AGENTS.md) for the complete architecture and coding rules.

### Build and Validation

**Build a debug APK:**

```bash
python scripts/build.py build-apk --abi arm64-v8a --build-type debug
```

Run `python scripts/prebuild.py` first; the build script fails early when the portable JDK is missing.

| Option | Values and behavior |
|---|---|
| `--abi` | `arm64-v8a` or `x86_64`; omit to build both |
| `--build-type` | `debug`, `release`, or `release-with-debug-signing`; defaults to `release` |
| `--verbose` | Print detailed Gradle output |

The script saves APKs to `build/apk/`:

- Debug: `Only-Player-debug-<abi>-<version>.apk`
- Release: `Only-Player-<abi>-<version>.apk`

The `release` build type requires signing configuration to produce an installable APK. For local checks with release optimizations and a debug key, use `--build-type release-with-debug-signing`.

**Format and check code changes:**

```bash
./gradlew ktlintFormat ktlintCheck
```

**Validate changes affecting builds or app behavior:**

```bash
./gradlew ktlintCheck test assembleDebug --warning-mode=all
```

Inspect test reports under each module's `build/reports/tests/` directory as well as the command result. For playback changes, verify actual playback on a device or emulator. Run `./gradlew connectedAndroidTest` when instrumentation tests are needed.

Documentation-only changes do not require formatting, tests, or APK builds.

### Release Channels

| Channel | Branch | Workflow |
|---|---|---|
| Development | `dev` | [Validation](.github/workflows/test.yaml) can trigger [development publishing](.github/workflows/publish-dev.yaml) after detecting product changes and passing checks; development publishing also supports manual runs |
| Stable | `main` | [Stable publishing](.github/workflows/publish.yaml) runs when `versionName` changes in `app/build.gradle.kts`, or when started manually on `main` |

For a stable release, update the version and [changelog](.github/CHANGELOG.md) on `dev`, then promote `dev` to `main`. Publishing workflows validate signing configuration before building signed APKs. If publishing fails, check the workflow's signing validation output.

<br>

---

## 📋 Contribution Guidelines

<sub>[↑ Back to Navigation](#navigation)</sub>

Feature and fix pull requests target **`dev`**. Use **`dev` → `main`** only to promote a stable release.

| Check | Requirement |
|---|---|
| Scope | Keep each commit focused on the current change |
| Architecture | Follow module boundaries and the rules in [AGENTS.md](AGENTS.md) |
| UI controls | Add stable test tags or content descriptions for new interactive controls |
| Validation | Run the checks relevant to the change and report the actual results |
| Documentation | Keep both README languages aligned when updating shared information |

<br>

---

## 📄 License

<sub>[↑ Back to Navigation](#navigation)</sub>

Only Player is licensed under [GNU GPL v3](LICENSE). Third-party components remain subject to their respective licenses.

<br>

---

## 🤝 Acknowledgements

<sub>[↑ Back to Navigation](#navigation)</sub>

Only Player continues from [Next Player](https://github.com/anilbeesetti/nextplayer). Thanks to the original project and all upstream contributors for the foundation and continued maintenance.
