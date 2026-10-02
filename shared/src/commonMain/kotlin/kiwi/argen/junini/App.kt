package kiwi.argen.junini

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import kiwi.argen.junini.gemini.InMemoryKnownHostsStore
import kiwi.argen.junini.gemini.KnownHostsStore
import kiwi.argen.junini.history.HistoryStore
import kiwi.argen.junini.history.InMemoryHistoryStore
import kiwi.argen.junini.identity.IdentityFiles
import kiwi.argen.junini.identity.IdentityManager
import kiwi.argen.junini.ui.BrowserScreen
import kiwi.argen.junini.ui.BrowserViewModel

/**
 * [knownHostsStore] holds pinned server certificates and [historyStore] the visited pages behind URL
 * suggestions; platforms with networking pass persistent ones. [identityManager] enables identity management
 * (client certificates), and [identityFiles] lets the user import and export them.
 */
@Composable
@Preview
fun App(
    knownHostsStore: KnownHostsStore = remember { InMemoryKnownHostsStore() },
    historyStore: HistoryStore = remember { InMemoryHistoryStore() },
    identityManager: IdentityManager? = null,
    identityFiles: IdentityFiles? = null,
) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        BrowserScreen(
            viewModel = viewModel {
                BrowserViewModel(knownHostsStore, historyStore = historyStore, identityManager = identityManager)
            },
            identityFiles = identityFiles,
        )
    }
}
