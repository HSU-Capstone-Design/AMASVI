import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// local.properties에서 키 불러오기
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val openAiKey = localProperties.getProperty("OPENAI_API_KEY") ?: "\"dummy-key\""

android {
    namespace   = "com.example.application"
    compileSdk  = 35

    defaultConfig {
        applicationId  = "com.example.application"
        minSdk         = 28
        targetSdk      = 35
        versionCode    = 1
        versionName    = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiKey\"")
    }

    buildTypes {
        getByName("release") {
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
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose    = false
        viewBinding = true
        buildConfig = true
    }

    packagingOptions {
        pickFirst("lib/arm64-v8a/libc++_shared.so")
        pickFirst("lib/armeabi-v7a/libc++_shared.so")
        pickFirst("lib/x86/libc++_shared.so")
        pickFirst("lib/x86_64/libc++_shared.so")
    }

    aaptOptions {
        noCompress("tflite")
    }
}

dependencies {
    // Android 기본 구성요소 및 머터리얼 디자인 지원
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")

    // CameraX 관련 라이브러리 (카메라 기능, 라이프사이클 연동, 프리뷰 지원)
    implementation("androidx.camera:camera-core:1.2.2")
    implementation("androidx.camera:camera-camera2:1.2.2")
    implementation("androidx.camera:camera-lifecycle:1.2.2")
    implementation("androidx.camera:camera-view:1.2.2")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.0") // 라이프사이클-aware 코루틴 지원
    implementation(project(":OpenCV")) // 프로젝트 내 OpenCV 모듈 참조

    testImplementation("junit:junit:4.13.2") // 단위 테스트용 JUnit
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1") // Android UI 테스트용

    // OpenCV Android AAR (최신 4.11.0 버전)
    implementation("org.opencv:opencv:4.11.0")

    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("org.json:json:20220320")  // JSONObject/JSONArray 파싱

    // ConstraintLayout UI 레이아웃 지원
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")


    // TensorFlow Lite – 중복된 litert-* 모듈을 제외
    implementation("org.tensorflow:tensorflow-lite:2.13.0") {
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
    }
    implementation("org.tensorflow:tensorflow-lite-support:0.4.2") {
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
    }
    implementation("org.tensorflow:tensorflow-lite-gpu:2.13.0") {
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
    }
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.13.0") {
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
    }



    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1") // ML Kit 텍스트 인식 (일반 + 한국어)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation ("com.microsoft.onnxruntime:onnxruntime-android:1.16.3") // ONNX 모델 실행용 런타임

}