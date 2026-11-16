plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
}


android {
    namespace = "com.clipvault.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clipvault.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "1.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Room schema export for migration verification
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }
    }

    // Configurable Production Release Signing via Environment Variables or local gradle.properties
    // Never hardcodes or commits private signing secrets.
    val keystorePath = System.getenv("CLIPVAULT_KEYSTORE_PATH")
        ?: (project.findProperty("CLIPVAULT_KEYSTORE_PATH") as? String)
    val keystorePassword = System.getenv("CLIPVAULT_KEYSTORE_PASSWORD")
        ?: (project.findProperty("CLIPVAULT_KEYSTORE_PASSWORD") as? String)
    val keyAliasName = System.getenv("CLIPVAULT_KEY_ALIAS")
        ?: (project.findProperty("CLIPVAULT_KEY_ALIAS") as? String)
    val keyAliasPassword = System.getenv("CLIPVAULT_KEY_PASSWORD")
        ?: (project.findProperty("CLIPVAULT_KEY_PASSWORD") as? String)
    val hasReleaseSigningCredentials = !keystorePath.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAliasName.isNullOrBlank() &&
        !keyAliasPassword.isNullOrBlank() &&
        file(keystorePath).exists()

    signingConfigs {
        if (hasReleaseSigningCredentials) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyAliasPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningCredentials) {
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
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Jetpack Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // Room Database with FTS4
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Biometric Authentication
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // AndroidX Security Crypto (Hardware-backed Keystore & AES-256 GCM)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // WorkManager for background cleanup & scheduled retention
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Unit Testing (JUnit 4 + Kotlinx Coroutines Test)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
