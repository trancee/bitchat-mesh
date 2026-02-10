import SwiftUI
import BitchatMesh

@main
struct MeshSampleApp: App {
    @StateObject private var model = MeshSampleModel()

    var body: some Scene {
        WindowGroup {
            ContentView(model: model)
        }
    }
}
