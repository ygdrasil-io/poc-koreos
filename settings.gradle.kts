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

        // Toolchain Kotlin/JS : le plugin KGP télécharge Node.js et Yarn depuis des
        // repos ivy qu'il ajoute normalement au niveau projet — ce que
        // FAIL_ON_PROJECT_REPOS rejette (échec de `kotlinNodeJsSetup`). On les déclare
        // donc ici, côté settings, pour rester hermétique tout en permettant les builds
        // webpack de production (jsBrowserDistribution / wasmJsBrowserDistribution).
        // Voir Redmine #91 ; débloque #20 (GitHub Pages) et #22 (E2E web).
        ivy("https://nodejs.org/dist") {
            name = "Node.js Distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "poc-koreos"

// Modules Koreos
include(":koreos-web-common")
include(":koreos-core")
include(":koreos-test")
include(":benchmarks:jmh-core")
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
include(":samples:pong")
include(":samples:hello-window-web")
include(":samples:hello-triangle-web")
