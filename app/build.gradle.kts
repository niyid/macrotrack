import java.util.Properties

// MacroTrack — privacy-first calorie & macro tracker
//
// Adopted from buzzr-p2p's build.gradle.kts structure, with the following changes:
//  - Removed: I2P embedded router AAR wiring, geohash, libphonenumber, Bugfender,
//             Java-WebSocket, ccp (country code picker) — none of this app's business.
//  - Added:   CameraX + ML Kit barcode-scanning (the scanner in "cronometer with a
//             barcode scanner"), Retrofit + Moshi (Open Food Facts lookups — the
//             only outbound network calls this app makes), Coil (product photos).
//  - Kept:    Room + KSP (local diary/food cache), Hilt DI, Compose, Timber logging,
//             16 KB ELF alignment task, flavor/signing pattern, coroutines.
//  - Privacy posture: no analytics/crash-reporting SDK, no account/phone number
//    requirement, no location permission. All diary data lives in local Room only;
//    Open Food Facts calls send only the scanned barcode / search text, nothing else.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun getLocalProperty(key: String, defaultValue: String = ""): String =
    localProperties.getProperty(key) ?: System.getenv(key) ?: defaultValue

android {
    namespace = "com.techducat.macrotrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.techducat.macrotrack"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Open Food Facts is a free, open, no-API-key database — safe to bake the
        // base URL in as a BuildConfig field so it can be swapped per-flavor/test.
        buildConfigField("String", "OFF_BASE_URL", "\"https://world.openfoodfacts.org/\"")
    }

    lint {
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            val resolvedKeystorePath = getLocalProperty("RELEASE_STORE_FILE")
            if (resolvedKeystorePath.isNotEmpty()) {
                storeFile     = file(resolvedKeystorePath)
                storePassword = getLocalProperty("RELEASE_STORE_PASSWORD")
                keyAlias      = getLocalProperty("RELEASE_KEY_ALIAS")
                keyPassword   = getLocalProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("playstore") { dimension = "distribution" }
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            isDebuggable      = false
            isJniDebuggable   = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (getLocalProperty("RELEASE_STORE_FILE").isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk { debugSymbolLevel = "NONE" }
        }

        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions { jvmTarget = "17" }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("dagger.fastInit", "enabled")
        arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
    }

    sourceSets {
        getByName("main") { assets.srcDirs("$projectDir/schemas") }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // ===== CORE ANDROID =====
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Pulled in solely for the XML launch theme (Theme.Material3.DayNight.NoActionBar
    // in themes.xml) — the app itself is 100% Compose/Material3, but the manifest's
    // pre-Compose window background theme still resolves against this library.
    implementation("com.google.android.material:material:1.13.0")

    // ===== LIFECYCLE =====
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // ===== COMPOSE =====
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ===== ROOM (local diary + food cache — this app's ONLY persistent store) =====
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ===== COROUTINES =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // ===== HILT =====
    implementation("com.google.dagger:hilt-android:2.57")
    ksp("com.google.dagger:hilt-android-compiler:2.57")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ===== CAMERAX + ML KIT BARCODE SCANNING =====
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // ===== NETWORKING (Open Food Facts lookups ONLY — no other outbound traffic) =====
    val retrofitVersion = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-moshi:$retrofitVersion")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ===== IMAGES =====
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ===== UTILITIES =====
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ===== TESTING =====
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
