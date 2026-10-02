package kiwi.argen.junini

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kiwi.argen.junini.identity.IdentityFiles
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Imports and exports PEM identities through the system file picker. The launchers have to be registered
 * before the activity is started, so create this in `onCreate`.
 *
 * If the activity is recreated while a picker is open, the caller's coroutine is cancelled with the old
 * screen, so the result is dropped rather than imported behind the user's back; they just pick again.
 */
class AndroidIdentityFiles(private val activity: ComponentActivity) : IdentityFiles {
    private var pendingImport: CancellableContinuation<String?>? = null
    private var pendingExport: CancellableContinuation<Boolean>? = null
    private var exportText: String? = null

    private val importLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Read on the main thread, which is fine for the small PEM files this is used for.
        takeImport()?.resume(uri?.let(::read))
    }

    private val exportLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-pem-file"),
    ) { uri ->
        val text = exportText
        exportText = null
        takeExport()?.resume(uri != null && write(uri, text))
    }

    override suspend fun pickImport(): String? = suspendCancellableCoroutine { continuation ->
        // A picker that is already open can't be shared: the earlier caller gets "cancelled" instead of hanging.
        takeImport()?.resume(null)
        pendingImport = continuation
        continuation.invokeOnCancellation { if (pendingImport === continuation) pendingImport = null }
        importLauncher.launch(arrayOf("*/*"))
    }

    override suspend fun saveExport(fileName: String, text: String): Boolean = suspendCancellableCoroutine { continuation ->
        takeExport()?.resume(false)
        pendingExport = continuation
        exportText = text
        continuation.invokeOnCancellation {
            if (pendingExport === continuation) {
                pendingExport = null
                exportText = null
            }
        }
        exportLauncher.launch(fileName)
    }

    private fun takeImport() = pendingImport.also { pendingImport = null }

    private fun takeExport() = pendingExport.also { pendingExport = null }

    private fun read(uri: Uri): String? =
        runCatching { activity.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()

    private fun write(uri: Uri, text: String?): Boolean = text != null && runCatching {
        activity.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) } != null
    }.getOrDefault(false)
}
