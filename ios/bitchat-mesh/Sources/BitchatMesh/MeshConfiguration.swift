import Foundation

public struct MeshConfiguration {
    public var keychainService: String
    public var keychainAccessGroup: String?

    public init(
        keychainService: String = Bundle.main.bundleIdentifier ?? "com.permissionless.bitchat.mesh",
        keychainAccessGroup: String? = nil
    ) {
        self.keychainService = keychainService
        self.keychainAccessGroup = keychainAccessGroup
    }

    public static let `default` = MeshConfiguration()
}
