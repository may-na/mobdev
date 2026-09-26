import Foundation

/// Бинарные арифметические операторы.
public enum BinaryOperator: Equatable {
    case add, subtract, multiply, divide

    /// Символ для отображения пользователю.
    public var symbol: String {
        switch self {
        case .add: return "+"
        case .subtract: return "−"
        case .multiply: return "×"
        case .divide: return "÷"
        }
    }
}

/// Лексема исходного выражения.
public enum Token: Equatable {
    case number(Double)
    case op(BinaryOperator)
    case leftParen
    case rightParen
    case percent

    var description: String {
        switch self {
        case .number(let v): return NumberFormatting.format(v)
        case .op(let op): return op.symbol
        case .leftParen: return "("
        case .rightParen: return ")"
        case .percent: return "%"
        }
    }
}
