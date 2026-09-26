import Foundation

/// Ошибки вычисления. Каждая имеет понятное пользователю описание (по-русски).
public enum CalculatorError: Error, Equatable {
    case emptyExpression
    case divisionByZero
    case unbalancedParentheses
    case emptyParentheses
    case consecutiveOperators
    case multipleDecimalPoints
    case trailingOperator
    case invalidCharacter(Character)
    case unexpectedToken(String)
    case invalidNumber(String)
    case overflow
}

extension CalculatorError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case .emptyExpression:
            return "Введите выражение"
        case .divisionByZero:
            return "Деление на ноль невозможно"
        case .unbalancedParentheses:
            return "Неверная расстановка или количество скобок"
        case .emptyParentheses:
            return "Пустые скобки"
        case .consecutiveOperators:
            return "Два оператора подряд"
        case .multipleDecimalPoints:
            return "В числе несколько десятичных точек"
        case .trailingOperator:
            return "Выражение не может заканчиваться оператором"
        case .invalidCharacter(let ch):
            return "Недопустимый символ «\(ch)»"
        case .unexpectedToken(let text):
            return "Неожиданный символ «\(text)»"
        case .invalidNumber(let text):
            return "Некорректное число «\(text)»"
        case .overflow:
            return "Результат слишком велик"
        }
    }
}
