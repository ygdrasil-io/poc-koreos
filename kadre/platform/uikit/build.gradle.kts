import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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
    }

    // Cadre de framework pour chaque target native : `binaries` n'existe pas au niveau de
    // l'extension KMP (KGP 2.4.20), seulement sur chaque target KotlinNativeTarget.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries {
            framework {
                baseName = "KadreUikit"
            }
        }
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
