import SwiftUI

/// Кнопка клавиатуры. Цвет зависит от типа клавиши.
struct CalculatorButton: View {
    let key: CalculatorKey
    @EnvironmentObject private var viewModel: CalculatorViewModel

    var body: some View {
        Button {
            viewModel.press(key)
        } label: {
            Text(key.label)
                .font(.system(size: fontSize, weight: .medium, design: .rounded))
                .minimumScaleFactor(0.6)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .foregroundStyle(foreground)
                .background(background, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(PressableButtonStyle())
        .accessibilityLabel(key.accessibilityLabel)
    }

    private var fontSize: CGFloat {
        key.kind == .memory ? 17 : 24
    }

    private var background: Color {
        switch key.kind {
        case .digit: return Color(.tertiarySystemFill)
        case .op, .equals: return .accentColor
        case .function: return Color(.systemFill)
        case .memory: return Color.accentColor.opacity(0.15)
        }
    }

    private var foreground: Color {
        switch key.kind {
        case .op, .equals: return .white
        case .memory: return .accentColor
        default: return .primary
        }
    }
}

/// Лёгкое «нажатие»: кнопка чуть уменьшается и тускнеет.
struct PressableButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .opacity(configuration.isPressed ? 0.75 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}
