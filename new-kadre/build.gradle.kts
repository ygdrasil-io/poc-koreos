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
            api(project(":kadre-new:foundation"))
        }
        jvmMain.dependencies {
            api(project(":kadre-new:platform:desktop"))
        }
    }
}

tasks.named("check") {
    dependsOn(":kadre-new:foundation:check")
    dependsOn(":kadre-new:contracts:validator:check")
    dependsOn(":kadre-new:platform:desktop:check")
    dependsOn(":kadre-new:runtime:check")
    dependsOn("validateKotlinConsumer")
    dependsOn("validateJavaConsumer")
}

val contractTestRepository = rootProject.layout.buildDirectory.dir("new-kadre-contract-repository")
val contractPublications = tasks.register("publishContractArtifacts") {
    dependsOn(":kadre-new:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre-new:foundation:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre-new:platform:desktop:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre-new:runtime:publishAllPublicationsToContractTestRepository")
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
            url = rootProject.layout.buildDirectory.dir("new-kadre-contract-repository").get().asFile.toURI()
        }
    }
}
