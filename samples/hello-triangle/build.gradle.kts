/**
 * Sample hello-triangle — interactive wgpu4k RGB rendering (GRA-137/GRA-138).
 *
 * Consumes Kadre's [RawWindowHandle] to initialize wgpu4k:
 *   AppKit → CAMetalLayer → Metal surface, or Win32 → HWND/HINSTANCE → Primary surface
 *
 * Both paths create the adapter, device, surface, and render pipeline used by the triangle.
 *
 * Usage: ./gradlew :samples:hello-triangle:run
 * Requirements: JDK 25 on macOS or Windows (launched by Gradle).
 */
plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("org.graphiks.kadre.samples.hellotriangle.MainKt")

    // macOS: NSApplication must run on the main JVM thread.
    // -XstartOnFirstThread is a macOS-only JVM flag — the Windows/Linux JVM rejects it.
    // Panama FFM: --enable-native-access suppresses JDK 22+ warnings.
    applicationDefaultJvmArgs = buildList {
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
        add("--enable-native-access=ALL-UNNAMED")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":kadre"))
    implementation(libs.wgpu4k)
    // wgpu4k descriptor data classes (VertexState, FragmentState, RenderPipelineDescriptor, etc.)
    // synchronized with the webgpu-ktypes version embedded in wgpu4k:0.1.1
    implementation(libs.webgpu.ktypes.descriptors)
    // runBlocking — transitive via wgpu4k but declared explicitly for clarity
    implementation(libs.kotlinx.coroutines.core)
    // Kadre platform backends. The :kadre facade selects one by reflection at runtime
    // (AppKit on macOS, Win32 on Windows, X11/Wayland on Linux), so every desktop backend
    // must be on the classpath for the sample to open a window on each OS. Inert elsewhere.
    implementation(project(":kadre-win32"))
    implementation(project(":kadre-wayland"))
    implementation(project(":kadre-x11"))
}
