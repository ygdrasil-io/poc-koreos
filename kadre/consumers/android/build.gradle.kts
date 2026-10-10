// AGP 9.0.0 : le plugin `org.jetbrains.kotlin.android` n'existe plus (built-in Kotlin).
// Le compilateur du built-in Kotlin est piloté par la version de KGP sur le classpath
// buildscript — AGP 9.0.0 embarque KGP 2.2.10 par défaut, trop vieux pour lire les
// métadonnées 2.4.0 des AAR kadre publiés (compilés avec Kotlin 2.4.20). Épingler
// `kotlin-gradle-plugin:2.4.0` ici fixe le compilateur du built-in à 2.4.0 : l'épinglage
// Kotlin du brief est conservé, seul le plugin-id disparaît.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

plugins {
    id("com.android.application") version "9.0.0"
}

val kadreRepository = providers.gradleProperty("kadreRepository").orNull
    ?: error("-PkadreRepository is required")
val kadreVersion = providers.gradleProperty("kadreVersion").orNull
    ?: error("-PkadreVersion is required")

android {
    namespace = "org.graphiks.kadre.consumer"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Built-in Kotlin : jvmTarget hérite de compileOptions.targetCompatibility (17).
}

dependencies {
    implementation("org.graphiks.kadre:kadre:$kadreVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    // kotlin-test-junit (et non kotlin-test seul) : l'annotation kotlin.test.Test est un
    // `actual typealias Test = org.junit.Test` qui ne vit que dans l'adaptateur junit —
    // kotlin-test seul n'apporte que les assertions et échoue en « Unresolved reference 'Test' ».
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}
