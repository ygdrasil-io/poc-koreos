import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(25)

    // Opt-ins portées par le code partagé (ui/ compose). L'opt-in kffi reste propre
    // au JVM.
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
                // `-XstartOnFirstThread` est spécifique macOS (AppKit exige le thread
                // principal) : les JVM Windows/Linux le refusent au démarrage — l'hôte
                // win32 n'exige aucun thread principal, il n'est posé que sur macOS.
                applicationDefaultJvmArgs.set(
                    buildList {
                        if (providers.systemProperty("os.name").get().contains("mac", ignoreCase = true)) {
                            add("-XstartOnFirstThread")
                        }
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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.kffi.objc)
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
        }
    }
}

tasks.named<Test>("jvmTest") { useJUnitPlatform() }

// L'opt-in kffi (pont AppKit) reste propre aux compilations JVM.
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-opt-in=org.graphiks.kffi.objc.PlatformAvailability")
}
