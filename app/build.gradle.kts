plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.vovradio.car"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.vovradio.car"
        minSdk = 21          // chạy được trên đầu Android ô tô đời cũ (Android 5+)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ký bằng khóa debug để APK release cài được ngay (dùng cá nhân)
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.guava:guava:33.2.1-android")
}
