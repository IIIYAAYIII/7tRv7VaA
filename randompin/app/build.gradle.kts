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
        versionCode = 13
        versionName = "1.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
