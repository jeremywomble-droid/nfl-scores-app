plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nflscores.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.nflscores.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.2.2"
    }

    signingConfigs {
        // Stable release signing (added 2026-11-06). The keystore location
        // and passwords come from environment variables so the SAME key
        // signs every build and Android allows in-place updates:
        //   NFL_KEYSTORE_FILE, NFL_KEYSTORE_PASSWORD,
        //   NFL_KEY_ALIAS, NFL_KEY_PASSWORD
        // In CI these are populated from GitHub Actions secrets. Locally,
        // point them at ~/workspace/android/nfl-scores-secrets/.
        create("release") {
            val keystorePath = System.getenv("NFL_KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("NFL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NFL_KEY_ALIAS")
                keyPassword = System.getenv("NFL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Apply the stable signing config only when the keystore env
            // vars are present; never fall back to an ephemeral debug key.
            if (System.getenv("NFL_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
