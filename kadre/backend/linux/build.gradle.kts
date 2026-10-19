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
            implementation(libs.kffi.evdev)
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// Linux-only integration task mirroring the wayland one: the evdev
// proofs load the real input stack and need /dev/input (+ uinput).
// The O3 proofs load the real evdev/udev stack and need /dev/input (+ uinput):
// they run only on Linux, in the dedicated task, never inside the portable jvmTest.
tasks.named<Test>("jvmTest") {
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.linux.EvdevGamepadLinuxTest",
    )
}

val onLinux = System.getProperty("os.name", "").contains("Linux", ignoreCase = true)
tasks.register<Test>("linuxEvdevTests") {
    group = "verification"
    description = "Runs the Linux-only evdev gamepad proofs (real nodes / uinput)."
    enabled = onLinux
    testClassesDirs = tasks.named<Test>("jvmTest").get().testClassesDirs
    classpath = tasks.named<Test>("jvmTest").get().classpath
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-XX:ErrorFile=${layout.buildDirectory.file("ci-diagnostics/hs_err_pid%p.log").get().asFile.absolutePath}",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.linux.EvdevGamepadLinuxTest",
    )
    forkEvery = 1
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/linuxEvdevTests"))
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}
