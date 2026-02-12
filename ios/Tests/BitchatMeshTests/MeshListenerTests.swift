import XCTest
import CoreBluetooth
@testable import BitchatMesh

final class MeshListenerTests: XCTestCase {
    private final class RecordingListener: MeshListener {
        var messages: [BitchatMessage] = []
        var received: [BitchatMessage] = []
        var peerListUpdates: [[PeerID]] = []
        var found: [PeerID] = []
        var lost: [PeerID] = []
        var connected: [PeerID] = []
        var disconnected: [PeerID] = []
        var established: [PeerID] = []
        var rssi: [(PeerID, Int)] = []
        var started = false
        var stopped = false
        var sent: [(String?, String?)] = []
        var deliveryAcks: [(String, String)] = []
        var readReceipts: [(String, String)] = []
        var verifyChallenges: [PeerID] = []
        var verifyResponses: [PeerID] = []
        var bluetoothStates: [CBManagerState] = []
        var publicMessages: [(PeerID, String, String)] = []
        var noisePayloads: [(PeerID, NoisePayloadType)] = []
        var transferEvents: [TransferProgressEvent] = []
        var filesReceived: [(PeerID, String)] = []
        var incomingExpectation: XCTestExpectation?
        var progressExpectation: XCTestExpectation?

        func onMessageReceived(_ message: BitchatMessage) {
            messages.append(message)
        }

        func onReceived(_ message: BitchatMessage) {
            received.append(message)
        }

        func onSent(messageID: String?, recipientPeerID: String?) {
            sent.append((messageID, recipientPeerID))
        }

        func onPeerListUpdated(_ peers: [PeerID]) {
            peerListUpdates.append(peers)
        }

        func onFound(_ peerID: PeerID) {
            found.append(peerID)
        }

        func onLost(_ peerID: PeerID) {
            lost.append(peerID)
        }

        func onPeerConnected(_ peerID: PeerID) {
            connected.append(peerID)
        }

        func onPeerDisconnected(_ peerID: PeerID) {
            disconnected.append(peerID)
        }

        func onEstablished(_ peerID: PeerID) {
            established.append(peerID)
        }

        func onRSSIUpdated(peerID: PeerID, rssi: Int) {
            self.rssi.append((peerID, rssi))
        }

        func onStarted() {
            started = true
        }

        func onStopped() {
            stopped = true
        }

        func onDeliveryAck(messageID: String, recipientNickname: String, timestamp: Date) {
            deliveryAcks.append((messageID, recipientNickname))
        }

        func onReadReceipt(messageID: String, recipientNickname: String, timestamp: Date) {
            readReceipts.append((messageID, recipientNickname))
        }

        func onVerifyChallenge(peerID: PeerID, payload: Data, timestamp: Date) {
            verifyChallenges.append(peerID)
        }

        func onVerifyResponse(peerID: PeerID, payload: Data, timestamp: Date) {
            verifyResponses.append(peerID)
        }

        func onBluetoothStateUpdated(_ state: CBManagerState) {
            bluetoothStates.append(state)
        }

        func onPublicMessageReceived(from peerID: PeerID, nickname: String, content: String, timestamp: Date, messageID: String?) {
            publicMessages.append((peerID, nickname, content))
        }

        func onNoisePayloadReceived(from peerID: PeerID, type: NoisePayloadType, payload: Data, timestamp: Date) {
            noisePayloads.append((peerID, type))
        }

        func onTransferProgress(transferId: String, sent: Int, total: Int, completed: Bool) {
            transferEvents.append(TransferProgressEvent(transferId: transferId, sent: sent, total: total, completed: completed))
            progressExpectation?.fulfill()
        }

        func onFileReceived(peerID: PeerID, fileName: String, fileSize: Int, mimeType: String, localURL: URL) {
            filesReceived.append((peerID, fileName))
            incomingExpectation?.fulfill()
        }
    }

    func testMeshListenerEndpoints() {
        let mesh = MeshManager()
        let listener = RecordingListener()
        mesh.listener = listener

        let peerA = PeerID(str: "peerA")
        let peerB = PeerID(str: "peerB")
        let message = BitchatMessage(sender: "alice", content: "hi", timestamp: Date(), isRelay: false)
        mesh.didReceiveMessage(message)
        XCTAssertEqual(listener.messages.count, 1)
        XCTAssertEqual(listener.received.count, 1)

        mesh.sendPrivateMessage("secret", to: peerA, recipientNickname: "bob", messageID: "msg-1")
        XCTAssertEqual(listener.sent.map { $0.0 }, ["msg-1"])
        mesh.didUpdatePeerList([peerA, peerB])
        XCTAssertEqual(listener.peerListUpdates.count, 1)

        mesh.didConnectToPeer(peerA)
        mesh.didDisconnectFromPeer(peerA)
        XCTAssertEqual(listener.found, [peerA])
        XCTAssertEqual(listener.connected, [peerA])
        XCTAssertEqual(listener.lost, [peerA])
        XCTAssertEqual(listener.disconnected, [peerA])

        let delivered = DeliveryStatus.delivered(to: "bob", at: Date())
        let read = DeliveryStatus.read(by: "bob", at: Date())
        mesh.didUpdateMessageDeliveryStatus("msg-1", status: delivered)
        mesh.didUpdateMessageDeliveryStatus("msg-2", status: read)
        XCTAssertEqual(listener.deliveryAcks.map { $0.0 }, ["msg-1"])
        XCTAssertEqual(listener.readReceipts.map { $0.0 }, ["msg-2"])

        mesh.didReceiveNoisePayload(from: peerA, type: .verifyChallenge, payload: Data([0x01]), timestamp: Date())
        mesh.didReceiveNoisePayload(from: peerA, type: .verifyResponse, payload: Data([0x02]), timestamp: Date())
        XCTAssertEqual(listener.verifyChallenges, [peerA])
        XCTAssertEqual(listener.verifyResponses, [peerA])
        XCTAssertEqual(listener.noisePayloads.map { $0.1 }, [.verifyChallenge, .verifyResponse])

        mesh.didUpdateBluetoothState(.poweredOn)
        XCTAssertEqual(listener.bluetoothStates, [.poweredOn])

        mesh.didReceivePublicMessage(from: peerB, nickname: "bob", content: "hello", timestamp: Date(), messageID: "m1")
        XCTAssertEqual(listener.publicMessages.count, 1)

        listener.incomingExpectation = expectation(description: "incoming file")
        mesh.didReceiveFileTransfer(peerID: peerA, fileName: "file.txt", fileSize: 12, mimeType: "text/plain", localURL: URL(fileURLWithPath: "/tmp/file.txt"))
        wait(for: [listener.incomingExpectation!], timeout: 1.0)
        XCTAssertEqual(listener.filesReceived.count, 1)
    }

    func testTransferProgressForwarding() {
        let mesh = MeshManager()
        let listener = RecordingListener()
        mesh.listener = listener

        listener.progressExpectation = expectation(description: "progress")
        listener.progressExpectation?.expectedFulfillmentCount = 3

        TransferProgressManager.shared.start(id: "t1", totalFragments: 2)
        TransferProgressManager.shared.recordFragmentSent(id: "t1")
        TransferProgressManager.shared.recordFragmentSent(id: "t1")

        wait(for: [listener.progressExpectation!], timeout: 2.0)
        XCTAssertEqual(listener.transferEvents.count, 3)
    }

    func testStartStopCallbacks() {
        let mesh = MeshManager()
        let listener = RecordingListener()
        mesh.listener = listener

        mesh.start(nickname: "tester")
        mesh.stop()

        XCTAssertTrue(listener.started)
        XCTAssertTrue(listener.stopped)
    }
}
