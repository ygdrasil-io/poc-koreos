@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("maven-publish")
}

group = "org.graphiks.kadre.internal"

val foundationJvmJar = project(":kadre:foundation").tasks.named<Jar>("jvmJar")
val foundationProject = project(":kadre:foundation")
val foundationAllMetadataJar = foundationProject.tasks.named<Jar>("allMetadataJar")
val foundationCommonMainMetadata = foundationProject.layout.buildDirectory.dir("classes/kotlin/metadata/commonMain")
val foundationAndroidClassesJar = foundationProject.layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar",
)
val foundationJsMain = foundationProject.layout.buildDirectory.dir("classes/kotlin/js/main")
val foundationWasmJsMain = foundationProject.layout.buildDirectory.dir("classes/kotlin/wasmJs/main")
val runtimeJsMain = layout.buildDirectory.dir("classes/kotlin/js/main")
val runtimeWasmJsMain = layout.buildDirectory.dir("classes/kotlin/wasmJs/main")

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    android {
        compileSdk = 35
        minSdk = 24
        namespace = "org.graphiks.kadre.internal.runtime"
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {
        }
    }
    jvm()
    js { browser() }
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
    explicitApi()

    sourceSets {
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            // Emplacement du brief : `src/jvmCommon/kotlin` (le défaut du source set
            // serait `src/jvmCommonMain/kotlin`).
            kotlin.srcDir("src/jvmCommon/kotlin")
        }
        jvmMain.get().dependsOn(jvmCommonMain)
        androidMain.get().dependsOn(jvmCommonMain)

        commonMain.dependencies {
            api(project(":kadre:foundation"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<BaseKotlinCompile>().configureEach {
    if (name.endsWith("Jvm")) {
        dependsOn(foundationJvmJar)
        friendPaths.from(foundationJvmJar.flatMap(Jar::getArchiveFile))
    }

    // Friend-wiring : le runtime voit les internals de foundation. Pour Android, la
    // compilation (main et host test) a besoin des classes Android de foundation comme
    // friend. On friende le classes.jar AGP (`bundleAndroidMainClassesToCompileJar`) —
    // le même fichier que le classpath, exactement comme le friend jvmJar de la cible
    // jvm ; le répertoire brut `classes/kotlin/android/main` (sortie sondée, présente)
    // ne suffit pas à lui seul.
    if (name == "compileAndroidMain" || name == "compileAndroidHostTest") {
        dependsOn(foundationProject.tasks.named("compileAndroidMain"))
        dependsOn(foundationProject.tasks.named("bundleAndroidMainClassesToCompileJar"))
        friendPaths.from(foundationAndroidClassesJar)
    }
}

// Native friend probe (2026-10-10, phase 0): verdict (a) — the Native friend mechanism works.
// `KotlinNativeCompile.friendPaths` does not exist in KGP 2.4.20; the working channel is
// `-friend-modules=<klib>` on compilerOptions.freeCompilerArgs, where each klib path must be the
// module dir (…/main/klib/<module>) exactly as KGP passes it to -library. The argument is
// single-valued with last-write-wins: for test compilations KGP already friends this module's
// own main klib there, so the wiring repeats it (plus foundation) — a foundation-only value
// would override it and break the tests' access to runtime's own internals.
// Not dormant: runtime's own commonMain consumes foundation internal constructors, so this
// wiring is what lets the ios/tvos targets compile at all. It will also serve platform:uikit
// consuming runtime internals in phase 1 (mirror of the JVM wiring above).
val foundationNativeTargets = listOf("IosArm64", "IosSimulatorArm64", "TvosArm64", "TvosSimulatorArm64")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    val targetSegment = foundationNativeTargets.firstOrNull { name.endsWith(it) } ?: return@configureEach
    val kotlinClassesDir = layout.buildDirectory.dir("classes/kotlin/${targetSegment.replaceFirstChar(Char::lowercase)}/main/klib")
    val ownMainKlib = kotlinClassesDir.map { it.dir(project.name) }
    val foundationMainKlib = foundationProject.layout.buildDirectory
        .dir("classes/kotlin/${targetSegment.replaceFirstChar(Char::lowercase)}/main/klib/${foundationProject.name}")
    dependsOn(foundationProject.tasks.named("compileKotlin$targetSegment"))
    compilerOptions.freeCompilerArgs.add(
        ownMainKlib.zip(foundationMainKlib) { own, foundation ->
            "-friend-modules=${own.asFile.absolutePath}${File.pathSeparator}${foundation.asFile.absolutePath}"
        },
    )
}

tasks.withType<KotlinCompileCommon>().configureEach {
    if (name == "compileCommonMainKotlinMetadata" || name == "compileWebMainKotlinMetadata") {
        dependsOn(foundationAllMetadataJar)
        friendPaths.from(foundationCommonMainMetadata)
    }
}

tasks.withType<Kotlin2JsCompile>().configureEach {
    when (name) {
        "compileKotlinJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinJs"))
            compilerOptions.freeCompilerArgs.add(
                foundationJsMain.map { "-Xfriend-modules=${it.asFile.absolutePath}" },
            )
        }

        "compileTestKotlinJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinJs"))
            compilerOptions.freeCompilerArgs.add(
                runtimeJsMain.zip(foundationJsMain) { runtime, foundation ->
                    "-Xfriend-modules=${runtime.asFile.absolutePath}${File.pathSeparator}${foundation.asFile.absolutePath}"
                },
            )
        }

        "compileKotlinWasmJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinWasmJs"))
            compilerOptions.freeCompilerArgs.add(
                foundationWasmJsMain.map { "-Xfriend-modules=${it.asFile.absolutePath}" },
            )
        }

        "compileTestKotlinWasmJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinWasmJs"))
            compilerOptions.freeCompilerArgs.add(
                runtimeWasmJsMain.zip(foundationWasmJsMain) { runtime, foundation ->
                    "-Xfriend-modules=${runtime.asFile.absolutePath}${File.pathSeparator}${foundation.asFile.absolutePath}"
                },
            )
        }
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

// Les variantes natives iOS/tvOS ne se publient que depuis un hôte Apple : les gates
// ubuntu (web-contracts) publient jvm/js/wasm via le même agrégat contractTest, et un
// hôte non-Apple ne produit pas les klibs. La résolution native depuis contractTest
// sera révisée quand platform:uikit sera officiellement publié (phase 1).
if (!org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
    tasks.configureEach {
        val nativePublicationTask = name.startsWith("publish") &&
            (name.contains("Ios") || name.contains("Tvos"))
        val nativeMetadataTask = name.startsWith("generateMetadataFileFor") &&
            (name.contains("Ios") || name.contains("Tvos"))
        if (nativePublicationTask || nativeMetadataTask) {
            enabled = false
        }
    }
}
