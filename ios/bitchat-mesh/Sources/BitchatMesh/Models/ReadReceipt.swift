//
// ReadReceipt.swift
// bitchat
//
// This is free and unencumbered software released into the public domain.
// For more information, see <https://unlicense.org>
//

import Foundation

public struct ReadReceipt: Codable {
    public let originalMessageID: String
    public let receiptID: String
    public var readerID: PeerID  // Who read it
    public let readerNickname: String
    public let timestamp: Date
    
    public init(originalMessageID: String, readerID: PeerID, readerNickname: String) {
        self.originalMessageID = originalMessageID
        self.receiptID = UUID().uuidString
        self.readerID = readerID
        self.readerNickname = readerNickname
        self.timestamp = Date()
    }
    
    // For binary decoding
    private init(originalMessageID: String, receiptID: String, readerID: PeerID, readerNickname: String, timestamp: Date) {
        self.originalMessageID = originalMessageID
        self.receiptID = receiptID
        self.readerID = readerID
        self.readerNickname = readerNickname
        self.timestamp = timestamp
    }
    
    public func encode() -> Data? {
        try? JSONEncoder().encode(self)
    }
    
    public static func decode(from data: Data) -> ReadReceipt? {
        try? JSONDecoder().decode(ReadReceipt.self, from: data)
    }
    
    // MARK: - Binary Encoding
    
    public func toBinaryData() -> Data {
        var data = Data()
        data.appendUUID(originalMessageID)
        data.appendUUID(receiptID)
        // ReaderID as 8-byte hex string
        var readerData = Data()
        var tempID = readerID.id
        while tempID.count >= 2 && readerData.count < 8 {
            let hexByte = String(tempID.prefix(2))
            if let byte = UInt8(hexByte, radix: 16) {
                readerData.append(byte)
            }
            tempID = String(tempID.dropFirst(2))
        }
        while readerData.count < 8 {
            readerData.append(0)
        }
        data.append(readerData)
        data.appendDate(timestamp)
        data.appendString(readerNickname)
        return data
    }
    
    public static func fromBinaryData(_ data: Data) -> ReadReceipt? {
        // Create defensive copy
        let dataCopy = Data(data)
        
        // Minimum size: 2 UUIDs (32) + readerID (8) + timestamp (8) + min nickname
        guard dataCopy.count >= 49 else { return nil }
        
        var offset = 0
        
        guard let originalMessageID = dataCopy.readUUID(at: &offset),
              let receiptID = dataCopy.readUUID(at: &offset) else { return nil }
        
        guard let readerIDData = dataCopy.readFixedBytes(at: &offset, count: 8) else { return nil }
        let readerID = PeerID(hexData: readerIDData)
        guard readerID.isValid else { return nil }
        
        guard let timestamp = dataCopy.readDate(at: &offset),
              InputValidator.validateTimestamp(timestamp),
              let readerNicknameRaw = dataCopy.readString(at: &offset),
              let readerNickname = InputValidator.validateNickname(readerNicknameRaw) else { return nil }
        
        return ReadReceipt(originalMessageID: originalMessageID,
                          receiptID: receiptID,
                          readerID: readerID,
                          readerNickname: readerNickname,
                          timestamp: timestamp)
    }
}
