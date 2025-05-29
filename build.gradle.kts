plugins {
    // Android 플러그인을 여기서 선언하고 하위 모듈에 전파(apply false)
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android")  version "1.9.10" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}