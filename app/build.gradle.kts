import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "me.yummydroid.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.yummydroid.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 117
        versionName = "1.1.7"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    kotlin {
        jvmToolchain(21)
    }
}

android.applicationVariants.all {
    outputs.all {
        val variantOutput = this as BaseVariantOutputImpl
        val version = versionName.orEmpty().ifBlank { "dev" }
        variantOutput.outputFileName = if (buildType.name == "release") {
            "YummyDroid-$version.apk"
        } else {
            "YummyDroid-$version-${buildType.name}.apk"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.github.hCaptcha.hcaptcha-android-sdk:sdk:5.0.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("me.saket.telephoto:zoomable:0.19.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
}
