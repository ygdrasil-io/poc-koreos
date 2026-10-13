import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// La liaison Compose Android (phase 9, Task 3) — module android-only qui suit LE motif
// de `platform/android` : `kotlin-multiplatform` + `com.android.kotlin.multiplatform.library`
// (cible android seule, `withHostTest` pour les tests hôtes JVM). Le choix, contre un
// `com.android.library` + kotlin-android : le plugin catalogue `org.jetbrains.compose`
// (compose-multiplatform 1.11.0) s'accroche nativement au conteneur KMP (le précédent
// `samples/desk-tour`), et le `withHostTest` de l'AGP KMP fournit le source set
// `androidHostTest` — les MÊMES noms de tâches que la plateforme (`compileAndroidMain`,
// `testAndroidHostTest`). Les dépendances compose restent minimales : runtime + ui +
// foundation (Box/AndroidView/fillMaxSize) — activity-compose n'y est pas nécessaire
// (`LocalContext` vient de compose.ui) et material3 n'y a aucun usage.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    android {
        compileSdk = 35
        minSdk = 24
        namespace = "org.graphiks.kadre.integration.compose"
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {
        }
    }
    explicitApi()

    sourceSets {
        androidMain.dependencies {
            // La surface d'attach PUBLIQUE de la plateforme (les overloads `attachKadre`)
            // — `implementation` : la liaison n'expose que les types fondation.
            implementation(project(":kadre:platform:android"))
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
        }
    }
}

// Le source set `androidHostTest` est créé par le plugin AGP KMP hors du conteneur KMP
// (le modèle de platform/android) : la dépendance passe par SA configuration Gradle.
dependencies {
    "androidHostTestImplementation"(kotlin("test"))
}
