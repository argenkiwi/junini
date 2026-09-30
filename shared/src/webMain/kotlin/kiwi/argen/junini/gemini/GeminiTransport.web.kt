package kiwi.argen.junini.gemini

// Browsers can't open raw TCP sockets; this would need a proxy server.
actual fun platformGeminiTransport(): GeminiTransport = UnsupportedGeminiTransport
