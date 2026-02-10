import Foundation

final class TransferProgressManager {
    static let shared = TransferProgressManager()

    func start(id: String, totalFragments: Int) {}
    func recordFragmentSent(id: String) {}
    func cancel(id: String) {}
}
