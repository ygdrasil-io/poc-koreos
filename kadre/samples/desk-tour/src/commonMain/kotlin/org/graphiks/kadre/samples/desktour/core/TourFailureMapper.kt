package org.graphiks.kadre.samples.desktour.core

import org.graphiks.kadre.diagnostics.KadreFailure

internal fun noteOpenOutcomeFor(failure: KadreFailure): NoteOpenOutcome =
    NoteOpenOutcome.Refused(failure.userMotif())
