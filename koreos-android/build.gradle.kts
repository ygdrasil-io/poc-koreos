/**
 * Module koreos-android — backend Android pour koreos.
 *
 * Stratégie A : expose android.view.Surface brute, pas de JNI custom.
 * Cibles : androidTarget uniquement (pas de jvm ni iOS dans ce module).
 *
 * Utilise kmp-library (qui ajoute jvm + iOS via convention) mais seule la
 * cible androidTarget a du code source. Les publications iOS/JVM sont désactivées
 * pour éviter les erreurs de klib manquant lors de publishToMavenLocal.
 *
 * GRA-147 : setup module Android.
 * GRA-159 : publication Maven Central.
 */
plugins {
    id("ygdrasil.conventions.kmp-library")
    id("ygdrasil.conventions.kmp-publish")
}

afterEvaluate {
    // koreos-android is Android-only. kmp-library adds iOS/JVM targets but they
    // have no source, so their klibrary files are never generated and Maven
    // publication would fail. Disable those publication/metadata tasks.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        val pubName = publication?.name ?: ""
        if (pubName.startsWith("ios") || pubName == "jvm") {
            enabled = false
        }
    }
    tasks.withType<GenerateModuleMetadata>().configureEach {
        val pubName = publication.orNull?.name ?: ""
        if (pubName.startsWith("ios") || pubName == "jvm") {
            enabled = false
        }
    }
}

android {
    namespace = "io.ygdrasil.koreos.android"
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":koreos-core"))
            }
        }
        androidMain {
            dependencies {
                implementation("androidx.activity:activity:1.10.1")
            }
        }
    }
}
