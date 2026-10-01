package kiwi.argen.junini

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import kiwi.argen.junini.gemini.FileKnownHostsStore

fun main() {
    val knownHostsStore = FileKnownHostsStore(File(System.getProperty("user.home"), ".junini/known_hosts"))
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Junini",
        ) {
            App(knownHostsStore)
        }
    }
}
