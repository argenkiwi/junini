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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val knownHostsStore = FileKnownHostsStore(File(filesDir, "known_hosts"))
        val historyStore = FileHistoryStore(File(filesDir, "history"))
        setContent {
            App(knownHostsStore, historyStore)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
