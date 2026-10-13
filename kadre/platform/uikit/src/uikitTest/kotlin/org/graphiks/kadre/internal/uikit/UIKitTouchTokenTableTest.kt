package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La vie d'un contact dans la table de jetons (revue I1) : allocation unique au `Began`,
 * identité stable pendant la vie du contact, RÉVOCATION à l'event terminal — l'`Ended`/
 * `Cancelled` porte encore le jeton du contact, puis l'entrée disparaît (la clé native retenue
 * est relâchée : la table ne grossit pas avec les contacts de la session) et un nouveau
 * `Began` reçoit un jeton neuf, jamais la valeur d'un contact terminé.
 */
class UIKitTouchTokenTableTest {
    private val table = UIKitTouchTokenTable<String>()

    // MARK: - Allocation / stabilité

    @Test
    fun beganAllocatesOnceAndReusesForTheSameContact() {
        val first = table.tokenFor("finger", UIKitTouchPhase.Began)
        assertTrue(first != null)
        assertEquals(first, table.tokenFor("finger", UIKitTouchPhase.Began))
        assertEquals(first, table.tokenFor("finger", UIKitTouchPhase.Moved))
    }

    @Test
    fun distinctKeysGetDistinctTokens() {
        val a = table.tokenFor("a", UIKitTouchPhase.Began)
        val b = table.tokenFor("b", UIKitTouchPhase.Began)
        assertTrue(a != null)
        assertTrue(b != null)
        assertNotEquals(a, b)
    }

    // MARK: - Clés inconnues

    @Test
    fun unknownKeyIsLostOutsideBegan() {
        assertNull(table.tokenFor("ghost", UIKitTouchPhase.Moved))
        assertNull(table.tokenFor("ghost", UIKitTouchPhase.Ended))
        assertNull(table.tokenFor("ghost", UIKitTouchPhase.Cancelled))
    }

    // MARK: - Révocation terminale (le coeur de I1)

    @Test
    fun endedDeliversSameTokenThenRevokesIt() {
        val token = table.tokenFor("finger", UIKitTouchPhase.Began)
        assertEquals(token, table.tokenFor("finger", UIKitTouchPhase.Ended))
        // Révoqué : plus aucun handler ne résout l'identité du contact terminé.
        assertNull(table.tokenFor("finger", UIKitTouchPhase.Moved))
        assertNull(table.tokenFor("finger", UIKitTouchPhase.Ended))
        assertNull(table.tokenFor("finger", UIKitTouchPhase.Cancelled))
    }

    @Test
    fun cancelledDeliversSameTokenThenRevokesIt() {
        val token = table.tokenFor("finger", UIKitTouchPhase.Began)
        assertEquals(token, table.tokenFor("finger", UIKitTouchPhase.Cancelled))
        assertNull(table.tokenFor("finger", UIKitTouchPhase.Moved))
        assertNull(table.tokenFor("finger", UIKitTouchPhase.Ended))
    }

    @Test
    fun aNewContactAfterRevocationGetsAFreshTokenNeverTheRevokedValue() {
        val revoked = table.tokenFor("finger", UIKitTouchPhase.Began)
        assertEquals(revoked, table.tokenFor("finger", UIKitTouchPhase.Ended))
        val fresh = table.tokenFor("finger", UIKitTouchPhase.Began)
        assertTrue(fresh != null)
        assertNotEquals(revoked, fresh)
        // Le nouveau contact vit sa vie propre.
        assertEquals(fresh, table.tokenFor("finger", UIKitTouchPhase.Moved))
    }

    @Test
    fun duplicateBeganForALivingContactNeverReallocates() {
        val token = table.tokenFor("finger", UIKitTouchPhase.Began)
        assertEquals(token, table.tokenFor("finger", UIKitTouchPhase.Began))
        // Le contact reste unique : la révocation du handler terminal le retire une fois.
        assertEquals(token, table.tokenFor("finger", UIKitTouchPhase.Ended))
        assertNull(table.tokenFor("finger", UIKitTouchPhase.Ended))
    }

    // MARK: - Teardown

    @Test
    fun clearEmptiesEverything() {
        assertTrue(table.tokenFor("a", UIKitTouchPhase.Began) != null)
        assertTrue(table.tokenFor("b", UIKitTouchPhase.Began) != null)
        table.clear()
        assertNull(table.tokenFor("a", UIKitTouchPhase.Moved))
        assertNull(table.tokenFor("b", UIKitTouchPhase.Ended))
    }
}
