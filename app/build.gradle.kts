import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Подпись release: сначала переменные окружения (GitHub Actions), потом app/key.properties (локально).
val keyProps = Properties().apply {
    val f = rootProject.file("app/key.properties")
    if (f.exists()) load(FileInputStream(f))
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keyProps.getProperty(prop)

val releaseStoreFile = signingValue("ANDROID_KEYSTORE_FILE", "storeFile")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists()

val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = "${project.property("appVersionName")}.$appVersionCode"

android {
    namespace = "com.kissedcode.pixel1keeper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kissedcode.pixel1keeper"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        // Точка отсчёта: 2026-10-08T00:00:00+02:00 (Europe/Luxembourg)
        buildConfigField("long", "CUTOFF_MS", "1791410400000L")
        // Необязательные значения по умолчанию для Telegram из GitHub Secrets (в UI их можно перекрыть).
        buildConfigField("String", "TG_TOKEN", "\"${System.getenv("TELEGRAM_BOT_TOKEN").orEmpty()}\"")
        buildConfigField("String", "TG_CHAT_ID", "\"${System.getenv("TELEGRAM_CHAT_ID").orEmpty()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
