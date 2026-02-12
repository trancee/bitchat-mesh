import SwiftUI
import CoreBluetooth
import BitchatMesh
import UIKit
import UniformTypeIdentifiers

final class MeshSampleModel: ObservableObject, MeshListener {
    @Published var isRunning = false
    @Published var peers: [PeerID] = []
    @Published var peerNicknames: [PeerID: String] = [:]
    @Published var logLines: [String] = []
    @Published var messageText = ""
    @Published var myPeerId = "Not available yet"
    @Published var selectedPeerId = ""
    @Published var pendingDirectCountByPeer: [String: Int] = [:]
    @Published var fileTransferProgress: TransferProgressState?
    @Published var lastIncomingFile: IncomingFileState?
    @Published var currentOutgoingTransferId: String?

    private let mesh: MeshManager
    private let dateFormatter: DateFormatter
    private let maxLogLines = 1000
    private let pendingRetryDelay: TimeInterval = 3
    private let pendingTimeout: TimeInterval = 15
    private let pendingMaxAttempts = 3
    private var pendingDirectMessages: [String: [PendingDirectMessage]] = [:]
    private var pendingRetryTimers: [String: Timer] = [:]
    private var transferInfoById: [String: TransferInfo] = [:]

    init() {
        mesh = MeshManager()
        dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "HH:mm:ss"
        mesh.listener = self
    }

    deinit {
        pendingRetryTimers.values.forEach { $0.invalidate() }
    }

    var pendingCount: Int {
        pendingDirectCountByPeer[selectedPeerId] ?? 0
    }

    var sessionStatusText: String {
        guard !selectedPeerId.isEmpty else { return "Session: no peer" }
        let peer = PeerID(str: selectedPeerId)
        let isEstablished = mesh.isEstablished(peer)
        return "Session: \(isEstablished ? "established" : "not established")"
    }

    var canEstablish: Bool {
        isRunning && !selectedPeerId.isEmpty
    }

    var canSendFile: Bool {
        guard isRunning, !selectedPeerId.isEmpty else { return false }
        let peer = PeerID(str: selectedPeerId)
        return mesh.isEstablished(peer)
    }

    var isSendingFile: Bool {
        currentOutgoingTransferId != nil
    }

    func start() {
        guard !isRunning else { return }
        mesh.start(nickname: defaultNickname())
        isRunning = true
        refreshMyPeerId()
        append("mesh started")
    }

    func stop() {
        guard isRunning else { return }
        mesh.stop()
        isRunning = false
        myPeerId = "Not available yet"
        peers = []
        selectedPeerId = ""
        fileTransferProgress = nil
        lastIncomingFile = nil
        currentOutgoingTransferId = nil
        pendingDirectMessages.removeAll()
        pendingRetryTimers.values.forEach { $0.invalidate() }
        pendingRetryTimers.removeAll()
        updatePendingCounts()
        append("mesh stopped")
    }

    func sendBroadcast() {
        let text = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        mesh.sendBroadcastMessage(text)
        append("sent: \(text)")
        messageText = ""
    }

    func sendDirect() {
        let text = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !selectedPeerId.isEmpty else {
            append("direct message needs a selected peer and message")
            return
        }
        let peer = PeerID(str: selectedPeerId)
        let nickname = peerNicknames[peer].flatMap { $0.isEmpty ? nil : $0 } ?? peer.id
        if mesh.isEstablished(peer) {
            mesh.sendPrivateMessage(text, to: peer, recipientNickname: nickname)
            append("sent direct to \(peer.id) (\(nickname)): \(text)")
        } else {
            queuePendingDirectMessage(peerId: peer.id, nickname: nickname, content: text)
            mesh.sendPrivateMessage(text, to: peer, recipientNickname: nickname)
            append("queued direct to \(peer.id) (\(nickname)): \(text)")
        }
        messageText = ""
    }

    func establishSelectedPeer() {
        guard canEstablish else {
            append("mesh is not started")
            return
        }
        let peer = PeerID(str: selectedPeerId)
        mesh.establish(peer)
        append("establishing session with \(peer.id)")
    }

    func sendFile(url: URL) {
        guard !selectedPeerId.isEmpty else {
            append("file transfer needs a selected peer")
            return
        }
        let peer = PeerID(str: selectedPeerId)
        guard mesh.isEstablished(peer) else {
            append("file transfer requires an established session")
            mesh.establish(peer)
            return
        }

        let didAccess = url.startAccessingSecurityScopedResource()
        defer {
            if didAccess { url.stopAccessingSecurityScopedResource() }
        }

        do {
            let data = try Data(contentsOf: url)
            let fileName = url.lastPathComponent.isEmpty ? "file" : url.lastPathComponent
            let mimeType = resolveMimeType(for: url)
            let packet = BitchatFilePacket(
                fileName: fileName,
                fileSize: UInt64(data.count),
                mimeType: mimeType,
                content: data
            )
            if let payload = packet.encode() {
                let transferId = payload.sha256Hex()
                transferInfoById[transferId] = TransferInfo(fileName: fileName, fileSize: data.count)
                currentOutgoingTransferId = transferId
            }
            mesh.sendFilePrivate(packet, to: peer)
            append("sending file to \(peer.id) (\(fileName), \(data.count) bytes)")
        } catch {
            append("failed to read file: \(error.localizedDescription)")
        }
    }

    func cancelCurrentTransfer() {
        guard let transferId = currentOutgoingTransferId else { return }
        mesh.cancelTransfer(transferId)
        transferInfoById.removeValue(forKey: transferId)
        currentOutgoingTransferId = nil
        fileTransferProgress = nil
        append("cancelled transfer \(transferId)")
    }

    private func resolveMimeType(for url: URL) -> String {
        let ext = url.pathExtension
        if !ext.isEmpty, let type = UTType(filenameExtension: ext) {
            return type.preferredMIMEType ?? "application/octet-stream"
        }
        return "application/octet-stream"
    }

    func clearLog() {
        logLines.removeAll()
        append("log cleared")
    }

    private func refreshMyPeerId() {
        let peerId = mesh.myPeerId.trimmingCharacters(in: .whitespacesAndNewlines)
        myPeerId = peerId.isEmpty ? "Not available yet" : peerId
    }

    private func defaultNickname() -> String {
        let env = ProcessInfo.processInfo.environment
        let simulatorName = env["SIMULATOR_DEVICE_NAME"]?.trimmingCharacters(in: .whitespacesAndNewlines)
        let deviceName = UIDevice.current.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let modelName = UIDevice.current.model.trimmingCharacters(in: .whitespacesAndNewlines)
        let modelDisplayName = deviceModelDisplayName().trimmingCharacters(in: .whitespacesAndNewlines)
        let baseName = simulatorName?.isEmpty == false ? simulatorName! : deviceName
        if baseName.isEmpty {
            return "sample"
        }
        if modelName.isEmpty || baseName.caseInsensitiveCompare(modelName) == .orderedSame {
            if !modelDisplayName.isEmpty, baseName.caseInsensitiveCompare(modelDisplayName) != .orderedSame {
                return modelDisplayName
            }
            return baseName
        }
        if baseName.localizedCaseInsensitiveContains(modelName) {
            return baseName
        }
        if !modelDisplayName.isEmpty, !baseName.localizedCaseInsensitiveContains(modelDisplayName) {
            return "\(baseName) \(modelDisplayName)"
        }
        return "\(baseName) \(modelName)"
    }

    private func deviceModelDisplayName() -> String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let identifier = withUnsafePointer(to: &systemInfo.machine) { ptr in
            ptr.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(cString: $0)
            }
        }
        let map: [String: String] = [
            "iPhone15,4": "iPhone 15",
            "iPhone15,5": "iPhone 15 Plus",
            "iPhone16,1": "iPhone 15 Pro",
            "iPhone16,2": "iPhone 15 Pro Max"
        ]
        if let name = map[identifier] {
            return name
        }
        if identifier == "x86_64" || identifier == "arm64" {
            let env = ProcessInfo.processInfo.environment
            return env["SIMULATOR_DEVICE_NAME"] ?? ""
        }
        return ""
    }

    private func append(_ line: String) {
        DispatchQueue.main.async {
            let stamp = self.dateFormatter.string(from: Date())
            self.logLines.append("\(stamp)  \(line)")
            if self.logLines.count > self.maxLogLines {
                self.logLines.removeFirst(self.logLines.count - self.maxLogLines)
            }
        }
    }

    func log(_ line: String) {
        append(line)
    }

    private func updatePeers(_ peers: [PeerID]) {
        DispatchQueue.main.async {
            self.peers = peers
            self.peerNicknames = self.mesh.peerNicknames()
            self.syncSelectedPeer(with: peers)
        }
        append("peers: \(peers.count)")
    }

    private func syncSelectedPeer(with peers: [PeerID]) {
        let peerIds = peers.map { $0.id }
        if peerIds.isEmpty {
            selectedPeerId = ""
            return
        }
        if !peerIds.contains(selectedPeerId) {
            selectedPeerId = peerIds.first ?? ""
        }
    }

    private func queuePendingDirectMessage(peerId: String, nickname: String, content: String) {
        var queue = pendingDirectMessages[peerId] ?? []
        queue.append(PendingDirectMessage(peerId: peerId, nickname: nickname, content: content, createdAt: Date()))
        pendingDirectMessages[peerId] = queue
        updatePendingCounts()
        schedulePendingRetry(peerId: peerId)
    }

    private func flushPendingDirectMessages(peerId: String) {
        cancelPendingRetry(peerId: peerId)
        guard let queue = pendingDirectMessages[peerId] else { return }
        queue.forEach { message in
            let peer = PeerID(str: message.peerId)
            mesh.sendPrivateMessage(message.content, to: peer, recipientNickname: message.nickname)
            append("sent queued direct to \(message.peerId) (\(message.nickname)): \(message.content)")
        }
        pendingDirectMessages.removeValue(forKey: peerId)
        updatePendingCounts()
    }

    private func clearPendingDirectMessages(peerId: String, reason: String) {
        cancelPendingRetry(peerId: peerId)
        if let queue = pendingDirectMessages.removeValue(forKey: peerId), !queue.isEmpty {
            append("pending direct messages cleared for \(peerId) (\(reason))")
        }
        updatePendingCounts()
    }

    private func schedulePendingRetry(peerId: String) {
        if pendingRetryTimers[peerId] != nil { return }
        let timer = Timer.scheduledTimer(withTimeInterval: pendingRetryDelay, repeats: true) { [weak self] timer in
            guard let self else { return }
            let peer = PeerID(str: peerId)
            if self.mesh.isEstablished(peer) {
                timer.invalidate()
                self.pendingRetryTimers.removeValue(forKey: peerId)
                self.flushPendingDirectMessages(peerId: peerId)
                return
            }
            guard var queue = self.pendingDirectMessages[peerId], !queue.isEmpty else {
                timer.invalidate()
                self.pendingRetryTimers.removeValue(forKey: peerId)
                return
            }
            let now = Date()
            queue = queue.filter { message in
                let expired = now.timeIntervalSince(message.createdAt) > self.pendingTimeout
                if expired || message.attempts >= self.pendingMaxAttempts {
                    self.append("direct to \(message.peerId) (\(message.nickname)) failed: session not established")
                    return false
                }
                self.mesh.establish(peer)
                message.attempts += 1
                return true
            }
            if queue.isEmpty {
                self.pendingDirectMessages.removeValue(forKey: peerId)
                timer.invalidate()
                self.pendingRetryTimers.removeValue(forKey: peerId)
            } else {
                self.pendingDirectMessages[peerId] = queue
            }
            self.updatePendingCounts()
        }
        pendingRetryTimers[peerId] = timer
    }

    private func cancelPendingRetry(peerId: String) {
        if let timer = pendingRetryTimers.removeValue(forKey: peerId) {
            timer.invalidate()
        }
    }

    private func updatePendingCounts() {
        var counts: [String: Int] = [:]
        for (peerId, queue) in pendingDirectMessages {
            counts[peerId] = queue.count
        }
        pendingDirectCountByPeer = counts
    }

    func onMessageReceived(_ message: BitchatMessage) {
        let prefix = message.isPrivate ? "direct" : "broadcast"
        append("\(prefix) message from \(message.sender): \(message.content)")
    }

    func onReceived(_ message: BitchatMessage) {
        append("received \(message.id) from \(message.sender)")
    }

    func onSent(messageID: String?, recipientPeerID: String?) {
        let id = messageID ?? "unknown"
        let recipient = recipientPeerID ?? "broadcast"
        append("sent \(id) to \(recipient)")
    }

    func onPeerListUpdated(_ peers: [PeerID]) {
        updatePeers(peers)
    }

    func onFound(_ peerID: PeerID) {
        append("found \(peerID.id)")
    }

    func onLost(_ peerID: PeerID) {
        append("lost \(peerID.id)")
        clearPendingDirectMessages(peerId: peerID.id, reason: "lost")
    }

    func onPeerConnected(_ peerID: PeerID) {
        append("connected \(peerID.id)")
    }

    func onPeerDisconnected(_ peerID: PeerID) {
        append("disconnected \(peerID.id)")
        clearPendingDirectMessages(peerId: peerID.id, reason: "disconnected")
    }

    func onEstablished(_ peerID: PeerID) {
        append("session established \(peerID.id)")
        flushPendingDirectMessages(peerId: peerID.id)
    }

    func onStarted() {
        refreshMyPeerId()
        isRunning = true
    }

    func onStopped() {
        isRunning = false
    }

    func onDeliveryAck(messageID: String, recipientNickname: String, timestamp: Date) {
        append("delivered to \(recipientNickname) (\(messageID))")
    }

    func onReadReceipt(messageID: String, recipientNickname: String, timestamp: Date) {
        append("read by \(recipientNickname) (\(messageID))")
    }

    func onVerifyChallenge(peerID: PeerID, payload: Data, timestamp: Date) {
        append("handshake initiated by \(peerID.id)")
    }

    func onVerifyResponse(peerID: PeerID, payload: Data, timestamp: Date) {
        append("verify response from \(peerID.id)")
        flushPendingDirectMessages(peerId: peerID.id)
    }

    func onBluetoothStateUpdated(_ state: CBManagerState) {
        append("bluetooth state: \(state.rawValue)")
    }

    func onPublicMessageReceived(from peerID: PeerID, nickname: String, content: String, timestamp: Date, messageID: String?) {
        append("public from \(nickname): \(content)")
    }

    func onNoisePayloadReceived(from peerID: PeerID, type: NoisePayloadType, payload: Data, timestamp: Date) {
        append("noise payload \(type) from \(peerID.id)")
    }

    func onTransferProgress(transferId: String, sent: Int, total: Int, completed: Bool) {
        DispatchQueue.main.async {
            let safeTotal = max(total, 1)
            let info = self.transferInfoById[transferId]
            self.fileTransferProgress = TransferProgressState(
                transferId: transferId,
                sent: min(sent, safeTotal),
                total: safeTotal,
                completed: completed,
                fileName: info?.fileName,
                fileSize: info?.fileSize
            )
            if completed {
                let currentId = transferId
                self.transferInfoById.removeValue(forKey: transferId)
                if transferId == self.currentOutgoingTransferId {
                    self.currentOutgoingTransferId = nil
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                    if self.fileTransferProgress?.transferId == currentId {
                        self.fileTransferProgress = nil
                    }
                }
            }
        }
    }

    func onFileReceived(peerID: PeerID, fileName: String, fileSize: Int, mimeType: String, localURL: URL) {
        DispatchQueue.main.async {
            self.lastIncomingFile = IncomingFileState(
                peerID: peerID.id,
                fileName: fileName,
                fileSize: fileSize
            )
        }
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        let sizeLabel = formatter.string(fromByteCount: Int64(fileSize))
        append("incoming file from \(peerID.id): \(fileName) (\(sizeLabel))")
    }

    private final class PendingDirectMessage {
        let peerId: String
        let nickname: String
        let content: String
        let createdAt: Date
        var attempts: Int

        init(peerId: String, nickname: String, content: String, createdAt: Date, attempts: Int = 0) {
            self.peerId = peerId
            self.nickname = nickname
            self.content = content
            self.createdAt = createdAt
            self.attempts = attempts
        }
    }

    struct TransferProgressState {
        let transferId: String
        let sent: Int
        let total: Int
        let completed: Bool
        let fileName: String?
        let fileSize: Int?

        var fraction: Double {
            guard total > 0 else { return 0 }
            return Double(sent) / Double(total)
        }

        var label: String {
            if let name = fileName, let size = fileSize {
                let formatter = ByteCountFormatter()
                formatter.countStyle = .file
                let sizeLabel = formatter.string(fromByteCount: Int64(size))
                return "File transfer: \(name) (\(sizeLabel)) \(sent)/\(total)"
            }
            return "File transfer: \(sent)/\(total)"
        }
    }

    struct IncomingFileState {
        let peerID: String
        let fileName: String
        let fileSize: Int

        var label: String {
            let formatter = ByteCountFormatter()
            formatter.countStyle = .file
            let sizeLabel = formatter.string(fromByteCount: Int64(fileSize))
            return "Incoming file: \(fileName) (\(sizeLabel)) from \(peerID)"
        }
    }

    private struct TransferInfo {
        let fileName: String
        let fileSize: Int
    }
}

struct ContentView: View {
    @ObservedObject var model: MeshSampleModel
    @State private var autoScroll = true
    @State private var isFileImporterPresented = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Mesh Sample")
                        .font(.title2.bold())
                        .foregroundColor(MeshSamplePalette.textPrimary)
                    Text("BLE mesh discovery, messaging, and debug telemetry")
                        .font(.footnote)
                        .foregroundColor(MeshSamplePalette.textSecondary)
                }
                Spacer()
                Text(model.isRunning ? "Running" : "Stopped")
                    .font(.footnote.weight(.semibold))
                    .foregroundColor(MeshSamplePalette.statusText)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(model.isRunning ? MeshSamplePalette.statusRunning : MeshSamplePalette.statusIdle)
                    .clipShape(Capsule())
            }

            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("My peer ID")
                    .font(.caption)
                    .foregroundColor(MeshSamplePalette.textSecondary)
                Text(model.myPeerId)
                    .font(.caption)
                    .foregroundColor(MeshSamplePalette.textPrimary)
            }

            HStack(spacing: 12) {
                Button("Start mesh") {
                    model.start()
                }
                .buttonStyle(.borderedProminent)
                .tint(MeshSamplePalette.primary)

                Button("Stop mesh") {
                    model.stop()
                }
                .buttonStyle(.borderedProminent)
                .tint(MeshSamplePalette.secondary)
            }

            TextField("Write a message", text: $model.messageText)
                .textFieldStyle(.plain)
                .padding(12)
                .background(MeshSamplePalette.surfaceAlt)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(MeshSamplePalette.inputBorder, lineWidth: 1)
                )

            Button("Send broadcast") {
                model.sendBroadcast()
            }
            .buttonStyle(.borderedProminent)
            .tint(MeshSamplePalette.primary)

            VStack(alignment: .leading, spacing: 8) {
                Text("Direct message")
                    .font(.headline)
                    .foregroundColor(MeshSamplePalette.textPrimary)

                Picker("Peer", selection: $model.selectedPeerId) {
                    ForEach(peerOptions) { option in
                        Text(option.label).tag(option.id)
                    }
                }
                .pickerStyle(.menu)
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(MeshSamplePalette.surfaceAlt)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(MeshSamplePalette.inputBorder, lineWidth: 1)
                )

                HStack(spacing: 12) {
                    Button("Establish session") {
                        model.establishSelectedPeer()
                    }
                    .buttonStyle(.bordered)
                    .tint(MeshSamplePalette.surfaceAlt)
                    .foregroundColor(MeshSamplePalette.textPrimary)
                    .disabled(!model.canEstablish)

                    Button(model.isSendingFile ? "Cancel transfer" : "Send file") {
                        if model.isSendingFile {
                            model.cancelCurrentTransfer()
                        } else {
                            isFileImporterPresented = true
                        }
                    }
                    .buttonStyle(.bordered)
                    .tint(MeshSamplePalette.surfaceAlt)
                    .foregroundColor(MeshSamplePalette.textPrimary)
                    .disabled(model.isSendingFile ? false : !model.canSendFile)
                }
                Text(model.sessionStatusText)
                    .font(.caption)
                    .foregroundColor(model.selectedPeerId.isEmpty ? MeshSamplePalette.textHint : MeshSamplePalette.textSecondary)

                if let progress = model.fileTransferProgress {
                    ProgressView(value: progress.fraction)
                        .tint(MeshSamplePalette.primary)
                    Text(progress.label)
                        .font(.caption)
                        .foregroundColor(MeshSamplePalette.textSecondary)
                }

                if let incoming = model.lastIncomingFile {
                    Text(incoming.label)
                        .font(.caption)
                        .foregroundColor(MeshSamplePalette.textSecondary)
                }

                Button("Send direct") {
                    model.sendDirect()
                }
                .buttonStyle(.borderedProminent)
                .tint(MeshSamplePalette.secondary)

                if model.pendingCount > 0 {
                    Text("Pending direct: \(model.pendingCount)")
                        .font(.caption)
                        .foregroundColor(MeshSamplePalette.textSecondary)

                    Text("Handshake pending")
                        .font(.caption2.weight(.medium))
                        .foregroundColor(MeshSamplePalette.textSecondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(MeshSamplePalette.statusIdle)
                        .clipShape(Capsule())
                }
            }

            HStack(spacing: 12) {
                Text("Debug log")
                    .font(.headline)
                    .foregroundColor(MeshSamplePalette.textPrimary)
                Spacer()
                HStack(spacing: 10) {
                    Text("Auto-scroll")
                        .font(.caption)
                        .foregroundColor(MeshSamplePalette.textSecondary)
                    Toggle("", isOn: $autoScroll)
                        .labelsHidden()
                    Button("Clear") {
                        model.clearLog()
                    }
                    .buttonStyle(.bordered)
                    .tint(MeshSamplePalette.surfaceAlt)
                }
            }

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 6) {
                        ForEach(Array(model.logLines.enumerated()), id: \.offset) { index, line in
                            Text(line)
                                .font(.system(.footnote, design: .monospaced))
                                .foregroundColor(MeshSamplePalette.textPrimary)
                                .id(index)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                }
                .background(MeshSamplePalette.surfaceAlt)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(MeshSamplePalette.logBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .onChange(of: model.logLines.count) { count in
                    guard autoScroll, count > 0 else { return }
                    withAnimation(.easeOut(duration: 0.2)) {
                        proxy.scrollTo(count - 1, anchor: .bottom)
                    }
                }
            }
        }
        .padding(20)
        .background(MeshSamplePalette.surface)
        .fileImporter(
            isPresented: $isFileImporterPresented,
            allowedContentTypes: [UTType.item],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                if let url = urls.first {
                    model.sendFile(url: url)
                }
            case .failure(let error):
                model.log("file picker failed: \(error.localizedDescription)")
            }
        }
    }

    private var peerOptions: [PeerOption] {
        if model.peers.isEmpty {
            return [PeerOption(id: "", label: "No peers found")]
        }
        return model.peers.map { peer in
            let nickname = model.peerNicknames[peer]
            if let nickname, !nickname.isEmpty {
                return PeerOption(id: peer.id, label: "\(peer.id) (\(nickname))")
            }
            return PeerOption(id: peer.id, label: peer.id)
        }
    }
}

private struct PeerOption: Identifiable {
    let id: String
    let label: String
}

private enum MeshSamplePalette {
    static let primary = Color(red: 0.12, green: 0.44, blue: 0.92)
    static let secondary = Color(red: 0.18, green: 0.2, blue: 0.23)
    static let surface = Color(red: 0.96, green: 0.97, blue: 0.98)
    static let surfaceAlt = Color.white
    static let textPrimary = Color(red: 0.06, green: 0.09, blue: 0.17)
    static let textSecondary = Color(red: 0.29, green: 0.33, blue: 0.39)
    static let textHint = Color(red: 0.58, green: 0.64, blue: 0.72)
    static let statusText = Color(red: 0.06, green: 0.09, blue: 0.17)
    static let statusIdle = Color(red: 0.89, green: 0.91, blue: 0.94)
    static let statusRunning = Color(red: 0.86, green: 0.99, blue: 0.91)
    static let logBorder = Color(red: 0.82, green: 0.84, blue: 0.87)
    static let inputBorder = Color(red: 0.8, green: 0.84, blue: 0.88)
}
