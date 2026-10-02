package kiwi.argen.junini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.io.File
import kiwi.argen.junini.gemini.FileKnownHostsStore
import kiwi.argen.junini.history.FileHistoryStore
import kiwi.argen.junini.identity.FileIdentityStore
import kiwi.argen.junini.identity.IdentityManager
import kiwi.argen.junini.identity.JdkIdentityCodec

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // Lets the bottom bar's colour reach the screen edge under 3-button navigation, rather than the
        // system laying its own translucent scrim over it.
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)

        val knownHostsStore = FileKnownHostsStore(File(filesDir, "known_hosts"))
        val historyStore = FileHistoryStore(File(filesDir, "history"))
        val identityManager = IdentityManager(FileIdentityStore(File(filesDir, "identities")), JdkIdentityCodec())
        val identityFiles = AndroidIdentityFiles(this)
        setContent {
            App(knownHostsStore, historyStore, identityManager, identityFiles)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
