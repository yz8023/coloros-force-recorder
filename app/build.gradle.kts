plugins {
    id("com.android.application")
}

android {
    namespace = "com.xiaokan.qzgflp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xiaokan.qzgflp"
        minSdk = 36
        targetSdk = 36
        versionCode = 104
        versionName = "1.0.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly(files("libs/api-82.jar"))
}
