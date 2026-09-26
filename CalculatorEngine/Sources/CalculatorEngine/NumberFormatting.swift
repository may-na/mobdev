import Foundation

/// Форматирование чисел для отображения: 27.0 → "27", 0.1 + 0.2 → "0.3".
public enum NumberFormatting {
    public static func format(_ value: Double) -> String {
        guard value.isFinite else { return "∞" }
        if value == 0 { return "0" }
        // 15 значащих цифр: скрывает погрешность double, не теряя точность обычных чисел.
        return String(format: "%.15g", value)
    }
}
