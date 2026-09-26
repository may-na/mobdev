import Foundation
import Combine
import CalculatorEngine

/// Связывает ввод пользователя, движок вычислений и хранилище состояния.
/// Не знает ничего про SwiftUI-вью, поэтому тестируется отдельно.
@MainActor
final class CalculatorViewModel: ObservableObject {
    static let historyLimit = 10

    @Published private(set) var state: CalculatorState {
        didSet { store.save(state) }
    }

    private let store: CalculatorStateStore

    init(store: CalculatorStateStore = UserDefaultsStateStore()) {
        self.store = store
        self.state = store.load() ?? CalculatorState()
    }

    // MARK: - Удобные геттеры для вью

    var expression: String { state.expression }
    var displayText: String { state.expression.isEmpty ? "0" : state.expression }
    var history: [HistoryItem] { state.history }
    var memory: Double? { state.memory }
    var memoryText: String? { state.memory.map(CalculatorEngine.format) }
    var lastResult: String? { state.lastResult }
    var errorMessage: String? { state.errorMessage }
    var isShowingResult: Bool { state.isShowingResult }
    var hasError: Bool { state.errorMessage != nil }

    // MARK: - Обработка нажатий

    func press(_ key: CalculatorKey) {
        switch key {
        case .digit(let d):
            beginEditing(replacingResult: true)
            state.expression += String(d)

        case .decimalPoint:
            beginEditing(replacingResult: true)
            // "5 + ." → "5 + 0."; повторная точка в числе не блокируется — движок сообщит об ошибке.
            state.expression += trailingNumber(in: state.expression).isEmpty ? "0." : "."

        case .add:
            appendOperator("+")
        case .multiply:
            appendOperator("×")
        case .divide:
            appendOperator("÷")

        case .subtract:
            beginEditing(replacingResult: false)
            // В начале выражения и после «(» минус унарный: "-5", "(-5)".
            if state.expression.isEmpty || state.expression.hasSuffix("(") {
                state.expression += "-"
            } else {
                state.expression += " − "
            }

        case .leftParen:
            beginEditing(replacingResult: true)
            state.expression += "("

        case .rightParen:
            beginEditing(replacingResult: false)
            state.expression += ")"

        case .percent:
            beginEditing(replacingResult: false)
            state.expression += "%"

        case .toggleSign:
            beginEditing(replacingResult: false)
            state.expression = Self.toggledSign(of: state.expression)

        case .clear:
            state.expression = ""
            state.errorMessage = nil
            state.isShowingResult = false

        case .backspace:
            state.errorMessage = nil
            if state.isShowingResult {
                // Результат не «набран» — стираем целиком.
                state.expression = ""
                state.isShowingResult = false
            } else if state.expression.hasSuffix(" ") {
                state.expression.removeLast(3) // " + "
            } else if !state.expression.isEmpty {
                state.expression.removeLast()
            }

        case .equals:
            evaluate()

        case .memoryAdd:
            guard let value = currentValue() else { return }
            state.memory = (state.memory ?? 0) + value

        case .memorySubtract:
            guard let value = currentValue() else { return }
            state.memory = (state.memory ?? 0) - value

        case .memoryRecall:
            insertNumber(state.memory ?? 0)

        case .memoryClear:
            state.memory = nil
        }
    }

    /// Нажатие на элемент истории: выражение снова в калькуляторе, доступно для правки.
    func restore(_ item: HistoryItem) {
        state.expression = item.expression
        state.errorMessage = nil
        state.isShowingResult = false
    }

    func clearHistory() {
        state.history.removeAll()
    }

    // MARK: - Внутренняя логика

    private func beginEditing(replacingResult: Bool) {
        state.errorMessage = nil
        guard state.isShowingResult else { return }
        if replacingResult { state.expression = "" }
        state.isShowingResult = false
    }

    private func appendOperator(_ symbol: String) {
        beginEditing(replacingResult: false)
        state.expression += " \(symbol) "
    }

    private func evaluate() {
        // Повторное «=» на уже показанном результате ничего не делает.
        guard !state.isShowingResult else { return }
        let text = state.expression.trimmingCharacters(in: .whitespaces)

        switch CalculatorEngine.calculate(text) {
        case .success(let value):
            let result = CalculatorEngine.format(value)
            state.history.insert(HistoryItem(expression: text, result: result), at: 0)
            if state.history.count > Self.historyLimit {
                state.history.removeLast(state.history.count - Self.historyLimit)
            }
            state.lastResult = result
            state.expression = result
            state.isShowingResult = true
            state.errorMessage = nil

        case .failure(let error):
            state.errorMessage = error.errorDescription
        }
    }

    /// Значение текущего выражения для M+/M−. При ошибке показывает её и возвращает nil.
    private func currentValue() -> Double? {
        switch CalculatorEngine.calculate(state.expression) {
        case .success(let value):
            state.errorMessage = nil
            return value
        case .failure(let error):
            state.errorMessage = error.errorDescription
            return nil
        }
    }

    /// Вставляет число (MR): заменяет число, которое сейчас набирается, иначе дописывает.
    private func insertNumber(_ value: Double) {
        beginEditing(replacingResult: true)
        let number = trailingNumber(in: state.expression)
        if !number.isEmpty {
            state.expression.removeLast(number.count)
        }
        let text = CalculatorEngine.format(value)
        let atStart = state.expression.isEmpty || state.expression.hasSuffix("(")
        state.expression += (value < 0 && !atStart) ? "(\(text))" : text
    }

    /// Число (цифры и точка), которым заканчивается выражение.
    private func trailingNumber(in text: String) -> String {
        String(text.reversed().prefix { $0.isNumber || $0 == "." }.reversed())
    }

    // MARK: - Смена знака (±)

    /// Меняет знак последнего операнда — числа или группы в скобках:
    /// "5" → "-5", "5 + 3" → "5 + (-3)", "5 + (-3)" → "5 + 3", "(5" → "(-5", "" → "-".
    static func toggledSign(of expression: String) -> String {
        guard let operandStart = trailingOperandStart(in: expression) else {
            // Операнда нет: начало выражения, после «(» или после оператора.
            if expression.isEmpty || expression.hasSuffix("(") { return expression + "-" }
            if expression.hasSuffix("-") { return String(expression.dropLast()) }
            return expression + "(-"
        }

        let prefix = String(expression[..<operandStart])
        let operand = String(expression[operandStart...])

        // "(-X)" → "X"
        if operand.hasPrefix("(-"), operand.hasSuffix(")") {
            let inner = String(operand.dropFirst(2).dropLast())
            if !inner.isEmpty, trailingOperandStart(in: inner) == inner.startIndex {
                return prefix + inner
            }
        }

        if prefix.isEmpty { return "-" + operand }
        if prefix == "-" { return operand }
        if prefix.hasSuffix("(-") { return String(prefix.dropLast()) + operand }
        if prefix.hasSuffix("(") { return prefix + "-" + operand }
        return prefix + "(-" + operand + ")"
    }

    /// Начало последнего операнда: числа или сбалансированной группы в скобках.
    private static func trailingOperandStart(in text: String) -> String.Index? {
        guard let last = text.last else { return nil }

        if last == ")" {
            var depth = 0
            var index = text.endIndex
            while index > text.startIndex {
                index = text.index(before: index)
                switch text[index] {
                case ")": depth += 1
                case "(":
                    depth -= 1
                    if depth == 0 { return index }
                default: break
                }
            }
            return nil
        }

        if last.isNumber || last == "." {
            var index = text.endIndex
            while index > text.startIndex {
                let previous = text.index(before: index)
                let ch = text[previous]
                guard ch.isNumber || ch == "." else { break }
                index = previous
            }
            return index
        }

        return nil
    }
}
