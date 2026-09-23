plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.ui.InternalComposeUiApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=org.graphiks.kffi.objc.PlatformAvailability",
        )
    }
}

tasks.test { useJUnitPlatform() }

application {
    mainClass.set("org.graphiks.kadre.samples.desktour.DeskTourMainKt")
    applicationDefaultJvmArgs = listOf(
        "-XstartOnFirstThread",
        "--enable-native-access=ALL-UNNAMED",
    )
}

dependencies {
    implementation(project(":kadre"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kffi.objc)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
