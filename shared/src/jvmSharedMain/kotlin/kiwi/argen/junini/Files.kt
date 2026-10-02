package kiwi.argen.junini

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

/**
 * Writes to a temporary file first and moves it into place, so a crash mid-write can't lose the old contents.
 * With [ownerOnly] the file is only readable by its owner, where the filesystem has such permissions.
 */
internal fun File.writeTextAtomically(text: String, ownerOnly: Boolean = false) {
    parentFile?.mkdirs()
    val temp = File("$path.tmp")
    if (ownerOnly) {
        // Restrict the file before the secret goes in, not after.
        temp.delete()
        temp.createNewFile()
        runCatching {
            Files.setPosixFilePermissions(temp.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
    }
    temp.writeText(text)
    Files.move(temp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
