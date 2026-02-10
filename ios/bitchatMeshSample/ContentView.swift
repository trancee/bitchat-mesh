import SwiftUI
import CoreBluetooth
import BitchatMesh

final class MeshSampleModel: ObservableObject, MeshListener {
    @Published var isRunning = false
    @Published var peers: [PeerID] = []
    @Published var peerNicknames: [PeerID: String] = [:]
    @Published var logLines: [String] = []
    @Published var messageText = ""

    private let mesh: MeshManager
    private let dateFormatter: DateFormatter
    private let maxLogLines = 200

    init() {
        mesh = MeshManager()
        dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "HH:mm:ss"
        mesh.listener = self
    }

    func start() {
        guard !isRunning else { return }
        mesh.start(nickname: "ios-sample")
        isRunning = true
        append("mesh started")
    }

    func stop() {
        guard isRunning else { return }
        mesh.stop()
        isRunning = false
        append("mesh stopped")
    }

    func sendBroadcast() {
        let text = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        mesh.sendBroadcastMessage(text)
        append("sent: \(text)")
        messageText = ""
    }

    func triggerHandshake(with peerID: PeerID) {
        mesh.triggerHandshake(with: peerID)
        append("handshake requested: \(peerID.id)")
    }

    func clearLog() {
        logLines.removeAll()
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

    func onMessageReceived(_ message: BitchatMessage) {
        append("message from \(message.sender): \(message.content)")
    }

    func onPeerListUpdated(_ peers: [PeerID]) {
        DispatchQueue.main.async {
            self.peers = peers
            self.peerNicknames = self.mesh.peerNicknames()
        }
        append("peers: \(peers.count)")
    }

    func onPeerConnected(_ peerID: PeerID) {
        append("connected: \(peerID.id)")
    }

    func onPeerDisconnected(_ peerID: PeerID) {
        append("disconnected: \(peerID.id)")
    }

    func onDeliveryAck(messageID: String, recipientNickname: String, timestamp: Date) {
        append("delivered to \(recipientNickname) (\(messageID.prefix(8)))")
    }

    func onReadReceipt(messageID: String, recipientNickname: String, timestamp: Date) {
        append("read by \(recipientNickname) (\(messageID.prefix(8)))")
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
}

struct ContentView: View {
    @ObservedObject var model: MeshSampleModel
    @State private var autoScroll = true
    @State private var selectedTab = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Mesh Sample")
                        .font(.title2.bold())
                    Text("Peers: \(model.peers.count)")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Text(model.isRunning ? "Running" : "Stopped")
                    .font(.footnote.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(model.isRunning ? Color.green.opacity(0.2) : Color.gray.opacity(0.2))
                    .clipShape(Capsule())
            }

            HStack(spacing: 12) {
                Button(model.isRunning ? "Restart" : "Start") {
                    model.start()
                }
                .buttonStyle(.borderedProminent)

                Button("Stop") {
                    model.stop()
                }
                .buttonStyle(.bordered)
            }

            Picker("View", selection: $selectedTab) {
                Text("Console").tag(0)
                Text("Quick connect").tag(1)
            }
            .pickerStyle(.segmented)

            if selectedTab == 0 {
                HStack(spacing: 12) {
                    TextField("Write a message", text: $model.messageText)
                        .textFieldStyle(.roundedBorder)

                    Button("Send") {
                        model.sendBroadcast()
                    }
                    .buttonStyle(.borderedProminent)
                }

                HStack {
                    Toggle("Auto-scroll", isOn: $autoScroll)
                    Spacer()
                    Button("Clear") {
                        model.clearLog()
                    }
                }

                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 6) {
                            ForEach(Array(model.logLines.enumerated()), id: \.offset) { index, line in
                                Text(line)
                                    .font(.system(.footnote, design: .monospaced))
                                    .foregroundColor(.primary)
                                    .id(index)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .background(Color.black.opacity(0.04))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .onChange(of: model.logLines.count) { count in
                        guard autoScroll, count > 0 else { return }
                        withAnimation(.easeOut(duration: 0.2)) {
                            proxy.scrollTo(count - 1, anchor: .bottom)
                        }
                    }
                }
            } else {
                QuickConnectView(model: model)
            }
        }
        .padding()
    }
}

struct QuickConnectView: View {
    @ObservedObject var model: MeshSampleModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if model.peers.isEmpty {
                Text("No nearby peers yet.")
                    .foregroundColor(.secondary)
            } else {
                List(model.peers, id: \.self) { peer in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(model.peerNicknames[peer] ?? peer.id)
                                .font(.headline)
                            Text(peer.id)
                                .font(.footnote)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Button("Handshake") {
                            model.triggerHandshake(with: peer)
                        }
                        .buttonStyle(.bordered)
                    }
                    .padding(.vertical, 4)
                }
                .listStyle(.plain)
            }
        }
    }
}
