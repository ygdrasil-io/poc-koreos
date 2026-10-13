import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("maven-publish")
}

group = "org.graphiks.kadre.internal"

val foundationProject = project(":kadre:foundation")
val runtimeProject = project(":kadre:runtime")
// Friend-wiring inputs: AGP's own per-source-set classes.jar (the same file the
// compilation puts on its classpath). The raw `classes/kotlin/android/main` directory
// produces an INVISIBLE_REFERENCE diagnostic on its own — the runtime module proved the
// `bundleAndroidMainClassesToCompileJar` output is the working friend artifact.
val foundationAndroidClassesJar = foundationProject.layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar",
)
val runtimeAndroidClassesJar = runtimeProject.layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar",
)

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    android {
        compileSdk = 35
        minSdk = 24
        namespace = "org.graphiks.kadre.platform.android"
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {
        }
    }
    explicitApi()

    // Discipline de baseline ABI, miroir de kadre/foundation/build.gradle.kts : le dump
    // public (api/android.api) est commité et le check échoue le build si l'ABI publique
    // bouge sans update de baseline. La surface publique de l'adapter (les quatre
    // overloads `attachKadre`, la façade Java `KadreAndroid`/`KadreSessionHandle`, les
    // types de session) est un contrat de consommation — sa régression se lit au build,
    // pas à la revue.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre:foundation"))
        }
        androidMain.dependencies {
            api(project(":kadre:foundation"))
            implementation(project(":kadre:runtime"))
            // Phase 1 (§15.1) : Activity/Lifecycle SDK types exposés dans les signatures
            // publiques des quatre overloads `attachKadre` — donc `api`, pas `implementation`.
            api(libs.androidx.activity.ktx)
            api(libs.androidx.lifecycle.runtime.ktx)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Les tests hôtes du bras HostChoice (phase 8, Task 2) pilotent la pompe de frames par le
// scheduler du runTest — le même parti pris que les tests du flux web (le loopContext injecté
// est le StandardTestDispatcher du TestScope, chaque tour de boucle est explicite). Le source
// set `androidHostTest` est créé par le plugin AGP KMP (`withHostTest`) hors du conteneur KMP,
// donc la dépendance passe par SA configuration Gradle, pas par `sourceSets`.
dependencies {
    "androidHostTestImplementation"(libs.kotlinx.coroutines.test)
}

// Friend-wiring Android : androidMain référence des internals du runtime (RuntimeHostController,
// RuntimePrimarySurface, RuntimeHostSurface) pour construire la session — la même couture que
// webMain (cf. platform/web/build.gradle.kts). AGP 9.0.0 nomme les compilations
// `compileAndroidMain` / `compileAndroidHostTest` (relevé `tasks --all`, et wiring identique
// prouvé dans kadre/runtime/build.gradle.kts) ; les deux ont besoin des classes Android de
// foundation ET du runtime comme friends.
tasks.withType<BaseKotlinCompile>().configureEach {
    if (name == "compileAndroidMain" || name == "compileAndroidHostTest") {
        dependsOn(foundationProject.tasks.named("compileAndroidMain"))
        dependsOn(foundationProject.tasks.named("bundleAndroidMainClassesToCompileJar"))
        dependsOn(runtimeProject.tasks.named("compileAndroidMain"))
        dependsOn(runtimeProject.tasks.named("bundleAndroidMainClassesToCompileJar"))
        friendPaths.from(foundationAndroidClassesJar)
        friendPaths.from(runtimeAndroidClassesJar)
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
