plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

group = "org.graphiks.kadre.internal"

kotlin {
    jvmToolchain(25)
    jvm()
    explicitApi()

    sourceSets {
        jvmMain.dependencies {
            api(project(":kadre:runtime"))
            implementation(libs.kffi.objc)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

if (System.getProperty("os.name", "").let { name ->
        name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
    }
) {
    val jvmTest = tasks.named<Test>("jvmTest") {
        jvmArgs(
            "-XstartOnFirstThread",
            "--enable-native-access=ALL-UNNAMED",
            "-XX:ErrorFile=${layout.buildDirectory.file("ci-diagnostics/hs_err_pid%p.log").get().asFile.absolutePath}",
        )
        // The standalone-loop proof owns NSApplication and must run in a fresh process.
        filter.excludeTestsMatching(
            "org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest.realKffiStandaloneLoopStartsAndStopsOnMacOs",
        )
        forkEvery = 1
    }
    val appKitStandaloneLoopTest = tasks.register<Test>("appKitStandaloneLoopTest") {
        group = "verification"
        description = "Runs the process-owning AppKit standalone-loop proof in an isolated JVM."
        testClassesDirs = jvmTest.get().testClassesDirs
        classpath = jvmTest.get().classpath
        jvmArgs(
            "-XstartOnFirstThread",
            "--enable-native-access=ALL-UNNAMED",
            "-XX:ErrorFile=${layout.buildDirectory.file("ci-diagnostics/hs_err_pid%p.log").get().asFile.absolutePath}",
        )
        filter.includeTestsMatching(
            "org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest.realKffiStandaloneLoopStartsAndStopsOnMacOs",
        )
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/appKitStandaloneLoopTest"))
    }
    val appKitNativeTests = tasks.register("appKitNativeTests") {
        group = "verification"
        description = "Runs the AppKit unit suite and its process-owning standalone-loop proof."
        dependsOn(jvmTest, appKitStandaloneLoopTest)
    }
    tasks.named("check") {
        dependsOn(appKitNativeTests)
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
