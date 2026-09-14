import AppKit
import Darwin
import Foundation

private struct FullscreenWitnessReport: Codable {
    let status: String
    let callbacks: [String]
    let failure: String?
    let elapsedMilliseconds: Int
    let osVersion: String
    let architecture: String
}

private struct Arguments {
    let output: URL
    let timeoutSeconds: Double

    init() throws {
        var output: URL?
        var timeoutSeconds = 15.0
        var index = 1
        let arguments = CommandLine.arguments
        while index < arguments.count {
            switch arguments[index] {
            case "--output":
                index += 1
                guard index < arguments.count else { throw WitnessError.missingOutputPath }
                output = URL(fileURLWithPath: arguments[index])
            case "--timeout-seconds":
                index += 1
                guard index < arguments.count,
                      let value = Double(arguments[index]),
                      value > 0 else {
                    throw WitnessError.invalidTimeout
                }
                timeoutSeconds = value
            default:
                throw WitnessError.invalidArgument(arguments[index])
            }
            index += 1
        }
        guard let output else { throw WitnessError.missingOutputPath }
        self.output = output
        self.timeoutSeconds = timeoutSeconds
    }
}

private enum WitnessError: Error, LocalizedError {
    case invalidArgument(String)
    case invalidTimeout
    case missingOutputPath

    var errorDescription: String? {
        switch self {
        case .invalidArgument(let argument): "unsupported argument: \(argument)"
        case .invalidTimeout: "--timeout-seconds must be greater than zero"
        case .missingOutputPath: "--output <path> is required"
        }
    }
}

private func machineArchitecture() -> String {
    var system = utsname()
    uname(&system)
    return withUnsafePointer(to: &system.machine) {
        $0.withMemoryRebound(to: CChar.self, capacity: 1) {
            String(cString: $0)
        }
    }
}

private final class FullscreenWitness: NSObject, NSApplicationDelegate, NSWindowDelegate {
    private let application: NSApplication
    private let arguments: Arguments
    private let startedAt = DispatchTime.now().uptimeNanoseconds
    private var callbacks: [String] = []
    private var completed = false
    private var failure: String?
    private var status = "failed"
    private var watchdog: DispatchWorkItem?
    private var window: NSWindow?

    init(application: NSApplication, arguments: Arguments) {
        self.application = application
        self.arguments = arguments
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        guard application.setActivationPolicy(.regular) else {
            complete(status: "failed", failure: "NSApplication refused the regular activation policy")
            return
        }

        let window = NSWindow(
            contentRect: NSRect(x: 100, y: 100, width: 480, height: 320),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false,
        )
        window.title = "Kadre AppKit fullscreen witness"
        window.collectionBehavior.insert(.fullScreenPrimary)
        window.delegate = self
        self.window = window
        window.makeKeyAndOrderFront(nil)
        application.activate(ignoringOtherApps: true)

        let watchdog = DispatchWorkItem { [weak self] in
            self?.complete(
                status: "timed-out",
                failure: "no terminal fullscreen callback after \(self?.arguments.timeoutSeconds ?? 0) seconds",
            )
        }
        self.watchdog = watchdog
        DispatchQueue.main.asyncAfter(deadline: .now() + arguments.timeoutSeconds, execute: watchdog)
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.completed else { return }
            self.window?.toggleFullScreen(nil)
        }
    }

    func windowWillEnterFullScreen(_ notification: Notification) {
        callbacks.append("WillEnter")
    }

    func windowDidEnterFullScreen(_ notification: Notification) {
        callbacks.append("DidEnter")
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.completed else { return }
            self.window?.toggleFullScreen(nil)
        }
    }

    func windowDidFailToEnterFullScreen(_ window: NSWindow) {
        callbacks.append("DidFailEnter")
        complete(status: "failed", failure: "AppKit rejected fullscreen entry")
    }

    func windowWillExitFullScreen(_ notification: Notification) {
        callbacks.append("WillExit")
    }

    func windowDidExitFullScreen(_ notification: Notification) {
        callbacks.append("DidExit")
        let expected = ["WillEnter", "DidEnter", "WillExit", "DidExit"]
        complete(
            status: callbacks == expected ? "passed" : "failed",
            failure: callbacks == expected ? nil : "unexpected fullscreen callback sequence",
        )
    }

    func windowDidFailToExitFullScreen(_ window: NSWindow) {
        callbacks.append("DidFailExit")
        complete(status: "failed", failure: "AppKit rejected fullscreen exit")
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }

    func report() -> FullscreenWitnessReport {
        let elapsedMilliseconds = Int((DispatchTime.now().uptimeNanoseconds - startedAt) / 1_000_000)
        return FullscreenWitnessReport(
            status: status,
            callbacks: callbacks,
            failure: failure,
            elapsedMilliseconds: elapsedMilliseconds,
            osVersion: ProcessInfo.processInfo.operatingSystemVersionString,
            architecture: machineArchitecture(),
        )
    }

    private func complete(status: String, failure: String?) {
        guard !completed else { return }
        completed = true
        self.status = status
        self.failure = failure
        watchdog?.cancel()
        watchdog = nil
        window?.orderOut(nil)
        application.stop(nil)
    }
}

do {
    let arguments = try Arguments()
    let application = NSApplication.shared
    let witness = FullscreenWitness(application: application, arguments: arguments)
    application.delegate = witness
    application.run()

    let report = witness.report()
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    try encoder.encode(report).write(to: arguments.output, options: .atomic)
    exit(report.status == "passed" ? 0 : 1)
} catch {
    fputs("Kadre AppKit fullscreen witness failed: \(error.localizedDescription)\n", stderr)
    exit(64)
}
