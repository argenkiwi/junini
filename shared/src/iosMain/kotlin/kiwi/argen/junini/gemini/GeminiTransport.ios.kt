package kiwi.argen.junini.gemini

// Ktor's Socket.tls() throws on Native; an NWConnection-based transport would be needed here.
actual fun platformGeminiTransport(): GeminiTransport = UnsupportedGeminiTransport
