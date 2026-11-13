// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val liteSourceHead = providers.exec {
    workingDir(rootDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.get().trim()
val liteSourceDirty = providers.exec {
    workingDir(rootDir)
    commandLine("git", "status", "--porcelain")
}.standardOutput.asText.get().isNotBlank()
val liteSourceRevision = liteSourceHead + if (liteSourceDirty) "-dirty" else ""
android {
    namespace = "com.zhuhuibin.hyperchange.lite"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.zhuhuibin.hyperchange.lite"
        minSdk = 35
        targetSdk = 37
        versionCode = 106000
        versionName = "0.1.6"
        manifestPlaceholders["liteSourceRevision"] = liteSourceRevision
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
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources { merges += "META-INF/xposed/*" } }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.github.libxposed:api:102.0.0")
    testImplementation("org.json:json:20240303")
}
