package kiwi.argen.junini

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform