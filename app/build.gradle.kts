plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun escapedBuildConfig(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
val releaseAdMobAppId = providers.environmentVariable("CARCAST_ADMOB_APP_ID").orNull.orEmpty()
val releaseAdMobBannerId = providers.environmentVariable("CARCAST_ADMOB_BANNER_AD_UNIT_ID").orNull.orEmpty()
val keystorePath = providers.environmentVariable("CARCAST_KEYSTORE_PATH").orNull ?: providers.environmentVariable("CARCAST_RELEASE_STORE_FILE").orNull
val keystorePassword = providers.environmentVariable("CARCAST_KEYSTORE_PASSWORD").orNull ?: providers.environmentVariable("CARCAST_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("CARCAST_KEY_ALIAS").orNull ?: providers.environmentVariable("CARCAST_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("CARCAST_KEY_PASSWORD").orNull ?: providers.environmentVariable("CARCAST_RELEASE_KEY_PASSWORD").orNull

android { namespace = "com.carcast.mirror"; compileSdk = 36
    defaultConfig {
        applicationId = "com.carcast.mirror"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "0.9.1-beta"
        buildConfigField("String", "ADMOB_APP_ID", escapedBuildConfig("ca-app-pub-3940256099942544~3347511713"))
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", escapedBuildConfig("ca-app-pub-3940256099942544/9214589741"))
        buildConfigField("Boolean", "ADS_CONFIGURED", "true")
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }
    signingConfigs {
        val storeFilePath = keystorePath
        if (!storeFilePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            buildConfigField("String", "ADMOB_APP_ID", escapedBuildConfig("ca-app-pub-3940256099942544~3347511713"))
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", escapedBuildConfig("ca-app-pub-3940256099942544/9214589741"))
            buildConfigField("Boolean", "ADS_CONFIGURED", "true")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!keystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "ADMOB_APP_ID", escapedBuildConfig(releaseAdMobAppId))
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", escapedBuildConfig(releaseAdMobBannerId))
            buildConfigField("Boolean", "ADS_CONFIGURED", (releaseAdMobAppId.isNotBlank() && releaseAdMobBannerId.isNotBlank()).toString())
            manifestPlaceholders["admobAppId"] = releaseAdMobAppId
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    buildFeatures { buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"; resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"; resources.excludes += "META-INF/DEPENDENCIES" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    testImplementation("junit:junit:4.13.2")
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.github.webrtc-sdk:android:125.6422.07")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
