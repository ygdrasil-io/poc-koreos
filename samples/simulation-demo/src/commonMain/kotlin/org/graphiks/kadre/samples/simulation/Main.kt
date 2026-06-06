@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.graphiks.kadre.samples.simulation

import org.graphiks.kadre.core.*

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        val cliArgs = Cli.parse(args.toList())
        val exitCode = Cli.execute(cliArgs)
        System.exit(exitCode)
    }

    registerScenarios()
    ActiveEventLoop.run(SimulationApp())
}

class SimulationApp : ApplicationHandler by SimulationAppHandler()
