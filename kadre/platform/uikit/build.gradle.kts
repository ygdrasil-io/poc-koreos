import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
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

    // Phase 3 : XCFramework de distribution STATIQUE (Debug + Release), assemblé par les tasks
    // assembleKadreUikit{Debug,Release}XCFramework → build/XCFrameworks/<buildType>/KadreUikit.xcframework.
    val kadreUikitXcFramework = XCFramework("KadreUikit")

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
            // Distribution phase 3 : l'app consumer lie (sans embed) ce xcframework ET son propre
            // framework Kotlin (lui aussi statique) — une seule copie du runtime à l'édition de
            // liens, et les symboles Obj-C gardent le préfixe exact `KadreUikit` (identité de
            // types par swift_name + lien). Binaire nommé "KadreUikitDistribution" pour distinguer
            // ses tasks de link de celles du driver dynamique ci-dessus ; baseName reste
            // "KadreUikit" : c'est le nom du module/slices du xcframework, et KGP rassemble les
            // slices sous le nom du XCFramework (noms identiques → pas de diagnostic).
            framework("KadreUikitDistribution") {
                baseName = "KadreUikit"
                isStatic = true
                kadreUikitXcFramework.add(this)
                export(project(":kadre:foundation"))
            }
        }
    }
}

// Phase 1 : uikitMain consomme les internals de runtime (RuntimeWindowManager et ses ports).
// Phase 2 : et ceux de foundation (SurfaceRevision, porteur de l'état du kernel `apply`).
// Miroir du wiring runtime→foundation : -friend-modules est mono-valeur last-write-wins,
// donc le klib propre d'uikit est répété avec runtime et foundation (main et tests).
val uikitNativeTargets = listOf("IosArm64", "IosSimulatorArm64", "TvosArm64", "TvosSimulatorArm64")
val runtimeProject = project(":kadre:runtime")
val foundationProject = project(":kadre:foundation")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    val targetSegment = uikitNativeTargets.firstOrNull { name.endsWith(it) } ?: return@configureEach
    val lowerSegment = targetSegment.replaceFirstChar(Char::lowercase)
    val ownMainKlib = layout.buildDirectory.dir("classes/kotlin/$lowerSegment/main/klib/${project.name}")
    val runtimeMainKlib = runtimeProject.layout.buildDirectory
        .dir("classes/kotlin/$lowerSegment/main/klib/${runtimeProject.name}")
    val foundationMainKlib = foundationProject.layout.buildDirectory
        .dir("classes/kotlin/$lowerSegment/main/klib/${foundationProject.name}")
    dependsOn(runtimeProject.tasks.named("compileKotlin$targetSegment"))
    dependsOn(foundationProject.tasks.named("compileKotlin$targetSegment"))
    // Phase 2 : le kernel d'`apply` construit l'état porteur du verdict (`SurfaceRevision`,
    // internal foundation) — foundation rejoint runtime en friend, pour main comme pour les
    // tests. C'est le même patron que les autres modules plateforme consommateurs d'internals
    // (androidMain/webMain : foundation ET runtime en friends à main ET au host test).
    compilerOptions.freeCompilerArgs.add(
        ownMainKlib.zip(runtimeMainKlib) { own, runtime ->
            val friends = listOf(
                own.asFile.absolutePath,
                runtime.asFile.absolutePath,
                foundationMainKlib.get().asFile.absolutePath,
            )
            "-friend-modules=${friends.joinToString(File.pathSeparator)}"
        },
    )
}

// Le compilateur de metadata klib applique aussi la visibilité interne : depuis que uikitMain
// consomme RuntimeLock (expect en commonMain, actual apple), compileUikitMainKotlinMetadata
// (compilation KotlinNativeCompile de la cible "metadata") a besoin des trois klibs metadata de
// runtime en friend — même channel -friend-modules que les cibles natives ci-dessus. Le actual
// d'RuntimeLock est résolu depuis runtime_appleMain/runtime_nativeMain, le expect depuis
// runtime_commonMain : friend uniquement sur commonMain ne suffit pas.
// Phase 2 : le kernel d'`apply` lit aussi un internal de foundation (SurfaceRevision) — le
// klib metadata de foundation (commonMain seul : module purement common) rejoint la liste.
// Phase 7 : les compilations metadata des source sets iosMain/tvosMain (publishing klib —
// compileIosMainKotlinMetadata/compileTvosMainKotlinMetadata) appliquent la même visibilité ;
// le binding drop iOS (UIKitDropBindingIos, premier consommateur iosMain des internals
// runtime) les a fait surface. Même liste de friends que uikitMain : les hiérarchies sont
// les mêmes (iosMain/tvosMain ⊂ appleMain ⊂ nativeMain ⊂ common).
val runtimeMetadataKlibs = listOf(
    "commonMain" to runtimeProject.layout.buildDirectory.dir("classes/kotlin/metadata/commonMain"),
    "appleMain" to runtimeProject.layout.buildDirectory.dir("classes/kotlin/metadata/appleMain/klib/${runtimeProject.name}_appleMain"),
    "nativeMain" to runtimeProject.layout.buildDirectory.dir("classes/kotlin/metadata/nativeMain/klib/${runtimeProject.name}_nativeMain"),
)
val foundationMetadataKlib = foundationProject.layout.buildDirectory.dir("classes/kotlin/metadata/commonMain")

val metadataFriendModules = providers.provider {
    (runtimeMetadataKlibs.map { it.second } + foundationMetadataKlib)
        .joinToString(File.pathSeparator) { it.get().asFile.absolutePath }
}

val metadataCompilationNames = setOf(
    "compileUikitMainKotlinMetadata",
    "compileIosMainKotlinMetadata",
    "compileTvosMainKotlinMetadata",
)

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    if (name in metadataCompilationNames) {
        runtimeMetadataKlibs.forEach { (sourceSet, _) ->
            val capSegment = sourceSet.replaceFirstChar(Char::uppercase)
            dependsOn(runtimeProject.tasks.named("compile${capSegment}KotlinMetadata"))
        }
        // Le commonMain de foundation n'a pas de tâche `compileCommonKotlinMetadata` (le
        // partagé-common se compile dans chaque source set metadata) : son klib
        // `classes/kotlin/metadata/commonMain` est assemblé par `metadataCommonMainClasses`.
        dependsOn(foundationProject.tasks.named("metadataCommonMainClasses"))
        // -friend-modules est mono-valeur (last-write-wins) : les quatre klibs en une seule valeur.
        compilerOptions.freeCompilerArgs.add(metadataFriendModules.map { "-friend-modules=$it" })
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
