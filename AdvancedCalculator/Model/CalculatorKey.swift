import Foundation

/// Кнопки калькулятора.
enum CalculatorKey: Hashable {
    case digit(Int)
    case decimalPoint
    case add, subtract, multiply, divide
    case leftParen, rightParen
    case percent
    case toggleSign
    case clear
    case backspace
    case equals
    case memoryAdd, memorySubtract, memoryRecall, memoryClear

    var label: String {
        switch self {
        case .digit(let d): return String(d)
        case .decimalPoint: return "."
        case .add: return "+"
        case .subtract: return "−"
        case .multiply: return "×"
        case .divide: return "÷"
        case .leftParen: return "("
        case .rightParen: return ")"
        case .percent: return "%"
        case .toggleSign: return "±"
        case .clear: return "C"
        case .backspace: return "⌫"
        case .equals: return "="
        case .memoryAdd: return "M+"
        case .memorySubtract: return "M−"
        case .memoryRecall: return "MR"
        case .memoryClear: return "MC"
        }
    }

    /// Описание для VoiceOver.
    var accessibilityLabel: String {
        switch self {
        case .digit(let d): return String(d)
        case .decimalPoint: return "Десятичная точка"
        case .add: return "Плюс"
        case .subtract: return "Минус"
        case .multiply: return "Умножить"
        case .divide: return "Разделить"
        case .leftParen: return "Открывающая скобка"
        case .rightParen: return "Закрывающая скобка"
        case .percent: return "Процент"
        case .toggleSign: return "Сменить знак"
        case .clear: return "Очистить ввод"
        case .backspace: return "Удалить символ"
        case .equals: return "Равно"
        case .memoryAdd: return "Добавить в память"
        case .memorySubtract: return "Вычесть из памяти"
        case .memoryRecall: return "Вставить из памяти"
        case .memoryClear: return "Очистить память"
        }
    }

    enum Kind { case digit, op, function, equals, memory }

    var kind: Kind {
        switch self {
        case .digit, .decimalPoint: return .digit
        case .add, .subtract, .multiply, .divide: return .op
        case .equals: return .equals
        case .memoryAdd, .memorySubtract, .memoryRecall, .memoryClear: return .memory
        default: return .function
        }
    }
}
