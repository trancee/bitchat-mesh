import Combine
import XCTest
@testable import BitchatMesh

final class IntegrationMessagingTests: XCTestCase {
    private final class InMemoryKeychain: KeychainManagerProtocol {
        private var storage: [String: Data] = [:]
        private var genericStorage: [String: Data] = [:]

        func saveIdentityKey(_ keyData: Data, forKey key: String) -> Bool {
            storage[identityKey(key)] = keyData
            return true
        }

        func getIdentityKey(forKey key: String) -> Data? {
            storage[identityKey(key)]
        }

        func deleteIdentityKey(forKey key: String) -> Bool {
            storage.removeValue(forKey: identityKey(key))
            return true
        }

        func deleteAllKeychainData() -> Bool {
            storage.removeAll()
            genericStorage.removeAll()
            return true
        }

        func secureClear(_ data: inout Data) {
            data.removeAll(keepingCapacity: false)
        }

        func secureClear(_ string: inout String) {
            string.removeAll(keepingCapacity: false)
        }

        func verifyIdentityKeyExists() -> Bool {
            storage.keys.contains { $0.hasPrefix("identity_") }
        }

        func getIdentityKeyWithResult(forKey key: String) -> KeychainReadResult {
            if let value = storage[identityKey(key)] {
                return .success(value)
            }
            return .itemNotFound
        }

        func saveIdentityKeyWithResult(_ keyData: Data, forKey key: String) -> KeychainSaveResult {
            storage[identityKey(key)] = keyData
            return .success
        }

        func save(key: String, data: Data, service: String, accessible: CFString?) {
            genericStorage[genericKey(service, key)] = data
        }

        func load(key: String, service: String) -> Data? {
            genericStorage[genericKey(service, key)]
        }

        func delete(key: String, service: String) {
            genericStorage.removeValue(forKey: genericKey(service, key))
        }

        private func identityKey(_ key: String) -> String {
            "identity_\(key)"
        }

        private func genericKey(_ service: String, _ key: String) -> String {
            "\(service)::\(key)"
        }
    }

    private final class FakeTransport: Transport {
        var delegate: BitchatDelegate?
        var peerEventsDelegate: TransportPeerEventsDelegate?

        private let snapshotSubject = CurrentValueSubject<[TransportPeerSnapshot], Never>([])
        var peerSnapshotPublisher: AnyPublisher<[TransportPeerSnapshot], Never> {
            snapshotSubject.eraseToAnyPublisher()
        }

        var myPeerID: PeerID
        var myNickname: String
        var lastPrivate: (String, PeerID, String, String)?

        private let noiseService: NoiseEncryptionService

        init(peerID: PeerID, nickname: String, noiseService: NoiseEncryptionService) {
            self.myPeerID = peerID
            self.myNickname = nickname
            self.noiseService = noiseService
        }

        func currentPeerSnapshots() -> [TransportPeerSnapshot] {
            snapshotSubject.value
        }

        func setNickname(_ nickname: String) {
            myNickname = nickname
        }

        func startServices() {}
        func stopServices() {}
        func emergencyDisconnectAll() {}

        func isPeerConnected(_ peerID: PeerID) -> Bool { false }
        func isPeerReachable(_ peerID: PeerID) -> Bool { false }

        func peerNickname(peerID: PeerID) -> String? { nil }
        func getPeerNicknames() -> [PeerID: String] { [:] }

        func getFingerprint(for peerID: PeerID) -> String? { nil }
        func getNoiseSessionState(for peerID: PeerID) -> LazyHandshakeState { .none }
        func triggerHandshake(with peerID: PeerID) {}
        func getNoiseService() -> NoiseEncryptionService { noiseService }

        func sendMessage(_ content: String, mentions: [String]) {}
        func sendPrivateMessage(_ content: String, to peerID: PeerID, recipientNickname: String, messageID: String) {
            lastPrivate = (content, peerID, recipientNickname, messageID)
        }
        func sendReadReceipt(_ receipt: ReadReceipt, to peerID: PeerID) {}
        func sendBroadcastAnnounce() {}
        func sendDeliveryAck(for messageID: String, to peerID: PeerID) {}
        func sendFileBroadcast(_ packet: BitchatFilePacket, transferId: String) {}
        func sendFilePrivate(_ packet: BitchatFilePacket, to peerID: PeerID, transferId: String) {}
        func cancelTransfer(_ transferId: String) {}

        func acceptPendingFile(id: String) -> URL? { nil }
        func declinePendingFile(id: String) {}
    }

    func testDirectMessageIntegrationUsesTransport() {
        let noiseService = NoiseEncryptionService(keychain: InMemoryKeychain())
        let transport = FakeTransport(peerID: PeerID(str: "peer-1"), nickname: "anon", noiseService: noiseService)
        let mesh = MeshManager(transport: transport)

        let peer = PeerID(str: "peer-2")
        mesh.sendPrivateMessage("secret", to: peer, recipientNickname: "bob", messageID: "msg-1")

        XCTAssertEqual(transport.lastPrivate?.0, "secret")
        XCTAssertEqual(transport.lastPrivate?.1, peer)
        XCTAssertEqual(transport.lastPrivate?.2, "bob")
        XCTAssertEqual(transport.lastPrivate?.3, "msg-1")
    }

    func testRoutedMessageSelectsNextHop() {
        let selfData = Data(hexString: "0102030405060708") ?? Data()
        let nextHop = Data(hexString: "1112131415161718") ?? Data()
        let recipient = Data(hexString: "2122232425262728") ?? Data()
        let route = [selfData, nextHop]

        let resolved = BLEService.nextHopData(route: route, selfData: selfData, recipientID: recipient)

        XCTAssertEqual(resolved, nextHop)
    }

    func testRoutedMessageSelectsRecipientWhenLastHop() {
        let selfData = Data(hexString: "0102030405060708") ?? Data()
        let recipient = Data(hexString: "2122232425262728") ?? Data()
        let route = [selfData]

        let resolved = BLEService.nextHopData(route: route, selfData: selfData, recipientID: recipient)

        XCTAssertEqual(resolved, recipient)
    }
}
