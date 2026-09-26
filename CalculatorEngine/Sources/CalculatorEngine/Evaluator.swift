import Foundation

/// Вычисляет значение синтаксического дерева.
enum Evaluator {
    static func evaluate(_ expr: Expr) throws -> Double {
        let value = try eval(expr)
        guard value.isFinite else { throw CalculatorError.overflow }
        return value == 0 ? 0 : value // убираем «-0»
    }

    private static func eval(_ expr: Expr) throws -> Double {
        switch expr {
        case .number(let value):
            return value

        case .negate(let inner):
            return -(try eval(inner))

        case .percent(let inner):
            return try eval(inner) / 100

        case .binary(let op, let lhs, let rhs):
            let left = try eval(lhs)
            var right = try eval(rhs)

            // Процент в контексте сложения/вычитания берётся от левого операнда:
            // 200 + 10% = 220, 200 − 10% = 180. В остальных случаях x% = x / 100.
            if case .percent = rhs, op == .add || op == .subtract {
                right = left * right
            }

            switch op {
            case .add: return left + right
            case .subtract: return left - right
            case .multiply: return left * right
            case .divide:
                guard right != 0 else { throw CalculatorError.divisionByZero }
                return left / right
            }
        }
    }
}
