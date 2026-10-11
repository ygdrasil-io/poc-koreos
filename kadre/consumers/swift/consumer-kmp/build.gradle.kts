import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/*
 * Le consumer KMP externe (phase 3) : il résout `org.graphiks.kadre:uikit` depuis le
 * repository contractTest (artefacts publiés, JAMAIS un `project(...)`) et produit, par
 * cible native, un framework STATIQUE — l'autre moitié du lien deux-frameworks avec
 * `KadreUikit.xcframework` (distribution Task 1). L'app Swift lie les DEUX archives sans
 * embed : l'édition de liens à sémantique d'archive paresseuse ne tire du second
 * fragment que les symboles encore indéfinis — une seule copie du runtime Kotlin et de
 * chaque classe Obj-C survit dans le binaire (des doublons seraient refusés par ld ;
 * des classes dupliquées crasheraient le lancement).
 *
 * POURQUOI baseName = "KadreUikit" (et pas "ConsumerKadre") : KGP 2.4.20 dérive le
 * préfixe Obj-C exporté du NOM DU FRAMEWORK — aucun override n'existe (option binaire
 * `objcExportPrefix` supprimée : `unknown binary option`, vérifié sur le toolchain
 * 2.4.20). Le module applicatif du consumer doit exporter SES classes avec le MÊME
 * préfixe exact `KadreUikit` que la distribution (Décision D-duplication du plan :
 * « les symboles Obj-C gardent le préfixe exact KadreUikit — identité de types par
 * swift_name + lien ») : un préfixe différent produirait deux familles de classes
 * Obj-C distinctes pour les MÊMES types Kotlin et casserait l'identité de types au
 * passage de la factory. Le framework du consumer est donc un SECOND `KadreUikit`
 * compilé contre les mêmes artefacts publiés — les classes partagées sont identiques
 * au symbole près, et le lien n'en garde qu'une copie.
 */
plugins {
    kotlin("multiplatform") version "2.4.20"
}

val kadreRepository = providers.gradleProperty("kadreRepository")
    .orNull
    ?: error("-PkadreRepository is required")
val kadreVersion = providers.gradleProperty("kadreVersion")
    .orNull
    ?: error("-PkadreVersion is required")

repositories {
    maven { url = uri(kadreRepository) }
    mavenCentral()
}

kotlin {
    // Même liste de cibles que platform:uikit (les slices device servent un consumer
    // réel ; les slices simulateur servent les tests xcodebuild du consumer-app).
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
    explicitApi()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            framework {
                baseName = "KadreUikit" // préfixe Obj-C identique à la distribution — voir l'en-tête
                // STATIQUE : l'app lie l'archive à l'édition de liens (embed interdit —
                // un framework statique ne s'embarque pas dans l'app bundle).
                isStatic = true
                // L'API uikit (KadreIos, KadreApplicationFactory, KadreSession…) doit
                // traverser le module consumer jusqu'au header Obj-C : sans export, la
                // factory produite ici ne serait pas typable côté Swift contre le
                // paramètre `applicationFactory:` de `KadreIos.attach`.
                export("org.graphiks.kadre:uikit:$kadreVersion")
                // foundation aussi : un module transitif NON exporté voit SES classes
                // préfixées d'un segment `Foundation` (KadreUikitFoundation*) au lieu du
                // nom plat (KadreUikit*) que la distribution exporte — les deux archives
                // porteraient des noms différents pour les MÊMES classes, brisant
                // l'identité de symboles qu'exige le lien une-seule-copie.
                export("org.graphiks.kadre:foundation:$kadreVersion")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.graphiks.kadre:uikit:$kadreVersion")
        }
    }
}
