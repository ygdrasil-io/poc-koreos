package org.graphiks.kadre

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AndroidVariantPurityTest {

    @Test
    fun androidVariantTransitiveGraphHasNoDesktopOrKffi() {
        val repository = System.getProperty("kadreContractRepository")
            ?: fail("kadreContractRepository system property is required")
        val version = System.getProperty("kadreVersion") ?: fail("kadreVersion is required")
        val moduleFile = java.io.File(
            repository,
            "org/graphiks/kadre/kadre/$version/kadre-$version.module",
        )
        assertTrue(moduleFile.exists(), "published umbrella module metadata not found at ${moduleFile.path}")
        // Le `groovy.json.JsonSlurper` du brief est du Groovy — indisponible sur le classpath
        // de compilation d'un jvmTest Kotlin. `Json.parseToJsonElement` (kotlinx-serialization-json,
        // ajouté en testImplementation) parse en JsonElement sans plugin de sérialisation : c'est
        // l'équivalent structurel le plus simple (l'arbre JsonElement remplace la Map slurpée).
        val metadata = Json.parseToJsonElement(moduleFile.readText()).jsonObject
        val variants = (metadata["variants"] ?: fail("no variants in published umbrella metadata")).jsonArray
        val androidVariants = variants.map { it.jsonObject }.filter { variant ->
            val attributes = variant["attributes"]?.jsonObject
            // Variantes AGP-KMP : org.jetbrains.kotlin.platform.type = androidJvm
            attributes?.get("org.jetbrains.kotlin.platform.type")?.jsonPrimitive?.content == "androidJvm"
        }
        assertTrue(androidVariants.isNotEmpty(), "no androidJvm variant in published umbrella metadata")
        for (variant in androidVariants) {
            assertNoDesktopOrKffiInVariant(variant)
            // Indirection de publication KMP : les variantes androidJvm de l'umbrella délèguent
            // via `available-at` au module plateforme (org.graphiks.kadre:kadre-android) — les
            // tableaux `dependencies` de l'umbrella sont structurellement vides. Les mêmes
            // vérifications doivent donc porter sur le metadata délégué, sinon le verrou ne
            // pourrait jamais mordre.
            val availableAt = variant["available-at"]?.jsonObject ?: continue
            val delegatedFile = java.io.File(
                moduleFile.parentFile,
                availableAt["url"]?.jsonPrimitive?.content
                    ?: fail("android variant ${variant["name"]?.jsonPrimitive?.content} has available-at without url"),
            )
            assertTrue(delegatedFile.exists(), "delegated android module metadata not found at ${delegatedFile.path}")
            val delegatedMetadata = Json.parseToJsonElement(delegatedFile.readText()).jsonObject
            val delegatedVariants = delegatedMetadata["variants"]?.jsonArray
                ?: fail("no variants in delegated android metadata ${delegatedFile.path}")
            val delegatedAndroidVariants = delegatedVariants.map { it.jsonObject }.filter { delegated ->
                delegated["attributes"]?.jsonObject
                    ?.get("org.jetbrains.kotlin.platform.type")?.jsonPrimitive?.content == "androidJvm"
            }
            assertTrue(
                delegatedAndroidVariants.isNotEmpty(),
                "no androidJvm variant in delegated android metadata ${delegatedFile.path}",
            )
            for (delegated in delegatedAndroidVariants) {
                assertNoDesktopOrKffiInVariant(delegated)
            }
        }
    }

    private fun assertNoDesktopOrKffiInVariant(variant: JsonObject) {
        val variantName = variant["name"]?.jsonPrimitive?.content
        val dependencies = variant["dependencies"]?.jsonArray ?: JsonArray(emptyList())
        for (dependency in dependencies) {
            val dependencyObject = dependency.jsonObject
            val group = dependencyObject["group"]?.jsonPrimitive?.content
            val module = dependencyObject["module"]?.jsonPrimitive?.content
            if (group == "org.graphiks" && module?.startsWith("kffi-") == true) {
                fail("android variant $variantName depends on KFFI artifact $module")
            }
            if (module?.contains("desktop") == true) {
                fail("android variant $variantName depends on desktop artifact $module")
            }
        }
    }
}
