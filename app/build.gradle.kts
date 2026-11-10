plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val chatVersePostHogApiKey = providers.gradleProperty("CHATVERSE_POSTHOG_API_KEY")
    .orElse(providers.environmentVariable("CHATVERSE_POSTHOG_API_KEY"))
    .getOrElse("")
val chatVersePostHogHost = providers.gradleProperty("CHATVERSE_POSTHOG_HOST")
    .orElse(providers.environmentVariable("CHATVERSE_POSTHOG_HOST"))
    .getOrElse("")

android {
    namespace = "com.chatverse.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chatverse.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"https://mpwlhlvxvvplzjtgasic.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_6QT8b1Ji75OaDQ9XCIiN7g_i1JHiZuC\"")
        buildConfigField("String", "POSTHOG_API_KEY", "\"$chatVersePostHogApiKey\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$chatVersePostHogHost\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.posthog:posthog-android:3.+")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
