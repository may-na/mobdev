import XCTest
@testable import AdvancedCalculator

@MainActor
final class CalculatorViewModelTests: XCTestCase {

    private func makeViewModel(state: CalculatorState? = nil) -> (CalculatorViewModel, InMemoryStateStore) {
        let store = InMemoryStateStore(state: state)
        return (CalculatorViewModel(store: store), store)
    }

    private func press(_ vm: CalculatorViewModel, _ keys: CalculatorKey...) {
        keys.forEach { vm.press($0) }
    }

    /// Набирает цифры/точку по строке: "12.5" → нажатия 1, 2, ., 5
    private func type(_ vm: CalculatorViewModel, _ text: String) {
        for ch in text {
            if let d = ch.wholeNumberValue { vm.press(.digit(d)) }
            else if ch == "." { vm.press(.decimalPoint) }
        }
    }

    // MARK: - Ввод и вычисление

    func testTypingBuildsReadableExpression() {
        let (vm, _) = makeViewModel()
        type(vm, "12"); press(vm, .add); type(vm, "5"); press(vm, .multiply); type(vm, "3")
        XCTAssertEqual(vm.expression, "12 + 5 × 3")
        XCTAssertEqual(vm.displayText, "12 + 5 × 3")
    }

    func testEqualsShowsResultAndAddsHistory() {
        let (vm, _) = makeViewModel()
        press(vm, .leftParen); type(vm, "12"); press(vm, .add); type(vm, "5"); press(vm, .rightParen)
        press(vm, .multiply); type(vm, "3"); press(vm, .equals)

        XCTAssertEqual(vm.expression, "51")
        XCTAssertTrue(vm.isShowingResult)
        XCTAssertEqual(vm.lastResult, "51")
        XCTAssertEqual(vm.history.first?.expression, "(12 + 5) × 3")
        XCTAssertEqual(vm.history.first?.result, "51")
    }

    func testDigitAfterResultStartsNewExpression() {
        let (vm, _) = makeViewModel()
        type(vm, "2"); press(vm, .add); type(vm, "3"); press(vm, .equals)
        type(vm, "7")
        XCTAssertEqual(vm.expression, "7")
        XCTAssertFalse(vm.isShowingResult)
    }

    func testOperatorAfterResultContinuesFromIt() {
        let (vm, _) = makeViewModel()
        type(vm, "2"); press(vm, .add); type(vm, "3"); press(vm, .equals)
        press(vm, .multiply); type(vm, "2"); press(vm, .equals)
        XCTAssertEqual(vm.expression, "10")
        XCTAssertEqual(vm.history.first?.expression, "5 × 2")
    }

    func testRepeatedEqualsDoesNotDuplicateHistory() {
        let (vm, _) = makeViewModel()
        type(vm, "2"); press(vm, .add); type(vm, "3"); press(vm, .equals, .equals, .equals)
        XCTAssertEqual(vm.history.count, 1)
    }

    func testDecimalAfterOperatorInsertsLeadingZero() {
        let (vm, _) = makeViewModel()
        type(vm, "5"); press(vm, .add, .decimalPoint); type(vm, "5")
        XCTAssertEqual(vm.expression, "5 + 0.5")
    }

    func testUnaryMinusAtStartAndAfterParen() {
        let (vm, _) = makeViewModel()
        press(vm, .subtract); type(vm, "5"); press(vm, .multiply, .leftParen, .subtract); type(vm, "2"); press(vm, .rightParen)
        XCTAssertEqual(vm.expression, "-5 × (-2)")
        press(vm, .equals)
        XCTAssertEqual(vm.expression, "10")
    }

    // MARK: - C и ⌫

    func testClearResetsExpressionAndErrorOnly() {
        let (vm, _) = makeViewModel()
        type(vm, "5"); press(vm, .equals)
        type(vm, "9"); press(vm, .memoryAdd)
        press(vm, .divide); type(vm, "0"); press(vm, .equals)
        XCTAssertNotNil(vm.errorMessage)

        press(vm, .clear)
        XCTAssertEqual(vm.expression, "")
        XCTAssertNil(vm.errorMessage)
        XCTAssertEqual(vm.history.count, 1, "C не трогает историю")
        XCTAssertEqual(vm.memory, 9, "C не трогает память")
    }

    func testBackspaceRemovesOperatorWithSpaces() {
        let (vm, _) = makeViewModel()
        type(vm, "5"); press(vm, .add)
        XCTAssertEqual(vm.expression, "5 + ")
        press(vm, .backspace)
        XCTAssertEqual(vm.expression, "5")
        press(vm, .backspace)
        XCTAssertEqual(vm.expression, "")
        press(vm, .backspace) // на пустом — без падения
        XCTAssertEqual(vm.expression, "")
    }

    func testBackspaceOnResultClearsIt() {
        let (vm, _) = makeViewModel()
        type(vm, "12"); press(vm, .equals, .backspace)
        XCTAssertEqual(vm.expression, "")
        XCTAssertFalse(vm.isShowingResult)
    }

    // MARK: - История

    func testHistoryKeepsOnlyLastTen() {
        let (vm, _) = makeViewModel()
        for i in 1...13 {
            press(vm, .clear); type(vm, String(i)); press(vm, .equals)
        }
        XCTAssertEqual(vm.history.count, CalculatorViewModel.historyLimit)
        XCTAssertEqual(vm.history.first?.expression, "13", "самое новое — первое")
        XCTAssertEqual(vm.history.last?.expression, "4")
    }

    func testRestoreFromHistoryMakesExpressionEditable() {
        let (vm, _) = makeViewModel()
        type(vm, "20"); press(vm, .divide); type(vm, "4"); press(vm, .add); type(vm, "7"); press(vm, .equals)
        press(vm, .clear)

        vm.restore(vm.history[0])
        XCTAssertEqual(vm.expression, "20 ÷ 4 + 7")
        XCTAssertFalse(vm.isShowingResult)

        press(vm, .multiply); type(vm, "2"); press(vm, .equals)
        XCTAssertEqual(vm.expression, "19") // 20 ÷ 4 + 7 × 2
    }

    // MARK: - Память

    func testMemoryOperations() {
        let (vm, _) = makeViewModel()
        XCTAssertNil(vm.memory)

        type(vm, "5"); press(vm, .memoryAdd)
        XCTAssertEqual(vm.memory, 5)
        XCTAssertEqual(vm.expression, "5", "M+ не меняет выражение")

        press(vm, .clear); type(vm, "2"); press(vm, .multiply); type(vm, "3"); press(vm, .memoryAdd)
        XCTAssertEqual(vm.memory, 11, "M+ вычисляет всё выражение")

        press(vm, .clear); type(vm, "1"); press(vm, .memorySubtract)
        XCTAssertEqual(vm.memory, 10)

        press(vm, .clear); type(vm, "4"); press(vm, .add, .memoryRecall)
        XCTAssertEqual(vm.expression, "4 + 10")

        press(vm, .memoryClear)
        XCTAssertNil(vm.memory)
    }

    func testMemoryAddWithInvalidExpressionShowsError() {
        let (vm, _) = makeViewModel()
        type(vm, "5"); press(vm, .add, .memoryAdd)
        XCTAssertNil(vm.memory)
        XCTAssertNotNil(vm.errorMessage)
    }

    func testMemoryRecallReplacesNumberBeingTyped() {
        let (vm, _) = makeViewModel()
        type(vm, "7"); press(vm, .memoryAdd, .clear)
        type(vm, "1"); press(vm, .add); type(vm, "22"); press(vm, .memoryRecall)
        XCTAssertEqual(vm.expression, "1 + 7")
    }

    func testMemoryRecallNegativeIsWrappedInParentheses() {
        let (vm, _) = makeViewModel()
        type(vm, "3"); press(vm, .memorySubtract, .clear)
        type(vm, "1"); press(vm, .multiply, .memoryRecall)
        XCTAssertEqual(vm.expression, "1 × (-3)")
        press(vm, .equals)
        XCTAssertEqual(vm.expression, "-3")
    }

    // MARK: - ±

    func testToggleSign() {
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: ""), "-")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "-"), "")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "5"), "-5")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "-5"), "5")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "12.5"), "-12.5")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "5 + 3"), "5 + (-3)")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "5 + (-3)"), "5 + 3")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "(5"), "(-5")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "(-5"), "(5")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "2 × (1 + 2)"), "2 × (-(1 + 2))")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "2 × (-(1 + 2))"), "2 × (1 + 2)")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "(1 + 2)"), "-(1 + 2)")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "-(1 + 2)"), "(1 + 2)")
        XCTAssertEqual(CalculatorViewModel.toggledSign(of: "5 + "), "5 + (-")
    }

    func testToggleSignThenEvaluate() {
        let (vm, _) = makeViewModel()
        type(vm, "8"); press(vm, .subtract); type(vm, "3"); press(vm, .toggleSign, .equals)
        XCTAssertEqual(vm.history.first?.expression, "8 − (-3)")
        XCTAssertEqual(vm.expression, "11")
    }

    // MARK: - Ошибки

    func testErrorStateKeepsExpressionAndClearsOnNextInput() {
        let (vm, _) = makeViewModel()
        type(vm, "5"); press(vm, .divide); type(vm, "0"); press(vm, .equals)
        XCTAssertEqual(vm.errorMessage, "Деление на ноль невозможно")
        XCTAssertEqual(vm.expression, "5 ÷ 0", "выражение остаётся для правки")
        XCTAssertTrue(vm.history.isEmpty)

        press(vm, .backspace)
        XCTAssertNil(vm.errorMessage)
        type(vm, "2"); press(vm, .equals)
        XCTAssertEqual(vm.expression, "2.5")
    }

    func testAllRequiredErrorCasesAreReported() {
        let cases: [([CalculatorKey], String)] = [
            ([.digit(5), .divide, .digit(0)], "Деление на ноль невозможно"),
            ([.leftParen, .digit(2), .add, .digit(3)], "Неверная расстановка или количество скобок"),
            ([.digit(5), .add, .multiply, .digit(3)], "Два оператора подряд"),
            ([.digit(1), .decimalPoint, .digit(2), .decimalPoint, .digit(3)], "В числе несколько десятичных точек"),
            ([], "Введите выражение"),
            ([.digit(5), .add], "Выражение не может заканчиваться оператором"),
        ]
        for (keys, expected) in cases {
            let (vm, _) = makeViewModel()
            keys.forEach { vm.press($0) }
            vm.press(.equals)
            XCTAssertEqual(vm.errorMessage, expected, "\(keys)")
        }
    }

    // MARK: - Сохранение состояния

    func testStateIsPersistedAndRestored() {
        let (vm, store) = makeViewModel()
        type(vm, "9"); press(vm, .memoryAdd)
        type(vm, "9"); press(vm, .equals) // "99" в истории
        press(vm, .divide); type(vm, "0"); press(vm, .equals) // ошибка

        // «Пересоздаём» ViewModel из того же хранилища — как после перезапуска.
        let restored = CalculatorViewModel(store: store)
        XCTAssertEqual(restored.state, vm.state)
        XCTAssertEqual(restored.expression, "99 ÷ 0")
        XCTAssertEqual(restored.history.first?.result, "99")
        XCTAssertEqual(restored.memory, 9)
        XCTAssertEqual(restored.lastResult, "99")
        XCTAssertEqual(restored.errorMessage, "Деление на ноль невозможно")
    }

    func testStateRoundTripsThroughUserDefaults() {
        let suite = "test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }

        let vm = CalculatorViewModel(store: UserDefaultsStateStore(defaults: defaults))
        type(vm, "15.5"); press(vm, .multiply); type(vm, "2"); press(vm, .equals)

        let restored = CalculatorViewModel(store: UserDefaultsStateStore(defaults: defaults))
        XCTAssertEqual(restored.history.first?.expression, "15.5 × 2")
        XCTAssertEqual(restored.history.first?.result, "31")
    }
}
