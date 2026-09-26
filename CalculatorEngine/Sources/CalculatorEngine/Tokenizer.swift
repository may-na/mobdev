import Foundation

/// Разбивает строку выражения на лексемы.
/// Принимает как «красивые» символы (× ÷ −), так и ASCII (* / -), запятую как десятичный разделитель.
struct Tokenizer {
    static func tokenize(_ text: String) throws -> [Token] {
        var tokens: [Token] = []
        var numberBuffer = ""
        var dotCount = 0

        func flushNumber() throws {
            guard !numberBuffer.isEmpty else { return }
            defer { numberBuffer = ""; dotCount = 0 }
            if dotCount > 1 { throw CalculatorError.multipleDecimalPoints }
            var normalized = numberBuffer
            if normalized.hasPrefix(".") { normalized = "0" + normalized }
            if normalized.hasSuffix(".") { normalized += "0" }
            guard let value = Double(normalized) else {
                throw CalculatorError.invalidNumber(numberBuffer)
            }
            tokens.append(.number(value))
        }

        for ch in text {
            switch ch {
            case "0"..."9":
                numberBuffer.append(ch)
            case ".", ",":
                numberBuffer.append(".")
                dotCount += 1
            case " ", "\t", "\n":
                try flushNumber()
            case "+":
                try flushNumber(); tokens.append(.op(.add))
            case "-", "−", "–":
                try flushNumber(); tokens.append(.op(.subtract))
            case "*", "×", "·":
                try flushNumber(); tokens.append(.op(.multiply))
            case "/", "÷", ":":
                try flushNumber(); tokens.append(.op(.divide))
            case "(":
                try flushNumber(); tokens.append(.leftParen)
            case ")":
                try flushNumber(); tokens.append(.rightParen)
            case "%":
                try flushNumber(); tokens.append(.percent)
            default:
                throw CalculatorError.invalidCharacter(ch)
            }
        }
        try flushNumber()
        return tokens
    }
}
