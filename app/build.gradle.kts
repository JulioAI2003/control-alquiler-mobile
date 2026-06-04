// ─── app/build.gradle.kts ────────────────────────────────────────────────────
// Proyecto: MyApplication   Paquete: com.example.myapplication
// Stack: Kotlin 2.0.21 · Compose BOM 2025 · Retrofit · DataStore · Serialization
// ─────────────────────────────────────────────────────────────────────────────
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Kotlin 2.0+: el compilador de Compose es un plugin separado
    alias(libs.plugins.kotlin.compose)
    // Para @Serializable en los modelos de datos
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace  = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk        = 26          // java.time.LocalDate sin desugaring
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // ── URL del backend ──────────────────────────────────────────────────
        // Local:
        // buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:4000/api/\"")
        
        // Producción (Usa la misma que en tu front de Angular):
        buildConfigField("String", "BASE_URL", "\"https://control-alquiler-backend.vercel.app/api/\"")
    }

    buildTypes {
        debug {
            // Para pruebas contra el backend real:
            buildConfigField("String", "BASE_URL", "\"https://control-alquiler-backend.vercel.app/api/\"")
            // buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:4000/api/\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://control-alquiler-backend.vercel.app/api/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    buildFeatures {
        compose     = true
        buildConfig = true   // genera BuildConfig con BASE_URL
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // ── Core ──────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.material)

    // ── Compose BOM (fija versiones coherentes de todo Compose) ───────────────
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)         // íconos extendidos

    // ── Navigation + ViewModel ────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.viewmodel.compose)
    implementation(libs.androidx.lifecycle.compose)     // collectAsStateWithLifecycle

    // ── Red ───────────────────────────────────────────────────────────────────
    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)                 // logs de peticiones en debug

    // ── Serialización ─────────────────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization) // converter para Retrofit

    // ── Persistencia de sesión ────────────────────────────────────────────────
    implementation(libs.androidx.datastore)             // guarda el JWT localmente

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── WorkManager (notificaciones locales en segundo plano) ─────────────────
    implementation(libs.androidx.work.runtime)

    // ── Tests ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
