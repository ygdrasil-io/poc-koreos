package org.graphiks.kadre.samples.desktour

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.graphiks.kadre.samples.desktour.core.DeskTourNote
import org.graphiks.kadre.samples.desktour.core.NoteControls
import org.graphiks.kadre.samples.desktour.core.NoteKey
import org.graphiks.kadre.samples.desktour.core.NoteOpenOutcome
import org.graphiks.kadre.samples.desktour.core.NoteUpdateOutcome
import org.graphiks.kadre.samples.desktour.core.NoteWindowHost
import org.graphiks.kadre.window.Window

/**
 * Le support de notes du host Android : la démonstration du chemin NON pris en charge.
 * Android ne crée pas de fenêtre (BACKEND-CAPABILITIES §3 : `requestWindow` y vaut
 * `Unsupported(RequestWindow)`, requête immédiatement `Rejected`), donc aucune note
 * n'existe sur cette cible — et le host le dit, avec ses motifs, sans rien inventer :
 * la création est refusée, les contrôles du route Desk restent AFFICHÉS mais désactivés
 * (spec §5 ligne 115 : une capacité absente porte son motif), et le flux de fermetures
 * n'émet jamais. Miroir du support web ([DemoNoteWindows]) : mêmes clés, mêmes outcomes,
 * même contrat [NoteWindowHost] — la matière, elle, n'existe pas sur cette cible.
 */
internal class AndroidTourHost : NoteWindowHost {
    override suspend fun openNote(): NoteOpenOutcome =
        NoteOpenOutcome.Refused(
            "Le host Android ne crée pas de fenêtre flottante (requestWindow non pris en charge).",
        )

    /** Décision du host : aucune fenêtre Kadre n'existe sur Android, aucune note non plus. */
    override fun noteWindow(key: NoteKey): Window? = null

    override fun noteControls(key: NoteKey): NoteControls = NoteControls(
        // Aucun contrôle n'est offert : il n'y a pas de fenêtre — et le motif de l'hôte
        // porte la raison de chacune, les contrôles restant affichés, désactivés.
        canRename = false,
        canRequestAttention = false,
        canChangeDecorations = false,
        canClose = false,
        motifs = listOf(
            "Renommage — aucune note ne peut exister sur ce host, il n'y a rien à renommer.",
            "Attention — pas de fenêtre à signaler sur ce host.",
            "Décoration — pas de fenêtre à décorer sur ce host.",
            "Fermeture — pas de fenêtre à fermer sur ce host.",
        ),
    )

    /** Aucune note n'est jamais publiée : le flux est vide, comme pour une clé inconnue. */
    override fun observeNote(key: NoteKey): Flow<DeskTourNote> = emptyFlow()

    override suspend fun renameNote(key: NoteKey, title: String): NoteUpdateOutcome =
        NoteUpdateOutcome.Refused("Aucune note n'existe sur le host Android.")

    override suspend fun requestNoteAttention(key: NoteKey): NoteUpdateOutcome =
        NoteUpdateOutcome.Refused("Le host Android n'a pas de demande d'attention à transmettre.")

    override suspend fun toggleNoteDecorations(key: NoteKey): NoteUpdateOutcome =
        NoteUpdateOutcome.Refused("Le host Android n'a pas de décoration à changer.")

    override suspend fun closeNote(key: NoteKey): NoteUpdateOutcome =
        NoteUpdateOutcome.Refused("Aucune note n'existe sur le host Android.")

    /** Aucune fenêtre ne peut demander une fermeture : le flux n'émet jamais sur cette cible. */
    override fun observeNoteCloseRequests(): Flow<NoteKey> = emptyFlow()
}
