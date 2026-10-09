plugins {
    id("com.android.application")
}

android {
    namespace = "com.xiaokan.qzgflp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xiaokan.qzgflp"
        minSdk = 28
        targetSdk = 36
        versionCode = 110
        versionName = "1.1.0"
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
    compileOnly(files("libs/libxposed-api-102.0.0.jar", "libs/annotation-1.0.0.jar"))
}
