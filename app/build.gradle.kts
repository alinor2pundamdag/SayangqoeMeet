plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sayangqoemeet.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sayangqoemeet.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val localProperties = java.util.Properties().apply {
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) localFile.inputStream().use { load(it) }
    }
    val supabaseUrl = (System.getenv("SUPABASE_URL") ?: localProperties.getProperty("SUPABASE_URL") ?: "https://ktzvfnjcfjaiqhwrohel.supabase.co")
    val supabaseKey = (System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY") ?: "")
    buildConfigField("String", "SUPABASE_URL", "\\"$supabaseUrl\\"")
    buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\\"$supabaseKey\\"")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.03.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Supabase Kotlin SDK: Auth uses Supabase-managed password hashing and sessions.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.6"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.2.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
