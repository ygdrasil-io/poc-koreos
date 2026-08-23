import kotlinx.coroutines.CoroutineScope
import org.graphiks.kadre.application.KadreApplication
import org.graphiks.kadre.application.KadreApplicationFactory
import org.graphiks.kadre.application.SessionOutcome
import org.graphiks.kadre.diagnostics.KadreFailure
import org.graphiks.kadre.diagnostics.KadreResourceKind
import org.graphiks.kadre.diagnostics.KadreResult
import org.graphiks.kadre.diagnostics.flatMap
import org.graphiks.kadre.diagnostics.map
import org.graphiks.kadre.platform.desktop.DesktopHostOptions
import org.graphiks.kadre.platform.desktop.attachKadreDesktop
import org.graphiks.kadre.platform.desktop.runKadreApplication
import org.graphiks.kadre.policy.KadrePolicies
import org.graphiks.kadre.policy.KadrePolicy

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

    public fun attachBothForms(scope: CoroutineScope): List<KadreResult<*>> {
        val options = DesktopHostOptions.Embedded(
            integration = org.graphiks.kadre.platform.desktop.DesktopIntegration.AppKitMainLoop,
        )
        val factory = KadreApplicationFactory { KadreApplication { requestStop() } }
        return listOf(
            scope.attachKadreDesktop(factory, options),
            scope.attachKadreDesktop(options) { requestStop() },
        )
    }

    public fun runnersCompile(): List<Any> {
        val factoryRunner: (KadreApplicationFactory, DesktopHostOptions.Standalone, KadrePolicy) -> SessionOutcome =
            ::runKadreApplication
        val directRunner: (DesktopHostOptions.Standalone, KadrePolicy, KadreApplication) -> SessionOutcome =
            ::runKadreApplication
        return listOf(factoryRunner, directRunner)
    }

    public fun describe(outcome: SessionOutcome): String = when (outcome) {
        SessionOutcome.Completed -> "completed"
        is SessionOutcome.Stopped -> "stopped:${outcome.reason}"
        is SessionOutcome.Failed -> "failed:${outcome.failure}"
    }
}
