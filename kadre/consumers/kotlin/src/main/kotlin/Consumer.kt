import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreRunOptions
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.flatMap
import org.graphiks.kadre.diagnostics.map
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy
import org.graphiks.kadre.runKadreApplication

public object Consumer {
    public fun policyCopies(): List<KadrePolicy> = listOf(
        KadrePolicies.Default.copy(),
        KadrePolicies.Realtime.copy(),
        KadrePolicies.Recording.copy(),
    )

    public fun resultCombinators(value: Int): KadreResult<String> =
        KadreResult.Success(value)
            .map(Int::inc)
            .flatMap { incremented ->
                if (incremented > 0) {
                    KadreResult.Success(incremented.toString())
                } else {
                    KadreResult.Failure(KadreFailure.Closed(KadreResourceKind.Host))
                }
            }

    public fun rawInputResource(): KadreResourceKind = KadreResourceKind.RawInputAccess

    public fun runnersCompile(): List<Any> {
        val runner: suspend (KadreRunOptions, KadreApplication) -> KadreResult<SessionOutcome> =
            ::runKadreApplication
        return listOf(runner)
    }

    public fun describe(outcome: SessionOutcome): String = when (outcome) {
        SessionOutcome.Completed -> "completed"
        is SessionOutcome.Stopped -> "stopped:${outcome.reason}"
        is SessionOutcome.Failed -> "failed:${outcome.failure}"
    }
}
