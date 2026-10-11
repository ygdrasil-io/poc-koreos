import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(25)

    // Opt-ins portées par le code partagé (ui/ compose) : les mêmes marqueurs existent
    // dans les artefacts compose jvm et wasmJs. L'opt-in kffi reste propre au JVM —
    // le marqueur n'existe pas sur la classe-path wasm.
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.ui.InternalComposeUiApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            // Les atomiques communes du portage (TourStore, ActionDispatcher).
            "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
        )
    }

    jvm {
        // Le DSL « JVM binaries » remplace le plugin `application` (incompatible avec le
        // plugin multiplateforme en KGP 2.4) : il enregistre la tâche run (JavaExec) et la
        // distribution — le flux de lancement desktop reste le même.
        binaries {
            executable {
                mainClass.set("org.graphiks.kadre.samples.desktour.DeskTourMainKt")
                // État de départ inchangé du sample : `-XstartOnFirstThread` est spécifique
                // macOS — la démo desktop vise cette plateforme, le flux jvm ne change pas.
                applicationDefaultJvmArgs.set(
                    listOf(
                        "-XstartOnFirstThread",
                        "--enable-native-access=ALL-UNNAMED",
                    ),
                )
            }
        }
    }

    wasmJs {
        browser {
            // Décision T2 (le plus petit choix honnête) : aucun harnais de test navigateur
            // n'est configuré pour ce sample — karma + webpack + navigateur achèteraient
            // rien pour une suite de logique. La suite commune tourne déjà sur jvm
            // (jvmTest porte tout commonTest) et le binaire wasm est couvert bout-en-bout
            // par le filet e2e commis (e2e/README.md). La tâche est désactivée
            // explicitement pour qu'un `check` n'essaie pas d'amorcer un harnais qui
            // n'existe pas.
            testTask { enabled = false }
        }
        // Le binaire navigateur (wasmJsBrowserDistribution) est ce que l'hôte web
        // servira ; le host lui-même est la tâche 3.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kadre"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.kffi.objc)
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
        }
        wasmJsMain.dependencies {
            // Les mêmes accès que la sonde a compilés et exécutés (PROBE-NOTES.md).
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.material3)
        }
    }
}

tasks.named<Test>("jvmTest") { useJUnitPlatform() }

// L'opt-in kffi (pont AppKit) reste propre aux compilations JVM : le marqueur
// `org.graphiks.kffi.objc.PlatformAvailability` n'existe pas sur la classe-path wasm.
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-opt-in=org.graphiks.kffi.objc.PlatformAvailability")
}
