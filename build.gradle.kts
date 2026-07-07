// Root build — bootstrapped from the buzzr-p2p build strategy (AGP/Kotlin/KSP/Hilt
// versions kept identical for toolchain consistency across techducat projects).
// I2P / serialization plugins dropped — MacroTrack is a local-first app with no
// P2P transport; the only network calls are outbound lookups to Open Food Facts.

plugins {
    id("com.android.application")       version "8.10.1" apply false
    id("org.jetbrains.kotlin.android")  version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.dagger.hilt.android") version "2.57" apply false
    id("com.google.devtools.ksp")       version "2.1.21-2.0.1" apply false
}
