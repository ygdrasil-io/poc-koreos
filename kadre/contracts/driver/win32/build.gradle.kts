plugins {
    kotlin("jvm")
    id("maven-publish")
}

group = "org.graphiks.kadre.contracts"
val win32KffiVersion = providers.gradleProperty("kadreKffiWin32Version").orElse(libs.versions.kffi.win32)

kotlin {
    jvmToolchain(25)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation("org.graphiks:kffi-win32:${win32KffiVersion.get()}")
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}
