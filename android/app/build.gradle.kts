import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// --- Optional Firebase wiring ------------------------------------------------
// google-services.json is intentionally NOT committed (it is project-specific and
// listed in .gitignore). The Google Services plugin is applied only when a config
// file is actually present, so a fresh clone still builds and runs fully offline
// with the local auth/sync implementations.
val googleServicesConfig = file("google-services.json")
val firebaseEnabled = googleServicesConfig.exists()
if (firebaseEnabled) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// --- Optional release signing ------------------------------------------------
// Credentials live in keystore.properties (git-ignored) or, on CI, in environment
// variables. Nothing secret is ever committed or embedded in the source tree.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun secret(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = secret("storeFile", "AMA_STORE_FILE")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists()

android {
    namespace = "com.aimasteracademy.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.aimasteracademy.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.aimasteracademy.app.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true

        // Room exports its schema so migrations can be diffed in code review and
        // verified by MigrationTestHelper.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.generateKotlin", "true")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = secret("storePassword", "AMA_STORE_PASSWORD")
                keyAlias = secret("keyAlias", "AMA_KEY_ALIAS")
                keyPassword = secret("keyPassword", "AMA_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            buildConfigField("String", "TUTOR_API_BASE_URL", "\"https://api.aimasteracademy.com/v1/\"")
            buildConfigField("String", "CONTENT_API_BASE_URL", "\"https://content.aimasteracademy.com/v1/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "TUTOR_API_BASE_URL", "\"https://api.aimasteracademy.com/v1/\"")
            buildConfigField("String", "CONTENT_API_BASE_URL", "\"https://content.aimasteracademy.com/v1/\"")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // A single BuildConfig flag lets the DI layer pick cloud or local-only
    // implementations without sprinkling `try/catch` around Firebase calls.
    defaultConfig {
        buildConfigField("boolean", "FIREBASE_ENABLED", firebaseEnabled.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    bundle {
        language { enableSplit = false } // keep all locales so in-app language switching works
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        disable += setOf("GradleDependency", "NewerVersionAvailable")
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(project(":domain"))

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.browser)

    // Persistence
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // Background work
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)

    // Firebase (optional at runtime — see FIREBASE_ENABLED)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity)

    // Media
    implementation(libs.coil.compose)
    implementation(libs.lottie.compose)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)

    // Instrumentation tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    kspAndroidTest(libs.hilt.compiler)
}
