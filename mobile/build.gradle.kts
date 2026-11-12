import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Strava API credentials live in local.properties (never committed):
//   strava.clientId=12345
//   strava.clientSecret=abcdef...
//   nightscout.url=https://yoursite.example.com   (optional)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.sugarmiles.app"
    compileSdk = 35

    defaultConfig {
        // MUST be identical to the wear app's applicationId, otherwise the
        // phone and watch can't talk to each other over the Data Layer.
        applicationId = "com.sugarmiles.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "STRAVA_CLIENT_ID", "\"${localProps.getProperty("strava.clientId", "")}\"")
        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"${localProps.getProperty("strava.clientSecret", "")}\"")
        // Optional: prefills the Nightscout URL in the app (the token is only entered in the app).
        buildConfigField("String", "NIGHTSCOUT_URL", "\"${localProps.getProperty("nightscout.url", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sign release with the debug key so phone + watch builds always match.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
