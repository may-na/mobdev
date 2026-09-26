import Foundation

/// Хранилище состояния. Протокол нужен, чтобы подменять его в тестах.
protocol CalculatorStateStore {
    func load() -> CalculatorState?
    func save(_ state: CalculatorState)
}

/// Сохранение в UserDefaults: состояние переживает не только поворот, но и перезапуск приложения.
final class UserDefaultsStateStore: CalculatorStateStore {
    private let key = "calculator.state"
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> CalculatorState? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(CalculatorState.self, from: data)
    }

    func save(_ state: CalculatorState) {
        guard let data = try? JSONEncoder().encode(state) else { return }
        defaults.set(data, forKey: key)
    }
}

/// Хранилище в памяти — для тестов и превью.
final class InMemoryStateStore: CalculatorStateStore {
    private(set) var state: CalculatorState?

    init(state: CalculatorState? = nil) {
        self.state = state
    }

    func load() -> CalculatorState? { state }
    func save(_ state: CalculatorState) { self.state = state }
}
