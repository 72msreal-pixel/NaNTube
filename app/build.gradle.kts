plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// version nằm ở file version.txt ngoài thư mục gốc, dạng 00.00.01
val appVersion = rootProject.file("version.txt").readText().trim()
val parts = appVersion.split(".").map { it.toInt() }
val appVersionCode = parts[0] * 10000 + parts[1] * 100 + parts[2]

android {
    namespace = "com.nan.nantube"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nan.nantube"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        create("nan") {
            storeFile = rootProject.file("keystore/nantube.p12")
            storeType = "pkcs12"
            storePassword = "nan6a165de50cf77c23"
            keyAlias = "nantube"
            keyPassword = "nan6a165de50cf77c23"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("nan")
            isMinifyEnabled = false
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.composables:icons-lucide:1.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    // Phát trực tiếp (livestream) qua HLS.
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")
    implementation("androidx.media3:media3-database:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Phiên media + thông báo phát nhạc nền (bảng điều khiển nhanh, màn khóa, tai nghe).
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Đường tải phụ đề giống meetube (OkHttp, HTTP/2) thay cho HttpURLConnection.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.javascriptengine:javascriptengine:1.0.0")
    // Mã hóa MP3 (LAME) cho mục Tải âm thanh > MP3.
    // Bản 1.1 kéo theo com.android.support 26.1.0, trùng class với AndroidX nên phải loại ra.
    implementation("com.github.naman14:TAndroidLame:1.1") {
        exclude(group = "com.android.support")
    }
}
