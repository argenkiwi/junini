package kiwi.argen.junini

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kiwi.argen.junini.identity.IdentityFiles
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Imports and exports PEM identities through the system file picker. The launchers have to be registered
 * before the activity is started, so create this in `onCreate`.
 */
class AndroidIdentityFiles(private val activity: ComponentActivity) : IdentityFiles {
    private var pendingImport: Continuation<String?>? = null
    private var pendingExport: Continuation<Boolean>? = null
    private var exportText: String? = null

    private val importLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport?.resume(uri?.let(::read))
        pendingImport = null
    }

    private val exportLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-pem-file"),
    ) { uri ->
        val written = uri != null && write(uri, exportText)
        exportText = null
        pendingExport?.resume(written)
        pendingExport = null
    }

    override suspend fun pickImport(): String? = suspendCoroutine { continuation ->
        pendingImport = continuation
        importLauncher.launch(arrayOf("*/*"))
    }

    override suspend fun saveExport(fileName: String, text: String): Boolean = suspendCoroutine { continuation ->
        pendingExport = continuation
        exportText = text
        exportLauncher.launch(fileName)
    }

    private fun read(uri: Uri): String? =
        runCatching { activity.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()

    private fun write(uri: Uri, text: String?): Boolean = text != null && runCatching {
        activity.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) } != null
    }.getOrDefault(false)
}
