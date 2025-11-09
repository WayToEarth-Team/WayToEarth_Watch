import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Load keystore.properties for release signing (if present)
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {
    namespace = "cloud.waytoearth.watch"
    compileSdk = 36

    defaultConfig {
        applicationId = "cloud.waytoearth"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val sFile = keystoreProps.getProperty("storeFile")
            val sPass = keystoreProps.getProperty("storePassword")
            val kAlias = keystoreProps.getProperty("keyAlias")
            val kPass = keystoreProps.getProperty("keyPassword")
            if (sFile != null && sPass != null && kAlias != null && kPass != null) {
                storeFile = rootProject.file(sFile)
                storePassword = sPass
                keyAlias = kAlias
                keyPassword = kPass
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    useLibrary("wear-sdk")

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)

    // Wear OS
    implementation("androidx.wear:wear:1.3.0")
    implementation("com.google.android.support:wearable:2.9.0")
    compileOnly("com.google.android.wearable:wearable:2.9.0")

    // Google Play Services - Location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Health Services
    implementation("androidx.health:health-services-client:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Debug
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}