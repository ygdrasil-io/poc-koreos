pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Le brouillon du brief utilisait `orElse(error(...))` : `error` est évalué
        // eagerly et lèverait toujours. `orNull ?: error(...)` garde la même exigence
        // (propriété obligatoire) sans lever quand -PkadreRepository est fourni.
        val kadreRepository = providers.gradleProperty("kadreRepository").orNull
            ?: error("-PkadreRepository is required")
        maven { url = uri(kadreRepository) }
        mavenCentral()
        google()
    }
}

rootProject.name = "kadre-android-consumer"
