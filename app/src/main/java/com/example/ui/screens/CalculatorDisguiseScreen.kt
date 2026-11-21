package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.CalculatorEngine
import com.example.ui.theme.LocalVaultColors
import kotlinx.coroutines.launch

@Composable
fun CalculatorDisguiseScreen(
    onCheckSecretTrigger: suspend (String) -> Boolean,
    onFallbackToStandardLock: (() -> Unit)? = null,
    isPreviewOnly: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val vaultColors = LocalVaultColors.current

    var expression by remember { mutableStateOf("") }
    var displayResult by remember { mutableStateOf("0") }
    var scientificExpanded by remember { mutableStateOf(false) }

    fun appendToken(token: String) {
        if (expression == "Error") expression = ""
        expression += token
        displayResult = expression
    }

    fun handleEquals() {
        val currentExpr = expression.ifBlank { displayResult }
        scope.launch {
            val unlocked = onCheckSecretTrigger(currentExpr)
            if (!unlocked) {
                val res = CalculatorEngine.evaluateExpression(currentExpr)
                displayResult = res
                expression = if (res == "Error") "" else res
            }
        }
    }

    fun handleUnary(op: String) {
        val currentExpr = expression.ifBlank { displayResult }
        val res = CalculatorEngine.applyUnary(currentExpr, op)
        displayResult = res
        expression = if (res == "Error") "" else res
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 460.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = scientificExpanded,
                    onClick = { scientificExpanded = !scientificExpanded },
                    label = { Text(if (scientificExpanded) "Scientific" else "Basic") }
                )
                if (onFallbackToStandardLock != null) {
                    TextButton(
                        onClick = onFallbackToStandardLock,
                        modifier = Modifier.testTag("calc_preview_back")
                    ) {
                        Text(if (isPreviewOnly) "Close Preview" else "Biometric / Pattern")
                    }
                }
            }

            // Display area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                if (expression.isNotEmpty() && expression != displayResult) {
                    Text(
                        text = expression,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = displayResult,
                    fontSize = 48.sp,
                    lineHeight = 54.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("calculator_display")
                )
            }

            if (scientificExpanded) {
                val sciRows = listOf(
                    listOf("sin", "cos", "tan", "√"),
                    listOf("(", ")", "x²", "π")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sciRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { key ->
                                CalcButton(
                                    label = key,
                                    containerColor = vaultColors.glassSurface,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (key) {
                                        "sin" -> handleUnary("sin")
                                        "cos" -> handleUnary("cos")
                                        "tan" -> handleUnary("tan")
                                        "√" -> handleUnary("sqrt")
                                        "x²" -> handleUnary("sq")
                                        else -> appendToken(key)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val mainRows = listOf(
                listOf("C", "⌫", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("+/-", "0", ".", "=")
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                mainRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { key ->
                            val isOperator = key in setOf("÷", "×", "-", "+", "%")
                            val isEquals = key == "="
                            val isClear = key in setOf("C", "⌫")
                            val bg = when {
                                isEquals -> MaterialTheme.colorScheme.primary
                                isOperator -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                isClear -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                else -> vaultColors.glassSurface
                            }
                            val fg = when {
                                isEquals -> MaterialTheme.colorScheme.onPrimary
                                isOperator -> MaterialTheme.colorScheme.primary
                                isClear -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            CalcButton(
                                label = key,
                                containerColor = bg,
                                contentColor = fg,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .testTag("calc_btn_$key")
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                when (key) {
                                    "C" -> {
                                        expression = ""
                                        displayResult = "0"
                                    }
                                    "⌫" -> {
                                        if (expression.isNotEmpty()) {
                                            expression = expression.dropLast(1)
                                            displayResult = expression.ifEmpty { "0" }
                                        }
                                    }
                                    "+/-" -> handleUnary("neg")
                                    "=" -> handleEquals()
                                    else -> appendToken(key)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
