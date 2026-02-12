import Foundation

struct TransferProgressEvent {
    let transferId: String
    let sent: Int
    let total: Int
    let completed: Bool
}

final class TransferProgressManager {
    static let shared = TransferProgressManager()

    private let queue = DispatchQueue(label: "mesh.transfer.progress")
    private var progress: [String: (sent: Int, total: Int)] = [:]
    private var observers: [UUID: (TransferProgressEvent) -> Void] = [:]

    @discardableResult
    func addObserver(_ observer: @escaping (TransferProgressEvent) -> Void) -> UUID {
        let id = UUID()
        queue.async {
            self.observers[id] = observer
        }
        return id
    }

    func removeObserver(_ id: UUID) {
        queue.async {
            self.observers.removeValue(forKey: id)
        }
    }

    func start(id: String, totalFragments: Int) {
        guard totalFragments > 0 else { return }
        queue.async {
            self.progress[id] = (sent: 0, total: totalFragments)
            self.emit(id: id, sent: 0, total: totalFragments, completed: false)
        }
    }

    func recordFragmentSent(id: String) {
        queue.async {
            guard var state = self.progress[id] else { return }
            state.sent = min(state.sent + 1, state.total)
            self.progress[id] = state
            let completed = state.sent >= state.total
            self.emit(id: id, sent: state.sent, total: state.total, completed: completed)
            if completed {
                self.progress.removeValue(forKey: id)
            }
        }
    }

    func cancel(id: String) {
        queue.async {
            if let state = self.progress[id] {
                self.emit(id: id, sent: state.sent, total: state.total, completed: true)
            }
            self.progress.removeValue(forKey: id)
        }
    }

    private func emit(id: String, sent: Int, total: Int, completed: Bool) {
        let event = TransferProgressEvent(transferId: id, sent: sent, total: total, completed: completed)
        for observer in observers.values {
            observer(event)
        }
    }
}
