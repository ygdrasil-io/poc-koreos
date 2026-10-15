plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

group = "org.graphiks.kadre.internal"
val win32KffiVersion = providers.gradleProperty("kadreKffiWin32Version").orElse(libs.versions.kffi.win32)

kotlin {
    jvmToolchain(25)
    jvm()
    explicitApi()

    sourceSets {
        jvmMain.dependencies {
            api(project(":kadre:runtime"))
            implementation("org.graphiks:kffi-win32:${win32KffiVersion.get()}")
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":kadre:platform:desktop"))
                implementation(project(":kadre:contracts:driver:win32"))
            }
        }
    }
}

// Les preuves O3 chargent la pile native Windows (user32/kernel32) : elles ne
// tournent que sur Windows, dans le task dédié, jamais dans le jvmTest portable.
tasks.named<Test>("jvmTest") {
    filter.excludeTestsMatching("org.graphiks.kadre.internal.win32.Win32InputNativeContractTest")
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32NativeContractTest",
    )
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32HostNativeContractTest",
    )
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32WindowsNativeContractTest",
    )
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32SurfaceNativeContractTest",
    )
}

val onWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
val win32NativeTests = tasks.register<Test>("win32NativeTests") {
    group = "verification"
    description = "Runs the Windows-only Win32 O3 frontier proofs."
    enabled = onWindows
    filter.includeTestsMatching("org.graphiks.kadre.internal.win32.Win32InputNativeContractTest")
    testClassesDirs = tasks.named<Test>("jvmTest").get().testClassesDirs
    classpath = tasks.named<Test>("jvmTest").get().classpath
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-XX:ErrorFile=${layout.buildDirectory.file("ci-diagnostics/hs_err_pid%p.log").get().asFile.absolutePath}",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32NativeContractTest",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32HostNativeContractTest",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32WindowsNativeContractTest",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32SurfaceNativeContractTest",
    )
    forkEvery = 1
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/win32NativeTests"))
}

val win32ContractTests = tasks.register("win32ContractTests") {
    group = "verification"
    description = "Runs the O2 portable suite and the Windows-only O3 proofs feeding the contract evidence."
    dependsOn(tasks.named("jvmTest"), win32NativeTests)
}

tasks.register<JavaExec>("win32InputManual") {
    group = "verification"
    description = "Opens the interactive Win32 keyboard/pointer harness; no contract verdict is inferred."
    dependsOn(tasks.named("jvmTestClasses"))
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("org.graphiks.kadre.internal.win32.Win32InputManualHarness")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    onlyIf { onWindows }
}

// Friend-wiring JVM : le backend construit des internals de foundation
// (WindowManagerRevision pour l'état du WindowManager headless) — la même
// couture que :kadre:runtime et le backend wayland.
val foundationJvmJar = project(":kadre:foundation").tasks.named<Jar>("jvmJar")
tasks.withType<org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile>().configureEach {
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
