/**
 * Module koreos-android — backend Android pour koreos.
 *
 * Stratégie A : expose android.view.Surface brute, pas de JNI custom.
 * Cibles : androidTarget uniquement (pas de jvm ni iOS dans ce module).
 *
 * GRA-147 : setup module Android.
 */
plugins {
    id("ygdrasil.conventions.kmp-library")
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
    }
}
