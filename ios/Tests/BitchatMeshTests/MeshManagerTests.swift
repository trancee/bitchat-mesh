import Combine
import XCTest
@testable import BitchatMesh

final class MeshManagerTests: XCTestCase {
    private final class RecordingListener: MeshListener {
        var started = false
        var stopped = false
        var sent: [(String?, String?)] = []

        func onStarted() {
            started = true
        }

        func onStopped() {
            stopped = true
        }

        func onSent(messageID: String?, recipientPeerID: String?) {
            sent.append((messageID, recipientPeerID))
        }
    }

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
        var nicknames: [PeerID: String] = [:]

        var startCalls = 0
        var stopCalls = 0
        var setNicknameCalls: [String] = []
        var lastBroadcast: (String, [String])?
        var lastPrivate: (String, PeerID, String, String)?
        var lastReadReceipt: (ReadReceipt, PeerID)?
        var lastDeliveryAck: (String, PeerID)?
        var lastBroadcastFile: (BitchatFilePacket, String)?
        var lastPrivateFile: (BitchatFilePacket, PeerID, String)?
        var lastHandshakePeer: PeerID?
        var canceledTransfers: [String] = []

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
            setNicknameCalls.append(nickname)
        }

        func startServices() {
            startCalls += 1
        }

        func stopServices() {
            stopCalls += 1
        }

        func emergencyDisconnectAll() {}

        func isPeerConnected(_ peerID: PeerID) -> Bool { false }

        func isPeerReachable(_ peerID: PeerID) -> Bool { false }

        func peerNickname(peerID: PeerID) -> String? {
            nicknames[peerID]
        }

        func getPeerNicknames() -> [PeerID: String] {
            nicknames
        }

        func getFingerprint(for peerID: PeerID) -> String? { nil }

        func getNoiseSessionState(for peerID: PeerID) -> LazyHandshakeState { .none }

        func triggerHandshake(with peerID: PeerID) {
            lastHandshakePeer = peerID
        }

        func getNoiseService() -> NoiseEncryptionService {
            noiseService
        }

        func sendMessage(_ content: String, mentions: [String]) {
            lastBroadcast = (content, mentions)
        }

        func sendMessage(_ content: String, mentions: [String], messageID: String, timestamp: Date) {
            lastBroadcast = (content, mentions)
        }

        func sendPrivateMessage(_ content: String, to peerID: PeerID, recipientNickname: String, messageID: String) {
            lastPrivate = (content, peerID, recipientNickname, messageID)
        }

        func sendReadReceipt(_ receipt: ReadReceipt, to peerID: PeerID) {
            lastReadReceipt = (receipt, peerID)
        }

        func sendBroadcastAnnounce() {}

        func sendDeliveryAck(for messageID: String, to peerID: PeerID) {
            lastDeliveryAck = (messageID, peerID)
        }

        func sendFileBroadcast(_ packet: BitchatFilePacket, transferId: String) {
            lastBroadcastFile = (packet, transferId)
        }

        func sendFilePrivate(_ packet: BitchatFilePacket, to peerID: PeerID, transferId: String) {
            lastPrivateFile = (packet, peerID, transferId)
        }

        func cancelTransfer(_ transferId: String) {
            canceledTransfers.append(transferId)
        }

        func acceptPendingFile(id: String) -> URL? { nil }

        func declinePendingFile(id: String) {}
    }

    func testStartStopAndNickname() {
        let noiseService = NoiseEncryptionService(keychain: InMemoryKeychain())
        let transport = FakeTransport(peerID: PeerID(str: "peer-1"), nickname: "anon", noiseService: noiseService)
        let mesh = MeshManager(transport: transport)
        let listener = RecordingListener()
        mesh.setListener(listener)

        mesh.start(nickname: "  alice  ")
        XCTAssertEqual(transport.startCalls, 1)
        XCTAssertEqual(transport.setNicknameCalls, ["alice"])
        XCTAssertTrue(listener.started)
        XCTAssertEqual(mesh.myPeerId, "peer-1")

        mesh.setNickname("bob")
        XCTAssertEqual(transport.setNicknameCalls.last, "bob")

        mesh.stop()
        XCTAssertEqual(transport.stopCalls, 1)
        XCTAssertTrue(listener.stopped)
    }

    func testSendAndReceiptEndpoints() {
        let noiseService = NoiseEncryptionService(keychain: InMemoryKeychain())
        let transport = FakeTransport(peerID: PeerID(str: "peer-1"), nickname: "anon", noiseService: noiseService)
        let mesh = MeshManager(transport: transport)
        let listener = RecordingListener()
        mesh.setListener(listener)

        let peer = PeerID(str: "peer-2")
        mesh.sendBroadcastMessage("hi", mentions: ["bob"], channel: "room")
        mesh.sendPrivateMessage("secret", to: peer, recipientNickname: "bob", messageID: "msg-1")

        XCTAssertEqual(transport.lastBroadcast?.0, "hi")
        XCTAssertEqual(transport.lastBroadcast?.1, ["bob"])
        XCTAssertEqual(transport.lastPrivate?.0, "secret")
        XCTAssertEqual(transport.lastPrivate?.1, peer)
        XCTAssertEqual(transport.lastPrivate?.2, "bob")
        XCTAssertEqual(transport.lastPrivate?.3, "msg-1")
        XCTAssertEqual(listener.sent.map { $0.0 }, ["msg-1"])

        let receipt = ReadReceipt(originalMessageID: "m1", readerID: peer, readerNickname: "bob")
        mesh.sendReadReceipt(receipt, to: peer)
        mesh.sendDeliveryAck(for: "m2", to: peer)

        XCTAssertEqual(transport.lastReadReceipt?.0.originalMessageID, "m1")
        XCTAssertEqual(transport.lastDeliveryAck?.0, "m2")
    }

    func testHandshakeAndFileEndpoints() {
        let noiseService = NoiseEncryptionService(keychain: InMemoryKeychain())
        let transport = FakeTransport(peerID: PeerID(str: "peer-1"), nickname: "anon", noiseService: noiseService)
        transport.nicknames[PeerID(str: "peer-2")] = "carol"
        let mesh = MeshManager(transport: transport)

        let peer = PeerID(str: "peer-2")
        mesh.establish(peer)
        XCTAssertEqual(transport.lastHandshakePeer, peer)
        XCTAssertFalse(mesh.isEstablished(peer))

        let packet = BitchatFilePacket(fileName: "file.txt", fileSize: 4, mimeType: "text/plain", content: Data([0x01]))
        let expectedTransferId = packet.encode()!.sha256Hex()
        mesh.sendFileBroadcast(packet)
        mesh.sendFilePrivate(packet, to: peer)
        mesh.cancelTransfer("t1")

        XCTAssertEqual(transport.lastBroadcastFile?.0.fileName, "file.txt")
        XCTAssertEqual(transport.lastBroadcastFile?.1, expectedTransferId)
        XCTAssertEqual(transport.lastPrivateFile?.1, peer)
        XCTAssertEqual(transport.lastPrivateFile?.2, expectedTransferId)
        XCTAssertEqual(transport.canceledTransfers, ["t1"])
        XCTAssertEqual(mesh.peerNicknames()[peer], "carol")
    }
}
