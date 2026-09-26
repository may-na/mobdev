import SwiftUI

@main
struct AdvancedCalculatorApp: App {
    // Единственный источник состояния живёт на уровне приложения:
    // поворот экрана пересоздаёт вью, но не ViewModel.
    @StateObject private var viewModel = CalculatorViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(viewModel)
        }
    }
}
