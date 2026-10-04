package org.graphiks.kadre.platform.web

import org.graphiks.kadre.input.DropItemDescriptor
import org.graphiks.kadre.input.DropItemKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pure mime rules of the web drop snapshot, and the one rule they refuse to own.
 *
 * The classification is the closed vocabulary of D-D1: a file is a `File`, the plain-text format is
 * `Text`, the URI list is `Uri`, and everything else the element was handed is a `Binary` — named
 * for what it is, never translated into something more familiar. The canonicality question is *not*
 * re-implemented here: the single owner of "canonical media type" is the `DropItemDescriptor`
 * constructor's own requirement in the foundation (`TextDropRaw.kt`), and
 * [isCanonicalWebDropMediaType] asks it — which is why a non-canonical mime is provably dropped and
 * provably never fabricated: no descriptor the glue can build would carry one.
 */
class WebDropMappingTest {
    @Test
    fun theItemKindIsTheClosedVocabularyOfTheSnapshot() {
        assertEquals(DropItemKind.File, webDropItemKind(isFile = true, type = "image/png"))
        assertEquals(DropItemKind.File, webDropItemKind(isFile = true, type = ""))
        assertEquals(DropItemKind.Text, webDropItemKind(isFile = false, type = "text/plain"))
        assertEquals(DropItemKind.Uri, webDropItemKind(isFile = false, type = "text/uri-list"))
        assertEquals(DropItemKind.Binary, webDropItemKind(isFile = false, type = "text/html"))
        assertEquals(DropItemKind.Binary, webDropItemKind(isFile = false, type = "application/x-kadre"))
        assertEquals(DropItemKind.Binary, webDropItemKind(isFile = false, type = ""))
        assertEquals(
            DropItemKind.Binary,
            webDropItemKind(isFile = false, type = "TEXT/PLAIN"),
            "a format the browser did not name exactly as the model spells it is not reclassified",
        )
    }

    @Test
    fun canonicalMimesAreKeptAndNonCanonicalOnesAreDroppedNeverFabricated() {
        assertEquals("image/png", canonicalWebDropMimeTypeOrNull("image/png"))
        assertEquals("text/plain", canonicalWebDropMimeTypeOrNull("text/plain"))
        assertEquals("application/octet-stream", canonicalWebDropMimeTypeOrNull("application/octet-stream"))

        assertNull(canonicalWebDropMimeTypeOrNull("TEXT/PLAIN"), "uppercase is not the canonical spelling")
        assertNull(canonicalWebDropMimeTypeOrNull(""), "an empty type is no type at all")
        assertNull(canonicalWebDropMimeTypeOrNull("image"), "a type without a subtype is not a media type")
        assertNull(canonicalWebDropMimeTypeOrNull("image/png;charset=utf-8"), "parameters are not part of the type")
        assertNull(canonicalWebDropMimeTypeOrNull("image/pñg"), "outside printable ASCII is not a media type")
    }

    @Test
    fun theSnapshotDescriptorCarriesOnlyMimesTheFoundationRuleAccepted() {
        val canonical = webDropItemDescriptor(
            isFile = true,
            mimeType = "image/png",
            displayName = "picture.png",
            sizeBytes = 12L,
        )
        assertEquals(DropItemKind.File, canonical.kind)
        assertEquals(listOf("image/png"), canonical.mimeTypes)
        assertEquals("picture.png", canonical.displayName)
        assertEquals(12L, canonical.sizeBytes)

        val unnameable = webDropItemDescriptor(
            isFile = false,
            mimeType = "IMAGE/PNG",
            displayName = null,
            sizeBytes = null,
        )
        assertEquals(DropItemKind.Binary, unnameable.kind)
        assertEquals(
            emptyList(),
            unnameable.mimeTypes,
            "a mime the foundation's own rule refuses is dropped, never replaced by a fabricated one",
        )
        assertNull(unnameable.sizeBytes)
    }

    @Test
    fun theDescriptorRuleStillRejectsWhatItAlwaysRejected() {
        // The probe the canonicality question asks is the foundation's own constructor requirement,
        // so a value the probe accepts must be one a descriptor genuinely carries.
        val mime = canonicalWebDropMimeTypeOrNull("text/plain")
        requireNotNull(mime)
        DropItemDescriptor(displayName = null, sizeBytes = null, mimeTypes = listOf(mime), kind = DropItemKind.Text)
    }
}
