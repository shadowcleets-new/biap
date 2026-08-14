/**
 * @file baselineprofile/build.gradle.kts
 * @description Generates the baseline profile the app ships with.
 *
 * WHY THIS RUNS ON AN EMULATOR AND NOT THE PHONE:
 *   Profile generation needs root to read back the compiled profile, which a retail Pixel
 *   does not give you. More importantly, generating on a connected device installs a
 *   RELEASE-signed build of com.sc.biap - which cannot install over the debug build, so the
 *   tooling uninstalls first. Uninstalling destroys the Android Keystore key that encrypts
 *   the catalogue, and the database becomes permanently unreadable. That is the one
 *   unrecoverable mistake available in this project.
 *
 *   A Gradle Managed Device sidesteps all of it: an AOSP emulator image is rooted, is created
 *   and torn down by Gradle, and never touches the phone. `useConnectedDevices = false`
 *   below is a safety switch, not a preference - flipping it to true points this at whatever
 *   is plugged in.
 *
 *   Profiles are lists of classes and methods, so one generated on an emulator is valid on
 *   every device. Nothing about the hardware is baked in.
 */
plugins {
    alias(libs.plugins.android.test)
    kotlin("android")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.sc.biap.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Macrobenchmark needs 28+; the app itself still supports 24. This floor applies only
        // to the generator, never to what ships.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        // Matches :app exactly. Left at 11 it mismatched Kotlin's default of 21 and the
        // build refused to compile - the two toolchains have to agree.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.allDevices {
        create<com.android.build.api.dsl.ManagedVirtualDevice>("profileGenerator") {
            device = "Pixel 6"
            apiLevel = 34
            // aosp, not google_apis: the Play-flavoured images are not rootable, and without
            // root the generator cannot read the profile back off the device.
            systemImageSource = "aosp"
        }
    }
}

// Same toolchain as :app, so Java and Kotlin agree on a target.
kotlin {
    jvmToolchain(17)
}

baselineProfile {
    managedDevices += "profileGenerator"
    // The safety switch. True would run this against the plugged-in phone and uninstall the
    // real app to do it - see the file header.
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.benchmark.macro.junit4)
}
