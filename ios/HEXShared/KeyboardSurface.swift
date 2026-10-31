import Combine
import SwiftUI
import UIKit

@MainActor
final class KeyboardViewModel: ObservableObject {
    enum PickerTray: Equatable {
        case none
        case style
        case returnApp
    }

    struct PendingCommand: Equatable {
        let kind: KeyboardCommandKind
        let jobID: String
        let sentAt: TimeInterval
    }

    static let commandAckTimeout: TimeInterval = 1.6

    @Published var hasFullAccess = true
    @Published var needsGlobeKey = true
    @Published var returnKeyLabel = "Return"
    @Published var snapshot: KeyboardSnapshot = .offline
    @Published var isModelInstalled = false
    @Published var style: DictationStyle = .polished
    @Published var returnAppID: String?
    @Published var activeTray: PickerTray = .none
    @Published var now: TimeInterval = Date().timeIntervalSince1970
    @Published var coldStartURL: URL = KeyboardLaunchRequest.startSession(requestedAt: nil, returnAppID: nil).url
    @Published var pendingCommand: PendingCommand?
    /// Recent input levels, oldest first, for the scrolling meter.
    @Published private(set) var levelHistory = [Float](repeating: 0, count: KeyboardLevelMeter.barCount)

    var onDictateTap: (() -> Void)?
    var onStopTap: (() -> Void)?
    var onCancelTap: (() -> Void)?
    var onStyleSelected: ((DictationStyle) -> Void)?
    var onReturnAppSelected: ((String?) -> Void)?
    var onSpace: (() -> Void)?
    var onDelete: (() -> Void)?
    var onReturn: (() -> Void)?
    var onOpenURL: ((URL) -> Void)?
    weak var inputViewController: UIInputViewController?

    var isWarmAvailable: Bool {
        snapshot.isAvailable(at: now)
    }

    var selectedReturnApp: HostApp? {
        HostAppCatalog.app(for: returnAppID)
    }

    func recordLevel(_ level: Float) {
        levelHistory.removeFirst()
        levelHistory.append(max(0, min(1, level)))
    }

    func resetLevels() {
        guard levelHistory.contains(where: { $0 > 0 }) else { return }
        levelHistory = [Float](repeating: 0, count: KeyboardLevelMeter.barCount)
    }

    var isOptimisticStarting: Bool {
        guard let pending = pendingCommand, pending.kind == .start else { return false }
        return now - pending.sentAt < Self.commandAckTimeout && snapshot.state != .recording
    }

    var isOptimisticStopping: Bool {
        guard let pending = pendingCommand, pending.kind == .stop else { return false }
        return now - pending.sentAt < Self.commandAckTimeout && snapshot.state == .recording
    }
}

private struct GlobeKeyRepresentable: UIViewRepresentable {
    weak var controller: UIInputViewController?

    func makeUIView(context: Context) -> UIButton {
        let button = UIButton(type: .system)
        var config = UIButton.Configuration.plain()
        config.image = UIImage(
            systemName: "globe",
            withConfiguration: UIImage.SymbolConfiguration(pointSize: 15, weight: .medium)
        )
        config.baseForegroundColor = .label
        button.configuration = config
        button.accessibilityLabel = "Next keyboard"
        if let controller {
            button.addTarget(
                controller,
                action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                for: .allTouchEvents
            )
        }
        return button
    }

    func updateUIView(_ uiView: UIButton, context: Context) {}
}

private struct RepeatDeleteKey: View {
    let onDelete: () -> Void
    @State private var isPressing = false
    @State private var repeatTask: Task<Void, Never>?

    var body: some View {
        Image(systemName: "delete.left")
            .font(.system(size: 15, weight: .medium))
            .foregroundStyle(KeyboardPalette.text)
            .frame(width: 52, height: 40)
            .background(isPressing ? KeyboardPalette.keyPressed : KeyboardPalette.key, in: RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(KeyboardPalette.keyEdge, lineWidth: 0.5)
            )
            .contentShape(RoundedRectangle(cornerRadius: 10))
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        guard !isPressing else { return }
                        isPressing = true
                        onDelete()
                        repeatTask = Task { @MainActor in
                            try? await Task.sleep(for: .milliseconds(360))
                            while !Task.isCancelled {
                                onDelete()
                                try? await Task.sleep(for: .milliseconds(75))
                            }
                        }
                    }
                    .onEnded { _ in
                        isPressing = false
                        repeatTask?.cancel()
                        repeatTask = nil
                    }
            )
            .accessibilityLabel("Delete")
    }
}

/// Colours for controls drawn on the system keyboard backdrop. They follow the
/// keyboard's light or dark appearance; the backdrop itself is the system's.
enum KeyboardPalette {
    static let text = Color(uiColor: .label)
    static let textMuted = Color(uiColor: .secondaryLabel)
    static let textFaint = Color(uiColor: .tertiaryLabel)

    static let key = dynamic(light: UIColor(white: 1, alpha: 0.82), dark: UIColor(white: 1, alpha: 0.17))
    static let keyPressed = dynamic(light: UIColor(white: 0.70, alpha: 0.62), dark: UIColor(white: 1, alpha: 0.32))
    static let keyEdge = dynamic(light: UIColor(white: 0, alpha: 0.07), dark: UIColor(white: 1, alpha: 0.05))
    static let chip = dynamic(light: UIColor(white: 0, alpha: 0.06), dark: UIColor(white: 1, alpha: 0.11))
    static let chipActive = dynamic(light: UIColor(white: 1, alpha: 0.9), dark: UIColor(white: 1, alpha: 0.24))

    static let contrast = Color(uiColor: .label)
    static let textContrast = Color(uiColor: .systemBackground)

    static let recording = HexTheme.danger
    static let warm = HexTheme.warmMic

    private static func dynamic(light: UIColor, dark: UIColor) -> Color {
        Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? dark : light })
    }
}

/// Scrolling input-level meter: the newest level enters on the right. Silence is a
/// flat line, speech rises from it.
struct KeyboardLevelMeter: View {
    static let barCount = 28

    var levels: [Float]
    var active: Bool
    var height: CGFloat = 26

    var body: some View {
        ZStack {
            Capsule()
                .fill(KeyboardPalette.textFaint)
                .frame(height: 1.5)
            HStack(alignment: .center, spacing: 2) {
                ForEach(levels.indices, id: \.self) { index in
                    let level = CGFloat(active ? levels[index] : 0)
                    Capsule()
                        .fill(KeyboardPalette.text)
                        .frame(width: 3, height: level > 0.04 ? 4 + (height - 4) * min(1, level) : 0)
                }
            }
        }
        .frame(width: CGFloat(Self.barCount) * 5 - 2, height: height)
        .animation(.linear(duration: 0.12), value: levels)
        .accessibilityHidden(true)
    }
}

enum KeyboardSetupCopy {
    static let fullAccessPath = "Allow Full Access: Settings › Apps › Hex › Keyboards"
}

struct KeyboardSurfaceView: View {
    @ObservedObject var model: KeyboardViewModel

    var body: some View {
        VStack(spacing: 8) {
            topBar
            primaryStage
            utilityRow
        }
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private var topBar: some View {
        switch model.activeTray {
        case .style:
            HStack(spacing: 4) {
                ForEach(DictationStyle.allCases, id: \.self) { candidate in
                    let active = candidate == model.style
                    Button {
                        model.onStyleSelected?(candidate)
                        model.activeTray = .none
                    } label: {
                        Text(candidate.label)
                            .font(.system(size: 12, weight: active ? .semibold : .medium))
                            .foregroundStyle(active ? KeyboardPalette.text : KeyboardPalette.textMuted)
                            .padding(.horizontal, 10)
                            .frame(height: 28)
                            .background(active ? KeyboardPalette.chipActive : KeyboardPalette.chip, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 4)
                closeTrayButton
            }
            .frame(height: 30)

        case .returnApp:
            HStack(spacing: 4) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 4) {
                        returnChip(id: nil, label: "Swipe")
                        ForEach(HostAppCatalog.quickChips) { app in
                            returnChip(id: app.id, label: app.name)
                        }
                    }
                }
                closeTrayButton
            }
            .frame(height: 30)

        case .none:
            HStack(spacing: 8) {
                statusLead
                Spacer(minLength: 4)

                if !model.isWarmAvailable && model.hasFullAccess {
                    Button {
                        model.activeTray = .returnApp
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.uturn.backward")
                                .font(.system(size: 10, weight: .semibold))
                            Text(model.selectedReturnApp?.name ?? "Swipe")
                                .font(.system(size: 12, weight: .medium))
                        }
                        .foregroundStyle(KeyboardPalette.textMuted)
                        .padding(.horizontal, 9)
                        .frame(height: 26)
                        .background(KeyboardPalette.chip, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Return app after cold start")
                }

                if model.hasFullAccess {
                    Button {
                        model.activeTray = .style
                    } label: {
                        HStack(spacing: 4) {
                            Text(model.style.label)
                                .font(.system(size: 12, weight: .medium))
                            Image(systemName: "chevron.down")
                                .font(.system(size: 9, weight: .semibold))
                        }
                        .foregroundStyle(KeyboardPalette.text)
                        .padding(.horizontal, 10)
                        .frame(height: 26)
                        .background(KeyboardPalette.chip, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Dictation style: \(model.style.label)")
                }
            }
            .frame(height: 30)
        }
    }

    private func returnChip(id: String?, label: String) -> some View {
        let active = model.returnAppID == id
        return Button {
            model.onReturnAppSelected?(id)
            model.activeTray = .none
        } label: {
            Text(label)
                .font(.system(size: 12, weight: active ? .semibold : .medium))
                .foregroundStyle(active ? KeyboardPalette.text : KeyboardPalette.textMuted)
                .padding(.horizontal, 10)
                .frame(height: 28)
                .background(active ? KeyboardPalette.chipActive : KeyboardPalette.chip, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private var closeTrayButton: some View {
        Button {
            model.activeTray = .none
        } label: {
            Image(systemName: "xmark")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(KeyboardPalette.textMuted)
                .frame(width: 28, height: 28)
                .background(KeyboardPalette.chip, in: Circle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var statusLead: some View {
        if !model.hasFullAccess {
            HStack(spacing: 6) {
                HexStatusDot(color: KeyboardPalette.warm, size: 7)
                Text(KeyboardSetupCopy.fullAccessPath)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(KeyboardPalette.text)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Full Access is off. \(KeyboardSetupCopy.fullAccessPath)")
        } else if model.isWarmAvailable {
            switch model.snapshot.state {
            case .recording:
                HStack(spacing: 6) {
                    HexStatusDot(color: KeyboardPalette.recording, size: 8)
                    Text(HexTheme.formatClock(model.snapshot.recordingElapsed(at: model.now)))
                        .font(.system(size: 13, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(KeyboardPalette.text)
                }
            case .transcribing:
                HStack(spacing: 6) {
                    ProgressView()
                        .controlSize(.mini)
                        .tint(KeyboardPalette.textMuted)
                    Text("Transcribing…")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(KeyboardPalette.textMuted)
                }
            case .failed:
                HStack(spacing: 6) {
                    HexStatusDot(color: KeyboardPalette.recording, size: 7)
                    Text(model.snapshot.message ?? "Hex needs attention")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(KeyboardPalette.textMuted)
                        .lineLimit(1)
                }
            default:
                HStack(spacing: 6) {
                    HexStatusDot(color: KeyboardPalette.warm, size: 7)
                    Text(
                        model.snapshot.message
                            ?? "Warm · \(HexTheme.formatRemaining(model.snapshot.expiresAt - model.now))"
                    )
                    .font(.system(size: 12, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(KeyboardPalette.textMuted)
                    .lineLimit(1)
                }
            }
        } else {
            HStack(spacing: 6) {
                HexStatusDot(color: KeyboardPalette.textFaint, size: 6)
                Text(
                    model.snapshot.message
                        ?? (model.isModelInstalled ? "Hex · mic asleep" : "Hex · setup needed")
                )
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(KeyboardPalette.textFaint)
                .lineLimit(1)
            }
        }
    }

    @ViewBuilder
    private var primaryStage: some View {
        if !model.hasFullAccess {
            Button {
                model.onOpenURL?(KeyboardLaunchRequest.setup.url)
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.up.forward.app.fill")
                        .font(.system(size: 14, weight: .semibold))
                    Text("Open Hex setup")
                        .font(.system(size: 15, weight: .semibold))
                }
                .foregroundStyle(KeyboardPalette.textContrast)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(KeyboardPalette.contrast, in: Capsule())
            }
            .buttonStyle(.plain)
        } else if !model.isWarmAvailable || model.snapshot.state == .failed {
            Button {
                model.onOpenURL?(model.coldStartURL)
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "mic.fill")
                        .font(.system(size: 14, weight: .semibold))
                    Text(model.isModelInstalled ? "Start Hex" : "Set up Hex")
                        .font(.system(size: 15, weight: .semibold))
                }
                .foregroundStyle(KeyboardPalette.textContrast)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(KeyboardPalette.contrast, in: Capsule())
            }
            .buttonStyle(.plain)
        } else if model.snapshot.state == .recording {
            HStack(spacing: 10) {
                Button {
                    model.onCancelTap?()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(KeyboardPalette.textMuted)
                        .frame(width: 40, height: 40)
                        .background(KeyboardPalette.key, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Cancel dictation")

                Spacer(minLength: 4)

                KeyboardLevelMeter(
                    levels: model.levelHistory,
                    active: !model.isOptimisticStopping
                )

                Spacer(minLength: 4)

                Button {
                    model.onStopTap?()
                } label: {
                    HStack(spacing: 6) {
                        if model.isOptimisticStopping {
                            ProgressView()
                                .controlSize(.mini)
                                .tint(KeyboardPalette.textContrast)
                        } else {
                            Image(systemName: "checkmark")
                                .font(.system(size: 13, weight: .bold))
                        }
                        Text(model.isOptimisticStopping ? "Finishing" : "Done")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundStyle(KeyboardPalette.textContrast)
                    .padding(.horizontal, 16)
                    .frame(height: 40)
                    .background(KeyboardPalette.contrast, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(model.isOptimisticStopping)
            }
            .padding(.horizontal, 5)
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(KeyboardPalette.chip, in: Capsule())
        } else if model.snapshot.state == .transcribing || model.isOptimisticStarting {
            HStack(spacing: 8) {
                ProgressView()
                    .controlSize(.small)
                    .tint(KeyboardPalette.text)
                Text(model.isOptimisticStarting ? "Opening mic…" : "Transcribing on iPhone…")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(KeyboardPalette.text)
            }
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(KeyboardPalette.chip, in: Capsule())
        } else {
            Button {
                model.onDictateTap?()
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "mic.fill")
                        .font(.system(size: 14, weight: .semibold))
                    Text("Dictate")
                        .font(.system(size: 15, weight: .semibold))
                }
                .foregroundStyle(KeyboardPalette.textContrast)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(KeyboardPalette.contrast, in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    private var utilityRow: some View {
        HStack(spacing: 6) {
            if model.needsGlobeKey {
                GlobeKeyRepresentable(controller: model.inputViewController)
                    .frame(width: 44, height: 40)
                    .background(KeyboardPalette.key, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(KeyboardPalette.keyEdge, lineWidth: 0.5)
                    )
            }

            Button {
                model.onSpace?()
            } label: {
                Text("space")
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(KeyboardPalette.text)
                    .frame(maxWidth: .infinity)
                    .frame(height: 40)
                    .background(KeyboardPalette.key, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(KeyboardPalette.keyEdge, lineWidth: 0.5)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)

            RepeatDeleteKey {
                model.onDelete?()
            }

            Button {
                model.onReturn?()
            } label: {
                Image(systemName: "return")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(KeyboardPalette.text)
                    .frame(width: 56, height: 40)
                    .background(KeyboardPalette.key, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(KeyboardPalette.keyEdge, lineWidth: 0.5)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(model.returnKeyLabel)
        }
    }
}
