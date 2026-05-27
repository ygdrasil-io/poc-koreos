gradle.startParameter.projectProperties["android.builtInKotlin"] = "false"
gradle.startParameter.projectProperties["android.newDsl"] = "false"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
