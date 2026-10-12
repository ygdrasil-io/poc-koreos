import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

// Le porteur ON-DEVICE des scénarios drop de l'AND-007 (Task 3, phase 6) : le transfert RÉEL
// et la sentinelle stream-released-on-close exigent un `ContentResolver` réel — un host test
// JVM n'en a pas, et la construction runtime-friend (source Task 1 + réducteur Task 2) ne
// peut PAS vivre dans le consumer (le consumer compile contre les artefacts PUBLIÉS, où ces
// internals ne sont pas visibles). Ce module est donc la compilation AMIE des trois modules
// concernés — le miroir exact du câblage friend d'`androidHostTest` — et son AAR publié est
// consommé `androidTestImplementation` par le consumer : les classes de scénarios voyagent
// DANS l'APK androidTest du consumer et tournent sous SON runner connecté — le XML JUnit de
// la chaîne de preuves reste celui du consumer, le driver n'y change rien. Artefact de preuve
// interne (jamais consommé par la production) ; le scénario public (channel-absent) reste
// lui, dans le consumer.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("maven-publish")
}

group = "org.graphiks.kadre.internal"

val foundationProject = project(":kadre:foundation")
val runtimeProject = project(":kadre:runtime")
val platformProject = project(":kadre:platform:android")

// Friend-wiring inputs: AGP's own per-source-set classes.jar — le chemin prouvé du câblage
// de platform/android (cf. son build.gradle.kts ; le bundle jar est l'artefact friend qui
// marche, le répertoire brut produit un INVISIBLE_REFERENCE).
val foundationAndroidClassesJar = foundationProject.layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar",
)
val runtimeAndroidClassesJar = runtimeProject.layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar",
)
val platformAndroidClassesJar = platformProject.layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar",
)

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    android {
        compileSdk = 35
        minSdk = 24
        namespace = "org.graphiks.kadre.platform.android.devicetest"
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    explicitApi()

    sourceSets {
        androidMain.dependencies {
            api(project(":kadre:foundation"))
            implementation(project(":kadre:runtime"))
            implementation(project(":kadre:platform:android"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            // Les scénarios sont des tests JUnit4 exécutés par le runner du consumer :
            // junit et androidx.test:monitor (InstrumentationRegistry) sont compileOnly —
            // l'APK androidTest du consumer fournit déjà les runtimes (androidx.test:runner,
            // :core et kotlin-test-junit y sont déclarés), l'AAR ne publie que les classes
            // de scénarios.
            compileOnly("junit:junit:4.13.2")
            compileOnly("androidx.test:monitor:1.6.1")
        }
    }
}

// Friend-wiring Android : androidMain référence des internals du runtime (RuntimeSurfaceInput,
// RuntimeDropTransferBudget, SurfaceStimulus) ET de la platform android (la source drop Task 1
// et le port texte du réducteur) — le nom des compilations AGP 9.0.0 est relevé (`tasks --all`)
// et le wiring est celui de platform/android pour foundation+runtime.
tasks.withType<BaseKotlinCompile>().configureEach {
    if (name == "compileAndroidMain") {
        dependsOn(foundationProject.tasks.named("compileAndroidMain"))
        dependsOn(foundationProject.tasks.named("bundleAndroidMainClassesToCompileJar"))
        dependsOn(runtimeProject.tasks.named("compileAndroidMain"))
        dependsOn(runtimeProject.tasks.named("bundleAndroidMainClassesToCompileJar"))
        dependsOn(platformProject.tasks.named("compileAndroidMain"))
        dependsOn(platformProject.tasks.named("bundleAndroidMainClassesToCompileJar"))
        friendPaths.from(foundationAndroidClassesJar)
        friendPaths.from(runtimeAndroidClassesJar)
        friendPaths.from(platformAndroidClassesJar)
    }
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}
