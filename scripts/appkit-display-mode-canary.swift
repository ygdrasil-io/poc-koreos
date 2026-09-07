import CoreGraphics
import Foundation

private struct ModeRecord: Codable, Equatable {
    let width: Int
    let height: Int
    let pixelWidth: Int
    let pixelHeight: Int
    let refreshRateHz: Double
    let ioDisplayModeId: Int32
    let ioFlags: UInt32
}

private struct DisplayRecord: Codable {
    let id: UInt32
    let isMain: Bool
    let boundsX: Double
    let boundsY: Double
    let boundsWidth: Double
    let boundsHeight: Double
    let currentMode: ModeRecord?
    let availableModes: [ModeRecord]
    let alternateMode: ModeRecord?
}

private struct ModeChangeRecord: Codable {
    let status: String
    let captureResult: Int32?
    let setResult: Int32?
    let restoreResult: Int32?
    let releaseResult: Int32?
    let readbackMatchesCandidate: Bool?
    let restoredOriginalMode: Bool?
}

private struct ProbeReport: Codable {
    let status: String
    let requestedModeChange: Bool
    let displays: [DisplayRecord]
    let modeChange: ModeChangeRecord?
}

private final class MutableModeChange {
    var status = "failed"
    var captureResult: Int32?
    var setResult: Int32?
    var restoreResult: Int32?
    var releaseResult: Int32?
    var readbackMatchesCandidate: Bool?
    var restoredOriginalMode: Bool?

    func snapshot() -> ModeChangeRecord {
        ModeChangeRecord(
            status: status,
            captureResult: captureResult,
            setResult: setResult,
            restoreResult: restoreResult,
            releaseResult: releaseResult,
            readbackMatchesCandidate: readbackMatchesCandidate,
            restoredOriginalMode: restoredOriginalMode,
        )
    }
}

private struct Arguments {
    let applyModeCanary: Bool
    let output: URL

    init() throws {
        var applyModeCanary = false
        var output: URL?
        var index = 1
        let arguments = CommandLine.arguments
        while index < arguments.count {
            switch arguments[index] {
            case "--inspect-only":
                applyModeCanary = false
            case "--apply-mode-canary":
                applyModeCanary = true
            case "--output":
                index += 1
                guard index < arguments.count else { throw CanaryError.missingOutputPath }
                output = URL(fileURLWithPath: arguments[index])
            default:
                throw CanaryError.invalidArgument(arguments[index])
            }
            index += 1
        }
        guard let output else { throw CanaryError.missingOutputPath }
        self.applyModeCanary = applyModeCanary
        self.output = output
    }
}

private enum CanaryError: Error, LocalizedError {
    case invalidArgument(String)
    case missingOutputPath
    case activeDisplayList(CGError)

    var errorDescription: String? {
        switch self {
        case .invalidArgument(let argument): "unsupported argument: \(argument)"
        case .missingOutputPath: "--output <path> is required"
        case .activeDisplayList(let error): "CGGetActiveDisplayList failed: \(error.rawValue)"
        }
    }
}

private func modeRecord(_ mode: CGDisplayMode) -> ModeRecord {
    ModeRecord(
        width: mode.width,
        height: mode.height,
        pixelWidth: mode.pixelWidth,
        pixelHeight: mode.pixelHeight,
        refreshRateHz: mode.refreshRate,
        ioDisplayModeId: mode.ioDisplayModeID,
        ioFlags: mode.ioFlags,
    )
}

private func activeDisplays() throws -> [CGDirectDisplayID] {
    var count: UInt32 = 0
    let countResult = CGGetActiveDisplayList(0, nil, &count)
    guard countResult == .success else { throw CanaryError.activeDisplayList(countResult) }
    var displays = Array(repeating: CGDirectDisplayID(), count: Int(count))
    let listResult = CGGetActiveDisplayList(count, &displays, &count)
    guard listResult == .success else { throw CanaryError.activeDisplayList(listResult) }
    return Array(displays.prefix(Int(count)))
}

private func displayRecord(_ display: CGDirectDisplayID) -> DisplayRecord {
    let current = CGDisplayCopyDisplayMode(display)
    let currentRecord = current.map(modeRecord)
    let modes = CGDisplayCopyAllDisplayModes(display, nil) as? [CGDisplayMode] ?? []
    let alternate = modes.map(modeRecord).first { $0 != currentRecord }
    let bounds = CGDisplayBounds(display)
    return DisplayRecord(
        id: display,
        isMain: display == CGMainDisplayID(),
        boundsX: bounds.origin.x,
        boundsY: bounds.origin.y,
        boundsWidth: bounds.size.width,
        boundsHeight: bounds.size.height,
        currentMode: currentRecord,
        availableModes: modes.map(modeRecord),
        alternateMode: alternate,
    )
}

private func runModeCanary(display: CGDirectDisplayID, original: CGDisplayMode, candidate: CGDisplayMode) -> ModeChangeRecord {
    let result = MutableModeChange()
    let capture = CGDisplayCapture(display)
    result.captureResult = capture.rawValue
    guard capture == .success else {
        result.status = "unavailable"
        return result.snapshot()
    }

    do {
        defer {
            let restored = CGDisplaySetDisplayMode(display, original, nil)
            result.restoreResult = restored.rawValue
            result.restoredOriginalMode = CGDisplayCopyDisplayMode(display).map(modeRecord) == modeRecord(original)
            let released = CGDisplayRelease(display)
            result.releaseResult = released.rawValue
        }

        let set = CGDisplaySetDisplayMode(display, candidate, nil)
        result.setResult = set.rawValue
        if set == .success {
            result.readbackMatchesCandidate = CGDisplayCopyDisplayMode(display).map(modeRecord) == modeRecord(candidate)
            result.status = result.readbackMatchesCandidate == true ? "passed" : "failed"
        } else {
            result.status = "failed"
        }
    }
    return result.snapshot()
}

private func write(_ report: ProbeReport, to output: URL) throws {
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    try encoder.encode(report).write(to: output, options: .atomic)
}

do {
    let arguments = try Arguments()
    let displays = try activeDisplays()
    let records = displays.map(displayRecord)
    var modeChange: ModeChangeRecord?

    if arguments.applyModeCanary {
        guard displays.count == 1,
              let display = displays.first,
              let original = CGDisplayCopyDisplayMode(display),
              let candidate = (CGDisplayCopyAllDisplayModes(display, nil) as? [CGDisplayMode])?.first(where: {
                  $0.isUsableForDesktopGUI() && modeRecord($0) != modeRecord(original)
              }) else {
            modeChange = ModeChangeRecord(
                status: "unavailable",
                captureResult: nil,
                setResult: nil,
                restoreResult: nil,
                releaseResult: nil,
                readbackMatchesCandidate: nil,
                restoredOriginalMode: nil,
            )
            try write(ProbeReport(status: "unavailable", requestedModeChange: true, displays: records, modeChange: modeChange), to: arguments.output)
            exit(0)
        }
        modeChange = runModeCanary(display: display, original: original, candidate: candidate)
    }

    let status = modeChange?.status ?? "observed"
    try write(ProbeReport(status: status, requestedModeChange: arguments.applyModeCanary, displays: records, modeChange: modeChange), to: arguments.output)
} catch {
    fputs("Kadre AppKit display-mode canary failed: \(error.localizedDescription)\n", stderr)
    exit(64)
}
