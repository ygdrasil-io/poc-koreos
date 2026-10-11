import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }

    // Source set partagé iOS+tvOS, déclaré explicitement (le template par défaut ne crée pas "uikitMain").
    sourceSets {
        val uikitMain = create("uikitMain")
        uikitMain.dependsOn(commonMain.get())
        iosMain.get().dependsOn(uikitMain)
        tvosMain.get().dependsOn(uikitMain)

        uikitMain.dependencies {
            api(project(":kadre:foundation"))
            implementation(project(":kadre:runtime"))
        }

        // Source set de test partagé iOS+tvOS (le template par défaut ne crée pas "uikitTest").
        val uikitTest = create("uikitTest")
        iosTest.get().dependsOn(uikitTest)
        tvosTest.get().dependsOn(uikitTest)

        uikitTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    // Cadre de framework pour chaque target native : `binaries` n'existe pas au niveau de
    // l'extension KMP (KGP 2.4.20), seulement sur chaque target KotlinNativeTarget.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            framework {
                baseName = "KadreUikit"
                // `KadreResult<KadreSession>` s'efface en `id<KadreResult>` côté Obj-C (pas de
                // paramètre de type exporté) : sans export de la foundation, la session rendue
                // par `KadreIos.attach` est inutilisable depuis Swift — ni close(), ni
                // requestStop(), ni awaitTermination(). L'export rend l'API publique foundation
                // (KadreSession, KadreApplicationFactory, KadrePolicy…) visible du driver Swift.
                export(project(":kadre:foundation"))
            }
        }
    }
}

// Phase 1 : uikitMain consomme les internals de runtime (RuntimeWindowManager et ses ports).
// Miroir du wiring runtime→foundation : -friend-modules est mono-valeur last-write-wins,
// donc le klib propre d'uikit est répété avec runtime (et foundation pour les tests).
val uikitNativeTargets = listOf("IosArm64", "IosSimulatorArm64", "TvosArm64", "TvosSimulatorArm64")
val runtimeProject = project(":kadre:runtime")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    val targetSegment = uikitNativeTargets.firstOrNull { name.endsWith(it) } ?: return@configureEach
    val lowerSegment = targetSegment.replaceFirstChar(Char::lowercase)
    val ownMainKlib = layout.buildDirectory.dir("classes/kotlin/$lowerSegment/main/klib/${project.name}")
    val runtimeMainKlib = runtimeProject.layout.buildDirectory
        .dir("classes/kotlin/$lowerSegment/main/klib/${runtimeProject.name}")
    dependsOn(runtimeProject.tasks.named("compileKotlin$targetSegment"))
    compilerOptions.freeCompilerArgs.add(
        ownMainKlib.zip(runtimeMainKlib) { own, runtime ->
            "-friend-modules=${own.asFile.absolutePath}${File.pathSeparator}${runtime.asFile.absolutePath}"
        },
    )
}

// Le compilateur de metadata klib applique aussi la visibilité interne : depuis que uikitMain
// consomme RuntimeLock (expect en commonMain, actual apple), compileUikitMainKotlinMetadata
// (compilation KotlinNativeCompile de la cible "metadata") a besoin des trois klibs metadata de
// runtime en friend — même channel -friend-modules que les cibles natives ci-dessus. Le actual
// d'RuntimeLock est résolu depuis runtime_appleMain/runtime_nativeMain, le expect depuis
// runtime_commonMain : friend uniquement sur commonMain ne suffit pas.
val runtimeMetadataKlibs = listOf(
    "commonMain" to runtimeProject.layout.buildDirectory.dir("classes/kotlin/metadata/commonMain"),
    "appleMain" to runtimeProject.layout.buildDirectory.dir("classes/kotlin/metadata/appleMain/klib/${runtimeProject.name}_appleMain"),
    "nativeMain" to runtimeProject.layout.buildDirectory.dir("classes/kotlin/metadata/nativeMain/klib/${runtimeProject.name}_nativeMain"),
)

val runtimeMetadataFriendModules = providers.provider {
    runtimeMetadataKlibs.joinToString(File.pathSeparator) { it.second.get().asFile.absolutePath }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    if (name == "compileUikitMainKotlinMetadata") {
        runtimeMetadataKlibs.forEach { (sourceSet, _) ->
            val capSegment = sourceSet.replaceFirstChar(Char::uppercase)
            dependsOn(runtimeProject.tasks.named("compile${capSegment}KotlinMetadata"))
        }
        // -friend-modules est mono-valeur (last-write-wins) : les trois klibs en une seule valeur.
        compilerOptions.freeCompilerArgs.add(runtimeMetadataFriendModules.map { "-friend-modules=$it" })
    }
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = uri(rootProject.layout.buildDirectory.dir("kadre-contract-repository"))
        }
    }
}
