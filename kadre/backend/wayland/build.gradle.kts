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
    forkEvery = 1
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/waylandNativeTests"))
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
