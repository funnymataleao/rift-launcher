// Artemis streaming core: nvstream (pairing, serverinfo, applist, launch, connection), the MediaCodec
// decoder, audio and crypto bindings, PC database and polling service, mDNS discovery and the
// moonlight-common-c JNI library. Taken from ClassicOldSong/moonlight-android (branch moonlight-noir,
// commit c5cf27f4dc822db0e863c4691e7a70c74bea977a), GPL-3.0. Java packages stay com.limelight.* so
// upstream fixes can be diffed in.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.limelight.core"
    compileSdk = 36
    ndkVersion = "27.3.13750724"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            ndkBuild {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
}

dependencies {
    api("org.bouncycastle:bcprov-jdk18on:1.81")
    api("org.bouncycastle:bcpkix-jdk18on:1.81")
    implementation("org.jcodec:jcodec:0.2.5")
    // Same OkHttp as the app, so only one version ends up in the APK.
    api(libs.okhttp)
    implementation("org.jmdns:jmdns:3.6.2")
    implementation("androidx.annotation:annotation:1.9.1")
}
