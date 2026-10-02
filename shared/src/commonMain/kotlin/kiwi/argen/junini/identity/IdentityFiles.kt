package kiwi.argen.junini.identity

/** Lets the user pick a file to import an identity from and the place to export one to. Implemented per platform. */
interface IdentityFiles {
    /** Asks the user for a PEM file (a certificate, a private key, or both) and returns its text, or null if they cancelled. */
    suspend fun pickImport(): String?

    /** Asks the user where to save [text], suggesting [fileName]. Returns whether the file was written. */
    suspend fun saveExport(fileName: String, text: String): Boolean
}
