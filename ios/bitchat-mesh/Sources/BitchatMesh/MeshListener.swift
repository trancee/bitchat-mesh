import CoreBluetooth
import Foundation

public protocol MeshListener: AnyObject {
    func onMessageReceived(_ message: BitchatMessage)
    func onPeerListUpdated(_ peers: [PeerID])
    func onPeerConnected(_ peerID: PeerID)
    func onPeerDisconnected(_ peerID: PeerID)
    func onDeliveryAck(messageID: String, recipientNickname: String, timestamp: Date)
    func onReadReceipt(messageID: String, recipientNickname: String, timestamp: Date)
    func onBluetoothStateUpdated(_ state: CBManagerState)
    func onPublicMessageReceived(from peerID: PeerID, nickname: String, content: String, timestamp: Date, messageID: String?)
    func onNoisePayloadReceived(from peerID: PeerID, type: NoisePayloadType, payload: Data, timestamp: Date)
}

public extension MeshListener {
    func onMessageReceived(_ message: BitchatMessage) {}
    func onPeerListUpdated(_ peers: [PeerID]) {}
    func onPeerConnected(_ peerID: PeerID) {}
    func onPeerDisconnected(_ peerID: PeerID) {}
    func onDeliveryAck(messageID: String, recipientNickname: String, timestamp: Date) {}
    func onReadReceipt(messageID: String, recipientNickname: String, timestamp: Date) {}
    func onBluetoothStateUpdated(_ state: CBManagerState) {}
    func onPublicMessageReceived(from peerID: PeerID, nickname: String, content: String, timestamp: Date, messageID: String?) {}
    func onNoisePayloadReceived(from peerID: PeerID, type: NoisePayloadType, payload: Data, timestamp: Date) {}
}
