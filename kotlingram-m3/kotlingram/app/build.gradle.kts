plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.example.tg"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.example.tg"
        // 29: MediaRecorder умеет OGG/Opus (формат голосовых Telegram) только с API 29
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Картинки (локальные файлы TDLib)
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Воспроизведение голосовых/аудио/видео
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Предсобранный TDLib (JNI + обёртка org.drinkless.tdlib), через JitPack
    implementation("com.github.fazilus:tdlib-android:1.8.61")
}
