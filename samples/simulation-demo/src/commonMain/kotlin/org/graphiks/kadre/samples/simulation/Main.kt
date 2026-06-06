package org.graphiks.kadre.samples.simulation

import org.graphiks.kadre.*

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        val cliArgs = Cli.parse(args.toList())
        val exitCode = Cli.execute(cliArgs)
        System.exit(exitCode)
    }

    EventLoop().runApp(SimulationAppHandler())
}
