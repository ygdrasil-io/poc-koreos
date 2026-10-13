// La démo Compose migrée (phase 9, AND-010) : kadre-old `samples/compose/android` — la
// liaison Compose de phase 9 (`org.graphiks.kadre.internal:compose-android`, l'AAR PUBLIÉ),
// la session unique par host, le touch observé, et LES DEUX scénarios instrumentés du
// contrat AND-010 (`androidComposeShowcaseSingleSession`, `androidLegacyTypesAbsent`).
//
// Le module suit le pattern `consumers/android` : une application Android pure qui résout les
// artefacts PUBLIÉS du repository contractuel (`build/kadre-contract-repository`, déclaré dans
// les settings racine) — jamais les projets du build, la preuve consumateur passerait à côté.
// Hors de l'allowlist `check` du root : la gate d'instrumentation Android (AND-010) et le
// développeur du sample en sont les seuls pilotes.
plugins {
    id("com.android.application")
    // Le compilateur compose — le MÊME alias que la liaison (`kadre/integration/compose`) :
    // KGP 2.4.20, le compilateur exact des artefacts publiés consommés ici.
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.graphiks.kadre.samples.composeshowcase"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Built-in Kotlin (AGP 9) : le compilateur suit le KGP du classpath du build (2.4.20,
    // posé par buildSrc — la lecture des métadonnées 2.4.20 des AAR kadre publiés) et
    // jvmTarget hérite de compileOptions.targetCompatibility (17).
}

dependencies {
    // L'umbrella PUBLIÉE — sa variante android explicite (`kadre-android`, l'AAR exact que la
    // résolution de variantes sert au consumer depuis `org.graphiks.kadre:kadre`). Dans CE build
    // racine, la coordonnée par défaut (`org.graphiks.kadre:kadre`) serait substituée par le
    // projet `:kadre` (mêmes group:name — substitution automatique de Gradle entre projets du
    // build) : la variante publiée vise le MÊME AAR, sans la collision.
    implementation("org.graphiks.kadre:kadre-android:${project.version}")
    // La liaison Compose PUBLIÉE (phase 9, Task 3) — la surface `KadreComposeHost` : une
    // session par host, attachée à l'attachement réel de la vue, fermée à la sortie de
    // composition, recréée après un détachement terminal.
    implementation("org.graphiks.kadre.internal:compose-android:${project.version}")

    // Compose minimal (le même parti pris que la liaison : runtime + ui + foundation —
    // material3 n'y a aucun usage, `BasicText` suffit à la démo) ; activity-compose apporte
    // le `setContent` de l'Activity hôte.
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.activity.compose)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    // kotlin-test-junit (et non kotlin-test seul) : l'annotation kotlin.test.Test est un
    // `actual typealias Test = org.junit.Test` qui ne vit que dans l'adaptateur junit.
    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
