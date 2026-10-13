package org.graphiks.kadre.platform.android

import android.os.Build
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.graphiks.kadre.capture.CapturePermissionScope
import org.graphiks.kadre.capture.CaptureRequest
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.internal.runtime.CapturePortSourceKey
import org.graphiks.kadre.internal.runtime.CapturePortSources
import org.graphiks.kadre.internal.runtime.CapturePortTarget
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds

/**
 * Le scénario capture ON-DEVICE de l'AND-009 (Task 4, phase 8) qui nomme un INTERNAL — le
 * miroir exact du câblage drop AND-007 : il tourne dans l'APK androidTest du CONSUMER (ce
 * module est consommé `androidTestImplementation` — voir le build.gradle.kts), donc sur
 * l'émulateur réel, mais la construction runtime-friend reste possible car ce module est
 * l'AMI des internals.
 *
 * | scenario id (registre)      | méthode                     | contenu |
 * |-----------------------------|-----------------------------|---------|
 * | android-capture-source-refused | androidCaptureSourceRefused | `reserve(Source(...))` → `Unsupported(CaptureOpen)` AVANT tout seam — le port réel de production (l'API level du device), toutes coutures à `null`, répond le refus structurel SANS toucher le moindre registre (l'inventaire Android n'existe pas avant le consentement, le precedent web :495-530) |
 *
 * Les coutures sont `null` VOLONTAIREMENT : le refus de la cible Source est STRUCTUREL —
 * s'il atteignait un seam, le port avec zéro seam répondrait autre chose que le refus
 * nominal (et l'émulateur réel le prouverait à chaque run). Le même port affiche ses
 * sources `HostPickerOnly` (aucun inventaire n'existe pré-consentement) et son
 * `refreshSources` est le no-op honnête (le MÊME objet snapshot) : aucun canal
 * d'énumération n'est jamais inventé.
 */
public class AndroidCaptureDeviceContractTest {

    @Test
    public fun androidCaptureSourceRefused(): Unit = runBlocking {
        // Le port RÉEL de production, à l'API level RÉEL du device — aucune couture : le
        // refus structurel ne doit en atteindre AUCUNE.
        val port = AndroidCapturePort(
            apiLevel = Build.VERSION.SDK_INT,
            surfaces = AndroidHostCaptureSurfaceRegistry(),
        )
        assertTrue(
            "l'inventaire Android n'existe pas avant le consentement : HostPickerOnly",
            port.initialSnapshot.sources is CapturePortSources.HostPickerOnly,
        )

        // LA cible refusée avant tout fait — `Unsupported(CaptureOpen)`, le precedent web.
        val refused = port.reserve(
            CapturePortTarget.Source(CapturePortSourceKey("android-capture-probe", 0L)),
            CaptureRequest(),
        )
        assertEquals(
            "une cible source est refusée structurellement, ICI, avant tout seam",
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            refused,
        )

        // Même avec une surface hôte ENREGISTRÉE : la cible Source reste le refus structurel
        // (l'enregistrement de surface ne crée jamais un inventaire).
        port.registerHostCaptureSurface(RuntimeProcessIds.nextSurfaceId(), Any())
        assertEquals(
            "une surface enregistrée n'invente pas un inventaire : la cible Source reste refusée",
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CaptureOpen)),
            port.reserve(
                CapturePortTarget.Source(CapturePortSourceKey("android-capture-probe", 1L)),
                CaptureRequest(),
            ),
        )

        // Le refresh est le no-op honnête : le MÊME objet snapshot, jamais un canal de plus.
        val refreshed = port.refreshSources()
        assertTrue("le refresh réussit", refreshed is KadreResult.Success<*>)
        assertSame(
            "refreshSources rend le MÊME objet snapshot — le no-op honnête du web",
            port.initialSnapshot,
            (refreshed as KadreResult.Success<*>).value,
        )

        // Une demande de consentement sans couture répond l'Unsupported nominal — jamais une
        // devinette (la forme View sans activité hébergeante du câblage attach, éprouvée au
        // niveau du port sur le device réel).
        assertEquals(
            "sans couture de consentement, la demande répond l'Unsupported nominal du port",
            KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.CapturePermission)),
            port.requestPermission(CapturePermissionScope.Screen),
        )
    }
}
