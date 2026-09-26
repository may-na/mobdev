import Foundation

/// Абстрактное синтаксическое дерево выражения.
indirect enum Expr: Equatable {
    case number(Double)
    case negate(Expr)
    case percent(Expr)
    case binary(BinaryOperator, Expr, Expr)
}

/// Парсер методом рекурсивного спуска. Грамматика:
///
///     expression := term (('+' | '−') term)*
///     term       := unary (('×' | '÷') unary | '(' ... | number-after-')')*   // неявное умножение: 2(3), (2)3
///     unary      := '-' unary | postfix                                        // унарный минус только в начале и после '('
///     postfix    := primary ('%')*
///     primary    := number | '(' expression ')'
///
/// Приоритет операций задаётся структурой грамматики: × ÷ разбираются глубже, чем + −.
struct Parser {
    private let tokens: [Token]
    private var index = 0

    init(tokens: [Token]) {
        self.tokens = tokens
    }

    mutating func parse() throws -> Expr {
        guard !tokens.isEmpty else { throw CalculatorError.emptyExpression }
        let expr = try parseExpression()
        if let leftover = peek {
            switch leftover {
            case .rightParen: throw CalculatorError.unbalancedParentheses
            default: throw CalculatorError.unexpectedToken(leftover.description)
            }
        }
        return expr
    }

    // MARK: - Вспомогательные

    private var peek: Token? { index < tokens.count ? tokens[index] : nil }
    private var previous: Token? { index > 0 ? tokens[index - 1] : nil }

    // MARK: - Правила грамматики

    private mutating func parseExpression() throws -> Expr {
        var left = try parseTerm()
        while case .op(let op)? = peek, op == .add || op == .subtract {
            index += 1
            let right = try parseTerm()
            left = .binary(op, left, right)
        }
        return left
    }

    private mutating func parseTerm() throws -> Expr {
        var left = try parseUnary()
        loop: while let token = peek {
            switch token {
            case .op(let op) where op == .multiply || op == .divide:
                index += 1
                let right = try parseUnary()
                left = .binary(op, left, right)
            case .leftParen:
                // 2(3 + 4) → 2 × (3 + 4)
                let right = try parseUnary()
                left = .binary(.multiply, left, right)
            case .number where previous == .rightParen:
                // (2 + 3)4 → (2 + 3) × 4
                let right = try parseUnary()
                left = .binary(.multiply, left, right)
            default:
                break loop
            }
        }
        return left
    }

    private mutating func parseUnary() throws -> Expr {
        if case .op(.subtract)? = peek, previous == nil || previous == .leftParen {
            index += 1
            return .negate(try parseUnary())
        }
        return try parsePostfix()
    }

    private mutating func parsePostfix() throws -> Expr {
        var node = try parsePrimary()
        while peek == .percent {
            index += 1
            node = .percent(node)
        }
        return node
    }

    private mutating func parsePrimary() throws -> Expr {
        guard let token = peek else {
            // Ввод закончился там, где ожидался операнд.
            switch previous {
            case .op?: throw CalculatorError.trailingOperator
            case .leftParen?: throw CalculatorError.unbalancedParentheses
            default: throw CalculatorError.emptyExpression
            }
        }

        switch token {
        case .number(let value):
            index += 1
            return .number(value)

        case .leftParen:
            index += 1
            if peek == .rightParen { throw CalculatorError.emptyParentheses }
            let inner = try parseExpression()
            guard let next = peek else { throw CalculatorError.unbalancedParentheses }
            guard next == .rightParen else { throw CalculatorError.unexpectedToken(next.description) }
            index += 1
            return inner

        case .rightParen:
            switch previous {
            case .op?: throw CalculatorError.trailingOperator      // "(5 + )"
            case .leftParen?: throw CalculatorError.emptyParentheses
            default: throw CalculatorError.unbalancedParentheses  // ") + 2"
            }

        case .op(let op):
            switch previous {
            case .op?: throw CalculatorError.consecutiveOperators   // "5 + × 3"
            default: throw CalculatorError.unexpectedToken(op.symbol) // "+ 5", "(× 3)"
            }

        case .percent:
            throw CalculatorError.unexpectedToken("%")
        }
    }
}
