plugins {
    id("com.android.application")
}

android {
    namespace = "com.randompin.xposed"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.randompin.xposed"
        minSdk = 29
        targetSdk = 35
        versionCode = 17
        versionName = "1.7"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore.jks")
            storePassword = "randompin"
            keyAlias = "randompin"
            keyPassword = "randompin"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
