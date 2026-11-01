import java.util.Properties

plugins {
    alias(libs.plugins.musicrecognizer.android.library)
    alias(libs.plugins.musicrecognizer.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Audile+: the Spotify app for the playlist feature, from local.properties (spotify.client.id=...).
// Built in, the user only taps "Log in to Spotify"; empty, they paste their own Client ID instead.
val spotifyClientId = rootProject.file("local.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) }.getProperty("spotify.client.id") }
    .orEmpty().trim()
require(spotifyClientId.matches(Regex("[0-9a-f]{32}|"))) {
    "spotify.client.id in local.properties must be the 32-character Client ID from developer.spotify.com"
}

android {
    namespace = "com.mrsep.musicrecognizer.core.metadata"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.network)

    implementation(libs.kotlinx.serializationJson)
    implementation(libs.stringSimilarity)
    implementation(libs.jsoup)

    testImplementation(libs.junit4)
    testImplementation(libs.kotest)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)

    androidTestImplementation(libs.kotest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}