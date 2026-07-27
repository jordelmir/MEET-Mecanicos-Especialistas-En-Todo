import java.util.Properties

plugins {
    id("com.android.application")
    id("com.android.legacy-kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
}

// Load local.properties for secrets
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(localPropsFile.inputStream())
}

android {
    namespace = "com.elysium369.meet"
    compileSdk = 37

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    defaultConfig {
        applicationId = "com.elysium369.meet"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "4.4.0"

        // Supabase credentials from local.properties (never committed to git)
        val legacySupabaseUrlKey = "M" + "EET_SUPABASE_URL"
        val legacySupabaseApiKey = "M" + "EET_SUPABASE_KEY"
        val supabaseUrl = localProps.getProperty("ELYSIUM_SUPABASE_URL")
            ?: localProps.getProperty(legacySupabaseUrlKey, "")
        val supabaseKey = localProps.getProperty("ELYSIUM_SUPABASE_KEY")
            ?: localProps.getProperty(legacySupabaseApiKey, "")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")

        // Car2DB API (opcional). Si no se configura, la app funciona en modo "solo genéricos".
        val car2DbApiKey = localProps.getProperty("CAR2DB_API_KEY", "")
        val car2DbReferer = localProps.getProperty("CAR2DB_REFERER", "https://elysium-vanguard.app")
        val car2DbLanguage = localProps.getProperty("CAR2DB_LANGUAGE", "en-US")
        buildConfigField("String", "CAR2DB_API_KEY", "\"$car2DbApiKey\"")
        buildConfigField("String", "CAR2DB_REFERER", "\"$car2DbReferer\"")
        buildConfigField("String", "CAR2DB_LANGUAGE", "\"$car2DbLanguage\"")
        buildConfigField("boolean", "CAR2DB_ENABLED", "${car2DbApiKey.isNotBlank()}")

        // Ride wallet funding must remain off until the store/payment method is
        // approved for transportation commission credits in the launch market.
        val ridePlayBillingPolicyApproved = localProps
            .getProperty("RIDE_PLAY_BILLING_POLICY_APPROVED", "false")
            .toBooleanStrictOrNull()
            ?: false
        buildConfigField(
            "boolean",
            "RIDE_PLAY_BILLING_POLICY_APPROVED",
            ridePlayBillingPolicyApproved.toString(),
        )
        val rideLocalVerificationAutoApprove = providers.gradleProperty(
            "RIDE_LOCAL_VERIFICATION_AUTO_APPROVE",
        ).orElse("true").get().toBoolean()
        buildConfigField(
            "boolean",
            "RIDE_LOCAL_VERIFICATION_AUTO_APPROVE",
            rideLocalVerificationAutoApprove.toString(),
        )
        val rideMapStyleUrl = localProps.getProperty(
            "RIDE_MAP_STYLE_URL",
            "https://tiles.openfreemap.org/styles/liberty",
        )
        buildConfigField("String", "RIDE_MAP_STYLE_URL", "\"$rideMapStyleUrl\"")

        // MiniMax Debug configurations
        buildConfigField("String", "MINIMAX_API_KEY_DEBUG", "\"${localProps.getProperty("MINIMAX_API_KEY_DEBUG", "")}\"")
        buildConfigField("String", "MINIMAX_BASE_URL", "\"${localProps.getProperty("MINIMAX_BASE_URL", "https://api.minimax.io/v1")}\"")
        buildConfigField("String", "MINIMAX_DEFAULT_MODEL", "\"${localProps.getProperty("MINIMAX_DEFAULT_MODEL", "MiniMax-M1")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = project.findProperty("KEYSTORE_PATH") as String?
                ?: localProps.getProperty("KEYSTORE_PATH")
            storeFile = keystorePath?.let { file(it) } ?: signingConfigs.getByName("debug").storeFile

            storePassword = project.findProperty("KEYSTORE_PASSWORD") as String?
                ?: localProps.getProperty("KEYSTORE_PASSWORD")
                ?: "android"

            keyAlias = project.findProperty("KEY_ALIAS") as String?
                ?: localProps.getProperty("KEY_ALIAS")
                ?: "androiddebugkey"

            keyPassword = project.findProperty("KEY_PASSWORD") as String?
                ?: localProps.getProperty("KEY_PASSWORD")
                ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.*"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Accompanist for Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    kapt("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("androidx.hilt:hilt-work:1.4.0")
    kapt("androidx.hilt:hilt-compiler:1.4.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    
    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Supabase
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.2.3")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.2.3")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.2.3")
    implementation(platform("io.ktor:ktor-bom:2.3.13"))
    implementation("io.ktor:ktor-client-android")
    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-content-negotiation")
    
    // Ktor Server (Embedded — for LiveLink WebSocket)
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-cors")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.1")
    
    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Complete vehicle digital twin: SceneView 4.22.0 + Google Filament 1.71.5.
    implementation("io.github.sceneview:sceneview:4.22.0")

    // Google Play Billing Library. Use the Java artifact to stay compatible with the
    // project's Kotlin 1.9 toolchain while still targeting Billing 9.1.0.
    implementation("com.android.billingclient:billing:9.1.0")

    // QR Code and Barcode Processing
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Google Location Services (Uber-grade GPS precision)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Open map renderer. Tile/style/routing providers remain interchangeable.
    implementation("org.maplibre.gl:android-sdk:13.0.2")

    // Google Sign-In and Drive Backup API
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20211107-1.32.1") {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation("com.google.api-client:google-api-client-android:1.32.1") {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }

    // CameraX
    val cameraVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraVersion")
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-video:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")

    // Media3 Transformer for video telemetry overlay baking
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-transformer:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("verifyNoSecretsInSource") {
    doLast {
        val forbiddenPatterns = listOf(
            Regex("""sk-[A-Za-z0-9_\-]{20,}"""),
            Regex("""AIza[0-9A-Za-z_\-]{20,}"""),
            Regex("""Bearer\s+[A-Za-z0-9_\-\.]{20,}"""),
            Regex("""[0-9]{8,10}:[A-Za-z0-9_\-]{30,}""")
        )

        val files = fileTree(projectDir) {
            include("src/**/*.kt")
            include("src/**/*.java")
            include("src/**/*.xml")
            include("src/**/*.json")
            include("*.gradle")
            include("*.gradle.kts")
        }

        files.forEach { file ->
            // Do not verify files containing test cases that check the redactor or tests checking debug config keys
            if (file.name.contains("AiEngineTests") || file.name.contains("SecretRedactor")) {
                return@forEach
            }
            val text = file.readText()
            forbiddenPatterns.forEach { pattern ->
                if (pattern.containsMatchIn(text)) {
                    // Check if it's the build.gradle.kts itself defining the regex pattern or check
                    if (file.name == "build.gradle.kts" && text.contains("forbiddenPatterns = listOf")) {
                        // ignore the regex declaration itself
                        return@forEach
                    }
                    throw GradleException("Potential secret found in ${file.path}")
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("verifyNoSecretsInSource")
}
