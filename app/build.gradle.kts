plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.stitchcraft.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stitchcraft.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 115
        versionName = "1.0.0-rc8-billing-fix2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("stableDebug") {
            // Test-only key committed so GitHub Actions debug APKs can update each other.
            // Production releases MUST use a private release key stored outside the repository.
            storeFile = file("stitchcraft-test-debug.keystore")
            storePassword = "stitchcraft-test"
            keyAlias = "stitchcraft-test"
            keyPassword = "stitchcraft-test"
        }

        create("releaseUpload") {
            // Google Play upload key is supplied only through environment variables / GitHub Secrets.
            // No production password or keystore is committed to the repository.
            val keystorePath = System.getenv("STITCHCRAFT_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("STITCHCRAFT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("STITCHCRAFT_KEY_ALIAS")
                keyPassword = System.getenv("STITCHCRAFT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!System.getenv("STITCHCRAFT_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
