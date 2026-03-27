package io.github.mobdev

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var tvResult: TextView
    private lateinit var tvExpression: TextView

    private var inputExpression = ""
    private var resultShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvResult = findViewById(R.id.tvResult)
        tvExpression = findViewById(R.id.tvExpression)

        val digitButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        digitButtons.forEach { id ->
            findViewById<Button>(id).setOnClickListener {
                appendDigit((it as Button).text.toString())
            }
        }

        findViewById<Button>(R.id.btnDot).setOnClickListener { appendDot() }
        findViewById<Button>(R.id.btnAc).setOnClickListener { clearAll() }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener { deleteLast() }

        findViewById<Button>(R.id.btnPlus).setOnClickListener { appendOperator("+") }
        findViewById<Button>(R.id.btnMinus).setOnClickListener { appendOperator("-") }
        findViewById<Button>(R.id.btnMultiply).setOnClickListener { appendOperator("×") }
        findViewById<Button>(R.id.btnDivide).setOnClickListener { appendOperator("÷") }

        findViewById<Button>(R.id.btnOpenClose).setOnClickListener { appendBracket() }
        findViewById<Button>(R.id.btnPercent).setOnClickListener { applyPercent() }
        findViewById<Button>(R.id.btnEquals).setOnClickListener { calculateResult() }

        if (savedInstanceState != null) {
            inputExpression = savedInstanceState.getString("inputExpression", "")
            resultShown = savedInstanceState.getBoolean("resultShown", false)
            tvResult.text = savedInstanceState.getString("tvResultText", "0")
            tvExpression.text = savedInstanceState.getString("tvExpressionText", "")
        } else {
            updateInputState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("inputExpression", inputExpression)
        outState.putBoolean("resultShown", resultShown)
        outState.putString("tvResultText", tvResult.text.toString())
        outState.putString("tvExpressionText", tvExpression.text.toString())
    }

    private fun appendDigit(digit: String) {
        if (resultShown) {
            inputExpression = digit
            resultShown = false
            tvExpression.text = ""
            updateInputState()
            return
        }

        inputExpression = if (inputExpression == "0") digit else inputExpression + digit
        updateInputState()
    }

    private fun appendDot() {
        if (resultShown) {
            inputExpression = "0."
            resultShown = false
            tvExpression.text = ""
            updateInputState()
            return
        }

        val lastNumber = getLastNumberPart()
        if (lastNumber.contains(".")) return

        inputExpression += when {
            inputExpression.isEmpty() -> "0."
            inputExpression.last() == '(' -> "0."
            isOperator(inputExpression.last()) -> "0."
            else -> "."
        }

        updateInputState()
    }

    private fun appendOperator(operator: String) {
        if (resultShown) {
            resultShown = false
            tvExpression.text = ""
        }

        if (inputExpression.isEmpty()) {
            if (operator == "-") {
                inputExpression = "-"
                updateInputState()
            }
            return
        }

        val last = inputExpression.last()

        if (last == '(') {
            if (operator == "-") {
                inputExpression += "-"
                updateInputState()
            }
            return
        }

        if (isOperator(last)) {
            inputExpression = inputExpression.dropLast(1) + operator
            updateInputState()
            return
        }

        if (last == '.') return

        inputExpression += operator
        updateInputState()
    }

    private fun appendBracket() {
        if (resultShown) {
            resultShown = false
            tvExpression.text = ""
        }

        val openCount = inputExpression.count { it == '(' }
        val closeCount = inputExpression.count { it == ')' }

        if (inputExpression.isEmpty()) {
            inputExpression += "("
            updateInputState()
            return
        }

        val last = inputExpression.last()

        val shouldOpen = last == '(' || isOperator(last)
        val canClose = openCount > closeCount && (last.isDigit() || last == ')' || last == '%')

        when {
            shouldOpen -> inputExpression += "("
            canClose -> inputExpression += ")"
            last.isDigit() || last == ')' || last == '%' -> inputExpression += "×("
            else -> inputExpression += "("
        }

        updateInputState()
    }

    private fun applyPercent() {
        if (inputExpression.isEmpty()) return
        if (resultShown) return

        val last = inputExpression.lastOrNull() ?: return
        if (!(last.isDigit() || last == ')' || last == '%')) return

        if (last == '%') return

        inputExpression += "%"
        updateInputState()
    }

    private fun calculateResult() {
        if (inputExpression.isEmpty()) return

        val fullExpression = inputExpression

        try {
            val preparedExpression = preprocessPercentages(inputExpression)
                .replace("×", "*")
                .replace("÷", "/")

            val result = evaluateExpression(preparedExpression)
            val formattedResult = formatResult(result)

            tvExpression.text = fullExpression
            tvResult.text = formattedResult

            inputExpression = formattedResult
            resultShown = true
        } catch (_: Exception) {
            tvExpression.text = fullExpression
            tvResult.text = getString(R.string.error_text)
            resultShown = true
        }
    }

    private fun clearAll() {
        inputExpression = ""
        resultShown = false
        tvResult.text = "0"
        tvExpression.text = ""
    }

    private fun deleteLast() {
        if (resultShown) return
        if (inputExpression.isNotEmpty()) {
            inputExpression = inputExpression.dropLast(1)
        }
        updateInputState()
    }

    private fun updateInputState() {
        tvResult.text = if (inputExpression.isEmpty()) "0" else inputExpression
        tvExpression.text = ""
    }

    private fun getLastNumberPart(): String {
        if (inputExpression.isEmpty()) return ""

        var i = inputExpression.length - 1
        while (i >= 0) {
            val ch = inputExpression[i]
            if (!ch.isDigit() && ch != '.') break
            i--
        }
        return inputExpression.substring(i + 1)
    }

    private fun isOperator(ch: Char): Boolean {
        return ch == '+' || ch == '-' || ch == '×' || ch == '÷' || ch == '*' || ch == '/'
    }

    private fun formatResult(value: Double): String {
        val rounded = if (abs(value) < 1e-10) 0.0 else value
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }

    private fun preprocessPercentages(expression: String): String {
        var expr = expression

        while (expr.contains("%")) {
            val percentIndex = expr.indexOf('%')
            if (percentIndex <= 0) {
                throw IllegalArgumentException("Invalid percent")
            }

            val operandRange = findOperandBeforePercent(expr, percentIndex)
                ?: throw IllegalArgumentException("No operand before percent")

            val operandText = expr.substring(operandRange.first, operandRange.last + 1)
            val operandValue = evaluateExpression(
                operandText.replace("×", "*").replace("÷", "/")
            )

            val operatorIndex = findMainOperatorBeforeOperand(expr, operandRange.first)

            val replacement = if (operatorIndex == -1) {
                "(${formatResultForExpression(operandValue / 100.0)})"
            } else {
                val operator = expr[operatorIndex]

                if (operator == '+' || operator == '-') {
                    val leftExpr = expr.substring(0, operatorIndex)
                    val leftValue = evaluateExpression(
                        preprocessPercentages(leftExpr)
                            .replace("×", "*")
                            .replace("÷", "/")
                    )
                    "(${formatResultForExpression(leftValue * operandValue / 100.0)})"
                } else {
                    "(${formatResultForExpression(operandValue / 100.0)})"
                }
            }

            expr = expr.substring(0, operandRange.first) +
                    replacement +
                    expr.substring(percentIndex + 1)
        }

        return expr
    }

    private fun findOperandBeforePercent(expression: String, percentIndex: Int): IntRange? {
        var end = percentIndex - 1
        if (end < 0) return null

        if (expression[end] == ')') {
            var balance = 1
            var i = end - 1
            while (i >= 0) {
                when (expression[i]) {
                    ')' -> balance++
                    '(' -> {
                        balance--
                        if (balance == 0) {
                            return i..end
                        }
                    }
                }
                i--
            }
            return null
        }

        var start = end
        while (start >= 0 && (expression[start].isDigit() || expression[start] == '.')) {
            start--
        }

        if (start >= 0 && expression[start] == '-') {
            if (start == 0 || expression[start - 1] == '(' || isOperator(expression[start - 1])) {
                return start..end
            }
        }

        return (start + 1)..end
    }

    private fun findMainOperatorBeforeOperand(expression: String, operandStart: Int): Int {
        var balance = 0
        for (i in operandStart - 1 downTo 0) {
            when (expression[i]) {
                ')' -> balance++
                '(' -> balance--
                '+', '-', '×', '÷' -> {
                    if (balance == 0) {
                        if (expression[i] == '-') {
                            if (i == 0) continue
                            val prev = expression[i - 1]
                            if (prev == '(' || isOperator(prev)) continue
                        }
                        return i
                    }
                }
            }
        }
        return -1
    }

    private fun formatResultForExpression(value: Double): String {
        val rounded = if (abs(value) < 1e-10) 0.0 else value
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }

    private fun evaluateExpression(expression: String): Double {
        val values = mutableListOf<Double>()
        val ops = mutableListOf<Char>()
        var i = 0

        while (i < expression.length) {
            val ch = expression[i]

            when {
                ch == ' ' -> {
                    i++
                }

                ch == '(' -> {
                    ops.add(ch)
                    i++
                }

                ch.isDigit() || ch == '.' || (ch == '-' && isUnaryMinus(expression, i)) -> {
                    val sb = StringBuilder()
                    sb.append(ch)
                    i++

                    while (i < expression.length && (expression[i].isDigit() || expression[i] == '.')) {
                        sb.append(expression[i])
                        i++
                    }

                    values.add(sb.toString().toDouble())
                }

                ch == ')' -> {
                    while (ops.isNotEmpty() && ops.last() != '(') {
                        applyTopOperation(values, ops)
                    }
                    if (ops.isEmpty() || ops.last() != '(') {
                        throw IllegalArgumentException("Mismatched brackets")
                    }
                    ops.removeAt(ops.lastIndex)
                    i++
                }

                ch == '+' || ch == '-' || ch == '*' || ch == '/' -> {
                    while (
                        ops.isNotEmpty() &&
                        ops.last() != '(' &&
                        precedence(ops.last()) >= precedence(ch)
                    ) {
                        applyTopOperation(values, ops)
                    }
                    ops.add(ch)
                    i++
                }

                else -> throw IllegalArgumentException("Unexpected char: $ch")
            }
        }

        while (ops.isNotEmpty()) {
            if (ops.last() == '(') throw IllegalArgumentException("Mismatched brackets")
            applyTopOperation(values, ops)
        }

        if (values.size != 1) throw IllegalArgumentException("Invalid expression")
        return values.last()
    }

    private fun isUnaryMinus(expression: String, index: Int): Boolean {
        if (expression[index] != '-') return false
        if (index == 0) return true

        val prev = expression[index - 1]
        return prev == '(' || prev == '+' || prev == '-' || prev == '*' || prev == '/'
    }

    private fun precedence(op: Char): Int {
        return when (op) {
            '+', '-' -> 1
            '*', '/' -> 2
            else -> 0
        }
    }

    private fun applyTopOperation(values: MutableList<Double>, ops: MutableList<Char>) {
        if (values.size < 2) throw IllegalArgumentException("Not enough operands")
        if (ops.isEmpty()) throw IllegalArgumentException("No operator")

        val right = values.removeAt(values.lastIndex)
        val left = values.removeAt(values.lastIndex)
        val op = ops.removeAt(ops.lastIndex)

        val result = when (op) {
            '+' -> left + right
            '-' -> left - right
            '*' -> left * right
            '/' -> {
                if (right == 0.0) throw ArithmeticException("Division by zero")
                left / right
            }
            else -> throw IllegalArgumentException("Unknown operator")
        }

        values.add(result)
    }
}