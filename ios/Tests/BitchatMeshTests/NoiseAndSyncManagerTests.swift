import CryptoKit
import XCTest
@testable import BitchatMesh

private final class InMemoryKeychain: KeychainManagerProtocol {
    private var store: [String: Data] = [:]

    func saveIdentityKey(_ keyData: Data, forKey key: String) -> Bool {
        store["identity_\(key)"] = keyData
        return true
    }

    func getIdentityKey(forKey key: String) -> Data? {
        store["identity_\(key)"]
    }

    func deleteIdentityKey(forKey key: String) -> Bool {
        store.removeValue(forKey: "identity_\(key)") != nil
    }

    func deleteAllKeychainData() -> Bool {
        store.removeAll()
        return true
    }

    func secureClear(_ data: inout Data) {
        data.removeAll()
    }

    func secureClear(_ string: inout String) {
        string.removeAll()
    }

    func verifyIdentityKeyExists() -> Bool {
        store.keys.contains { $0.hasPrefix("identity_") }
    }

    func getIdentityKeyWithResult(forKey key: String) -> KeychainReadResult {
        if let data = store["identity_\(key)"] {
            return .success(data)
        }
        return .itemNotFound
    }

    func saveIdentityKeyWithResult(_ keyData: Data, forKey key: String) -> KeychainSaveResult {
        store["identity_\(key)"] = keyData
        return .success
    }

    func save(key: String, data: Data, service: String, accessible: CFString?) {
        store["\(service):\(key)"] = data
    }

    func load(key: String, service: String) -> Data? {
        store["\(service):\(key)"]
    }

    func delete(key: String, service: String) {
        store.removeValue(forKey: "\(service):\(key)")
    }
}

final class NoiseEncryptionServiceTests: XCTestCase {
    func testSignVerifyAndAnnounceSignatures() {
        let keychain = InMemoryKeychain()
        let service = NoiseEncryptionService(keychain: keychain)

        let payload = Data("hello".utf8)
        let signature = service.signData(payload)
        XCTAssertNotNil(signature)

        let pubKey = service.getSigningPublicKeyData()
        XCTAssertTrue(service.verifySignature(signature ?? Data(), for: payload, publicKey: pubKey))
        XCTAssertFalse(service.verifySignature(signature ?? Data(), for: Data("bye".utf8), publicKey: pubKey))

        let peerID = Data(repeating: 0x11, count: 8)
        let noiseKey = service.getStaticPublicKeyData()
        let announceSig = service.buildAnnounceSignature(peerID: peerID,
                                                         noiseKey: noiseKey,
                                                         ed25519Key: pubKey,
                                                         nickname: "alice",
                                                         timestampMs: 1234)
        XCTAssertNotNil(announceSig)
        XCTAssertTrue(service.verifyAnnounceSignature(signature: announceSig ?? Data(),
                                                      peerID: peerID,
                                                      noiseKey: noiseKey,
                                                      ed25519Key: pubKey,
                                                      nickname: "alice",
                                                      timestampMs: 1234,
                                                      publicKey: pubKey))
        XCTAssertFalse(service.verifyAnnounceSignature(signature: announceSig ?? Data(),
                                                       peerID: peerID,
                                                       noiseKey: noiseKey,
                                                       ed25519Key: pubKey,
                                                       nickname: "bob",
                                                       timestampMs: 1234,
                                                       publicKey: pubKey))
    }

    func testSignPacketAndVerifySignature() {
        let keychain = InMemoryKeychain()
        let service = NoiseEncryptionService(keychain: keychain)

        let packet = BitchatPacket(
            type: MessageType.message.rawValue,
            senderID: Data(repeating: 0x01, count: 8),
            recipientID: nil,
            timestamp: 1_234,
            payload: Data([0x01, 0x02]),
            signature: nil,
            ttl: 1
        )

        let signed = service.signPacket(packet)
        XCTAssertNotNil(signed?.signature)

        let pubKey = service.getSigningPublicKeyData()
        XCTAssertTrue(service.verifyPacketSignature(signed ?? packet, publicKey: pubKey))
        XCTAssertFalse(service.verifyPacketSignature(packet, publicKey: pubKey))
    }
}

final class NoiseSessionTests: XCTestCase {
    func testStartHandshakeTwiceThrows() throws {
        let keychain = InMemoryKeychain()
        let session = NoiseSession(
            peerID: PeerID(str: "0123456789abcdef"),
            role: .initiator,
            keychain: keychain,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )

        _ = try session.startHandshake()
        XCTAssertThrowsError(try session.startHandshake()) { error in
            XCTAssertEqual(error as? NoiseSessionError, .invalidState)
        }
    }

    func testResponderStartHandshakeReturnsEmpty() throws {
        let keychain = InMemoryKeychain()
        let session = NoiseSession(
            peerID: PeerID(str: "fedcba9876543210"),
            role: .responder,
            keychain: keychain,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )

        let data = try session.startHandshake()
        XCTAssertTrue(data.isEmpty)
    }

    func testEncryptDecryptRoundTrip() throws {
        let keychainA = InMemoryKeychain()
        let keychainB = InMemoryKeychain()
        let sessionA = NoiseSession(
            peerID: PeerID(str: "fedcba9876543210"),
            role: .initiator,
            keychain: keychainA,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )
        let sessionB = NoiseSession(
            peerID: PeerID(str: "0123456789abcdef"),
            role: .responder,
            keychain: keychainB,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )

        let msg1 = try sessionA.startHandshake()
        let msg2 = try sessionB.processHandshakeMessage(msg1)
        let msg3 = try sessionA.processHandshakeMessage(msg2 ?? Data())
        if let msg3 = msg3 {
            _ = try sessionB.processHandshakeMessage(msg3)
        }

        XCTAssertTrue(sessionA.isEstablished())
        XCTAssertTrue(sessionB.isEstablished())

        let plaintext = Data("hello".utf8)
        let ciphertext = try sessionA.encrypt(plaintext)
        let decrypted = try sessionB.decrypt(ciphertext)
        XCTAssertEqual(decrypted, plaintext)

        sessionA.reset()
        XCTAssertThrowsError(try sessionA.encrypt(plaintext)) { error in
            XCTAssertEqual(error as? NoiseSessionError, .notEstablished)
        }
    }

    func testInitiatorProcessHandshakeWithoutStartThrows() {
        let keychain = InMemoryKeychain()
        let session = NoiseSession(
            peerID: PeerID(str: "0123456789abcdef"),
            role: .initiator,
            keychain: keychain,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )

        XCTAssertThrowsError(try session.processHandshakeMessage(Data([0x00]))) { error in
            XCTAssertEqual(error as? NoiseSessionError, .invalidState)
        }
    }

    func testResetAfterDecryptFailure() throws {
        let keychainA = InMemoryKeychain()
        let keychainB = InMemoryKeychain()
        let sessionA = NoiseSession(
            peerID: PeerID(str: "fedcba9876543210"),
            role: .initiator,
            keychain: keychainA,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )
        let sessionB = NoiseSession(
            peerID: PeerID(str: "0123456789abcdef"),
            role: .responder,
            keychain: keychainB,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )

        let msg1 = try sessionA.startHandshake()
        let msg2 = try sessionB.processHandshakeMessage(msg1)
        let msg3 = try sessionA.processHandshakeMessage(msg2 ?? Data())
        if let msg3 = msg3 {
            _ = try sessionB.processHandshakeMessage(msg3)
        }

        let plaintext = Data("hello".utf8)
        let ciphertext = try sessionA.encrypt(plaintext)
        let corrupted = ciphertext + Data([0xFF])

        XCTAssertThrowsError(try sessionB.decrypt(corrupted))
        sessionB.reset()
        XCTAssertThrowsError(try sessionB.decrypt(ciphertext)) { error in
            XCTAssertEqual(error as? NoiseSessionError, .notEstablished)
        }
    }

    func testProcessHandshakeAfterEstablishedThrows() throws {
        let keychainA = InMemoryKeychain()
        let keychainB = InMemoryKeychain()
        let sessionA = NoiseSession(
            peerID: PeerID(str: "fedcba9876543210"),
            role: .initiator,
            keychain: keychainA,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )
        let sessionB = NoiseSession(
            peerID: PeerID(str: "0123456789abcdef"),
            role: .responder,
            keychain: keychainB,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )

        let msg1 = try sessionA.startHandshake()
        let msg2 = try sessionB.processHandshakeMessage(msg1)
        let msg3 = try sessionA.processHandshakeMessage(msg2 ?? Data())
        if let msg3 = msg3 {
            _ = try sessionB.processHandshakeMessage(msg3)
        }

        XCTAssertTrue(sessionA.isEstablished())
        XCTAssertThrowsError(try sessionA.processHandshakeMessage(Data([0x01]))) { error in
            XCTAssertEqual(error as? NoiseSessionError, .invalidState)
        }
    }

    func testResetAllowsHandshakeRestart() throws {
        let keychainA = InMemoryKeychain()
        let keychainB = InMemoryKeychain()
        let sessionA = NoiseSession(
            peerID: PeerID(str: "fedcba9876543210"),
            role: .initiator,
            keychain: keychainA,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )
        let sessionB = NoiseSession(
            peerID: PeerID(str: "0123456789abcdef"),
            role: .responder,
            keychain: keychainB,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )

        let msg1 = try sessionA.startHandshake()
        let msg2 = try sessionB.processHandshakeMessage(msg1)
        let msg3 = try sessionA.processHandshakeMessage(msg2 ?? Data())
        if let msg3 = msg3 {
            _ = try sessionB.processHandshakeMessage(msg3)
        }

        sessionA.reset()
        sessionB.reset()

        let retry1 = try sessionA.startHandshake()
        let retry2 = try sessionB.processHandshakeMessage(retry1)
        _ = try sessionA.processHandshakeMessage(retry2 ?? Data())
    }
}

final class SecureNoiseSessionTests: XCTestCase {
    func testNeedsRenegotiationThresholds() {
        let keychain = InMemoryKeychain()
        let session = SecureNoiseSession(
            peerID: PeerID(str: "0123456789abcdef"),
            role: .initiator,
            keychain: keychain,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )

        XCTAssertFalse(session.needsRenegotiation())

        session.setMessageCountForTesting(NoiseSecurityConstants.maxMessagesPerSession)
        XCTAssertTrue(session.needsRenegotiation())

        session.setMessageCountForTesting(0)
        let old = Date(timeIntervalSinceNow: -(NoiseSecurityConstants.sessionTimeout + 1))
        session.setLastActivityTimeForTesting(old)
        XCTAssertTrue(session.needsRenegotiation())
    }

    func testRejectsOversizedMessages() {
        let keychain = InMemoryKeychain()
        let session = SecureNoiseSession(
            peerID: PeerID(str: "0123456789abcdef"),
            role: .initiator,
            keychain: keychain,
            localStaticKey: Curve25519.KeyAgreement.PrivateKey()
        )

        let big = Data(repeating: 0x00, count: NoiseSecurityConstants.maxMessageSize + 1)
        XCTAssertThrowsError(try session.encrypt(big)) { error in
            XCTAssertTrue(error is NoiseSecurityError)
        }
        XCTAssertThrowsError(try session.decrypt(big)) { error in
            XCTAssertTrue(error is NoiseSecurityError)
        }
    }
}

final class NoiseSessionManagerTests: XCTestCase {
    func testInitiateAndRemoveSession() throws {
        let keychain = InMemoryKeychain()
        let manager = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychain)
        let peer = PeerID(str: "0123456789abcdef")

        let data = try manager.initiateHandshake(with: peer)
        XCTAssertFalse(data.isEmpty)
        XCTAssertNotNil(manager.getSession(for: peer))

        manager.removeSession(for: peer)
        XCTAssertNil(manager.getSession(for: peer))
    }

    func testHandshakeExchangeEstablishesSessions() throws {
        let keychainA = InMemoryKeychain()
        let keychainB = InMemoryKeychain()
        let managerA = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainA)
        let managerB = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainB)
        let peerA = PeerID(str: "0123456789abcdef")
        let peerB = PeerID(str: "fedcba9876543210")

        let msg1 = try managerA.initiateHandshake(with: peerB)
        let msg2 = try managerB.handleIncomingHandshake(from: peerA, message: msg1)
        let msg3 = try managerA.handleIncomingHandshake(from: peerB, message: msg2 ?? Data())
        if let msg3 = msg3 {
            _ = try managerB.handleIncomingHandshake(from: peerA, message: msg3)
        }

        XCTAssertTrue(managerA.getSession(for: peerB)?.isEstablished() ?? false)
        XCTAssertTrue(managerB.getSession(for: peerA)?.isEstablished() ?? false)
    }

    func testSessionsNeedingRekey() throws {
        let keychainA = InMemoryKeychain()
        let keychainB = InMemoryKeychain()
        let managerA = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainA)
        let managerB = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainB)
        let peerA = PeerID(str: "0123456789abcdef")
        let peerB = PeerID(str: "fedcba9876543210")

        let msg1 = try managerA.initiateHandshake(with: peerB)
        let msg2 = try managerB.handleIncomingHandshake(from: peerA, message: msg1)
        let msg3 = try managerA.handleIncomingHandshake(from: peerB, message: msg2 ?? Data())
        if let msg3 = msg3 {
            _ = try managerB.handleIncomingHandshake(from: peerA, message: msg3)
        }

        guard let session = managerA.getSession(for: peerB) as? SecureNoiseSession else {
            XCTFail("Missing session")
            return
        }
        session.setMessageCountForTesting(NoiseSecurityConstants.maxMessagesPerSession)
        let needing = managerA.getSessionsNeedingRekey()
        XCTAssertEqual(needing.first?.peerID, peerB)
        XCTAssertTrue(needing.first?.needsRekey ?? false)
    }

    func testEstablishedSessionReplacedByNewHandshake() throws {
        let keychainA = InMemoryKeychain()
        let keychainB = InMemoryKeychain()
        let managerA = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainA)
        let managerB = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainB)
        let peerA = PeerID(str: "0123456789abcdef")
        let peerB = PeerID(str: "fedcba9876543210")

        let msg1 = try managerA.initiateHandshake(with: peerB)
        let msg2 = try managerB.handleIncomingHandshake(from: peerA, message: msg1)
        let msg3 = try managerA.handleIncomingHandshake(from: peerB, message: msg2 ?? Data())
        if let msg3 = msg3 {
            _ = try managerB.handleIncomingHandshake(from: peerA, message: msg3)
        }

        let keychainC = InMemoryKeychain()
        let managerC = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainC)
        let newInit = try managerC.initiateHandshake(with: peerA)
        _ = try managerA.handleIncomingHandshake(from: peerB, message: newInit)

        XCTAssertNotNil(managerA.getSession(for: peerB))
    }

    func testHandshakingSessionResetsOnFreshInitiation() throws {
        let keychain = InMemoryKeychain()
        let manager = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychain)
        let peer = PeerID(str: "0123456789abcdef")

        _ = try manager.initiateHandshake(with: peer)

        do {
            _ = try manager.handleIncomingHandshake(from: peer, message: Data(repeating: 0x00, count: 32))
        } catch {
            // Expected failure; session should be cleared.
        }

        XCTAssertNil(manager.getSession(for: peer))
    }

    func testEncryptDecryptRoundTripAfterHandshake() throws {
        let keychainA = InMemoryKeychain()
        let keychainB = InMemoryKeychain()
        let managerA = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainA)
        let managerB = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainB)
        let peerA = PeerID(str: "0123456789abcdef")
        let peerB = PeerID(str: "fedcba9876543210")

        let msg1 = try managerA.initiateHandshake(with: peerB)
        let msg2 = try managerB.handleIncomingHandshake(from: peerA, message: msg1)
        let msg3 = try managerA.handleIncomingHandshake(from: peerB, message: msg2 ?? Data())
        if let msg3 = msg3 {
            _ = try managerB.handleIncomingHandshake(from: peerA, message: msg3)
        }

        let plaintext = Data("hello".utf8)
        let ciphertext = try managerA.encrypt(plaintext, for: peerB)
        let decrypted = try managerB.decrypt(ciphertext, from: peerA)
        XCTAssertEqual(decrypted, plaintext)
    }

    func testInitiateHandshakeThrowsWhenAlreadyEstablished() throws {
        let keychainA = InMemoryKeychain()
        let keychainB = InMemoryKeychain()
        let managerA = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainA)
        let managerB = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychainB)
        let peerA = PeerID(str: "0123456789abcdef")
        let peerB = PeerID(str: "fedcba9876543210")

        let msg1 = try managerA.initiateHandshake(with: peerB)
        let msg2 = try managerB.handleIncomingHandshake(from: peerA, message: msg1)
        let msg3 = try managerA.handleIncomingHandshake(from: peerB, message: msg2 ?? Data())
        if let msg3 = msg3 {
            _ = try managerB.handleIncomingHandshake(from: peerA, message: msg3)
        }

        XCTAssertThrowsError(try managerA.initiateHandshake(with: peerB)) { error in
            XCTAssertEqual(error as? NoiseSessionError, .alreadyEstablished)
        }
    }

    func testEncryptDecryptWithoutSessionThrows() {
        let keychain = InMemoryKeychain()
        let manager = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychain)
        let peer = PeerID(str: "0123456789abcdef")

        XCTAssertThrowsError(try manager.encrypt(Data([0x01]), for: peer)) { error in
            XCTAssertEqual(error as? NoiseSessionError, .sessionNotFound)
        }
        XCTAssertThrowsError(try manager.decrypt(Data([0x01]), from: peer)) { error in
            XCTAssertEqual(error as? NoiseSessionError, .sessionNotFound)
        }
    }

    func testHandleIncomingHandshakeFailureRemovesSession() {
        let keychain = InMemoryKeychain()
        let manager = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychain)
        let peer = PeerID(str: "0123456789abcdef")

        XCTAssertThrowsError(try manager.handleIncomingHandshake(from: peer, message: Data()))
        XCTAssertNil(manager.getSession(for: peer))
    }

    func testMidFlightHandshakeFailureClearsSession() throws {
        let keychain = InMemoryKeychain()
        let manager = NoiseSessionManager(localStaticKey: Curve25519.KeyAgreement.PrivateKey(), keychain: keychain)
        let peer = PeerID(str: "0123456789abcdef")

        _ = try manager.initiateHandshake(with: peer)
        XCTAssertNotNil(manager.getSession(for: peer))

        XCTAssertThrowsError(try manager.handleIncomingHandshake(from: peer, message: Data([0x00, 0x01, 0x02])))
        XCTAssertNil(manager.getSession(for: peer))
    }
}

final class NoiseEncryptionServiceErrorTests: XCTestCase {
    func testEncryptRequiresHandshake() {
        let keychain = InMemoryKeychain()
        let service = NoiseEncryptionService(keychain: keychain)
        let peer = PeerID(str: "0123456789abcdef")
        var requested = false
        service.onHandshakeRequired = { _ in requested = true }

        XCTAssertThrowsError(try service.encrypt(Data([0x01, 0x02]), for: peer)) { error in
            XCTAssertTrue(error is NoiseEncryptionError)
        }
        XCTAssertTrue(requested)
    }

    func testDecryptWithoutSessionThrows() {
        let keychain = InMemoryKeychain()
        let service = NoiseEncryptionService(keychain: keychain)
        let peer = PeerID(str: "0123456789abcdef")

        XCTAssertThrowsError(try service.decrypt(Data([0x01]), from: peer)) { error in
            XCTAssertTrue(error is NoiseEncryptionError)
        }
    }

    func testInitiateHandshakeRejectsInvalidPeer() {
        let keychain = InMemoryKeychain()
        let service = NoiseEncryptionService(keychain: keychain)
        let invalid = PeerID(str: "zzzzzzzzzzzzzzzz")

        XCTAssertThrowsError(try service.initiateHandshake(with: invalid)) { error in
            XCTAssertTrue(error is NoiseSecurityError)
        }
    }

    func testProcessHandshakeRejectsOversizedMessage() {
        let keychain = InMemoryKeychain()
        let service = NoiseEncryptionService(keychain: keychain)
        let peer = PeerID(str: "0123456789abcdef")
        let oversized = Data(repeating: 0x00, count: NoiseSecurityConstants.maxHandshakeMessageSize + 1)

        XCTAssertThrowsError(try service.processHandshakeMessage(from: peer, message: oversized)) { error in
            XCTAssertTrue(error is NoiseSecurityError)
        }
    }
}

final class VerificationServiceFailureTests: XCTestCase {
    func testVerifyScannedQRRejectsExpiredAndBadSignature() {
        let keychain = InMemoryKeychain()
        let noise = NoiseEncryptionService(keychain: keychain)
        let service = VerificationService.shared
        service.configure(with: noise)

        let now = Int64(Date().timeIntervalSince1970)
        let base = VerificationService.VerificationQR(
            v: 1,
            noiseKeyHex: noise.getStaticPublicKeyData().hexEncodedString(),
            signKeyHex: noise.getSigningPublicKeyData().hexEncodedString(),
            npub: nil,
            nickname: "alice",
            ts: now - 120,
            nonceB64: "nonce",
            sigHex: ""
        )
        let sig = noise.signData(base.canonicalBytes())?.hexEncodedString() ?? ""
        let expired = VerificationService.VerificationQR(
            v: base.v,
            noiseKeyHex: base.noiseKeyHex,
            signKeyHex: base.signKeyHex,
            npub: base.npub,
            nickname: base.nickname,
            ts: base.ts,
            nonceB64: base.nonceB64,
            sigHex: sig
        )
        XCTAssertNil(service.verifyScannedQR(expired.toURLString(), maxAge: 60))

        let badSig = VerificationService.VerificationQR(
            v: base.v,
            noiseKeyHex: base.noiseKeyHex,
            signKeyHex: base.signKeyHex,
            npub: base.npub,
            nickname: base.nickname,
            ts: now,
            nonceB64: base.nonceB64,
            sigHex: "00"
        )
        XCTAssertNil(service.verifyScannedQR(badSig.toURLString()))
    }
}

final class NoiseMessageTests: XCTestCase {
    func testNoiseMessageCodableRoundTrip() {
        let message = NoiseMessage(type: .handshakeInitiation, sessionID: UUID().uuidString, payload: Data([0x01, 0x02]))
        let data = message.encode()
        XCTAssertNotNil(data)
        let decoded = NoiseMessage.decode(from: data ?? Data())
        XCTAssertEqual(decoded?.type, message.type)
        XCTAssertEqual(decoded?.payload, message.payload)
    }

    func testNoiseMessageBinaryRoundTrip() {
        let message = NoiseMessage(type: .encryptedMessage, sessionID: UUID().uuidString, payload: Data([0xAA, 0xBB]))
        let data = message.toBinaryData()
        let decoded = NoiseMessage.fromBinaryData(data)
        XCTAssertEqual(decoded?.type, message.type)
        XCTAssertEqual(decoded?.payload, message.payload)
    }
}

final class VerificationServiceTests: XCTestCase {
    func testVerifyChallengeAndResponseParsing() {
        let keychain = InMemoryKeychain()
        let noise = NoiseEncryptionService(keychain: keychain)
        let service = VerificationService.shared
        service.configure(with: noise)

        let nonce = Data([0x01, 0x02, 0x03])
        let challenge = service.buildVerifyChallenge(noiseKeyHex: "deadbeef", nonceA: nonce)
        let parsedChallenge = service.parseVerifyChallenge(NoisePayload.decode(challenge)?.data ?? Data())
        XCTAssertEqual(parsedChallenge?.noiseKeyHex, "deadbeef")
        XCTAssertEqual(parsedChallenge?.nonceA, nonce)

        let response = service.buildVerifyResponse(noiseKeyHex: "deadbeef", nonceA: nonce)
        XCTAssertNotNil(response)
        let payload = NoisePayload.decode(response ?? Data())?.data ?? Data()
        let parsedResponse = service.parseVerifyResponse(payload)
        XCTAssertEqual(parsedResponse?.noiseKeyHex, "deadbeef")
        XCTAssertEqual(parsedResponse?.nonceA, nonce)
        XCTAssertNotNil(parsedResponse?.signature)

        let ok = service.verifyResponseSignature(noiseKeyHex: "deadbeef",
                                                nonceA: nonce,
                                                signature: parsedResponse?.signature ?? Data(),
                                                signerPublicKeyHex: noise.getSigningPublicKeyData().hexEncodedString())
        XCTAssertTrue(ok)
    }

    func testQRBuildAndVerify() {
        let keychain = InMemoryKeychain()
        let noise = NoiseEncryptionService(keychain: keychain)
        let service = VerificationService.shared
        service.configure(with: noise)

        let qrString = service.buildMyQRString(nickname: "alice", npub: "npub1")
        XCTAssertNotNil(qrString)
        let verified = service.verifyScannedQR(qrString ?? "")
        XCTAssertNotNil(verified)
        XCTAssertEqual(verified?.nickname, "alice")
    }
}

private final class GossipSyncDelegate: GossipSyncManager.Delegate {
    var sentBroadcast: [BitchatPacket] = []
    var sentToPeer: [(PeerID, BitchatPacket)] = []
    var connectedPeers: [PeerID] = []

    func sendPacket(_ packet: BitchatPacket) {
        sentBroadcast.append(packet)
    }

    func sendPacket(to peerID: PeerID, packet: BitchatPacket) {
        sentToPeer.append((peerID, packet))
    }

    func signPacketForBroadcast(_ packet: BitchatPacket) -> BitchatPacket {
        packet
    }

    func getConnectedPeers() -> [PeerID] {
        connectedPeers
    }
}

final class GossipSyncManagerTests: XCTestCase {
    func testRequestSyncRespondsWithMissingPackets() {
        let requestManager = RequestSyncManager()
        let manager = GossipSyncManager(myPeerID: PeerID(str: "a1b2c3d4"), requestSyncManager: requestManager)
        let delegate = GossipSyncDelegate()
        manager.delegate = delegate

        let packet = BitchatPacket(
            type: MessageType.message.rawValue,
            senderID: Data(repeating: 0x01, count: 8),
            recipientID: nil,
            timestamp: UInt64(Date().timeIntervalSince1970 * 1000),
            payload: Data([0x01]),
            signature: nil,
            ttl: 3
        )

        let storeExp = expectation(description: "store packet")
        manager.onPublicPacketSeen(packet)
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) {
            storeExp.fulfill()
        }
        wait(for: [storeExp], timeout: 1.0)

        let request = RequestSyncPacket(p: 1, m: 1, data: Data(), types: .publicMessages)
        let responseExp = expectation(description: "response")
        manager.handleRequestSync(from: PeerID(str: "deadbeef"), request: request)
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) {
            responseExp.fulfill()
        }
        wait(for: [responseExp], timeout: 1.0)

        XCTAssertEqual(delegate.sentToPeer.count, 1)
        XCTAssertTrue(delegate.sentToPeer.first?.1.isRSR ?? false)
        XCTAssertEqual(delegate.sentToPeer.first?.1.ttl, 0)
    }

    func testCleanupRemovesStaleAnnouncements() {
        let requestManager = RequestSyncManager()
        var config = GossipSyncManager.Config()
        config.stalePeerTimeoutSeconds = 1
        let manager = GossipSyncManager(myPeerID: PeerID(str: "a1b2c3d4"), config: config, requestSyncManager: requestManager)

        let oldTimestamp = UInt64((Date().timeIntervalSince1970 - 60) * 1000)
        let announce = BitchatPacket(
            type: MessageType.announce.rawValue,
            senderID: Data(repeating: 0x02, count: 8),
            recipientID: nil,
            timestamp: oldTimestamp,
            payload: Data([0x01]),
            signature: nil,
            ttl: 0
        )

        let storeExp = expectation(description: "store announce")
        manager.onPublicPacketSeen(announce)
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.05) {
            storeExp.fulfill()
        }
        wait(for: [storeExp], timeout: 1.0)

        manager._performMaintenanceSynchronously(now: Date())
        XCTAssertFalse(manager._hasAnnouncement(for: PeerID(hexData: announce.senderID)))
    }
}
