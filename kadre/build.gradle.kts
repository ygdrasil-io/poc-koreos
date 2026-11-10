plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    // ownership-first: réactivé par le plan Android/UIKit/Web
    // (android, js, wasmJs, iosArm64, iosSimulatorArm64, tvosArm64, tvosSimulatorArm64)
    jvm()
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre:foundation"))
        }
        // ownership-first: réactivé par le plan Android/UIKit/Web
        // (androidMain → platform:android, jsMain/wasmJsMain → platform:web,
        // appleMain → platform:uikit)
        jvmMain.dependencies {
            api(project(":kadre:platform:desktop"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.named("check") {
    dependsOn(":kadre:foundation:check")
    dependsOn(":kadre:contracts:validator:check")
    dependsOn(":kadre:backend:appkit:check")
    dependsOn(":kadre:backend:wayland:check")
    dependsOn(":kadre:platform:desktop:check")
    // ownership-first: réactivé par le plan Android/UIKit/Web
    // (:kadre:platform:web:check, :kadre:platform:android:check,
    // validateWebKotlinConsumer, validateTypeScriptConsumer, validateSwiftConsumer)
    dependsOn(":kadre:runtime:check")
    dependsOn("validateKotlinConsumer")
    dependsOn("validateJavaConsumer")
    // pin : la pureté est vérifiée à chaque check local avec les artefacts publiés
    dependsOn("jvmTest")
}

val contractTestRepository = rootProject.layout.buildDirectory.dir("kadre-contract-repository")
val contractPublications = tasks.register("publishContractArtifacts") {
    dependsOn(":kadre:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:foundation:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:backend:appkit:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:backend:wayland:publishAllPublicationsToContractTestRepository")
    // phase 11 : le graphe runtime COMPLET — le consumer exécute une session
    // réelle, linux et win32 doivent rider l'agrégateur depuis ce repository
    dependsOn(":kadre:backend:linux:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:backend:win32:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:platform:desktop:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:runtime:publishAllPublicationsToContractTestRepository")
    // ownership-first: réactivé par le plan Android/UIKit/Web
    // (platform:web, platform:android, platform:android-devicetest, integration:compose)
    dependsOn(":kadre:backend:win32:publishAllPublicationsToContractTestRepository")
}

tasks.register<GradleBuild>("validateKotlinConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/kotlin")
    tasks = listOf("test")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

tasks.register<GradleBuild>("validateJavaConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/java")
    tasks = listOf("compileJava")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

// ownership-first: réactivé par le plan Android/UIKit/Web
// (validateWebKotlinConsumer, validateTypeScriptConsumer, validateSwiftConsumer,
// emitTypeScriptBrowserConsumer — consumers web/typescript/swift)

tasks.named<Test>("jvmTest") {
    systemProperty("kadreContractRepository", rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.absolutePath)
    systemProperty("kadreVersion", project.version.toString())
    dependsOn("publishContractArtifacts")
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}

// ownership-first: le bloc tasks.configureEach désactivant les publications natives
// Ios/Tvos est retiré — l'umbrella n'a plus de cible native.

// Preuve consumer exécutée (phase 13) : le consumer tourne la session
// standalone win32 via la découverte ServiceLoader depuis le repo contractuel
// — le backend win32 rejoint `contractPublications` (clôture wayland-D4).
val win32ConsumerRun = tasks.register<GradleBuild>("win32ConsumerRun") {
    group = "verification"
    description = "Runs the published-umbrella consumer session on the win32 provider."
    dependsOn(contractPublications)
    dir = file("consumers/kotlin")
    tasks = listOf(":win32ConsumerRun")
    startParameter.projectProperties["kadreRepository"] =
        rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.absolutePath
    startParameter.projectProperties["kadreVersion"] =
        project(":kadre").version.toString()
}
