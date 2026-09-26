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

/**
 * The browser entry of the same consumer, emitted once per target.
 *
 * The browser driver serves the target's package and runs this file, so each emission is compiled
 * against that target's published `index.d.ts`; the source, and therefore the executed logic, is the
 * single `src/consumer.ts` the type check above compiles. The bare specifier `@kadre/host` is left
 * untouched, because the page resolves it with an import map.
 */
val browserConsumerDirectory = layout.buildDirectory.dir("browser-consumer")

val writeBrowserConsumerProjects by tasks.registering {
    group = "build"
    description = "Writes the per-target TypeScript project that compiles the browser consumer."
    dependsOn(unpackHostPackage)
    inputs.dir(unpackedPackages)
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    outputs.dir(browserConsumerDirectory)
    doLast {
        listOf("js", "wasmJs").forEach { target ->
            val packageDirectory = unpackedPackages.get().dir(target).asFile
            val outputDirectory = browserConsumerDirectory.get().dir(target).asFile
            outputDirectory.mkdirs()
            outputDirectory.resolve("tsconfig.json").writeText(
                """
                {
                  "extends": "${layout.projectDirectory.file("tsconfig.json").asFile.absolutePath}",
                  "compilerOptions": {
                    "noEmit": false,
                    "rootDir": "${layout.projectDirectory.dir("src").asFile.absolutePath}",
                    "outDir": "${outputDirectory.absolutePath}",
                    "paths": {
                      "@kadre/host": ["${packageDirectory.resolve("index.d.ts").absolutePath}"]
                    }
                  }
                }
                """.trimIndent() + "\n",
            )
        }
    }
}

fun browserConsumerTsconfig(target: String) = browserConsumerDirectory.map { it.file("$target/tsconfig.json") }
fun browserConsumerEntry(target: String) = browserConsumerDirectory.map { it.file("$target/consumer.js") }

fun registerBrowserConsumer(target: String) = tasks.register<Exec>("emit${target.replaceFirstChar(Char::uppercase)}BrowserConsumer") {
    group = "build"
    description = "Compiles the browser consumer against the $target published package."
    dependsOn(npmInstall, verifySameDeclarations, writeBrowserConsumerProjects)
    workingDir(projectDir)
    commandLine(
        "npx",
        "--no-install",
        "tsc",
        "--project",
        browserConsumerTsconfig(target).get().asFile.absolutePath,
    )
    inputs.file(layout.projectDirectory.file("src/consumer.ts"))
    inputs.file(browserConsumerTsconfig(target))
    outputs.file(browserConsumerEntry(target))
    doLast {
        check(browserConsumerEntry(target).get().asFile.isFile) {
            "the $target consumer must compile to consumer.js"
        }
    }
}

val emitJsBrowserConsumer = registerBrowserConsumer("js")
val emitWasmJsBrowserConsumer = registerBrowserConsumer("wasmJs")

val emitBrowserConsumer by tasks.registering {
    group = "build"
    description = "Emits the browser entry of the TypeScript consumer for each target's package."
    dependsOn(emitJsBrowserConsumer, emitWasmJsBrowserConsumer)
}

tasks.named("check") {
    dependsOn(typeCheck, emitBrowserConsumer)
}
