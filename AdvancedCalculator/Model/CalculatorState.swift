import Foundation

/// Элемент истории вычислений.
struct HistoryItem: Codable, Identifiable, Equatable {
    let id: UUID
    let expression: String
    let result: String
    let date: Date

    init(expression: String, result: String, date: Date = Date()) {
        self.id = UUID()
        self.expression = expression
        self.result = result
        self.date = date
    }
}

/// Полное пользовательское состояние калькулятора.
/// Хранится целиком (Codable), чтобы переживать поворот экрана и перезапуск приложения.
struct CalculatorState: Codable, Equatable {
    var expression: String = ""
    var history: [HistoryItem] = []
    var memory: Double? = nil
    var lastResult: String? = nil
    var errorMessage: String? = nil
    /// На экране показан результат последнего «=» (следующая цифра начнёт новое выражение).
    var isShowingResult: Bool = false
}
