package ygdrasil.conventions

import java.net.URI
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("org.jetbrains.dokka")
}

dependencies {
    add("dokkaGfmPlugin", "org.jetbrains.dokka:gfm-plugin:2.2.0")
}

tasks.withType<DokkaTask>().configureEach {
    moduleName.set(project.name)
    outputDirectory.set(rootProject.file("docs/koreos/api/${project.name}"))

    dokkaSourceSets.configureEach {
        skipEmptyPackages.set(true)
        reportUndocumented.set(false)

        val srcSetName = name
        val srcDir = project.file("src/${srcSetName}/kotlin")
        if (srcDir.exists()) {
            sourceLink {
                localDirectory.set(srcDir)
                remoteUrl.set(URI("https://github.com/ygdrasil-io/poc-koreos/blob/master/${project.name}/src/${srcSetName}/kotlin").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}
