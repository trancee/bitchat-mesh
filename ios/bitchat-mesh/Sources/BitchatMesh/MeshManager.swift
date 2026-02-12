import CoreBluetooth
import Foundation

/// High-level mesh API aligned with the Android MeshManager surface.
public final class MeshManager {
    public weak var listener: MeshListener?

    private let keychainManager: KeychainManagerProtocol
    private let identityManager: SecureIdentityStateManager
    private let bleService: BLEService
    private var hasStarted: Bool = false
    private var transferProgressObserverId: UUID?

    public var myPeerID: PeerID {
        bleService.myPeerID
    }

    public var myPeerId: String {
        myPeerID.id
    }

    public var myNickname: String {
        bleService.myNickname
    }

    public init(configuration: MeshConfiguration = .default) {
        let keychain = KeychainManager(configuration: configuration)
        self.keychainManager = keychain
        self.identityManager = SecureIdentityStateManager(keychain)
        self.bleService = BLEService(
            keychain: keychain,
            identityManager: identityManager
        )
        self.bleService.delegate = self
        VerificationService.shared.configure(with: bleService.getNoiseService())
        self.transferProgressObserverId = TransferProgressManager.shared.addObserver { [weak self] event in
            self?.listener?.onTransferProgress(
                transferId: event.transferId,
                sent: event.sent,
                total: event.total,
                completed: event.completed
            )
        }
    }

    deinit {
        if let observerId = transferProgressObserverId {
            TransferProgressManager.shared.removeObserver(observerId)
        }
    }

    /// Assign a listener for mesh events.
    public func setListener(_ listener: MeshListener?) {
        self.listener = listener
    }

    /// Start BLE services and optionally set the local nickname.
    public func start(nickname: String? = nil) {
        if let nickname = nickname?.trimmingCharacters(in: .whitespacesAndNewlines), !nickname.isEmpty {
            bleService.setNickname(nickname)
        }
        bleService.startServices()
        hasStarted = true
        listener?.onStarted()
    }

    /// Stop BLE services.
    public func stop() {
        bleService.stopServices()
        hasStarted = false
        listener?.onStopped()
    }

    /// Update the local nickname used in announces and messages.
    public func setNickname(_ nickname: String) {
        bleService.setNickname(nickname)
    }

    /// Return true once services have started and not yet stopped.
    public func isRunning() -> Bool {
        hasStarted
    }

    /// Alias for `isRunning()` to match Android naming.
    public func isStarted() -> Bool {
        hasStarted
    }

    /// Send a broadcast chat message with optional mentions.
    /// - Note: `channel` is accepted for parity but is currently ignored on iOS.
    public func sendBroadcastMessage(_ content: String, mentions: [String] = [], channel: String? = nil) {
        _ = channel
        bleService.sendMessage(content, mentions: mentions)
    }

    /// Send a private message to a peer.
    public func sendPrivateMessage(_ content: String, to peerID: PeerID, recipientNickname: String, messageID: String? = nil) {
        let id = messageID ?? UUID().uuidString
        bleService.sendPrivateMessage(content, to: peerID, recipientNickname: recipientNickname, messageID: id)
        listener?.onSent(messageID: id, recipientPeerID: peerID.id)
    }

    /// Initiate the Noise handshake with a peer.
    public func establish(_ peerID: PeerID) {
        bleService.triggerHandshake(with: peerID)
    }

    /// Alias for `establish(_:)`.
    public func triggerHandshake(with peerID: PeerID) {
        establish(peerID)
    }

    /// Return true if a Noise session is established with the peer.
    public func isEstablished(_ peerID: PeerID) -> Bool {
        bleService.getNoiseService().hasEstablishedSession(with: peerID)
    }

    /// Send a file packet as a broadcast transfer.
    public func sendFileBroadcast(_ packet: BitchatFilePacket) {
        guard let payload = packet.encode() else { return }
        let transferId = payload.sha256Hex()
        bleService.sendFileBroadcast(packet, transferId: transferId)
    }

    /// Send a file packet directly to a peer.
    public func sendFilePrivate(_ packet: BitchatFilePacket, to peerID: PeerID) {
        guard let payload = packet.encode() else { return }
        let transferId = payload.sha256Hex()
        bleService.sendFilePrivate(packet, to: peerID, transferId: transferId)
    }

    /// Send a read receipt to a peer.
    public func sendReadReceipt(_ receipt: ReadReceipt, to peerID: PeerID) {
        bleService.sendReadReceipt(receipt, to: peerID)
    }

    /// Send a delivery acknowledgment to a peer.
    public func sendDeliveryAck(for messageID: String, to peerID: PeerID) {
        bleService.sendDeliveryAck(for: messageID, to: peerID)
    }

    /// Current peer nickname map as known by the mesh.
    public func peerNicknames() -> [PeerID: String] {
        bleService.getPeerNicknames()
    }

    /// Current peer RSSI map, if available.
    /// - Note: iOS does not expose RSSI yet, so this returns an empty map.
    public func peerRssi() -> [PeerID: Int] {
        [:]
    }
}

extension MeshManager: BitchatDelegate {
    func didReceiveMessage(_ message: BitchatMessage) {
        listener?.onMessageReceived(message)
        listener?.onReceived(message)
    }

    func didConnectToPeer(_ peerID: PeerID) {
        listener?.onFound(peerID)
        listener?.onPeerConnected(peerID)
    }

    func didDisconnectFromPeer(_ peerID: PeerID) {
        listener?.onLost(peerID)
        listener?.onPeerDisconnected(peerID)
    }

    func didUpdatePeerList(_ peers: [PeerID]) {
        listener?.onPeerListUpdated(peers)
    }

    func didUpdateMessageDeliveryStatus(_ messageID: String, status: DeliveryStatus) {
        switch status {
        case .delivered(let nickname, let at):
            listener?.onDeliveryAck(messageID: messageID, recipientNickname: nickname, timestamp: at)
        case .read(let nickname, let at):
            listener?.onReadReceipt(messageID: messageID, recipientNickname: nickname, timestamp: at)
        default:
            break
        }
    }

    func didReceiveNoisePayload(from peerID: PeerID, type: NoisePayloadType, payload: Data, timestamp: Date) {
        listener?.onNoisePayloadReceived(from: peerID, type: type, payload: payload, timestamp: timestamp)
        switch type {
        case .verifyChallenge:
            listener?.onVerifyChallenge(peerID: peerID, payload: payload, timestamp: timestamp)
        case .verifyResponse:
            listener?.onVerifyResponse(peerID: peerID, payload: payload, timestamp: timestamp)
        default:
            break
        }
    }

    func didUpdateBluetoothState(_ state: CBManagerState) {
        listener?.onBluetoothStateUpdated(state)
    }

    func didReceivePublicMessage(from peerID: PeerID, nickname: String, content: String, timestamp: Date, messageID: String?) {
        listener?.onPublicMessageReceived(from: peerID, nickname: nickname, content: content, timestamp: timestamp, messageID: messageID)
    }
}
