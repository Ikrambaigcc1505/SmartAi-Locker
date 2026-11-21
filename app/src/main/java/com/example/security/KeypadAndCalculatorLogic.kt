package com.example.security

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

object KeypadHelper {

    fun generatePinKeypadRows(
        randomized: Boolean,
        random: Random = Random.Default
    ): List<List<String>> {
        if (!randomized) {
            return listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "DEL")
            )
        }
        val digits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9").shuffled(random)
        return listOf(
            listOf(digits[0], digits[1], digits[2]),
            listOf(digits[3], digits[4], digits[5]),
            listOf(digits[6], digits[7], digits[8]),
            listOf("BIO", digits[9], "DEL")
        )
    }
}

object CalculatorEngine {

    /**
     * Checks if the entered calculator input before '=' is a pure numeric PIN candidate (4..8 digits).
     * When operators are present (e.g. "12+34"), it returns null so normal math runs instead.
     */
    fun extractPinCandidate(expression: String): String? {
        val trimmed = expression.trim()
        if (trimmed.length in 4..8 && trimmed.all { it.isDigit() }) {
            return trimmed
        }
        return null
    }

    fun evaluateExpression(rawExpression: String): String {
        val cleaned = rawExpression
            .replace('×', '*')
            .replace('÷', '/')
            .replace("−", "-")
            .replace("π", Math.PI.toString())
            .replace("\\s+".toRegex(), "")

        if (cleaned.isEmpty()) return "0"

        return runCatching {
            val value = ExpressionParser(cleaned).parse()
            if (value.isNaN() || value.isInfinite()) {
                "Error"
            } else {
                formatNumber(value)
            }
        }.getOrElse { "Error" }
    }

    fun applyUnary(rawExpression: String, op: String): String {
        val evaluated = evaluateExpression(rawExpression)
        val num = evaluated.toDoubleOrNull() ?: return "Error"
        val res = when (op) {
            "sqrt" -> if (num < 0) Double.NaN else sqrt(num)
            "sq" -> num * num
            "sin" -> sin(Math.toRadians(num))
            "cos" -> cos(Math.toRadians(num))
            "tan" -> tan(Math.toRadians(num))
            "pct" -> num / 100.0
            "neg" -> -num
            else -> num
        }
        return if (res.isNaN() || res.isInfinite()) "Error" else formatNumber(res)
    }

    private fun formatNumber(value: Double): String {
        val bd = BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        val str = bd.toPlainString()
        return if (str == "-0") "0" else str
    }

    private class ExpressionParser(private val s: String) {
        private var pos = -1
        private var ch = 0

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < s.length) throw IllegalArgumentException("Unexpected char: ${ch.toChar()}")
            return x
        }

        private fun nextChar() {
            ch = if (++pos < s.length) s[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    eat('+'.code) -> x + parseTerm()
                    eat('-'.code) -> x - parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                x = when {
                    eat('*'.code) -> x * parseFactor()
                    eat('/'.code) -> {
                        val d = parseFactor()
                        if (d == 0.0) Double.NaN else x / d
                    }
                    eat('%'.code) -> {
                        val d = parseFactor()
                        if (d == 0.0) Double.NaN else x % d
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            val startPos = pos
            val x: Double
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
            } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                x = s.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected token")
            }
            return x
        }
    }
}
