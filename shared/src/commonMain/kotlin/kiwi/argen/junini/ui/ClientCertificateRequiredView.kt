package kiwi.argen.junini.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kiwi.argen.junini.identity.IdentityFiles

/** Offers to pick or import an identity for a capsule that answered with status 60, 61 or 62. */
@Composable
fun ClientCertificateRequiredView(
    page: PageState.ClientCertificateRequired,
    onUseIdentity: (String) -> Unit,
    onImportIdentity: (List<String>) -> Unit,
    files: IdentityFiles?,
    onUnassign: () -> Unit,
    onCancel: () -> Unit,
    contentPadding: PaddingValues,
) {
    val startImport = files?.let { rememberIdentityImporter(it, onImportIdentity) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (page.status == 60) "Identity required" else "Identity not accepted",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (page.message.isNotBlank()) Text(page.message, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when {
                        page.current != null ->
                            "${page.host} didn't accept \"${page.current.name}\" (status ${page.status}). " +
                                "Pick another identity, import one, or stop using this one."
                        page.status == 60 ->
                            "${page.host} wants to know who you are. Pick one of your identities or import one."
                        else ->
                            "${page.host} didn't accept your identity (status ${page.status}). Pick or import another."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                // An identity is a private key: only the one the user picks is ever sent, and only to this host.
                page.available.filter { it.id != page.current?.id }.forEach { identity ->
                    OutlinedButton(onClick = { onUseIdentity(identity.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Use ${identity.name}")
                    }
                }
                if (startImport != null) {
                    Button(onClick = startImport, modifier = Modifier.fillMaxWidth()) { Text("Import identity") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (page.current != null) TextButton(onClick = onUnassign) { Text("Stop using ${page.current.name}") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}
