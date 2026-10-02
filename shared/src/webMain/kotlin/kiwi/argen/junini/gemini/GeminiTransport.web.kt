package kiwi.argen.junini.gemini

import kiwi.argen.junini.identity.IdentityStore

// Browsers can't open raw TCP sockets; this would need a proxy server.
actual fun platformGeminiTransport(knownHosts: KnownHosts, identities: IdentityStore): GeminiTransport = UnsupportedGeminiTransport
