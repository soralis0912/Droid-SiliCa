plugins {
    id("com.android.application") version "8.3.1"
    id("org.jetbrains.kotlin.android") version "1.9.23"
}

android {
    namespace = "org.soralis.droidsilica"
    compileSdk = 34
    defaultConfig {
        applicationId = "org.soralis.droidsilica"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Local/dev keystore shipped in repository (android.jks)
        create("android") {
            storeFile = file("android.jks")
            storePassword = "android"
            keyAlias = "android"
            keyPassword = "android"
        }

        // Release keystore optionally provided as android/app/release.jks
        // and credentials via environment variables (CI-friendly).
        create("release") {
            storeFile = file("release.jks")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("android")
        }
        release {
            // Prefer release keystore if present; otherwise fall back to android (dev) keystore
            signingConfig = if (file("release.jks").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("android")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
}
