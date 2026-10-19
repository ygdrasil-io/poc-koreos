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
            // the D-Bus control plane rides the posix AF_UNIX surface
            implementation(libs.kffi.posix)
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
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
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.linux.LinuxCapturePortalLinuxTest",
    )
}

val onLinux = System.getProperty("os.name", "").contains("Linux", ignoreCase = true)
tasks.register<Test>("linuxEvdevTests") {
    group = "verification"
    description = "Runs the Linux-only proofs: evdev gamepads (real nodes / uinput) and the capture portal (real dbus-daemon)."
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
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.linux.LinuxCapturePortalLinuxTest",
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
