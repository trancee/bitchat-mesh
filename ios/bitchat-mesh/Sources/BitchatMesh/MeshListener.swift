import CoreBluetooth
import Foundation

/// Receives mesh events from `MeshManager`.
public protocol MeshListener: AnyObject {
    /// Called when a message is fully decoded.
    func onMessageReceived(_ message: BitchatMessage)
    /// Called when a message is received (alias for parity).
    func onReceived(_ message: BitchatMessage)
    /// Called after a message is queued for send.
    func onSent(messageID: String?, recipientPeerID: String?)
    /// Called when the peer list changes.
    func onPeerListUpdated(_ peers: [PeerID])
    /// Called when a peer is discovered.
    func onFound(_ peerID: PeerID)
    /// Called when a peer is lost.
    func onLost(_ peerID: PeerID)
    /// Called when a peer connects.
    func onPeerConnected(_ peerID: PeerID)
    /// Called when a peer disconnects.
    func onPeerDisconnected(_ peerID: PeerID)
    /// Called when a Noise session is established.
    func onEstablished(_ peerID: PeerID)
    /// Called when a peer RSSI update is available.
    func onRSSIUpdated(peerID: PeerID, rssi: Int)
    /// Called when mesh services start.
    func onStarted()
    /// Called when mesh services stop.
    func onStopped()
    /// Called when a delivery acknowledgment is received.
    func onDeliveryAck(messageID: String, recipientNickname: String, timestamp: Date)
    /// Called when a read receipt is received.
    func onReadReceipt(messageID: String, recipientNickname: String, timestamp: Date)
    /// Called when a verification challenge arrives.
    func onVerifyChallenge(peerID: PeerID, payload: Data, timestamp: Date)
    /// Called when a verification response arrives.
    func onVerifyResponse(peerID: PeerID, payload: Data, timestamp: Date)
    /// Called when the Bluetooth state changes.
    func onBluetoothStateUpdated(_ state: CBManagerState)
    /// Called when a public message is received from a peer.
    func onPublicMessageReceived(from peerID: PeerID, nickname: String, content: String, timestamp: Date, messageID: String?)
    /// Called when a Noise payload is received.
    func onNoisePayloadReceived(from peerID: PeerID, type: NoisePayloadType, payload: Data, timestamp: Date)
    /// Called when a file transfer makes progress.
    func onTransferProgress(transferId: String, sent: Int, total: Int, completed: Bool)
}

public extension MeshListener {
    func onMessageReceived(_ message: BitchatMessage) {}
    func onReceived(_ message: BitchatMessage) {}
    func onSent(messageID: String?, recipientPeerID: String?) {}
    func onPeerListUpdated(_ peers: [PeerID]) {}
    func onFound(_ peerID: PeerID) {}
    func onLost(_ peerID: PeerID) {}
    func onPeerConnected(_ peerID: PeerID) {}
    func onPeerDisconnected(_ peerID: PeerID) {}
    func onEstablished(_ peerID: PeerID) {}
    func onRSSIUpdated(peerID: PeerID, rssi: Int) {}
    func onStarted() {}
    func onStopped() {}
    func onDeliveryAck(messageID: String, recipientNickname: String, timestamp: Date) {}
    func onReadReceipt(messageID: String, recipientNickname: String, timestamp: Date) {}
    func onVerifyChallenge(peerID: PeerID, payload: Data, timestamp: Date) {}
    func onVerifyResponse(peerID: PeerID, payload: Data, timestamp: Date) {}
    func onBluetoothStateUpdated(_ state: CBManagerState) {}
    func onPublicMessageReceived(from peerID: PeerID, nickname: String, content: String, timestamp: Date, messageID: String?) {}
    func onNoisePayloadReceived(from peerID: PeerID, type: NoisePayloadType, payload: Data, timestamp: Date) {}
    func onTransferProgress(transferId: String, sent: Int, total: Int, completed: Bool) {}
}
