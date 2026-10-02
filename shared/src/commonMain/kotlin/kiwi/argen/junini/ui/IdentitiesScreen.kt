package kiwi.argen.junini.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kiwi.argen.junini.gemini.GEMINI_DEFAULT_PORT
import kiwi.argen.junini.gemini.formatFingerprint
import kiwi.argen.junini.identity.IdentityFiles
import kiwi.argen.junini.identity.MissingPart
import kiwi.argen.junini.identity.missingPart
import kiwi.argen.junini.identity.parsePemBlocks
import kotlinx.coroutines.launch

private sealed interface IdentityDialog {
    class Export(val item: IdentityItem) : IdentityDialog
    class Assign(val item: IdentityItem) : IdentityDialog
    class Delete(val item: IdentityItem) : IdentityDialog
}

/** Imports, exports, deletes and assigns identities. [files] is null where import and export aren't possible. */
@Composable
fun IdentitiesScreen(
    items: List<IdentityItem>,
    currentHost: String?,
    files: IdentityFiles?,
    onImport: (texts: List<String>) -> Unit,
    onExport: (id: String) -> String?,
    onDelete: (id: String) -> Unit,
    onAssign: (hostInput: String, id: String) -> Unit,
    onUnassign: (host: String, port: Int) -> Unit,
    onClose: () -> Unit,
    contentPadding: PaddingValues,
) {
    var dialog by remember { mutableStateOf<IdentityDialog?>(null) }
    val scope = rememberCoroutineScope()
    val startImport = files?.let { rememberIdentityImporter(it, onImport) }

    Surface(modifier = Modifier.fillMaxSize().padding(contentPadding), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Identities", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("Done") }
            }
            if (startImport != null) Button(onClick = startImport) { Text("Import identity") }
            if (items.isEmpty()) {
                Text(
                    "No identities yet. An identity is a client certificate that lets a capsule recognise you. " +
                        "Import a .crt and .key pair, or a .pem file that holds both, such as the ones Lagrange uses.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { it.identity.id }) { item ->
                    IdentityCard(
                        item = item,
                        canExport = files != null,
                        onAssign = { dialog = IdentityDialog.Assign(item) },
                        onUnassign = onUnassign,
                        onExport = { dialog = IdentityDialog.Export(item) },
                        onDelete = { dialog = IdentityDialog.Delete(item) },
                    )
                }
            }
        }
    }

    when (val current = dialog) {
        null -> Unit
        is IdentityDialog.Export -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Export ${current.item.identity.name}") },
            text = {
                Text(
                    "The file will hold the identity's private key, unprotected. Anyone who has it can pose as you, " +
                        "so keep it somewhere safe.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    val text = onExport(current.item.identity.id)
                    if (text != null && files != null) {
                        scope.launch { files.saveExport(current.item.identity.name.toFileName() + ".pem", text) }
                    }
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        is IdentityDialog.Assign -> TextInputDialog(
            title = "Assign ${current.item.identity.name}",
            message = "The capsule that will be shown this identity, e.g. example.org or example.org:1966.",
            confirmLabel = "Assign",
            fields = listOf(TextInputField("Capsule", initial = currentHost.orEmpty())),
            onConfirm = {
                dialog = null
                onAssign(it[0], current.item.identity.id)
            },
            onDismiss = { dialog = null },
        )
        is IdentityDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${current.item.identity.name}?") },
            text = {
                Text(
                    "Its private key will be erased and it will stop being used by ${current.item.hosts.size} capsule(s). " +
                        "Export it first if you may need it again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    onDelete(current.item.identity.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IdentityCard(
    item: IdentityItem,
    canExport: Boolean,
    onAssign: () -> Unit,
    onUnassign: (host: String, port: Int) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.identity.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatFingerprint(item.identity.fingerprint),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "Expires ${item.identity.notAfter.toString().substringBefore('T')}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (item.hosts.isEmpty()) {
                Text("Not assigned to any capsule", style = MaterialTheme.typography.bodySmall)
            }
            item.hosts.forEach { host ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(host, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val (name, port) = host.splitHostPort()
                        onUnassign(name, port)
                    }) { Text("Unassign") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAssign) { Text("Assign") }
                if (canExport) TextButton(onClick = onExport) { Text("Export") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

/** A text field of a [TextInputDialog]. */
internal class TextInputField(val label: String, val initial: String = "")

/** Asks for one or more strings; [onConfirm] gets them in the order of [fields]. */
@Composable
internal fun TextInputDialog(
    title: String,
    confirmLabel: String,
    fields: List<TextInputField>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    val values = remember { mutableStateOf(fields.map { it.initial }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = values.value[index],
                        onValueChange = { new -> values.value = values.value.toMutableList().also { it[index] = new } },
                        label = { Text(field.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(values.value) }, enabled = values.value.all { it.isNotBlank() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Returns a function that starts importing an identity: it asks for a file and, if that file has only the certificate
 * or only the key, explains what is missing and asks for a second one. [onImport] gets the text of each file.
 */
@Composable
internal fun rememberIdentityImporter(files: IdentityFiles, onImport: (List<String>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    var firstFile by remember { mutableStateOf<String?>(null) }

    firstFile?.let { first ->
        val wanted = if (missingPart(listOf(first)) == MissingPart.PRIVATE_KEY) "private key (.key)" else "certificate (.crt)"
        AlertDialog(
            onDismissRequest = { firstFile = null },
            title = { Text("One more file") },
            text = { Text("That file doesn't have the whole identity. Now choose the $wanted file.") },
            confirmButton = {
                TextButton(onClick = {
                    firstFile = null
                    scope.launch { files.pickImport()?.let { onImport(listOf(first, it)) } }
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { firstFile = null }) { Text("Cancel") } },
        )
    }

    return {
        scope.launch {
            val text = files.pickImport() ?: return@launch
            // A file with no PEM in it at all goes straight to the import, which explains what's wrong.
            if (parsePemBlocks(text).isEmpty() || missingPart(listOf(text)) == null) onImport(listOf(text)) else firstFile = text
        }
    }
}

private fun String.toFileName(): String = replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "identity" }

/** Splits `host` or `host:port`, falling back to the Gemini default port. IPv6 hosts have more than one colon and keep it. */
private fun String.splitHostPort(): Pair<String, Int> {
    val index = lastIndexOf(':')
    val port = if (index > 0 && indexOf(':') == index) substring(index + 1).toIntOrNull() else null
    return if (port != null) substring(0, index) to port else this to GEMINI_DEFAULT_PORT
}
