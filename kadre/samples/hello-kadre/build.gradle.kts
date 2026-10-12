// hello-kadre : le sample minimal — une fenêtre, des événements imprimés en
// console, sans rendu ni Compose. Deux runtimes : le bureau JVM (jvmMain, la
// façade desktop) et le web wasmJs (wasmJsMain, l'attachement à une page). Une
// seule dépendance : l'agrégat `:kadre` expose en `api` la foundation et la
// façade de chaque plateforme, et branche les backends natifs au runtime.
plugins {
    kotlin("multiplatform")
}

// `-XstartOnFirstThread` est une option de la JVM macOS (AppKit exige le thread
// principal) : les JVM Linux/Windows la refusent au démarrage, on ne la pose
// donc que lorsque Gradle tourne sur un hôte macOS.
val onMacOS = providers.systemProperty("os.name").get().contains("mac", ignoreCase = true)

kotlin {
    jvmToolchain(25)

    jvm {
        // Le DSL « JVM binaries » remplace le plugin `application` (incompatible
        // avec le plugin multiplateforme en KGP 2.4) ; il enregistre la tâche run.
        binaries {
            executable {
                mainClass.set("org.graphiks.kadre.samples.hellokadre.HelloKadreMainKt")
                applicationDefaultJvmArgs.set(
                    buildList {
                        if (onMacOS) add("-XstartOnFirstThread")
                        // `--enable-native-access` : le pont kffi vers le backend natif.
                        add("--enable-native-access=ALL-UNNAMED")
                    },
                )
            }
        }
    }

    wasmJs {
        browser {
            // Aucun harnais de test navigateur : ce sample n'a pas de tests ; la
            // variante web se vérifie en la lançant (wasmJsBrowserDevelopmentRun).
        }
        // Le binaire navigateur est ce que la page index.html charge via le
        // chargeur webpack émis à côté d'elle.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kadre"))
            implementation(libs.kotlinx.coroutines.core)
        }
        wasmJsMain.dependencies {
            // Les bindings DOM (document, HTMLElement) — Compose les apporte à
            // desk-tour ; ce sample, sans Compose, les déclare lui-même.
            implementation(libs.kotlinx.browser)
        }
    }
}
