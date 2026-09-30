# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Junini is a Kotlin Multiplatform + Compose Multiplatform project (package `kiwi.argen.junini`) targeting Android, iOS, Desktop (JVM) and Web (JS + Wasm). It is currently the unmodified KMP wizard template (a "Click me!" button that shows a greeting).

## Commands

```
./gradlew :androidApp:assembleDebug                      # Android
./gradlew :desktopApp:run                                # Desktop
./gradlew :desktopApp:hotRun --auto                      # Desktop with hot reload
./gradlew :webApp:wasmJsBrowserDevelopmentRun            # Web (Wasm)
./gradlew :webApp:jsBrowserDevelopmentRun                # Web (JS, older browsers)
```

iOS: open `iosApp/` in Xcode and run from there (it consumes the `Shared` static framework built from `:shared`).

Tests (all live in `:shared`):

```
./gradlew :shared:jvmTest
./gradlew :shared:testAndroidHostTest
./gradlew :shared:wasmJsTest
./gradlew :shared:jsTest
./gradlew :shared:iosSimulatorArm64Test
./gradlew :shared:jvmTest --tests "kiwi.argen.junini.SharedCommonTest"   # single test class
```

No lint task is configured.

## Architecture

Modules (`settings.gradle.kts`): `:shared`, `:androidApp`, `:desktopApp`, `:webApp`, plus the Xcode project in `iosApp/` (not a Gradle module).

- **`:shared`** holds essentially all code, including the UI. The root `App()` composable is in `commonMain`. Platform specifics use `expect`/`actual` (e.g. `Platform.kt` in `commonMain` with `Platform.<target>.kt` actuals in `androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`). Compose resources live in `shared/src/commonMain/composeResources` and are accessed through the generated `junini.shared.generated.resources.Res`.
- **App modules are thin entry points** that depend on `:shared` and only host `App()`: `androidApp` (`MainActivity`), `desktopApp` (`main.kt`), `webApp` (`main.kt`, `index.html`, JS and Wasm executables), and `iosApp` (SwiftUI `ContentView` wrapping `MainViewController` from `shared/src/iosMain`).
- `:shared` uses the new `com.android.kotlin.multiplatform.library` plugin (AGP 9), so the Android target is declared via the `android { }` block inside `kotlin { }`, and Android source sets are named `androidMain` / `androidHostTest` (not `androidUnitTest`).
- The web target is split: `:shared` produces both `js` and `wasmJs` targets, while `webApp` builds the executables. Web tests use the `webTest` source set shared by both.
- Dependency versions are centralised in `gradle/libs.versions.toml`. Gradle configuration cache and build cache are enabled (`gradle.properties`).
