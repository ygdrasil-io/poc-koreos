pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "poc-koreos"

// Modules Koreos
include(":koreos-web-common")
include(":koreos-core")
include(":koreos-appkit")
include(":koreos-uikit")
include(":koreos-win32")
include(":koreos-x11")
include(":koreos-wayland")
include(":koreos-js")
include(":koreos-wasm")
include(":koreos")
include(":koreos-android")
include(":samples:hello-metal")
include(":samples:hello-triangle")
include(":samples:hello-touch")
include(":samples:hello-touch-android")
include(":samples:hello-window")
include(":samples:hello-window-android")
include(":samples:hello-window-web")
