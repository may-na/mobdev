import Foundation

/// Публичный фасад движка: строка выражения → число или ошибка.
/// Не зависит от UI, поэтому тестируется отдельно (см. CalculatorEngineTests).
public enum CalculatorEngine {
    /// Вычисляет выражение с учётом приоритета операций и скобок.
    public static func evaluate(_ expression: String) throws -> Double {
        let tokens = try Tokenizer.tokenize(expression)
        var parser = Parser(tokens: tokens)
        let ast = try parser.parse()
        return try Evaluator.evaluate(ast)
    }

    /// Вариант без `throws`: удобно для UI.
    public static func calculate(_ expression: String) -> Result<Double, CalculatorError> {
        do {
            return .success(try evaluate(expression))
        } catch let error as CalculatorError {
            return .failure(error)
        } catch {
            return .failure(.unexpectedToken(expression))
        }
    }

    /// Форматирует результат для отображения.
    public static func format(_ value: Double) -> String {
        NumberFormatting.format(value)
    }
}
