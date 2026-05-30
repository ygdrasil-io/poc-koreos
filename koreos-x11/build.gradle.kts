/**
 * Module koreos-x11 — backend Linux Desktop via FFM JVM 25.
 *
 * Cible : jvm uniquement.
 * Ce module implémente les interfaces de koreos-core pour X11 (Display,
 * XCreateSimpleWindow, boucle d'événements, etc.) en utilisant la Foreign
 * Function & Memory API (JEP 454).
 *
 * Dépendance amont : koreos-core (uniquement la sourceSet jvm via jvmMain).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(25)

    jvm()

    sourceSets {
        jvmMain {
            dependencies {
                api(project(":koreos-core"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
