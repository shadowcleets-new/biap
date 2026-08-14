import java.util.Properties

/**
 * Developer-local settings, never committed. Absent on a fresh clone, which is fine:
 * every value read from here has a working fallback.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.services)
  kotlin("android")
  alias(libs.plugins.ksp)
  alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.sc.biap"
    // 37 required by material3 1.5.0-alpha (Expressive). targetSdk stays 36 on
    // purpose: compileSdk only changes what we can compile against, targetSdk changes
    // runtime behaviour, and there is no reason to take that risk here.
    compileSdk = 37
    defaultConfig {
        applicationId = "com.sc.biap"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    /**
     * Release signing, read from local.properties (gitignored) with the keystore itself
     * kept OUTSIDE the repository so it cannot be committed by accident.
     *
     * Previously absent entirely, which meant a "release" build was signed with the debug
     * key - a key whose password is public and identical on every developer machine on
     * earth. Anyone could have produced a build Android would accept as an update to this
     * app. It is now a 4096-bit RSA key valid for ~27 years.
     *
     * Configured only when the properties are present, so a fresh clone still builds; it
     * just produces an unsigned release, which fails loudly at install rather than
     * silently shipping something signed with a throwaway key.
     */
    val releaseStorePath = localProperties.getProperty("RELEASE_STORE_FILE")
    val hasReleaseSigning = releaseStorePath != null && file(releaseStorePath).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Pin the App Check debug token.
            //
            // The debug provider mints a RANDOM secret on first run and stores it in the
            // app's SharedPreferences. "Clear storage" and reinstall both wipe that store,
            // so a new unregistered secret is minted and every App Check-gated call starts
            // failing silently - photos stop loading with no error shown anywhere.
            //
            // Pinning it to a value registered once in Firebase Console makes debug builds
            // survive both. Set APPCHECK_DEBUG_SECRET in local.properties (gitignored, so
            // the secret never enters version control). Left blank, behaviour is unchanged.
            resValue(
                "string",
                "appcheck_debug_secret",
                localProperties.getProperty("APPCHECK_DEBUG_SECRET").orEmpty()
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
      // Enabled for the debug-only App Check token resource; see the debug buildType.
      resValues = true
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.material.icons.extended)
  implementation(libs.okhttp)
  implementation(libs.play.services.auth)

  // Installs the shipped baseline profile at first run. Without it the profile is inert on
  // anything below API 31 and unreliable above it - the file would ship and do nothing.
  implementation(libs.androidx.profileinstaller)
  baselineProfile(project(":baselineprofile"))

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // WorkManager & Security
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.security.crypto)
  // App lock: BiometricPrompt, plus process-wide lifecycle to know when the whole app
  // (not just one screen) goes to the background.
  // Encrypts the catalogue at rest. Without it the database is plain SQLite:
  // designs, costs and margins readable by anyone with file access to the phone.
  implementation(libs.sqlcipher)
  implementation(libs.androidx.sqlite)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.lifecycle.process)

  // DataStore
  implementation(libs.androidx.datastore.preferences)

  // Firebase
  val firebaseBom = platform(libs.firebase.bom)
  implementation(firebaseBom)
  androidTestImplementation(firebaseBom)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  // firebase-storage removed: design photos now live in the same Google Drive
  // appDataFolder as the catalogue. See DriveImageStore for why.
  // .await() on Play Services Tasks. Firebase pulls this in transitively, but relying on
  // that means a Firebase bump could silently break the build - so declare it.
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.firebase.appcheck.playintegrity)
  debugImplementation(libs.firebase.appcheck.debug)
}


