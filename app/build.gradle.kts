plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.helper.captchaalarm"
    // compileSdk 仅影响编译期；targetSdk(=28) 才决定运行时行为(FGS/ scoped storage 限制)。
    // 提到 33 以满足 androidx 依赖的 minCompileSdk 要求。
    compileSdk = 33

    defaultConfig {
        applicationId = "com.helper.captchaalarm"
        minSdk = 23
        targetSdk = 28          // 与设备 Android 9 对齐，规避 Android 10+ FGS/scoped storage 限制
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
