package kiwi.argen.junini.identity

/** Test-only PEM files from `src/jvmTest/resources/identity`, made with openssl (certificates valid for 100 years). */
object Fixtures {
    fun read(name: String): String =
        checkNotNull(Fixtures::class.java.getResource("/identity/$name")) { "Missing fixture $name" }.readText()

    /** Alice (RSA, PKCS#8 key). */
    val aliceCertificate get() = read("alice.crt")
    val aliceKey get() = read("alice.key")

    /** Bob, a second RSA identity. */
    val bobCertificate get() = read("bob.crt")
    val bobKey get() = read("bob.key")
}
