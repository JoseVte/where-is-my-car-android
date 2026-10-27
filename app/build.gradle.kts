import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

val properties = Properties()
val localPropertiesFile: File = project.rootProject.file("local.properties")
properties.load(localPropertiesFile.inputStream())
val googleApiKey: String = properties.getProperty("google.api.key")
// Base URL of the Where Is My Car API. 10.0.2.2 is the host machine as seen from the emulator.
// Override it in local.properties with api.base.url (for example your Mac's IP when using a real device).
val apiBaseUrl: String = properties.getProperty("api.base.url", "http://10.0.2.2:8080/")
// Release builds talk to the production API (override with api.base.url.release)
val apiBaseUrlRelease: String = properties.getProperty("api.base.url.release", "https://api-where-is-my-car.josrom.io/")


android {
    namespace = "io.josrom.whereismycar"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.josrom.whereismycar"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_API_KEY", googleApiKey)
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    flavorDimensions += "device"
    productFlavors {
        create("mobile") {
            dimension = "device"
        }
        create("wear") { dimension = "device" }
    }

    buildTypes {
        release {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlRelease\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "LICENSE_OFL"
            excludes += "LICENSE_UNICODE"
        }
    }
    useLibrary("wear-sdk")

    configurations.all {
        exclude(group = "com.j256.ormlite", module = "ormlite-core")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size.class1)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.osmdroid.android)
    implementation(libs.osmbonuspack)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.preference)
    implementation(libs.play.services.auth)
    implementation(libs.gson)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // App Check: Play Integrity in release, debug provider (token in logcat) in debug builds
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.recyclerview)

    implementation(libs.colorpickerview)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.emoji2.bundled)
    
    implementation(libs.glide)

    // API client
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}