import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

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
            implementation(libs.kffi.wayland)
            implementation(libs.kffi.posix)
            // gamepads ride the process-wide evdev service (design D6)
            implementation(project(":kadre:backend:linux"))
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":kadre:platform:desktop"))
                implementation(project(":kadre:contracts:driver:wayland"))
            }
        }
    }
}

// The O3 session proofs load the real Wayland native stack and need a compositor:
// they run only on Linux, in the dedicated task, never inside the portable jvmTest.
tasks.named<Test>("jvmTest") {
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.wayland.WaylandNativeSessionLinuxTest",
    )
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.wayland.WaylandWindowsLinuxTest",
    )
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.wayland.WaylandShmDiagLinuxTest",
    )
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.wayland.WaylandMetricsLinuxTest",
    )
    // les preuves stress exigent un compositor ET leur tâche dédiée : hors
    // jvmTest portable (sinon l'evidence lit la même classe deux fois)
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.wayland.WaylandStressLinuxTest",
    )
}

val onLinux = System.getProperty("os.name", "").contains("Linux", ignoreCase = true)
val waylandNativeTests = tasks.register<Test>("waylandNativeTests") {
    group = "verification"
    description = "Runs the Linux-only Wayland O3 session proofs against a real compositor."
    enabled = onLinux
    testClassesDirs = tasks.named<Test>("jvmTest").get().testClassesDirs
    classpath = tasks.named<Test>("jvmTest").get().classpath
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "-XX:ErrorFile=${layout.buildDirectory.file("ci-diagnostics/hs_err_pid%p.log").get().asFile.absolutePath}",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.wayland.WaylandNativeSessionLinuxTest",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.wayland.WaylandWindowsLinuxTest",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.wayland.WaylandMetricsLinuxTest",
    )
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
    forkEvery = 1
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/waylandNativeTests"))
}

// Stress phase 11 : files, interruption, teardown répété — tâche dédiée
// hors gate bloquante (design WAYLAND-PHASE-11-CLOSURE-DESIGN D5).
val waylandStressTests = tasks.register<Test>("waylandStressTests") {
    group = "verification"
    description = "Runs the Linux-only Wayland stress proofs (queues, interruption, repeated teardown)."
    enabled = onLinux
    testClassesDirs = tasks.named<Test>("jvmTest").get().testClassesDirs
    classpath = tasks.named<Test>("jvmTest").get().classpath
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "-XX:ErrorFile=${layout.buildDirectory.file("ci-diagnostics/hs_err_pid%p.log").get().asFile.absolutePath}",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.wayland.WaylandStressLinuxTest",
    )
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
    forkEvery = 1
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/waylandStressTests"))
}

// Les preuves contractuelles consomment les deux suites : O2 (portable) et
// O3 (compositor réel). L'agrégat garantit que les deux répertoires JUnit
// existent là où la gate tourne.
val waylandContractTests = tasks.register("waylandContractTests") {
    group = "verification"
    description = "Runs the O2 suite and the Linux-only O3 proofs feeding the contract evidence."
    dependsOn(tasks.named("jvmTest"), waylandNativeTests)
}

// Friend-wiring JVM : le backend construit des internals de foundation
// (WindowManagerRevision pour l'état du WindowManager headless) — la même
// couture que :kadre:runtime, qui friende le jvmJar (le répertoire brut
// `classes/kotlin/jvm/main` ne suffit pas, cf. runtime/build.gradle.kts).
val foundationJvmJar = project(":kadre:foundation").tasks.named<Jar>("jvmJar")
tasks.withType<BaseKotlinCompile>().configureEach {
    if (name == "compileKotlinJvm" || name == "compileTestKotlinJvm") {
        dependsOn(foundationJvmJar)
        friendPaths.from(foundationJvmJar.flatMap(Jar::getArchiveFile))
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
