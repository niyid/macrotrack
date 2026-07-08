// Root build — bootstrapped from the buzzr-p2p build strategy (AGP/Kotlin/KSP/Hilt
// versions kept identical for toolchain consistency across techducat projects).
// MacroTrack is local-first with no P2P transport; its only network calls are
// outbound lookups to Open Food Facts, now routed through an I2P outproxy tunnel
// (see app/src/main/java/.../network/I2POutproxyTunnel.kt + EmbeddedI2PRouter.kt).
// No extra root-level plugins are needed for this — Hilt/KSP are already present.

plugins {
    id("com.android.application")       version "8.10.1" apply false
    id("org.jetbrains.kotlin.android")  version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.dagger.hilt.android") version "2.57" apply false
    id("com.google.devtools.ksp")       version "2.1.21-2.0.1" apply false
}
