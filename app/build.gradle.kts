plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { namespace = "com.carcast.mirror"; compileSdk = 36
    defaultConfig { applicationId = "com.carcast.mirror"; minSdk = 24; targetSdk = 36; versionCode = 2; versionName = "0.9.0-beta1" }
    signingConfigs {
        val storeFilePath = providers.environmentVariable("CARCAST_RELEASE_STORE_FILE").orNull
        if (!storeFilePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.environmentVariable("CARCAST_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("CARCAST_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("CARCAST_RELEASE_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        debug { isDebuggable = true; isMinifyEnabled = false }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (providers.environmentVariable("CARCAST_RELEASE_STORE_FILE").orNull?.isNotBlank() == true) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"; resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"; resources.excludes += "META-INF/DEPENDENCIES" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    testImplementation("junit:junit:4.13.2")
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.github.webrtc-sdk:android:125.6422.07")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
