// hello-kadre : le sample minimal — une fenêtre, des événements imprimés en
// console, sans rendu ni Compose. Un seul module JVM, une seule dépendance :
// l'agrégat `:kadre` expose en `api` la foundation et la façade desktop, et
// branche le backend natif (AppKit ici) au runtime.
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(25)

    jvm {
        // Le DSL « JVM binaries » remplace le plugin `application` (incompatible
        // avec le plugin multiplateforme en KGP 2.4) ; il enregistre la tâche run.
        binaries {
            executable {
                mainClass.set("org.graphiks.kadre.samples.hellokadre.HelloKadreMainKt")
                // `-XstartOnFirstThread` : AppKit exige le thread principal macOS.
                // `--enable-native-access` : le pont kffi vers AppKit.
                applicationDefaultJvmArgs.set(
                    listOf(
                        "-XstartOnFirstThread",
                        "--enable-native-access=ALL-UNNAMED",
                    ),
                )
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kadre"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
