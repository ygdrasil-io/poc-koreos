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
                implementation(libs.kotlinx.coroutines.test)
                implementation("org.graphiks:kffi-win32-jvm:${win32KffiVersion.get()}:native-test-support")
                implementation(project(":kadre:platform:desktop"))
                implementation(project(":kadre:contracts:driver:win32"))
            }
        }
    }
}

dependencies {
    constraints {
        add("jvmMainImplementation", "org.graphiks:kffi-win32-jvm") {
            version { strictly(win32KffiVersion.get()) }
            because("The root snapshot metadata redirects to a moving JVM snapshot")
        }
    }
}

// Les preuves O3 chargent la pile native Windows (user32/kernel32) : elles ne
// tournent que sur Windows, dans le task dédié, jamais dans le jvmTest portable.
tasks.named<Test>("jvmTest") {
    filter.excludeTestsMatching("org.graphiks.kadre.internal.win32.Win32RichInputNativeContractTest")
    filter.excludeTestsMatching("org.graphiks.kadre.internal.win32.Win32TextInputNativeContractTest")
    filter.excludeTestsMatching("org.graphiks.kadre.internal.win32.Win32AdvancedWindowNativeContractTest")
    filter.excludeTestsMatching("org.graphiks.kadre.internal.win32.Win32WindowMutationNativeContractTest")
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
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32DropNativeContractTest",
    )
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32RawInputNativeContractTest",
    )
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32DisplaysNativeContractTest",
    )
    filter.excludeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32GamepadNativeContractTest",
    )
    // Les preuves embarquées partagent un état process global (registre de
    // sessions, EDT, broker raw input) : elles tournent dans leur propre
    // tâche de test, isolée des autres suites du module.
    filter.excludeTestsMatching("org.graphiks.kadre.internal.win32.Win32EmbeddedO2Test")
    filter.excludeTestsMatching("org.graphiks.kadre.internal.win32.Win32EmbeddedHostContractTest")
    filter.excludeTestsMatching("org.graphiks.kadre.internal.win32.Win32StressNativeContractTest")
    filter.excludeTestsMatching("org.graphiks.kadre.internal.win32.Win32ClassloaderReuseTest")
}

val onWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
val win32ReuseTests = tasks.register<Test>("win32ReuseTests") {
    group = "verification"
    description = "Runs the classloader reuse qualification in an isolated test JVM."
    enabled = onWindows
    filter.includeTestsMatching("org.graphiks.kadre.internal.win32.Win32ClassloaderReuseTest")
    testClassesDirs = tasks.named<Test>("jvmTest").get().testClassesDirs
    classpath = tasks.named<Test>("jvmTest").get().classpath
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/win32ReuseTests"))
}
val win32EmbeddedTests = tasks.register<Test>("win32EmbeddedTests") {
    group = "verification"
    description = "Runs the AWT-embedded host proofs in an isolated test JVM."
    enabled = onWindows
    filter.includeTestsMatching("org.graphiks.kadre.internal.win32.Win32EmbeddedO2Test")
    filter.includeTestsMatching("org.graphiks.kadre.internal.win32.Win32StressNativeContractTest")

    testClassesDirs = tasks.named<Test>("jvmTest").get().testClassesDirs
    classpath = tasks.named<Test>("jvmTest").get().classpath
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/win32EmbeddedTests"))
}
val win32NativeTests = tasks.register<Test>("win32NativeTests") {
    group = "verification"
    description = "Runs the Windows-only Win32 O3 frontier proofs."
    enabled = onWindows
    filter.includeTestsMatching("org.graphiks.kadre.internal.win32.Win32RichInputNativeContractTest")
    filter.includeTestsMatching("org.graphiks.kadre.internal.win32.Win32AdvancedWindowNativeContractTest")
    filter.includeTestsMatching("org.graphiks.kadre.internal.win32.Win32TextInputNativeContractTest")
    filter.includeTestsMatching("org.graphiks.kadre.internal.win32.Win32WindowMutationNativeContractTest")
    filter.includeTestsMatching("org.graphiks.kadre.internal.win32.Win32InputNativeContractTest")
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32DropNativeContractTest",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32RawInputNativeContractTest",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32DisplaysNativeContractTest",
    )
    filter.includeTestsMatching(
        "org.graphiks.kadre.internal.win32.Win32GamepadNativeContractTest",
    )
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
    dependsOn(tasks.named("jvmTest"), win32NativeTests, win32EmbeddedTests, win32ReuseTests)
}

tasks.register<JavaExec>("win32RichInputManual") {
    group = "verification"
    description = "Opens explicit rich-input reception; stdout contains no application text."
    dependsOn(tasks.named("jvmTestClasses"))
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("org.graphiks.kadre.internal.win32.Win32RichInputManualHarness")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    onlyIf { onWindows }
}

tasks.register<JavaExec>("win32DropManual") {
    group = "verification"
    description = "Opens explicit OLE drop reception; stdout contains counts and categories only."
    dependsOn(tasks.named("jvmTestClasses"))
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("org.graphiks.kadre.internal.win32.Win32DropManualHarness")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    onlyIf { onWindows }
}

tasks.register<JavaExec>("win32RawInputManual") {
    group = "verification"
    description = "Opens explicit raw-input reception; stdout contains counters only."
    dependsOn(tasks.named("jvmTestClasses"))
    classpath = tasks.named<Test>("jvmTest").get().classpath
    mainClass.set("org.graphiks.kadre.internal.win32.Win32RawInputManualHarness")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    onlyIf { onWindows }
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
