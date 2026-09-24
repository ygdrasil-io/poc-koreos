plugins {
    base
}

/*
 * The published coordinates of the `@kadre/host` package, chosen by `:kadre:platform:web`:
 *
 *   org.graphiks.kadre:web-host-package:<version>:js     -> web-host-package-<version>-js.zip
 *   org.graphiks.kadre:web-host-package:<version>:wasmJs -> web-host-package-<version>-wasmJs.zip
 *
 * The Kotlin publications of `platform:web` only carry klibs, poms, modules and sources jars, so
 * this consumer resolves the classified zip instead of a `project(...)` dependency. Both archives
 * carry the same curated `index.d.ts`, which this build proves by comparing them.
 */
val kadreRepository = providers.gradleProperty("kadreRepository").orNull
    ?: error("-PkadreRepository is required")
val kadreVersion = providers.gradleProperty("kadreVersion").orNull
    ?: error("-PkadreVersion is required")

val repositoryDirectory = file(kadreRepository)
val publishedGroup = "org/graphiks/kadre/web-host-package/$kadreVersion"
val unpackedPackages = layout.buildDirectory.dir("kadre-host-package")

val unpackHostPackage by tasks.registering {
    group = "build"
    description = "Unpacks the @kadre/host packages from the contract test repository."
    inputs.dir(repositoryDirectory)
    inputs.property("kadreVersion", kadreVersion)
    outputs.dir(unpackedPackages)
    doLast {
        val source = repositoryDirectory.resolve(publishedGroup)
        check(source.isDirectory) { "no published @kadre/host package under $source" }
        listOf("js", "wasmJs").forEach { target ->
            val archive = source.resolve("web-host-package-$kadreVersion-$target.zip")
            check(archive.isFile) { "the repository must publish $archive" }
            val into = unpackedPackages.get().dir(target).asFile
            into.deleteRecursively()
            into.mkdirs()
            copy { from(zipTree(archive)); into(into) }
            check(into.resolve("index.d.ts").isFile) { "$archive must carry index.d.ts" }
        }
    }
}

val verifySameDeclarations by tasks.registering {
    group = "verification"
    description = "Checks both targets publish the same curated @kadre/host declaration."
    dependsOn(unpackHostPackage)
    doLast {
        val js = unpackedPackages.get().dir("js").asFile.resolve("index.d.ts").readText()
        val wasmJs = unpackedPackages.get().dir("wasmJs").asFile.resolve("index.d.ts").readText()
        check(js == wasmJs) { "the js and wasmJs packages must publish the same index.d.ts" }
    }
}

val npmInstall by tasks.registering(Exec::class) {
    dependsOn(unpackHostPackage)
    workingDir(projectDir)
    commandLine("npm", "install", "--ignore-scripts")
    inputs.file(layout.projectDirectory.file("package.json"))
    outputs.dir(layout.projectDirectory.dir("node_modules"))
}

val typeCheck by tasks.registering(Exec::class) {
    group = "verification"
    description = "Type-checks the @kadre/host TypeScript consumer against the published declaration."
    dependsOn(npmInstall, verifySameDeclarations)
    workingDir(projectDir)
    commandLine("npx", "--no-install", "tsc", "--noEmit")
}

tasks.named("check") {
    dependsOn(typeCheck)
}
