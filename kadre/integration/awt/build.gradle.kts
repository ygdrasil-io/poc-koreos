// integration:awt (fermeture win32) — la liaison consommateur AWT : un module
// JVM qui possède explicitement la dépendance java.desktop (c'est SA raison
// d'être — le runtime commun en reste exempt) et enveloppe la façade desktop
// publique pour attacher une session kadre à la boucle EventDispatchThread
// d'un hôte AWT/Swing existant. Consommateur de référence exigé par la
// roadmap phase 12 avant l'annonce de l'intégration dans
// `supportedIntegrations` ; sa preuve O3 (vraie EDT, vraie boucle AWT) vit
// dans jvmTest et tourne sur le gate Windows.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

group = "org.graphiks.kadre.internal"

kotlin {
    jvmToolchain(25)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            // L'agrégat public : façade desktop + foundation, backends branchés.
            api(project(":kadre"))
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":kadre:backend:win32"))
            }
        }
    }
}
