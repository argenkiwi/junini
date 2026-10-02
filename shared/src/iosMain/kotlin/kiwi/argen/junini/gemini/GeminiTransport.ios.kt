package kiwi.argen.junini.gemini

import kiwi.argen.junini.identity.IdentityStore

// Ktor's Socket.tls() throws on Native; an NWConnection-based transport would be needed here.
actual fun platformGeminiTransport(knownHosts: KnownHosts, identities: IdentityStore): GeminiTransport = UnsupportedGeminiTransport
