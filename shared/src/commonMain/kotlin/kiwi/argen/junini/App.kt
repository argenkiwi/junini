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
import kiwi.argen.junini.ui.BrowserScreen
import kiwi.argen.junini.ui.BrowserViewModel

/** [knownHostsStore] holds pinned server certificates; platforms with networking pass a persistent one. */
@Composable
@Preview
fun App(knownHostsStore: KnownHostsStore = remember { InMemoryKnownHostsStore() }) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        BrowserScreen(viewModel { BrowserViewModel(knownHostsStore) })
    }
}
