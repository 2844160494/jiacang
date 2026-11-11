plugins {
    id("com.android.application")
}

android {
    namespace = "dev.local.globalclip"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.local.globalclip"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "2.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
