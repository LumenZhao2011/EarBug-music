import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 每次构建自动递增构建号：主版本 1.1.x，versionCode 同步递增
val versionFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionFile.exists()) {
    versionFile.inputStream().use { versionProps.load(it) }
}
val buildNumber = (versionProps.getProperty("buildNumber") ?: "0").toInt() + 1
versionProps.setProperty("buildNumber", buildNumber.toString())
versionFile.outputStream().use { versionProps.store(it, "auto incremented by gradle") }
val appVersionName = "1.1." + buildNumber

android {
    namespace = "com.zhaoguanghao.earbugmusic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zhaoguanghao.earbugmusic"
        minSdk = 24
        targetSdk = 34
        versionCode = 10000 + buildNumber
        versionName = appVersionName
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
