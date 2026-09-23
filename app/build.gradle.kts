plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.motoeq.app"
    // Motorola one 5G (XT2075-2) ships Android 11 (API 30); we target it exactly
    // but compile against a newer SDK for tooling support.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.motoeq.app"
        minSdk = 26          // AudioEffect session broadcasts are reliable from API 26+
        targetSdk = 30       // matches your device's OS to avoid new-target-SDK behavior changes
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
