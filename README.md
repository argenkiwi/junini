<div align="center">

# Junini

**A small, multiplatform client for the [Gemini protocol](https://geminiprotocol.net/).**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20Desktop%20%7C%20iOS%20%7C%20Web-lightgrey)

[Features](#features) • [Platform support](#platform-support) • [Getting started](#getting-started) • [Project structure](#project-structure) • [Testing](#testing)

</div>

Junini is a lightweight browser for [Geminispace](https://geminiprotocol.net/), written in Kotlin with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/). It's a minimal, distraction-free reader: a URL bar and a viewport that renders gemtext.

## Features

- **Gemtext rendering**: headings, links, lists, quotes and preformatted blocks.
- **Plain text** support for `text/*` responses.
- **Simple navigation**: a bottom URL bar that hides as you scroll down and comes back when you scroll up, plus back and forward buttons (and the system back gesture on Android).
- **Forgiving input**: type `geminiprotocol.net` and Junini adds the `gemini://` for you.
- **Spec-aware networking**: follows redirects (with loop detection), resolves relative links per RFC 3986, and turns status codes into readable messages.
- **One codebase**: protocol, state and UI are all shared in Kotlin.

## Platform support

| Platform         | Builds | Browses Gemini |
| ---------------- | :----: | :------------: |
| Android          |   ✅   |       ✅       |
| Desktop (JVM)    |   ✅   |       ✅       |
| iOS              |   ✅   |       —        |
| Web (Wasm / JS)  |   ✅   |       —        |

Gemini runs TLS over raw TCP on port 1965. Ktor's TLS sockets aren't available on Kotlin/Native yet, and browsers can't open raw TCP sockets, so for now the iOS and Web apps show a "not supported on this platform yet" message.

> [!WARNING]
> Junini currently **accepts every server certificate**. [TOFU](https://en.wikipedia.org/wiki/Trust_on_first_use) certificate pinning is planned. Until it lands, don't trust Junini with anything sensitive.

### Not yet supported

- Sending input to servers (`1x` responses)
- Client certificates (`6x` responses)
- Non-UTF-8 charsets and non-text media
- Links to other schemes (`https://`, `gopher://`, …)

## Getting started

### Prerequisites

- JDK 21 (Gradle's daemon toolchain will provision it if needed)
- [Android Studio](https://developer.android.com/studio) or [IntelliJ IDEA](https://www.jetbrains.com/idea/) with the Kotlin Multiplatform plugin
- Xcode, to run the iOS app (macOS only)

### Run the app

```bash
git clone https://github.com/argenkiwi/junini.git
cd junini
```

| Target          | Command                                               |
| --------------- | ----------------------------------------------------- |
| Desktop         | `./gradlew :desktopApp:run`                           |
| Desktop (hot reload) | `./gradlew :desktopApp:hotRun --auto`            |
| Android (APK)   | `./gradlew :androidApp:assembleDebug`                 |
| Web (Wasm)      | `./gradlew :webApp:wasmJsBrowserDevelopmentRun`       |
| Web (JS, older browsers) | `./gradlew :webApp:jsBrowserDevelopmentRun`  |
| iOS             | Open [`iosApp/`](./iosApp) in Xcode and run it        |

> [!TIP]
> Not sure where to start? Try `gemini://geminiprotocol.net/`, the protocol's home in Geminispace.

## Project structure

```
junini/
├── shared/        # Nearly all the code, UI included
│   └── src/
│       ├── commonMain/     # gemini/ (protocol) and ui/ (Compose screens + view model)
│       ├── jvmSharedMain/  # Ktor TLS socket transport, shared by Android and Desktop
│       ├── iosMain/        # iOS entry point and stub transport
│       ├── webMain/        # Stub transport for JS and Wasm
│       └── commonTest/     # Tests, run against a fake transport
├── androidApp/    # Android entry point (MainActivity)
├── desktopApp/    # Desktop entry point
├── webApp/        # Web entry point and index.html
└── iosApp/        # Xcode project wrapping the shared framework
```

The app modules are thin shells that host the shared `App()` composable. In `shared`:

- `gemini/` holds the protocol: URL normalisation and resolution (`GeminiUrl`), header and MIME parsing (`GeminiProtocol`), redirect handling (`GeminiClient`) and the gemtext parser (`Gemtext`).
- `ui/` holds `BrowserViewModel`, which manages loading and history, and the Compose UI (`BrowserScreen`, `GemtextView`).
- Networking sits behind `expect fun platformGeminiTransport()`, so adding a platform only takes a new transport.

## Testing

Tests live in `shared/src/commonTest` and run on every target:

```bash
./gradlew :shared:jvmTest                 # Desktop (JVM)
./gradlew :shared:testAndroidHostTest     # Android
./gradlew :shared:wasmJsTest              # Web (Wasm)
./gradlew :shared:jsTest                  # Web (JS)
./gradlew :shared:iosSimulatorArm64Test   # iOS simulator
```

Run a single test class with `--tests`:

```bash
./gradlew :shared:jvmTest --tests "kiwi.argen.junini.gemini.GemtextParserTest"
```

## Resources

- [Gemini protocol specification](https://geminiprotocol.net/docs/protocol-specification.gmi)
- [Gemtext reference](https://geminiprotocol.net/docs/gemtext-specification.gmi)
- [Kotlin Multiplatform documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
