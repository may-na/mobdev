import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var viewModel: CalculatorViewModel

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height

            Group {
                if isLandscape {
                    HStack(spacing: 12) {
                        VStack(spacing: 10) {
                            DisplayView()
                            KeypadView()
                        }
                        HistoryView(newestAtBottom: false)
                            .frame(width: geometry.size.width * 0.32)
                    }
                } else {
                    VStack(spacing: 10) {
                        HistoryView(newestAtBottom: true)
                            .frame(maxHeight: geometry.size.height * 0.26)
                        DisplayView()
                        KeypadView()
                    }
                }
            }
            .padding(12)
        }
        .background(Color(.systemBackground))
    }
}

#Preview("Портрет") {
    ContentView()
        .environmentObject(CalculatorViewModel(store: InMemoryStateStore()))
}

#Preview("Ландшафт", traits: .landscapeLeft) {
    ContentView()
        .environmentObject(CalculatorViewModel(store: InMemoryStateStore()))
}
