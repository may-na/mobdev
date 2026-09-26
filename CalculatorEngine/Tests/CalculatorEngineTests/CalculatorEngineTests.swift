import XCTest
@testable import CalculatorEngine

final class CalculatorEngineTests: XCTestCase {

    // MARK: - Вспомогательные

    private func eval(_ text: String, file: StaticString = #filePath, line: UInt = #line) -> Double? {
        do {
            return try CalculatorEngine.evaluate(text)
        } catch {
            XCTFail("«\(text)» неожиданно упало: \(error)", file: file, line: line)
            return nil
        }
    }

    private func assertError(_ text: String, _ expected: CalculatorError,
                             file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertThrowsError(try CalculatorEngine.evaluate(text), "«\(text)» должно давать ошибку", file: file, line: line) { error in
            XCTAssertEqual(error as? CalculatorError, expected, "«\(text)»", file: file, line: line)
        }
    }

    // MARK: - Примеры из задания

    func testExamplesFromAssignment() {
        XCTAssertEqual(eval("12 + 5 × 3"), 27)
        XCTAssertEqual(eval("(12 + 5) × 3"), 51)
        XCTAssertEqual(eval("20 ÷ 4 + 7"), 12)
        XCTAssertEqual(eval("15.5 × 2"), 31)
    }

    // MARK: - Приоритет и ассоциативность

    func testPrecedence() {
        XCTAssertEqual(eval("2 + 3 × 4"), 14)
        XCTAssertEqual(eval("2 × 3 + 4"), 10)
        XCTAssertEqual(eval("10 − 6 ÷ 2"), 7)
        XCTAssertEqual(eval("1 + 2 × 3 − 4 ÷ 2"), 5)
    }

    func testLeftAssociativity() {
        XCTAssertEqual(eval("10 − 4 − 3"), 3)
        XCTAssertEqual(eval("100 ÷ 10 ÷ 2"), 5)
    }

    func testNestedParentheses() {
        XCTAssertEqual(eval("((2 + 3) × (4 − 1)) ÷ 5"), 3)
        XCTAssertEqual(eval("(((7)))"), 7)
        XCTAssertEqual(eval("2 × (3 + (4 − 1) × 2)"), 18)
    }

    func testImplicitMultiplication() {
        XCTAssertEqual(eval("2(3 + 4)"), 14)
        XCTAssertEqual(eval("(2)(3)"), 6)
        XCTAssertEqual(eval("(1 + 1)5"), 10)
    }

    // MARK: - Числа и операторы

    func testDecimals() {
        XCTAssertEqual(eval("0.5 + 0.25"), 0.75)
        XCTAssertEqual(eval(".5 × 4"), 2)
        XCTAssertEqual(eval("3. + 1"), 4)
        XCTAssertEqual(eval("1,5 + 1,5"), 3) // запятая как разделитель
        XCTAssertEqual(eval("0.1 + 0.2")!, 0.3, accuracy: 1e-12)
    }

    func testAsciiOperators() {
        XCTAssertEqual(eval("2*3/4-1"), 0.5)
        XCTAssertEqual(eval("8/2+1"), 5)
    }

    func testUnaryMinus() {
        XCTAssertEqual(eval("-5 + 3"), -2)
        XCTAssertEqual(eval("(-5) × 2"), -10)
        XCTAssertEqual(eval("2 × (-3)"), -6)
        XCTAssertEqual(eval("-(2 + 3)"), -5)
        XCTAssertEqual(eval("-(-4)"), 4)
    }

    func testPercent() {
        XCTAssertEqual(eval("50%"), 0.5)
        XCTAssertEqual(eval("200 × 10%"), 20)
        XCTAssertEqual(eval("200 ÷ 50%"), 400)
        XCTAssertEqual(eval("200 + 10%"), 220)
        XCTAssertEqual(eval("200 − 10%"), 180)
        XCTAssertEqual(eval("(10 + 10)%"), 0.2)
        XCTAssertEqual(eval("50%%"), 0.005)
    }

    // MARK: - Ошибки из задания

    func testDivisionByZero() {
        assertError("5 ÷ 0", .divisionByZero)
        assertError("5 ÷ (2 − 2)", .divisionByZero)
        assertError("1 / 0.0", .divisionByZero)
    }

    func testUnbalancedParentheses() {
        assertError("(2 + 3", .unbalancedParentheses)
        assertError("2 + 3)", .unbalancedParentheses)
        assertError("((2)", .unbalancedParentheses)
        assertError("(2))", .unbalancedParentheses)
        assertError(")", .unbalancedParentheses)
        assertError("(", .unbalancedParentheses)
        assertError("2 × (", .unbalancedParentheses)
    }

    func testConsecutiveOperators() {
        assertError("5 + × 3", .consecutiveOperators)
        assertError("5 × − 3", .consecutiveOperators)
        assertError("2 − − 2", .consecutiveOperators)
        assertError("1 ++ 1", .consecutiveOperators)
    }

    func testMultipleDecimalPoints() {
        assertError("1.2.3 + 1", .multipleDecimalPoints)
        assertError("5..5", .multipleDecimalPoints)
    }

    func testEmptyExpression() {
        assertError("", .emptyExpression)
        assertError("   ", .emptyExpression)
    }

    func testTrailingOperator() {
        assertError("5 +", .trailingOperator)
        assertError("5 × 3 −", .trailingOperator)
        assertError("(5 + )", .trailingOperator)
    }

    // MARK: - Прочие ошибки

    func testEmptyParentheses() {
        assertError("()", .emptyParentheses)
        assertError("5 × ()", .emptyParentheses)
    }

    func testInvalidCharacter() {
        assertError("5 + a", .invalidCharacter("a"))
        assertError("2 ^ 3", .invalidCharacter("^"))
    }

    func testUnexpectedToken() {
        assertError("+ 5", .unexpectedToken("+"))
        assertError("% 5", .unexpectedToken("%"))
        assertError("5 5", .unexpectedToken("5"))
        assertError("5 + %", .unexpectedToken("%"))
    }

    func testOverflow() {
        let big = String(repeating: "9", count: 200)
        assertError("\(big) × \(big)", .overflow)
    }

    // MARK: - Форматирование

    func testFormatting() {
        XCTAssertEqual(CalculatorEngine.format(27), "27")
        XCTAssertEqual(CalculatorEngine.format(31.0), "31")
        XCTAssertEqual(CalculatorEngine.format(2.5), "2.5")
        XCTAssertEqual(CalculatorEngine.format(0.1 + 0.2), "0.3")
        XCTAssertEqual(CalculatorEngine.format(-0.0), "0")
        XCTAssertEqual(CalculatorEngine.format(1.0 / 3), "0.333333333333333")
        XCTAssertEqual(CalculatorEngine.format(-12.75), "-12.75")
        XCTAssertEqual(CalculatorEngine.format(1e20), "1e+20")
    }

    func testResultRoundTrip() {
        // Результат, показанный пользователю, можно снова использовать в выражении.
        let value = eval("10 ÷ 3")!
        let text = CalculatorEngine.format(value)
        XCTAssertEqual(eval("\(text) × 3")!, 10, accuracy: 1e-9)
    }

    // MARK: - Устойчивость: любой мусор → ошибка, а не падение

    func testGarbageNeverCrashes() {
        let alphabet = Array("0123456789.+-×÷*/()% abc,")
        var generator = SystemRandomNumberGenerator()
        for _ in 0..<2000 {
            let length = Int.random(in: 0...12, using: &generator)
            let text = String((0..<length).map { _ in alphabet.randomElement(using: &generator)! })
            _ = CalculatorEngine.calculate(text)
        }
    }
}
