package ygdrasil.conventions

import java.net.URI

plugins {
    id("org.jetbrains.dokka")
}

dependencies {
    add("dokkaGfmPlugin", "org.jetbrains.dokka:gfm-plugin:2.2.0")
}

tasks.dokkaGfm {
    moduleName.set("shared")
    dokkaSourceSets.named("commonMain") {
        sourceLink {
            localDirectory.set(project.file("src/commonMain/kotlin"))
            remoteUrl.set(URI("https://github.com/ygdrasil-io/project-template/blob/master/shared/src/commonMain/kotlin").toURL())
            remoteLineSuffix.set("#L")
        }
    }
}
