plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "dat.nguyenvan.smarthandwritingai"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dat.nguyenvan.smarthandwritingai"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Không nén file .tflite trong assets
    androidResources {
        noCompress += "tflite"
    }
    buildFeatures {
        mlModelBinding = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.material)
    implementation(libs.cardview)
    implementation(libs.recyclerview)

    // TensorFlow Lite (chỉ cần core, ImageProcessor tự xử lý ảnh)
    implementation(libs.tflite)

    // Biểu đồ MPAndroidChart
    implementation(libs.mpandroidchart)

    // Lottie Animation
    implementation(libs.lottie)

    // Room Database
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // UCrop — crop ảnh trước khi nhận dạng
    implementation(libs.ucrop)
    implementation("androidx.exifinterface:exifinterface:1.3.6")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
