package kiwi.argen.junini

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kiwi.argen.junini.identity.IdentityFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Imports and exports PEM identities through the native file dialog. */
class DesktopIdentityFiles(private val parent: Frame?) : IdentityFiles {
    override suspend fun pickImport(): String? = withContext(Dispatchers.IO) {
        chooseFile("Import identity", FileDialog.LOAD, "")?.let { runCatching { it.readText() }.getOrNull() }
    }

    override suspend fun saveExport(fileName: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val file = chooseFile("Export identity", FileDialog.SAVE, fileName) ?: return@withContext false
        runCatching { file.writeText(text) }.isSuccess
    }

    // The dialog blocks until the user closes it, hence the IO dispatcher above.
    private fun chooseFile(title: String, mode: Int, fileName: String): File? {
        val dialog = FileDialog(parent, title, mode).apply {
            file = fileName.ifEmpty { null }
            isVisible = true
        }
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }
}
