// Le sample « hello touch » Android migré (phase 9, AND-010) : le même attach Kotlin forme
// ACTIVITY que `android-hello-window`, plus l'observation du touch réel — les événements
// `SurfaceInput` (TouchChanged/Key/Scrolled) journalisés dans une console simple.
//
// Le module suit le pattern `consumers/android` : une application Android pure qui résout les
// artefacts PUBLIÉS du repository contractuel (`build/kadre-contract-repository`, déclaré dans
// les settings racine) — jamais les projets du build, la preuve consumateur passerait à côté.
// Hors de l'allowlist `check` du root : la gate d'instrumentation Android (AND-010) et le
// développeur du sample en sont les seuls pilotes.
plugins {
    id("com.android.application")
}

android {
    namespace = "org.graphiks.kadre.samples.hellotouch"
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
}
