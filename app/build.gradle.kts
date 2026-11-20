import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "net.bitplane.android.microphone"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.bitplane.android.microphone"
        minSdk = 23
        targetSdk = 36
        // CI passes these from the release tag (see .github/workflows/release.yml).
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 9
        versionName = providers.gradleProperty("versionName").orNull ?: "0.9"
    }

    // Release signing comes from the environment so the keystore never lives in the repo.
    val keystorePath = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("SIGNING_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
}
