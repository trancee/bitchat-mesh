import Foundation
import CoreBluetooth

/// Represents a peer in the BitChat network with all associated metadata
struct BitchatPeer: Equatable {
    let peerID: PeerID // Hex-encoded peer ID
    let noisePublicKey: Data
    let nickname: String
    let lastSeen: Date
    let isConnected: Bool
    let isReachable: Bool
    
    // Connection state
    enum ConnectionState {
        case bluetoothConnected
        case meshReachable      // Seen via mesh recently, not directly connected
        case offline            // Not connected via any transport
    }
    
    var connectionState: ConnectionState {
        if isConnected {
            return .bluetoothConnected
        } else if isReachable {
            return .meshReachable
        }
        return .offline
    }
    
    // Display helpers
    var displayName: String {
        nickname.isEmpty ? String(peerID.id.prefix(8)) : nickname
    }
    
    var statusIcon: String {
        switch connectionState {
        case .bluetoothConnected:
            return "📻" // Radio icon for mesh connection
        case .meshReachable:
            return "📡" // Antenna for mesh reachable
        case .offline:
            return ""
        }
    }
    
    // Initialize from mesh service data
    init(
        peerID: PeerID,
        noisePublicKey: Data,
        nickname: String,
        lastSeen: Date = Date(),
        isConnected: Bool = false,
        isReachable: Bool = false
    ) {
        self.peerID = peerID
        self.noisePublicKey = noisePublicKey
        self.nickname = nickname
        self.lastSeen = lastSeen
        self.isConnected = isConnected
        self.isReachable = isReachable
        
    }
    
    static func == (lhs: BitchatPeer, rhs: BitchatPeer) -> Bool {
        lhs.peerID == rhs.peerID
    }
}
