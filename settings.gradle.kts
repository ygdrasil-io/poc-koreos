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

// Local Win32 qualification must use the paired core even when another desktop
// backend brings the generic remote snapshot through its multiplatform metadata.
if (providers.gradleProperty("kadreKffiLocal").orNull == "true") {
    val localVersion = providers.gradleProperty("kadreKffiWin32Version").get()
    gradle.beforeProject {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution {
                substitute(module("org.graphiks:kffi")).using(module("org.graphiks:kffi:$localVersion"))
                substitute(module("org.graphiks:kffi-jvm")).using(module("org.graphiks:kffi-jvm:$localVersion"))
            }
        }
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS: the repos declared here take precedence for resolution, but we
    // tolerate that a plugin (typically Kotlin/Wasm via `BinaryenSetupTask`) adds
    // ITS own repo at the project level without crashing the build. Security
    // is preserved — a project repo cannot override a settings repo — and
    // the production wasm build (Binaryen / wasm-opt) is unblocked.
    // Before: FAIL_ON_PROJECT_REPOS rejected every project repo, including the one
    // added by the Wasm plugin itself → `wasmJsBrowserProductionWebpack` failed.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Explicit development opt-in for the jointly developed KFFI Win32
        // artifact. No unrelated dependency may resolve from Maven local.
        if (providers.gradleProperty("kadreKffiLocal").orNull == "true") {
            mavenLocal {
                content {
                    includeModule("org.graphiks", "kffi")
                    includeModule("org.graphiks", "kffi-jvm")
                    includeModule("org.graphiks", "kffi-win32")
                    includeModule("org.graphiks", "kffi-win32-jvm")
                }
            }
        }
        google()
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            name = "Central Portal Snapshots"
            mavenContent { snapshotsOnly() }
        }
        mavenCentral()

        // Kotlin/JS toolchain: the KGP plugin downloads Node.js and Yarn from ivy
        // repos that it normally adds at the project level — which
        // FAIL_ON_PROJECT_REPOS rejects (failure of `kotlinNodeJsSetup`). We therefore
        // declare them here, in settings, to stay hermetic while still allowing the
        // production webpack builds (jsBrowserDistribution / wasmJsBrowserDistribution).
        // See; unblocks #20 (GitHub Pages) and #22 (web E2E).
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
        // Binaryen (wasm-opt) — used by the Kotlin/Wasm plugin to optimize the
        // `.wasm` bundle of the production build (`wasmJsBrowserProductionWebpack`).
        // Declared here (in addition to the repo the plugin adds at the project level via
        // `BinaryenSetupTask`) to ensure hermetic resolution under `PREFER_SETTINGS`
        // and to document the external dependency just like Node / Yarn.
        // Actual URL:
        //   https://github.com/WebAssembly/binaryen/releases/download/version_<v>/binaryen-version_<v>-<classifier>.tar.gz
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen Distributions"
            patternLayout { artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }

        // Repository contractuel : les artefacts kadre PUBLIÉS à HEAD (le pattern
        // `consumers/android`) pour les samples Android migrés (phase 9, AND-010).
        // Filtré par groupe : seuls les artefacts kadre y sont résolus, jamais rien
        // d'autre — et aucun autre module du build n'en résout (toutes les dépendances
        // kadre internes sont des projets).
        maven {
            name = "KadreContractRepository"
            url = rootDir.resolve("build/kadre-contract-repository").toURI()
            content {
                includeGroup("org.graphiks.kadre")
                includeGroup("org.graphiks.kadre.internal")
            }
        }
    }
}

rootProject.name = "kadre"

// Kadre — only projects with an effective JVM responsibility
// enter the build. Additional targets and adapters are added with their first
// implementation and contract evidence.
include(":kadre")
include(":kadre:foundation")
include(":kadre:contracts:validator")
include(":kadre:contracts:driver:web")
include(":kadre:contracts:driver:uikit")
include(":kadre:platform:desktop")
include(":kadre:platform:web")
include(":kadre:platform:uikit")
include(":kadre:platform:android")
include(":kadre:platform:android-devicetest")
include(":kadre:contracts:driver:android")
// La liaison Compose Android (phase 9) — android-only, hors de l'allowlist `check` du
// root : la gate de phase (Task 4, instrumentation) en est propriétaire.
include(":kadre:integration:compose")
include(":kadre:contracts:driver:wayland")
include(":kadre:contracts:driver:win32")
include(":kadre:runtime")
include(":kadre:backend:appkit")
include(":kadre:backend:wayland")
include(":kadre:backend:win32")
include(":kadre:samples:desk-tour")
include(":kadre:samples:hello-kadre")

// Les samples Android migrés (phase 9, AND-010) : applications Android AGP pures qui
// consomment les artefacts PUBLIÉS du repository contractuel (l'umbrella `kadre-android`,
// la liaison `compose-android` — le pattern `consumers/android`). Comme
// `:kadre:integration:compose`, ils restent HORS de l'allowlist `check` du root : la gate
// d'instrumentation Android (`androidContractsCheck`) en est propriétaire. Le repository
// contractuel est déclaré plus haut (filtré sur les groupes kadre) — les coordonnées
// `-android` visent les AAR publiés ; la coordonnée par défaut de l'umbrella serait
// substituée par le projet `:kadre` (substitution automatique group:name du build).
//
// Leur COMPILATION DE SCRIPT exige les flags AGP 9 (`systemProp.android.builtInKotlin/
// newDsl=false` — la forme DSL héritée y est écrite). Les invocations racine directes
// fonctionnaient (la fuite JVM du buildSrc-first est déterministe — CI 38679060381 :
// démon frais, configuration racine complète OK) ; le seul contexte cassé était le build
// IMBRIQUÉ GradleBuild de l'agrégateur consumer (racine = racine du dépôt, macOS
// uniquement, jamais exercé en CI) : aucun systemProp ne le traverse (ni -D, ni
// startParameter.systemPropertiesArgs, ni le gradle.properties parent — Gradle 9.8,
// vérifié), la configuration racine complète y échouait donc. L'exclusion
// `kadre.androidSamples=false` du nesting est SANS PERTE (il ne résout que
// assembleKadreUikitDebugXCFramework). Défaut : inclus.
val androidSamplesEnabled: Boolean =
    providers.gradleProperty("kadre.androidSamples").getOrElse("true") != "false"
if (androidSamplesEnabled) {
    include(":kadre:samples:android-hello-window")
    include(":kadre:samples:android-hello-touch")
    include(":kadre:samples:android-compose-showcase")
    // Le tour Kadre sur Android (phase 11) : la visite commune (`desk-tour`) sur le host
    // Android — sources core/ + ui/ partagées par `kotlin.srcDir`, host de notes
    // capability-off, managers Android réels. Même pattern `consumers/android`.
    include(":kadre:samples:android-kadre-tour")
}
