import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.ksp)
}


android {
    namespace = "com.fanta.androidsport"
    compileSdk = 36

    // Read .env file
    val envFile = project.rootProject.file(".env")
    val envProperties = Properties()
    if (envFile.exists()) {
        envFile.inputStream().use { envProperties.load(it) }
    }
    val supabaseUrl = envProperties.getProperty("SUPABASE_URL") ?: ""
    val supabaseKey = envProperties.getProperty("SUPABASE_PUBLISHABLE_KEY") ?: ""
    val mapboxToken = envProperties.getProperty("MAPBOX_PUBLIC_TOKEN") ?: ""

    defaultConfig {
        applicationId = "com.fanta.androidsport"
        minSdk = 35
        targetSdk = 36
        versionCode = 23
        versionName = "4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabaseKey\"")
        buildConfigField("String", "MAPBOX_PUBLIC_TOKEN", "\"$mapboxToken\"")
    }

    val envKeystorePath = envProperties.getProperty("KEYSTORE_PATH") ?: System.getenv("KEYSTORE_PATH") ?: "${System.getProperty("user.home")}/androidsport-release.jks"
    val envKeystorePassword = envProperties.getProperty("KEYSTORE_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
    val envKeyAlias = envProperties.getProperty("KEY_ALIAS") ?: System.getenv("KEY_ALIAS") ?: "sport-key"
    val envKeyPassword = envProperties.getProperty("KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD") ?: ""

    signingConfigs {
        create("release") {
            storeFile = file(envKeystorePath)
            storePassword = envKeystorePassword
            keyAlias = envKeyAlias
            keyPassword = envKeyPassword
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
            signingConfig = signingConfigs.getByName("release")
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
}

base {
    archivesName.set("arpentio")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.material)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Activity & Lifecycle & Navigation
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Supabase & Ktor & Serialization
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)

    // Mapbox SDK & Compose Extension
    implementation(libs.mapbox.sdk)
    implementation(libs.mapbox.compose)

    // Image Caching with Coil
    implementation(libs.coil.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Play Services Location (Fused Location Client)
    implementation(libs.play.services.location)

    // Tooling support (Previews)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}