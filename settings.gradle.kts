pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()

        maven("https://jitpack.io")
    }

}

dependencyResolutionManagement {
    // 프로젝트 내에서 루트 이외의 리포지토리 사용을 방지
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Application"
include(":app")
include(":OpenCV")
