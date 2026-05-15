plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    // alias(libs.plugins.google.services) // Uncomment khi cấu hình Firebase
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
        release {
            isMinifyEnabled = false
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

    // Firebase (uncomment khi cấu hình Firebase)
    // implementation(platform(libs.firebase.bom))
    // implementation(libs.firebase.storage)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    implementation("androidx.exifinterface:exifinterface:1.3.6")
}
