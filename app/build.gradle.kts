import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.etaris.astrahomework"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.etaris.astrahomework"
        minSdk = 28
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Optional local-only convenience default. Keep real deployments out of VCS:
        // add `homework.defaultUrl=https://your-host` to the ignored local.properties.
        val localProperties = rootProject.file("local.properties")
        val defaultBaseUrl = localProperties.takeIf { it.exists() }?.let { file ->
            Properties().apply { file.inputStream().use { stream -> load(stream) } }
                .getProperty("homework.defaultUrl").orEmpty()
        }.orEmpty()
        buildConfigField("String", "DEFAULT_BASE_URL", "\"$defaultBaseUrl\"")
        // Release metadata used by the in-app update check; matched to GitHub tags.
        buildConfigField("String", "GITHUB_REPO", "\"HwlloChen/AstraHomework\"")
    }

    signingConfigs {
        // CI release builds inject a keystore through environment secrets. Without
        // them the release variant stays debug-signed so local builds keep working.
        val keystore = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (System.getenv("KEYSTORE_FILE").isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                keepRules {
                    includeDefault = true
                    ignoreFromAllExternalDependencies = false
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}