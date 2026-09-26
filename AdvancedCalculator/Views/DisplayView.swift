import SwiftUI

/// Экран калькулятора: память, выражение, результат и ошибка.
struct DisplayView: View {
    @EnvironmentObject private var viewModel: CalculatorViewModel

    var body: some View {
        VStack(alignment: .trailing, spacing: 6) {
            HStack {
                if let memory = viewModel.memoryText {
                    Label("M = \(memory)", systemImage: "memorychip")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.tint)
                        .accessibilityLabel("Память: \(memory)")
                }
                Spacer()
                if viewModel.isShowingResult, let previous = viewModel.history.first {
                    Text("\(previous.expression) =")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .truncationMode(.head)
                }
            }
            .frame(minHeight: 18)

            ScrollView(.horizontal, showsIndicators: false) {
                Text(viewModel.displayText)
                    .font(.system(size: 44, weight: .light, design: .rounded))
                    .foregroundStyle(viewModel.hasError ? Color.red : Color.primary)
                    .lineLimit(1)
                    .padding(.vertical, 2)
                    .accessibilityLabel("Выражение: \(viewModel.displayText)")
            }
            .defaultScrollAnchor(.trailing)

            if let error = viewModel.errorMessage {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.red)
                    .transition(.opacity)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .trailing)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(viewModel.hasError ? Color.red.opacity(0.6) : Color.clear, lineWidth: 1.5)
        )
        .animation(.easeInOut(duration: 0.15), value: viewModel.errorMessage)
    }
}
