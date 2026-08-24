plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

kotlin {
    jvmToolchain(25)
    jvm()
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre:foundation"))
        }
        jvmMain.dependencies {
            api(project(":kadre:platform:desktop"))
        }
    }
}

tasks.named("check") {
    dependsOn(":kadre:foundation:check")
    dependsOn(":kadre:contracts:validator:check")
    dependsOn(":kadre:backend:appkit:check")
    dependsOn(":kadre:platform:desktop:check")
    dependsOn(":kadre:runtime:check")
    dependsOn("validateKotlinConsumer")
    dependsOn("validateJavaConsumer")
}

val contractTestRepository = rootProject.layout.buildDirectory.dir("kadre-contract-repository")
val contractPublications = tasks.register("publishContractArtifacts") {
    dependsOn(":kadre:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:foundation:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:backend:appkit:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:platform:desktop:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:runtime:publishAllPublicationsToContractTestRepository")
}

tasks.register<GradleBuild>("validateKotlinConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/kotlin")
    tasks = listOf("compileKotlin")
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

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}
