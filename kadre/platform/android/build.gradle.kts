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

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre:foundation"))
        }
        androidMain.dependencies {
            api(project(":kadre:foundation"))
            implementation(project(":kadre:runtime"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
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
