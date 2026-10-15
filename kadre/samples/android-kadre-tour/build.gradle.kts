// Le tour Kadre sur Android (phase 11, Task 1) : la visite commune des capabilities
// (`samples/desk-tour`) transportée sur le host Android. Les sources core/ + ui/ de la
// visite ne sont PAS copiées : le module les partage par `kotlin.srcDir` vers
// `../desk-tour/src/commonMain/kotlin` — une seule matière, trois cibles (jvm, wasmJs,
// android), zéro #ifdef. Ce qui change de cible vit ici, dans la forme des autres hosts :
// le support de notes capability-off (`AndroidTourHost`, miroir `DemoNoteWindows`), le
// gateway (`AndroidTourGateway`, miroir `WebTourGateway` sans son override) et
// l'Activity (`MainActivity`, miroir `DeskTourWebMain` assemblé par la liaison P9).
//
// Le module suit le pattern `consumers/android` : une application Android pure qui
// résout les artefacts PUBLIÉS du repository contractuel (`build/kadre-contract-repository`,
// déclaré dans les settings racine) — jamais les projets du build, la preuve consumateur
// passerait à côté. Hors de l'allowlist `check` du root : le développeur du sample en est
// le seul pilote (le showcase, phase 9, a posé le même parti pris).
plugins {
    id("com.android.application")
    // Le compilateur compose — le MÊME alias que la liaison (`kadre/integration/compose`)
    // et le sample showcase : KGP 2.4.20, le compilateur exact des artefacts publiés
    // consommés ici.
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.graphiks.kadre.samples.androidkadretour"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Les sources partagées de la visite : core/ + ui/ de `desk-tour` compilent ICI, dans
    // CE module (visibilité `internal` incluse) — l'Activity et le host Android parlent au
    // store / gateway / dispatcher exactement comme les hosts jvm et wasmJs le font.
    sourceSets {
        getByName("main") {
            kotlin.srcDir("../desk-tour/src/commonMain/kotlin")
        }
    }

    // Built-in Kotlin (AGP 9) : le compilateur suit le KGP du classpath du build (2.4.20,
    // posé par buildSrc) et jvmTarget hérite de compileOptions.targetCompatibility (17).
}

// Les opt-ins portés par le code partagé — les MÊMES que le build `desk-tour` : les
// marqueurs compose de ui/ (TopAppBar expérimental material3, API internes compose) et
// les atomiques communes du portage (TourStore, ActionDispatcher).
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.ui.InternalComposeUiApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
        )
    }
}

dependencies {
    // L'umbrella PUBLIÉE — sa variante android explicite (`kadre-android`, l'AAR exact que
    // la résolution de variantes sert au consumer depuis `org.graphiks.kadre:kadre`). Dans
    // CE build racine, la coordonnée par défaut serait substituée par le projet `:kadre`
    // (substitution automatique de Gradle entre projets du build) : la variante publiée
    // vise le MÊME AAR, sans la collision.
    implementation("org.graphiks.kadre:kadre-android:${project.version}")
    // La liaison Compose PUBLIÉE (phase 9) — la surface `KadreComposeHost` : une session
    // par host, attachée à l'attachement réel de la vue, fermée à la sortie de composition.
    implementation("org.graphiks.kadre.internal:compose-android:${project.version}")

    // Compose de la visite : runtime + ui + foundation (le parti pris du showcase) PLUS
    // material3 — le shell de la visite (Scaffold, NavigationBar, drawer) le demande ; le
    // MÊME artefact multiplateforme que les cibles jvm et wasmJs du tour.
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    // activity-compose apporte le `setContent` de l'Activity hôte.
    implementation(libs.androidx.activity.compose)
    // Les flux de la visite (core/ ne connaît que kotlinx.coroutines.flow).
    implementation(libs.kotlinx.coroutines.core)
}
