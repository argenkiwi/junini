package kiwi.argen.junini

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import kiwi.argen.junini.gemini.FileKnownHostsStore
import kiwi.argen.junini.history.FileHistoryStore
import kiwi.argen.junini.identity.FileIdentityStore
import kiwi.argen.junini.identity.IdentityManager
import kiwi.argen.junini.identity.JdkIdentityCodec

fun main() {
    val dataDir = File(System.getProperty("user.home"), ".junini")
    val knownHostsStore = FileKnownHostsStore(File(dataDir, "known_hosts"))
    val historyStore = FileHistoryStore(File(dataDir, "history"))
    val identityManager = IdentityManager(FileIdentityStore(File(dataDir, "identities")), JdkIdentityCodec())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Junini",
        ) {
            App(knownHostsStore, historyStore, identityManager, DesktopIdentityFiles(window))
        }
    }
}
