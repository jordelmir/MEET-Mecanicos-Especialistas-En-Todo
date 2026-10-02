plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jsm.core"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("InvalidFragmentVersionForActivityResult")
    }
}

dependencies {
    implementation(project(":app"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.window:window:1.3.0")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("io.github.webrtc-sdk:android:125.6422.07")
    implementation("com.google.protobuf:protobuf-javalite:3.25.1")
    implementation("com.google.android.play:feature-delivery:2.1.0")
}
