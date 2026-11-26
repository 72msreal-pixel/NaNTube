pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Chỉ dùng cho bộ mã hóa MP3 (LAME) khi tải âm thanh dạng MP3.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.naman14") }
        }
    }
}

rootProject.name = "NaNTube"
include(":app")
