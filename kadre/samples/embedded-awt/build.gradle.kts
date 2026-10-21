// embedded-awt : le sample minimal de l'hôte embarqué — une application
// Swing existante (un JFrame avec son bouton) à laquelle une session kadre
// s'attache sur l'EDT via la liaison integration:awt. Windows uniquement
// (le backend win32) ; le sample se ferme par son bouton Swing, jamais par
// kadre — la boucle hôte garde l'ownership.
plugins {
    kotlin("multiplatform")
}

val onWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)

kotlin {
    jvmToolchain(25)
    jvm {
        binaries {
            executable {
                mainClass.set("org.graphiks.kadre.samples.embeddedawt.EmbeddedAwtMainKt")
                applicationDefaultJvmArgs.set(listOf("--enable-native-access=ALL-UNNAMED"))
            }
        }
    }
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":kadre:integration:awt"))
        }
    }
}
