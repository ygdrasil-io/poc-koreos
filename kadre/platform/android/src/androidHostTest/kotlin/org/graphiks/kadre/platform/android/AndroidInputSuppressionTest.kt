package org.graphiks.kadre.platform.android

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.graphiks.kadre.application.EventStamp
import org.graphiks.kadre.application.SessionInstant
import org.graphiks.kadre.application.SessionSequence
import org.graphiks.kadre.diagnostics.Capability
import org.graphiks.kadre.diagnostics.FeatureAvailability
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreOperation
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.input.InputEvent
import org.graphiks.kadre.input.KeyState
import org.graphiks.kadre.input.LogicalKey
import org.graphiks.kadre.input.NamedKey
import org.graphiks.kadre.input.PhysicalKey
import org.graphiks.kadre.internal.runtime.RuntimeEventCollectorAllocator
import org.graphiks.kadre.internal.runtime.RuntimeFailureReporter
import org.graphiks.kadre.internal.runtime.RuntimeProcessIds
import org.graphiks.kadre.policy.CollectorOverflowAction
import org.graphiks.kadre.policy.ContinuousDelivery
import org.graphiks.kadre.policy.EventDeliveryPolicy
import org.graphiks.kadre.policy.IngressOverflowAction
import org.graphiks.kadre.policy.InputDeliveryPolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.RawInputDeliveryPolicy
import org.graphiks.kadre.policy.RawInputOverflowAction
import org.graphiks.kadre.policy.WindowDeliveryPolicy
import org.graphiks.kadre.surface.CursorIcon
import org.graphiks.kadre.surface.CursorStyle
import org.graphiks.kadre.surface.HitTestingMode
import org.graphiks.kadre.surface.InputDefaultBehavior
import org.graphiks.kadre.surface.LogicalInsets
import org.graphiks.kadre.surface.LogicalSize
import org.graphiks.kadre.surface.PhysicalSize
import org.graphiks.kadre.surface.PointerCaptureMode
import org.graphiks.kadre.surface.PropertyChange
import org.graphiks.kadre.surface.RejectedSurfaceField
import org.graphiks.kadre.surface.SurfaceAppearance
import org.graphiks.kadre.surface.SurfaceAttachmentState
import org.graphiks.kadre.surface.SurfaceContrast
import org.graphiks.kadre.surface.SurfaceFocus
import org.graphiks.kadre.surface.SurfaceOcclusion
import org.graphiks.kadre.surface.SurfaceProperty
import org.graphiks.kadre.surface.SurfaceRevision
import org.graphiks.kadre.surface.SurfaceState
import org.graphiks.kadre.surface.SurfaceTheme
import org.graphiks.kadre.surface.SurfaceUpdate
import org.graphiks.kadre.surface.SurfaceUpdateOutcome
import org.graphiks.kadre.surface.SurfaceVisibility
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * La suppression du comportement par défaut Android (Task 4) — `inputDefaultBehavior` devient
 * le PREMIER champ committable de [AndroidHostSurface.apply] et la policy est consultée au
 * point de dispatch (le `shouldConsumeKey` du wrapper T1) :
 *
 * 1. `hostDefaultNeverConsumes` — sous `HostDefault`, tout est forwardé, rien n'est consommé ;
 * 2. `suppressWhenPossibleConsumesExactlyScrollKeys` — les NEUF scroll-keys documentaires
 *    consommées (retour `true`, forward JAMAIS appelé, observation QUAND MÊME livrée —
 *    l'observation n'est pas la consommation, le précédent web « delivered then suppressed ») ;
 *    toute autre touche forwardée ;
 * 3. `committedStateIsAuthoritative` — le commit fait autorité : `Applied` avec une révision
 *    nouvelle, l'état publié porte la valeur, la suppression la reflète immédiatement, et le
 *    retour à `HostDefault` rend toute la consommation ;
 * 4. `otherFieldsStayUnsupportedPerField` — cursor/pointerCapture/hitTesting posés dans le
 *    MÊME update sont rejetés `Unsupported(UpdateSurface)` champ par champ pendant que
 *    `inputDefaultBehavior` est admis et commis (admission par champ, le miroir web) ;
 * 5. `capabilityPublishedAtInstallRevokedAtClose` — la capability `inputDefaultBehavior`
 *    `Supported({HostDefault, SuppressWhenPossible}, Available)` est publiée au moment de
 *    l'installation (le miroir du snapshot attaché web) et retirée au terminal — jamais
 *    après le détached.
 *
 * Le chemin de dispatch est joué par la voie de PRODUCTION : le cœur
 * [AndroidInputWindowCallback] câblé exactement comme la couture l'installe
 * (`shouldConsumeKey = { surface.shouldConsumeKey(it) }`, le même lambda que
 * `KadreAndroidAttach` passe au wrapper). Aucun type SDK constructible n'est instancié.
 */
class AndroidInputSuppressionTest {

    private var sequence = 0L

    /** La source de stamps de session — séquentielle, la même instance pour toute une surface. */
    private fun stampSource(): () -> EventStamp {
        val started = sequence
        return {
            sequence += 1
            EventStamp(SessionSequence(sequence), SessionInstant((sequence - started).nanoseconds), null)
        }
    }

    private fun surface(): AndroidHostSurface = AndroidHostSurface(
        id = RuntimeProcessIds.nextSurfaceId(),
        initialState = testSurfaceState(),
        frameClock = { SuppressionInertFrameClock },
        failureReporter = RuntimeFailureReporter { },
    )

    /** L'installation de la configuration de session — les politiques par défaut du test. */
    private fun install(on: AndroidHostSurface) {
        val discrete = EventDeliveryPolicy(
            ingressCapacity = 64,
            collectorCapacity = 16,
            ingressOverflow = IngressOverflowAction.CloseSource,
            collectorOverflow = CollectorOverflowAction.CancelSlowCollector,
        )
        on.installSessionConfiguration(
            deliveryPolicy = WindowDeliveryPolicy(
                discreteEvents = discrete,
                geometryChanges = ContinuousDelivery.Coalesced,
                redrawRequests = ContinuousDelivery.Latest,
            ),
            inputDeliveryPolicy = InputDeliveryPolicy(
                discreteEvents = discrete,
                pointerMotion = ContinuousDelivery.Coalesced,
                touchMotion = ContinuousDelivery.Coalesced,
                scroll = ContinuousDelivery.Coalesced,
                gestureChanges = ContinuousDelivery.Coalesced,
                gamepadChanges = ContinuousDelivery.Latest,
                rawInput = RawInputDeliveryPolicy(16, RawInputOverflowAction.DropOldestAndReport),
            ),
            source = stampSource(),
            sessionFailureHandler = {},
            collectorAllocator = RuntimeEventCollectorAllocator(64),
            maxCollectorsPerFlow = 4,
            resources = KadrePolicies.Default.resources,
            dropTransferScope = null,
            diagnostics = {},
            rawInputPort = null,
        )
    }

    /**
     * Le dispatch de production : le cœur du wrapper T1 câblé à la surface — l'observation
     * vers le funnel de la surface (le câblage exact de `KadreAndroidAttach`), la
     * consommation depuis l'état COMMIS. Le journal local ne sert qu'aux comptes du test.
     */
    private class Dispatch(private val surface: AndroidHostSurface) {
        val observations = mutableListOf<AndroidInputObservation>()
        val forwards = mutableListOf<Int>()

        val core = AndroidInputWindowCallback(
            isTargetFocused = { true },
            onObservation = { observation ->
                observations += observation
                surface.onInputObservation(observation)
            },
            shouldConsumeKey = { nativeKeyCode -> surface.shouldConsumeKey(nativeKeyCode) },
            forwardKey = { code, _, _ ->
                forwards += code
                true
            },
            forwardFocusLost = {},
        )

        /** Un dispatch press/release complet, le chemin du wrapper réel. */
        fun key(nativeKeyCode: Int, keyState: KeyState = KeyState.Pressed): Boolean = core.dispatchKeyEvent(
            nativeKeyCode = nativeKeyCode,
            repeat = false,
            metaState = 0,
            unicodeChar = null,
            isSystemKey = false,
            keyState = keyState,
        )
    }

    // -----------------------------------------------------------------------
    // Cas 1 — HostDefault : tout est forwardé, rien n'est consommé
    // -----------------------------------------------------------------------

    @Test
    fun hostDefaultNeverConsumes() = runBlocking {
        val surface = surface()
        install(on = surface)
        surface.observationInstalled(inputWired = true)
        assertEquals(
            InputDefaultBehavior.HostDefault,
            surface.state.value.inputDefaultBehavior,
            "the committed state starts at the default",
        )
        val dispatch = Dispatch(surface)

        // LES NEUF scroll-keys comme toute autre touche : forwardées, jamais consommées —
        // la porte de sortie de la phase (« le host garde son comportement par défaut tant
        // que la policy ne demande pas son inhibition »).
        for (code in ANDROID_DOCUMENT_SCROLL_KEYS + KeyEvent.KEYCODE_A) {
            assertTrue(dispatch.key(code), "keycode $code is forwarded and accepted")
            assertFalse(surface.shouldConsumeKey(code), "keycode $code is never consumed")
        }
        assertEquals(
            (ANDROID_DOCUMENT_SCROLL_KEYS + KeyEvent.KEYCODE_A).size,
            dispatch.forwards.size,
            "every key reached the host exactly once",
        )
        assertEquals(dispatch.forwards.size, dispatch.observations.size, "every key was observed too")
    }

    // -----------------------------------------------------------------------
    // La table de suppression — le miroir Android des catégories web
    // -----------------------------------------------------------------------

    @Test
    fun translationTableMirrorsTheWebScrollKeys() {
        // Le wheel n'existe pas sur Android (le scroll roue entre par la motion, observation
        // passive jamais consommée) : la seule catégorie supprimable est la scroll-key.
        assertEquals(setOf(AndroidInputCategory.ScrollingKey), AndroidInputTranslation.suppressedDefaults)

        // La catégorie : exactement les 9 keycodes documentaires — le miroir des 9 touches
        // web (`WEB_DOCUMENT_SCROLL_KEYS`), traduites par la table de la delivery.
        assertEquals(
            ANDROID_DOCUMENT_SCROLL_KEYS,
            setOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_MOVE_HOME,
                KeyEvent.KEYCODE_MOVE_END,
                KeyEvent.KEYCODE_SPACE,
            ),
        )
        for (code in ANDROID_DOCUMENT_SCROLL_KEYS) {
            assertTrue(AndroidInputTranslation.isScrollingKey(code), "keycode $code is a scrolling key")
        }
        for (code in listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_BACK)) {
            assertFalse(AndroidInputTranslation.isScrollingKey(code), "keycode $code is an ordinary key")
        }

        // La décision totale : HostDefault ne supprime rien ; SuppressWhenPossible ne
        // supprime que la catégorie supprimable — ni plus, ni moins.
        for (code in ANDROID_DOCUMENT_SCROLL_KEYS + KeyEvent.KEYCODE_A) {
            assertFalse(
                AndroidInputTranslation.shouldSuppress(code, InputDefaultBehavior.HostDefault),
                "HostDefault never suppresses (keycode $code)",
            )
            assertTrue(
                AndroidInputTranslation.isScrollingKey(code) ==
                    AndroidInputTranslation.shouldSuppress(code, InputDefaultBehavior.SuppressWhenPossible),
                "SuppressWhenPossible suppresses exactly the scrolling keys (keycode $code)",
            )
        }
    }
    // -----------------------------------------------------------------------
    // Cas 2 — SuppressWhenPossible : EXACTEMENT les scroll-keys, observées quand même
    // -----------------------------------------------------------------------

    @Test
    fun suppressWhenPossibleConsumesExactlyScrollKeys() = runBlocking {
        val surface = surface()
        install(on = surface)
        surface.observationInstalled(inputWired = true)
        val applied = assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
            surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible))),
        )
        assertIs<SurfaceUpdateOutcome.Applied>(applied.value)

        val dispatch = Dispatch(surface)
        // Les NEUF : consommées — retour true au système, forward JAMAIS appelé, et
        // l'observation QUAND MÊME livrée (la delivery d'abord, la suppression ensuite —
        // l'observation n'est pas la consommation, le précédent web).
        for (code in ANDROID_DOCUMENT_SCROLL_KEYS) {
            assertTrue(dispatch.key(code), "scroll keycode $code reports consumed")
            assertTrue(surface.shouldConsumeKey(code), "keycode $code stays suppressed")
        }
        assertEquals(0, dispatch.forwards.size, "a consumed scroll key is NEVER forwarded")
        assertEquals(ANDROID_DOCUMENT_SCROLL_KEYS.size, dispatch.observations.size, "every consumed key was still observed")

        // Toute autre touche part au host, observée elle aussi.
        for (code in listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_ENTER)) {
            assertTrue(dispatch.key(code), "keycode $code is forwarded")
        }
        assertEquals(listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_ENTER), dispatch.forwards)

        // La preuve forte de l'indépendance observation/consommation : une touche MAPPÉE
        // (DPAD_DOWN → ArrowDown) consommée au dispatch livre QUAND MÊME son KeyChanged au
        // réducteur — press puis release, les deux consommés, les deux observés. Le
        // collecteur est créé APRÈS les boucles (les observations des touches ci-dessus ont
        // déjà nourri le réducteur) et enregistré avant le dispatch.
        val collector = EventCollector(this, surface.input)
        collector.settle()
        assertTrue(dispatch.key(KeyEvent.KEYCODE_DPAD_DOWN, KeyState.Pressed), "the press is consumed")
        assertTrue(dispatch.key(KeyEvent.KEYCODE_DPAD_DOWN, KeyState.Released), "the release is consumed")
        val pressed = assertIs<InputEvent.Key>(collector.next())
        assertEquals(PhysicalKey.Code(usagePage = 0x07, usageId = 0x51), pressed.physicalKey)
        assertEquals(LogicalKey.Named(NamedKey.ArrowDown), pressed.logicalKey)
        assertEquals(KeyState.Pressed, pressed.keyState)
        val released = assertIs<InputEvent.Key>(collector.next())
        assertEquals(PhysicalKey.Code(usagePage = 0x07, usageId = 0x51), released.physicalKey)
        assertEquals(KeyState.Released, released.keyState)
        collector.stop()
    }

    // -----------------------------------------------------------------------
    // Cas 3 — l'état COMMIS fait autorité
    // -----------------------------------------------------------------------

    @Test
    fun committedStateIsAuthoritative() = runBlocking {
        val surface = surface()
        install(on = surface)
        surface.observationInstalled(inputWired = true)
        val before = surface.state.value
        assertFalse(surface.shouldConsumeKey(KeyEvent.KEYCODE_PAGE_UP), "HostDefault consumes nothing")

        // Le commit : Applied, UNE révision nouvelle, l'outcome portant l'état publié —
        // le miroir exact du commit own-backend web.
        val applied = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible)),
                ),
            ).value,
        )
        assertEquals(InputDefaultBehavior.SuppressWhenPossible, applied.state.inputDefaultBehavior)
        assertEquals(before.revision.value + 1L, applied.state.revision.value, "one commit, one new revision")
        assertEquals(applied.state, surface.state.value, "the outcome carries the state the surface publishes")
        assertEquals(InputDefaultBehavior.SuppressWhenPossible, surface.state.value.inputDefaultBehavior)

        // La suppression reflète l'état COMMIS, immédiatement.
        assertTrue(surface.shouldConsumeKey(KeyEvent.KEYCODE_PAGE_UP), "the committed behaviour is authoritative")
        assertTrue(surface.shouldConsumeKey(KeyEvent.KEYCODE_SPACE))

        // Le chemin du retour : HostDefault est une valeur comme les autres, commise sans
        // erreur — et plus AUCUNE consommation.
        val restored = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault))),
            ).value,
        )
        assertEquals(InputDefaultBehavior.HostDefault, restored.state.inputDefaultBehavior)
        assertEquals(suppressedRevision(surface).value, restored.state.revision.value, "the way back bumps once too")
        assertFalse(surface.shouldConsumeKey(KeyEvent.KEYCODE_PAGE_UP), "nothing is consumed any more")

        // Demander la valeur déjà en effet est admis et répondu Applied — la révision ne
        // bouge pas (la révision est la marque d'un changement d'état, pas d'un appel).
        val again = assertIs<SurfaceUpdateOutcome.Applied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(SurfaceUpdate(inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.HostDefault))),
            ).value,
        )
        assertEquals(restored.state.revision.value, again.state.revision.value, "a no-op commit does not move the revision")
    }

    private fun suppressedRevision(surface: AndroidHostSurface): SurfaceRevision = surface.state.value.revision

    // -----------------------------------------------------------------------
    // Cas 4 — l'admission reste CHAMP PAR CHAMP : les trois refus tiennent
    // -----------------------------------------------------------------------

    @Test
    fun otherFieldsStayUnsupportedPerField() = runBlocking {
        val surface = surface()
        install(on = surface)
        surface.observationInstalled(inputWired = false)
        val before = surface.state.value

        // Un update portant les QUATRE champs : les trois refus d'update le sont champ par
        // champ, le quatrième est admis ET commis — un rejet ne bloque jamais les champs du
        // même update qui peuvent être honorés (le miroir web).
        val partial = assertIs<SurfaceUpdateOutcome.PartiallyApplied>(
            assertIs<KadreResult.Success<SurfaceUpdateOutcome>>(
                surface.apply(
                    SurfaceUpdate(
                        cursor = PropertyChange.Set(CursorStyle.System(CursorIcon.Default)),
                        pointerCapture = PropertyChange.Set(PointerCaptureMode.Confined),
                        hitTesting = PropertyChange.Set(HitTestingMode.Disabled),
                        inputDefaultBehavior = PropertyChange.Set(InputDefaultBehavior.SuppressWhenPossible),
                    ),
                ),
            ).value,
        )
        assertEquals(
            listOf(
                RejectedSurfaceField(SurfaceProperty.Cursor, KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
                RejectedSurfaceField(SurfaceProperty.PointerCapture, KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
                RejectedSurfaceField(SurfaceProperty.HitTesting, KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            ),
            partial.rejected,
            "exactly the three unsupported fields, in the update's own field order",
        )
        // Le champ admis du même update a pris : commis avec la révision d'un état qui a bougé.
        assertEquals(InputDefaultBehavior.SuppressWhenPossible, partial.state.inputDefaultBehavior)
        assertEquals(before.revision.value + 1L, partial.state.revision.value, "the admitted field moved the state")
        assertTrue(surface.shouldConsumeKey(KeyEvent.KEYCODE_PAGE_UP), "the same update's commit is effective")
    }

    // -----------------------------------------------------------------------
    // Cas 5 — la capability : publiée à l'install, retirée au terminal
    // -----------------------------------------------------------------------

    @Test
    fun capabilityPublishedAtInstallRevokedAtClose() = runBlocking {
        val surface = surface()
        // À la construction : le snapshot par défaut — aucune promesse, le champ refusé.
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            surface.capabilities.value.inputDefaultBehavior,
            "nothing is claimed before the installation",
        )

        // L'installation publie la capability Supported — les DEUX valeurs, Available —
        // SANS dépendre du câblage de l'observateur (le commit et la lecture de l'état
        // commis n'ont pas besoin du wrapper ; seules les capabilities d'entrée du
        // réducteur suivent cette présence). Le reste des refus tient.
        surface.observationInstalled(inputWired = false)
        val behaviour = assertIs<Capability.Supported<Set<InputDefaultBehavior>>>(
            surface.capabilities.value.inputDefaultBehavior,
            "an installed surface publishes the inputDefaultBehaviour capability",
        )
        assertEquals(
            setOf(InputDefaultBehavior.HostDefault, InputDefaultBehavior.SuppressWhenPossible),
            behaviour.constraints,
        )
        assertEquals(FeatureAvailability.Available, behaviour.availability)

        // Le terminal retire la capability — au plus tard au détached, ici avant — et rien
        // ne la ressuscite.
        surface.close()
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            surface.capabilities.value.inputDefaultBehavior,
            "the terminal revokes the capability no later than the detach",
        )
        surface.observationInstalled(inputWired = false)
        assertEquals(
            Capability.Unsupported(KadreFailure.Unsupported(KadreOperation.UpdateSurface)),
            surface.capabilities.value.inputDefaultBehavior,
            "nothing resurrects the capability after the terminal",
        )
        assertEquals(SurfaceAttachmentState.Detached, surface.state.value.attachment)
    }

    // -----------------------------------------------------------------------
    // Outils de test
    // -----------------------------------------------------------------------

    /** La collecte des événements d'entrée — le même pattern que la delivery (stop obligatoire). */
    private class EventCollector(scope: CoroutineScope, input: org.graphiks.kadre.input.SurfaceInput) {
        val channel = Channel<InputEvent>(Channel.UNLIMITED)

        private val job = scope.launch {
            input.events.collect { channel.send(it) }
        }

        /** Laisse le collecteur s'enregistrer (l'enregistrement est synchronisé sur ce thread). */
        suspend fun settle() {
            repeat(8) { yield() }
        }

        suspend fun next(): InputEvent = kotlinx.coroutines.withTimeout(5_000) { channel.receive() }

        fun stop() {
            job.cancel()
        }
    }
}

/** La clock inerte du test : les frames y sont posées et n'y courent jamais (aucun SDK). */
private object SuppressionInertFrameClock : AndroidRedrawScheduler.FrameClock {
    override fun schedule(frame: () -> Unit) = Unit
    override fun cancel() = Unit
}

/** L'instantané initial de surface du test — les mêmes valeurs que la couture, base 1×1. */
private fun testSurfaceState(): SurfaceState = SurfaceState(
    attachment = SurfaceAttachmentState.Attached,
    logicalSize = LogicalSize(1.0, 1.0),
    physicalSize = PhysicalSize(1, 1),
    scaleFactor = 1.0,
    safeAreaInsets = LogicalInsets(0.0, 0.0, 0.0, 0.0),
    visibility = SurfaceVisibility.Visible,
    occlusion = SurfaceOcclusion.Visible,
    focus = SurfaceFocus.Focused,
    appearance = SurfaceAppearance(SurfaceTheme.Unknown, SurfaceContrast.Unknown),
    cursor = CursorStyle.System(CursorIcon.Default),
    pointerCapture = PointerCaptureMode.None,
    hitTesting = HitTestingMode.Enabled,
    inputDefaultBehavior = InputDefaultBehavior.HostDefault,
    revision = SurfaceRevision(0L),
)
