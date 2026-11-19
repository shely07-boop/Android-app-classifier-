plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.sheyla_arteaga_lab15"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.sheyla_arteaga_lab15"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Do not compress the model inside the APK so it can be opened with
    // memory-mapping (openFd) directly from assets.
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // LiteRT (formerly TensorFlow Lite): interpreter that runs model.tflite
    implementation(libs.litert)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}