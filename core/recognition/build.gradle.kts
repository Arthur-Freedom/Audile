@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.LibraryVariant
import com.mrsep.musicrecognizer.CargoNdkBuildTask
import java.util.Properties

plugins {
    alias(libs.plugins.musicrecognizer.android.library)
    alias(libs.plugins.musicrecognizer.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Audile+: the AudD and ACRCloud keys from local.properties (the same quoted entries core:datastore
// reads), for trying the other services when the chosen one finds nothing. They end up readable
// in the APK, so never share a build that has them.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun builtInKey(name: String) = localProperties.getProperty(name).orEmpty().trim().removeSurrounding("\"").also {
    require(it.matches(Regex("[A-Za-z0-9._-]*"))) { "$name in local.properties has unexpected characters" }
}

android {
    namespace = "com.mrsep.musicrecognizer.core.recognition"
    ndkVersion = libs.versions.ndk.get()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AUDD_API_TOKEN", "\"${builtInKey("audd.api.token")}\"")
        buildConfigField("String", "ACR_HOST", "\"${builtInKey("acr.cloud.host")}\"")
        buildConfigField("String", "ACR_ACCESS_KEY", "\"${builtInKey("acr.cloud.access.key")}\"")
        buildConfigField("String", "ACR_ACCESS_SECRET", "\"${builtInKey("acr.cloud.access.secret")}\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }
}

// Build SongRec fingerprinting module

val rustCrateDir = layout.projectDirectory.dir("native/songrecfp")
val rustOutputRoot = layout.buildDirectory.dir("generated/rust/jniLibs")
val rustTargetRoot = layout.buildDirectory.dir("rust-target")

val abiFiltersForRust = android.defaultConfig.ndk.abiFilters.toList()
val minSdkVersion = libs.versions.sdkMin.get().toInt()

androidComponents.onVariants { variant: LibraryVariant ->
    val taskName = "build${variant.name.replaceFirstChar(Char::uppercaseChar)}SongRecFP"
    val buildRust = tasks.register<CargoNdkBuildTask>(taskName) {
        group = "build"
        description = "Build SongRecFP Rust library for ${variant.name}"

        crateDir = rustCrateDir
        variantName = variant.name
        buildProfile = if (variant.buildType == "release") "release" else "debug"
        minSdk = minSdkVersion
        abis = abiFiltersForRust
        outputDir = rustOutputRoot.map { it.dir(variant.name) }
        cargoTargetDir = rustTargetRoot.map { it.dir(variant.name) }
        ndkPath = androidComponents.sdkComponents.ndkDirectory.get().asFile.absolutePath
    }
    variant.sources.jniLibs?.addGeneratedSourceDirectory(buildRust, CargoNdkBuildTask::outputDir)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.audio)
    implementation(projects.core.metadata)

    implementation(libs.kotlinx.serializationJson)
    implementation(libs.uuidCreator)

    testImplementation(libs.junit4)
    testImplementation(libs.kotest)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockWebServer)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.slf4j.simple)

    androidTestImplementation(libs.kotest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}