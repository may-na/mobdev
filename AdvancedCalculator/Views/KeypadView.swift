import SwiftUI

/// Клавиатура: строка памяти + сетка 5×5.
struct KeypadView: View {
    private let memoryRow: [CalculatorKey] = [.memoryClear, .memoryRecall, .memorySubtract, .memoryAdd]

    private let rows: [[CalculatorKey]] = [
        [.clear, .backspace, .leftParen, .rightParen, .divide],
        [.digit(7), .digit(8), .digit(9), .percent, .multiply],
        [.digit(4), .digit(5), .digit(6), .toggleSign, .subtract],
        [.digit(1), .digit(2), .digit(3), .decimalPoint, .add],
    ]

    private let spacing: CGFloat = 8

    var body: some View {
        VStack(spacing: spacing) {
            HStack(spacing: spacing) {
                ForEach(memoryRow, id: \.self) { key in
                    CalculatorButton(key: key)
                        .frame(height: 40)
                }
            }

            Grid(horizontalSpacing: spacing, verticalSpacing: spacing) {
                ForEach(rows.indices, id: \.self) { rowIndex in
                    GridRow {
                        ForEach(rows[rowIndex], id: \.self) { key in
                            CalculatorButton(key: key)
                        }
                    }
                }
                GridRow {
                    CalculatorButton(key: .digit(0))
                        .gridCellColumns(4)
                    CalculatorButton(key: .equals)
                }
            }
        }
    }
}
