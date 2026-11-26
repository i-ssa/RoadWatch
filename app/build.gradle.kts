import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val serviceProperties = Properties().apply {
    val file = rootProject.file("roadwatch.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseProperties = Properties().apply {
    val file = rootProject.file(".signing/release.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun serviceSetting(name: String): String =
    sequenceOf(serviceProperties.getProperty(name), System.getenv(name), localProperties.getProperty(name))
        .map { it.orEmpty().trim() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

fun buildConfigString(value: String): String = "\"" + value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n") + "\""

val mapsApiKey = serviceSetting("MAPS_API_KEY")

android {
    namespace = "com.example.roadwatch"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.roadwatch"
        providers.gradleProperty("roadwatchAbi").orNull?.let { phoneAbi ->
            ndk { abiFilters += phoneAbi }
        }
        minSdk = 26
        targetSdk = 37
        versionCode = 16
        versionName = "1.2.10"
        buildConfigField("String", "GEMINI_API_KEY", buildConfigString(serviceSetting("GEMINI_API_KEY")))
        buildConfigField("String", "SUPABASE_URL", buildConfigString(serviceSetting("SUPABASE_URL")))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildConfigString(serviceSetting("SUPABASE_PUBLISHABLE_KEY")))

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "MAPS_CONFIGURED", mapsApiKey.isNotBlank().toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseProperties.isNotEmpty()) create("roadwatchRelease") {
            storeFile = rootProject.file(releaseProperties.getProperty("storeFile"))
            storePassword = releaseProperties.getProperty("storePassword")
            keyAlias = releaseProperties.getProperty("keyAlias")
            keyPassword = releaseProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            if (releaseProperties.isNotEmpty()) signingConfig = signingConfigs.getByName("roadwatchRelease")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Compress native libraries so one APK can include every supported ABI and fit Drive uploads.
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.maps.android:maps-compose:6.12.0")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.6"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-okhttp:3.3.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
