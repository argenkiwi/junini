package kiwi.argen.junini

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Writes to a temporary file first and moves it into place, so a crash mid-write can't lose the old contents. */
internal fun File.writeTextAtomically(text: String) {
    parentFile?.mkdirs()
    val temp = File("$path.tmp")
    temp.writeText(text)
    Files.move(temp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
