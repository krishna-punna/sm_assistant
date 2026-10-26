plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release builds (the Release workflow) pass the version and the signing key in the environment.
val releaseKeystore: String? = System.getenv("SM_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.smassistant.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smassistant.app"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("SM_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("SM_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseKeystore?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("SM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SM_KEY_ALIAS")
                keyPassword = System.getenv("SM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Without a keystore the release APK is signed with the debug key, so it still installs for testing.
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric reads assets (app.yml) and resources from the merged build.
        unitTests.isIncludeAndroidResources = true
        // Robolectric UI tests keep a lot in memory; Gradle's 512 MB default runs out (OutOfMemoryError in CI).
        unitTests.all { it.maxHeapSize = "2g" }
    }
    lint {
        abortOnError = true
        checkDependencies = false
        // New library versions appear constantly; don't fail CI on them.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

// APKs are named after the app, e.g. SMAssistant-0.1.0-debug.apk.
base {
    archivesName.set("SMAssistant-${android.defaultConfig.versionName}")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.snakeyaml)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidsvg)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
