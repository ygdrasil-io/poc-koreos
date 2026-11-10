// hello-kadre : le sample minimal — une fenêtre, des événements imprimés en
// console, sans rendu ni Compose. Ownership-first : runtime bureau JVM uniquement
// (jvmMain, la façade desktop) — la variante web wasmJs retournera avec le plan
// de résurrection Web. Une seule dépendance : l'agrégat `:kadre` expose en `api`
// la foundation et la façade desktop, et branche les backends natifs au runtime.
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

    // ownership-first: réactivé par le plan Android/UIKit/Web (wasmJs browser)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kadre"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
