# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Junini is a Kotlin Multiplatform + Compose Multiplatform project (package `kiwi.argen.junini`) targeting Android, iOS, Desktop (JVM) and Web (JS + Wasm). It is a client for the [Gemini protocol](https://geminiprotocol.net/): a URL bar (which hides on scroll down and reappears on scroll up) plus a viewport that renders gemtext.

Gemini is TLS over raw TCP (port 1965). Networking only works on **Android and Desktop**: Ktor's `Socket.tls()` throws on Native, and browsers can't open TCP sockets. iOS and Web build and run, but show a "not supported on this platform yet" message. Server certificates are checked with TOFU: `KnownHosts` (commonMain) pins the SHA-256 of each host:port's leaf certificate, silently replaces expired pins, and throws `CertificateMismatchException` on a mismatch. `BrowserViewModel` turns that exception into `PageState.CertificateChanged`, a trust/cancel prompt. Pins are persisted by `FileKnownHostsStore` (jvmShared), which `MainActivity` and the desktop `main.kt` pass into `App(knownHostsStore)`. Everything else uses `InMemoryKnownHostsStore`.

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
./gradlew :shared:jvmTest --tests "kiwi.argen.junini.gemini.GemtextParserTest"   # single test class
```

No lint task is configured.

## Architecture

Modules (`settings.gradle.kts`): `:shared`, `:androidApp`, `:desktopApp`, `:webApp`, plus the Xcode project in `iosApp/` (not a Gradle module).

- **`:shared`** holds essentially all code, including the UI. The root `App()` composable is in `commonMain`. Platform specifics use `expect`/`actual` (e.g. `Platform.kt` in `commonMain` with `Platform.<target>.kt` actuals in `androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`). Compose resources live in `shared/src/commonMain/composeResources` and are accessed through the generated `junini.shared.generated.resources.Res`.
- Code in `:shared` is split into:
  - `gemini/`: protocol code in commonMain. `GeminiUrl` handles input normalisation and RFC 3986 link resolution on top of Ktor's `Url`. `GeminiProtocol` covers header/MIME parsing and the `GeminiResponse` types. `GeminiClient` handles redirects and `Gemtext` does the parsing.
  - `ui/`: `BrowserViewModel`, `BrowserScreen` (M3 `BottomAppBar` + `exitAlwaysScrollBehavior`, holding the URL field and back/forward buttons) and `GemtextView`. `ui/BackHandler.kt` is an `expect` composable: Android wires it to the system back gesture, and the other actuals do nothing.
  - The network transport sits behind `expect fun platformGeminiTransport(knownHosts)`. The real one uses Ktor sockets and lives in a custom `jvmShared` source set (shared by `jvm` and `android`, declared through `applyDefaultHierarchyTemplate` in `shared/build.gradle.kts`). Its `TofuTrustManager` calls `KnownHosts.verify` during the handshake. `iosMain` and `webMain` return a stub that throws.
  - Tests in `commonTest` use a fake `GeminiTransport`. `jvmTest` covers `FileKnownHostsStore`.
- **App modules are thin entry points** that depend on `:shared` and only host `App()`: `androidApp` (`MainActivity`), `desktopApp` (`main.kt`), `webApp` (`main.kt`, `index.html`, JS and Wasm executables), and `iosApp` (SwiftUI `ContentView` wrapping `MainViewController` from `shared/src/iosMain`).
- `:shared` uses the new `com.android.kotlin.multiplatform.library` plugin (AGP 9), so the Android target is declared via the `android { }` block inside `kotlin { }`, and Android source sets are named `androidMain` / `androidHostTest` (not `androidUnitTest`).
- The web target is split: `:shared` produces both `js` and `wasmJs` targets, while `webApp` builds the executables. Web tests use the `webTest` source set shared by both.
- Dependency versions are centralised in `gradle/libs.versions.toml`. Gradle configuration cache and build cache are enabled (`gradle.properties`).
