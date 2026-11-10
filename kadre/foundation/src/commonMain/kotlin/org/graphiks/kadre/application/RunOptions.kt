package org.graphiks.kadre.application

import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.window.WindowSpec

public data class KadreRunOptions(
    public val policy: KadrePolicy = KadrePolicies.Default,
    public val firstWindow: WindowSpec? = null,
)
