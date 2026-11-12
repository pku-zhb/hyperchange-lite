// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("com.android.application")
}
android {
    namespace = "com.zhuhuibin.hyperchange.lite"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.zhuhuibin.hyperchange.lite"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-preview1"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources { merges += "META-INF/xposed/*" } }
}
dependencies {
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.github.libxposed:api:102.0.0")
    testImplementation("org.json:json:20240303")
}
