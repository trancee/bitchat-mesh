import CoreBluetooth
import Foundation

public final class MeshManager {
    public weak var listener: MeshListener?

    private let keychainManager: KeychainManagerProtocol
    private let identityManager: SecureIdentityStateManager
    private let bleService: BLEService

    public var myPeerID: PeerID {
        bleService.myPeerID
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
    }

    public func start(nickname: String) {
        bleService.setNickname(nickname)
        bleService.startServices()
    }

    public func stop() {
        bleService.stopServices()
    }

    public func setNickname(_ nickname: String) {
        bleService.setNickname(nickname)
    }

    public func sendBroadcastMessage(_ content: String) {
        bleService.sendMessage(content, mentions: [])
    }

    public func sendPrivateMessage(_ content: String, to peerID: PeerID, recipientNickname: String) {
        bleService.sendPrivateMessage(content, to: peerID, recipientNickname: recipientNickname, messageID: UUID().uuidString)
    }

    public func sendReadReceipt(_ receipt: ReadReceipt, to peerID: PeerID) {
        bleService.sendReadReceipt(receipt, to: peerID)
    }

    public func sendDeliveryAck(for messageID: String, to peerID: PeerID) {
        bleService.sendDeliveryAck(for: messageID, to: peerID)
    }

    public func triggerHandshake(with peerID: PeerID) {
        bleService.triggerHandshake(with: peerID)
    }

    public func peerNicknames() -> [PeerID: String] {
        bleService.getPeerNicknames()
    }
}

extension MeshManager: BitchatDelegate {
    func didReceiveMessage(_ message: BitchatMessage) {
        listener?.onMessageReceived(message)
    }

    func didConnectToPeer(_ peerID: PeerID) {
        listener?.onPeerConnected(peerID)
    }

    func didDisconnectFromPeer(_ peerID: PeerID) {
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
    }

    func didUpdateBluetoothState(_ state: CBManagerState) {
        listener?.onBluetoothStateUpdated(state)
    }

    func didReceivePublicMessage(from peerID: PeerID, nickname: String, content: String, timestamp: Date, messageID: String?) {
        listener?.onPublicMessageReceived(from: peerID, nickname: nickname, content: content, timestamp: timestamp, messageID: messageID)
    }
}
