import SwiftUI

/// Последние 10 вычислений. Нажатие возвращает выражение в калькулятор.
struct HistoryView: View {
    @EnvironmentObject private var viewModel: CalculatorViewModel
    /// В портрете список стоит над экраном, поэтому новые записи удобнее снизу.
    let newestAtBottom: Bool

    private var items: [HistoryItem] {
        newestAtBottom ? viewModel.history.reversed() : viewModel.history
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("История")
                    .font(.headline)
                Spacer()
                if !viewModel.history.isEmpty {
                    Button("Очистить", role: .destructive) {
                        viewModel.clearHistory()
                    }
                    .font(.caption)
                    .accessibilityLabel("Очистить историю")
                }
            }
            .padding(.horizontal, 4)

            if viewModel.history.isEmpty {
                Text("Пока нет вычислений")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 4) {
                            ForEach(items) { item in
                                HistoryRow(item: item) {
                                    viewModel.restore(item)
                                }
                                .id(item.id)
                            }
                        }
                    }
                    .defaultScrollAnchor(newestAtBottom ? .bottom : .top)
                    // Новая запись всегда видна: прокручиваем к ней при добавлении.
                    .onChange(of: viewModel.history.first?.id) { _, newest in
                        guard let newest else { return }
                        withAnimation { proxy.scrollTo(newest, anchor: newestAtBottom ? .bottom : .top) }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(8)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.secondarySystemBackground).opacity(0.6))
        )
    }
}

private struct HistoryRow: View {
    let item: HistoryItem
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(item.expression)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .truncationMode(.head)
                Spacer(minLength: 4)
                Text("= \(item.result)")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity)
            .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(PressableButtonStyle())
        .accessibilityLabel("\(item.expression) равно \(item.result)")
        .accessibilityHint("Вернуть выражение в калькулятор")
    }
}
