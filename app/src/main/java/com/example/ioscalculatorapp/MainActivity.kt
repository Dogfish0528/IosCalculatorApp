package com.example.ioscalculatorapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 提前非同步載入偏好設定並於背景執行緒預熱自訂字體快取，避免首幀主執行緒解碼延遲
        IosStylePreferences.preload(applicationContext)
        val appContext = applicationContext
        Thread {
            try {
                appContext.resources.getFont(R.font.inter_display_medium)
            } catch (_: Throwable) {
            }
        }.apply {
            isDaemon = true
            name = "FontWarmup"
            start()
        }
        enableEdgeToEdge()
        setContent {
            IosCalculatorApp()
        }
    }
}

private val BasicOperatorSymbols = setOf("÷", "×", "−", "+", "=")
private val ActiveOperatorSymbols = setOf("÷", "×", "−", "+")
private val TopFunctionSymbols = setOf("DEL", "C", "AC", "%")

// 綁定你匯入的 InterDisplay-Medium 字體 (res/font/inter_display_medium.ttf)
private val InterDisplayFont = FontFamily(
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_medium, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Light)
)

// iOS 27 原廠配色
private val IosBlack = Color(0xFF000000)
private val IosTopBarCircle = Color(0xFF1A1A1C)    // 頂部左右兩顆圓鈕低調深灰底色
private val IosSciGray = Color(0xFF1C1C1E)         // 科學計算機上排小圓鈕深灰底色
private val IosSciGrayPressed = Color(0xFF48484A)  // 科學計算機按鈕按壓色
private val IosDarkGray = Color(0xFF2C2C2E)        // 數字鍵深灰底色
private val IosDarkGrayPressed = Color(0xFF636366) // 數字鍵按壓色
private val IosLightGray = Color(0xFF5C5C5E)       // 第一排功能鍵(⌫, AC/C, %)中灰底色
private val IosLightGrayPressed = Color(0xFF8E8E93)// 第一排功能鍵按壓色
private val IosOrange = Color(0xFFF08800)          // 右排運算鍵沉穩深橘色
private val IosOrangePressed = Color(0xFFFFB340)   // 右排運算鍵按壓色
private val IosMenuBg = Color(0xFF2C2C2E)          // 彈出選單與拷貝氣泡背景色

// 紀錄「上一次的運算」以支援連按 "=" 或新數字直接按 "="
data class LastOperation(
    val operator: Char,
    val operandText: String
)

// 歷程記錄資料模型
data class CalculationHistoryItem(
    val id: Long,
    val expression: String,
    val rawResult: String,
    val displayResult: String,
    val timestamp: Long
)

private const val HISTORY_PREFS_NAME = "ios_calc_history_prefs"
private const val HISTORY_JSON_KEY = "history_json"

private fun loadHistoryFromPrefs(context: Context): List<CalculationHistoryItem> {
    return try {
        val prefs = context.getSharedPreferences(HISTORY_PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(HISTORY_JSON_KEY, null) ?: return emptyList()
        val arr = JSONArray(rawJson)
        val list = mutableListOf<CalculationHistoryItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                CalculationHistoryItem(
                    id = obj.optLong("id", i.toLong()),
                    expression = obj.optString("expression", ""),
                    rawResult = obj.optString("rawResult", "0"),
                    displayResult = obj.optString("displayResult", "0"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveHistoryToPrefs(context: Context, items: List<CalculationHistoryItem>) {
    try {
        val arr = JSONArray()
        items.take(200).forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("expression", item.expression)
                put("rawResult", item.rawResult)
                put("displayResult", item.displayResult)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(HISTORY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(HISTORY_JSON_KEY, arr.toString())
            .apply()
    } catch (_: Exception) {
    }
}

// 獨立封裝的核心數學計算與格式化引擎（方便單元測試與模組共用）
internal object CalculatorEngine {
    val basicOperators = setOf('+', '−', '×', '÷')
    val allBinaryOperators = setOf('+', '−', '×', '÷', '^', '√', '§')

    val funcPrefixes = listOf(
        "sinh⁻¹(", "cosh⁻¹(", "tanh⁻¹(",
        "sin⁻¹(", "cos⁻¹(", "tan⁻¹(",
        "log₁₀(", "log₂(", "logᵧ(", "sinh(", "cosh(", "tanh(",
        "sin(", "cos(", "tan(", "ln(", "²√(", "³√(", "1÷("
    )

    // 格式化單一浮點數為乾淨字串（修正極小值變 0 與超大值展開過長問題）
    fun formatDoublePlain(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "錯誤"
        val normalized = if (abs(value) == 0.0) 0.0 else value
        if (normalized == 0.0) return "0"
        val absVal = abs(normalized)
        val bd = BigDecimal(normalized, MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        return if (absVal >= 1e15 || absVal < 1e-9) {
            bd.toString().replace("E+", "E")
        } else {
            bd.toPlainString()
        }
    }

    // 將整串算式中的純數字加上千分位逗號顯示，同時保留科學符號與科學記號指數
    fun formatExpressionForDisplay(expr: String): String {
        if (expr == "錯誤") return expr
        val sb = StringBuilder()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            if (c.isDigit()) {
                val prev = if (i > 0) expr[i - 1] else ' '
                val prev2 = if (i > 1) expr[i - 2] else ' '
                // 避免把 log₁₀、sin⁻¹ 的下標數字，或科學記號 E 後方的指數加上千分位逗號
                if (prev == '₁' || prev == '⁻' || prev == 'E' || (prev2 == 'E' && (prev == '-' || prev == '+'))) {
                    while (i < expr.length && expr[i].isDigit()) {
                        sb.append(expr[i])
                        i++
                    }
                    continue
                }
                val start = i
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                    i++
                }
                val numStr = expr.substring(start, i)
                val parts = numStr.split(".")
                val intPart = parts[0]
                val formattedInt = try {
                    DecimalFormat("#,###").format(BigDecimal(intPart))
                } catch (e: Exception) {
                    intPart
                }
                sb.append(formattedInt)
                if (parts.size > 1) {
                    sb.append(".").append(parts[1])
                } else if (numStr.endsWith(".")) {
                    sb.append(".")
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // 完整科學與基本算式計算引擎
    fun evaluateExpression(rawExpr: String, isRadians: Boolean = false): String {
        try {
            var s = rawExpr.replace(",", "").replace(" ", "")
            // 移除結尾未完成的函數前綴、二元運算符或未閉合的左括號/科學記號
            var stripped = true
            while (s.isNotEmpty() && stripped) {
                stripped = false
                val matchedPrefix = funcPrefixes.firstOrNull { s.endsWith(it) }
                if (matchedPrefix != null) {
                    s = s.dropLast(matchedPrefix.length)
                    stripped = true
                } else if (
                    s.last() in allBinaryOperators ||
                    s.last() == '(' ||
                    s.last() == 'E' ||
                    (s.length > 1 && s.endsWith("E-"))
                ) {
                    s = if (s.endsWith("E-")) s.dropLast(2) else s.dropLast(1)
                    stripped = true
                }
            }
            if (s.isEmpty() || s == "-" || s == "−") return "0"

            // 1. 處理「A + B%」與「A − B%」的百分比加成邏輯 (例如 100+15% = 115)
            val pctAddSubRegex = Regex("""(\d+(?:\.\d+)?)([+−])(\d+(?:\.\d+)?)%""")
            while (true) {
                val m = pctAddSubRegex.find(s) ?: break
                val base = m.groupValues[1].toDouble()
                val op = m.groupValues[2]
                val pct = m.groupValues[3].toDouble() / 100.0
                val delta = base * pct
                s = s.replaceRange(m.range, "$base$op$delta")
            }

            // 2. 將多字元科學函數轉換為內部無歧義代碼，並自動補齊未閉合的右括號
            // 其中 "logᵧ(" 轉為二元運算符 "§(" (計算 x logᵧ y = ln(x) / ln(y))
            s = s.replace("sinh⁻¹(", "asinh(")
                .replace("cosh⁻¹(", "acosh(")
                .replace("tanh⁻¹(", "atanh(")
                .replace("sin⁻¹(", "asin(")
                .replace("cos⁻¹(", "acos(")
                .replace("tan⁻¹(", "atan(")
                .replace("log₁₀(", "logten(")
                .replace("log₂(", "logtwo(")
                .replace("logᵧ(", "§(")
                .replace("logᵧ", "§")
                .replace("²√(", "sqrt(")
                .replace("³√(", "cbrt(")

            val openCount = s.count { it == '(' }
            val closeCount = s.count { it == ')' }
            if (openCount > closeCount) {
                s += ")".repeat(openCount - closeCount)
            }

            // 3. 補上隱含乘號 (例如 2π -> 2×π, 2sin(30) -> 2×sin(30), )2 -> )×2)
            val withImplicitMul = StringBuilder()
            for (idx in s.indices) {
                val c = s[idx]
                withImplicitMul.append(c)
                if (idx + 1 < s.length) {
                    val next = s[idx + 1]
                    val leftIsValueEnd = c.isDigit() || c in setOf(')', 'π', 'ℯ', '!', '%', '²', '³')
                    val rightIsValueStart = next == '(' || next == 'π' || next == 'ℯ' || next.isLetter()
                    if (leftIsValueEnd && rightIsValueStart) {
                        // 排除科學記號 E (例如 5E3)
                        if (!(c.isDigit() && next == 'E')) {
                            withImplicitMul.append('×')
                        }
                    }
                }
            }
            val exprToParse = withImplicitMul.toString()

            // 4. 遞迴下降語法解析器 (Recursive Descent Parser)
            val parser = object {
                var pos = 0

                fun toRadIfNeeded(v: Double): Double = if (isRadians) v else Math.toRadians(v)
                fun fromRadIfNeeded(v: Double): Double = if (isRadians) v else Math.toDegrees(v)
                fun cleanTrigZero(v: Double): Double = if (abs(v) < 1e-14) 0.0 else v

                fun factorial(n: Double): Double {
                    if (n < 0 || n != floor(n) || n > 170) return Double.NaN
                    var res = 1.0
                    for (k in 2..n.toInt()) res *= k.toDouble()
                    return res
                }

                fun parseExpr(): Double {
                    var x = parseTerm()
                    while (pos < exprToParse.length) {
                        when (exprToParse[pos]) {
                            '+' -> { pos++; x += parseTerm() }
                            '−', '-' -> { pos++; x -= parseTerm() }
                            else -> break
                        }
                    }
                    return x
                }

                fun parseTerm(): Double {
                    var x = parseUnary()
                    while (pos < exprToParse.length) {
                        when (exprToParse[pos]) {
                            '×' -> { pos++; x *= parseUnary() }
                            '÷' -> {
                                pos++
                                val divisor = parseUnary()
                                if (divisor == 0.0) return Double.NaN
                                x /= divisor
                            }
                            else -> break
                        }
                    }
                    return x
                }

                // 一元正負號優先級低於次方與階乘（確保 -2² = -4，而 (-2)² = 4，且 2^-3 = 0.125）
                fun parseUnary(): Double {
                    if (pos < exprToParse.length) {
                        if (exprToParse[pos] == '+') {
                            pos++
                            return parseUnary()
                        }
                        if (exprToParse[pos] == '-' || exprToParse[pos] == '−') {
                            pos++
                            return -parseUnary()
                        }
                    }
                    return parsePower()
                }

                fun parsePower(): Double {
                    var x = parsePostfix()
                    while (pos < exprToParse.length) {
                        when (exprToParse[pos]) {
                            '^' -> {
                                pos++
                                val exp = parseUnary()
                                x = x.pow(exp)
                            }
                            '√' -> {
                                pos++
                                val radicand = parseUnary()
                                if (x == 0.0) return Double.NaN
                                x = if (radicand < 0.0) {
                                    // 支援負數開奇數次方根，如 3√(-8) = -2
                                    if (x == floor(x) && abs(x.toLong()) % 2L == 1L) {
                                        -((-radicand).pow(1.0 / x))
                                    } else {
                                        Double.NaN
                                    }
                                } else {
                                    radicand.pow(1.0 / x)
                                }
                            }
                            '§' -> {
                                // x logᵧ base = ln(x) / ln(base)
                                pos++
                                val base = parseUnary()
                                if (x <= 0.0 || base <= 0.0 || base == 1.0) return Double.NaN
                                x = ln(x) / ln(base)
                            }
                            'E' -> {
                                pos++
                                val exp = parseUnary()
                                x *= 10.0.pow(exp)
                            }
                            else -> break
                        }
                    }
                    return x
                }

                fun parsePostfix(): Double {
                    var x = parsePrimary()
                    while (pos < exprToParse.length) {
                        when (exprToParse[pos]) {
                            '!' -> { pos++; x = factorial(x) }
                            '%' -> { pos++; x /= 100.0 }
                            '²' -> { pos++; x = x.pow(2.0) }
                            '³' -> { pos++; x = x.pow(3.0) }
                            else -> break
                        }
                    }
                    return x
                }

                fun parsePrimary(): Double {
                    if (pos >= exprToParse.length) return 0.0
                    val c = exprToParse[pos]

                    if (c == '(') {
                        pos++
                        val x = parseExpr()
                        if (pos < exprToParse.length && exprToParse[pos] == ')') pos++
                        return x
                    }
                    if (c == 'π') {
                        pos++
                        return Math.PI
                    }
                    if (c == 'ℯ') {
                        pos++
                        return Math.E
                    }
                    if (c.isLetter()) {
                        val start = pos
                        while (pos < exprToParse.length && exprToParse[pos].isLetter()) pos++
                        val func = exprToParse.substring(start, pos)
                        if (pos < exprToParse.length && exprToParse[pos] == '(') pos++
                        val arg = parseExpr()
                        if (pos < exprToParse.length && exprToParse[pos] == ')') pos++

                        return when (func) {
                            "sin" -> cleanTrigZero(sin(toRadIfNeeded(arg)))
                            "cos" -> cleanTrigZero(cos(toRadIfNeeded(arg)))
                            "tan" -> {
                                val rad = toRadIfNeeded(arg)
                                if (abs(cos(rad)) < 1e-14) Double.NaN else cleanTrigZero(tan(rad))
                            }
                            "asin" -> if (arg in -1.0..1.0) fromRadIfNeeded(asin(arg)) else Double.NaN
                            "acos" -> if (arg in -1.0..1.0) fromRadIfNeeded(acos(arg)) else Double.NaN
                            "atan" -> fromRadIfNeeded(atan(arg))
                            "sinh" -> sinh(arg)
                            "cosh" -> cosh(arg)
                            "tanh" -> tanh(arg)
                            "asinh" -> asinh(arg)
                            "acosh" -> if (arg >= 1.0) acosh(arg) else Double.NaN
                            "atanh" -> if (arg > -1.0 && arg < 1.0) atanh(arg) else Double.NaN
                            "ln" -> if (arg > 0) ln(arg) else Double.NaN
                            "logten" -> if (arg > 0) log10(arg) else Double.NaN
                            "logtwo" -> if (arg > 0) ln(arg) / ln(2.0) else Double.NaN
                            "sqrt" -> if (arg >= 0) sqrt(arg) else Double.NaN
                            "cbrt" -> cbrt(arg)
                            else -> Double.NaN
                        }
                    }
                    if (c.isDigit() || c == '.') {
                        val start = pos
                        while (pos < exprToParse.length && (exprToParse[pos].isDigit() || exprToParse[pos] == '.')) {
                            pos++
                        }
                        return exprToParse.substring(start, pos).toDoubleOrNull() ?: 0.0
                    }
                    return Double.NaN
                }
            }

            val total = parser.parseExpr()
            if (parser.pos < exprToParse.length) return "錯誤"
            return formatDoublePlain(total)
        } catch (e: Exception) {
            return "錯誤"
        }
    }
}

private val IosStyleSettingsSaver = listSaver<IosStyleSettingsData, Any>(
    save = {
        listOf(
            it.styleId,
            it.accentColorId,
            it.layoutOverride,
            it.buttonShapeStyle,
            it.buttonSpacingMode,
            it.displayFontWeight,
            it.displayFontScale,
            it.hapticFeedbackEnabled,
            it.useThousandSeparator,
            it.classicOperatorHighlight,
            it.iconPackId,
            it.backgroundTheme,
            it.glassBorderMode,
            it.buttonContentScale,
            it.showExpressionHistoryLine,
            it.soundEffectEnabled,
            it.autoScientificOnPhoneLandscape,
            it.language
        )
    },
    restore = { list ->
        IosStyleSettingsData(
            styleId = list.getOrNull(0) as? String ?: IosCalculatorStyle.IOS_27.id,
            accentColorId = list.getOrNull(1) as? String ?: IosAccentColorOption.FOLLOW_VERSION.id,
            layoutOverride = list.getOrNull(2) as? String ?: "auto",
            buttonShapeStyle = list.getOrNull(3) as? String ?: "circle",
            buttonSpacingMode = list.getOrNull(4) as? String ?: "normal",
            displayFontWeight = list.getOrNull(5) as? String ?: "auto",
            displayFontScale = list.getOrNull(6) as? String ?: "normal",
            hapticFeedbackEnabled = list.getOrNull(7) as? Boolean ?: true,
            useThousandSeparator = list.getOrNull(8) as? Boolean ?: true,
            classicOperatorHighlight = list.getOrNull(9) as? Boolean ?: true,
            iconPackId = list.getOrNull(10) as? String ?: IosIconPackOption.DEFAULT_VECTOR.id,
            backgroundTheme = list.getOrNull(11) as? String ?: "auto",
            glassBorderMode = list.getOrNull(12) as? String ?: "auto",
            buttonContentScale = list.getOrNull(13) as? String ?: "normal",
            showExpressionHistoryLine = list.getOrNull(14) as? String ?: "auto",
            soundEffectEnabled = list.getOrNull(15) as? Boolean ?: false,
            autoScientificOnPhoneLandscape = list.getOrNull(16) as? Boolean ?: true,
            language = list.getOrNull(17) as? String ?: "auto"
        )
    }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosCalculatorApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current

    // 單次讀取已儲存的 iOS 樣式與首次啟動旗標，並使用 rememberSaveable 鎖定狀態
    val initialPrefs = remember { IosStylePreferences.loadInitial(context) }
    var styleSettings by rememberSaveable(stateSaver = IosStyleSettingsSaver) {
        mutableStateOf(initialPrefs.first)
    }
    var showSettingsScreen by rememberSaveable { mutableStateOf(false) }
    var showFirstLaunchDialog by rememberSaveable {
        mutableStateOf(!initialPrefs.second)
    }

    val resolved = remember(styleSettings) { resolveIosAppearance(styleSettings) }
    val lang = resolved.language
    val currentStyle = resolved.style
    val effectiveIsClassicLayout = resolved.isClassicLayout

    fun updateStyleSettings(newData: IosStyleSettingsData) {
        styleSettings = newData
        IosStylePreferences.save(context, newData)
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = resolved.isLightTheme
            insetsController.isAppearanceLightNavigationBars = resolved.isLightTheme
        }
    }

    // 全部改用 rememberSaveable 保存狀態，確保手機直橫向旋轉或平板調整視窗時絕不遺失算式或跳回「基本」模式
    var expression by rememberSaveable { mutableStateOf("0") }
    var historyExpression by rememberSaveable { mutableStateOf("") }
    var isResultShown by rememberSaveable { mutableStateOf(false) }
    var lastOpChar by rememberSaveable { mutableStateOf("") }
    var lastOpOperand by rememberSaveable { mutableStateOf("") }

    val lastOperation: LastOperation? = if (lastOpChar.isNotEmpty() && lastOpOperand.isNotEmpty()) {
        LastOperation(lastOpChar[0], lastOpOperand)
    } else null

    // 歷程記錄清單與抽屜開關狀態（首幀完成後改於背景 IO 讀取與解析 JSON，避免阻塞冷啟動主執行緒）
    var historyItems by remember { mutableStateOf<List<CalculationHistoryItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            loadHistoryFromPrefs(context)
        }
        if (loaded.isNotEmpty()) {
            historyItems = if (historyItems.isEmpty()) {
                loaded
            } else {
                val existingIds = historyItems.mapTo(HashSet(historyItems.size)) { it.id }
                historyItems + loaded.filterNot { it.id in existingIds }
            }
        }
    }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }

    // 科學計算機專屬狀態：記憶值 (Memory)、2nd 反函數切換、弧度/角度 (Rad/Deg)
    var memoryValue by rememberSaveable { mutableStateOf(0.0) }
    var hasMemory by rememberSaveable { mutableStateOf(false) }
    var isSecondMode by rememberSaveable { mutableStateOf(false) }
    var isRadians by rememberSaveable { mutableStateOf(false) } // 預設為度數 (Degrees)

    // 彈出選單與「拷貝」氣泡狀態（暫時移除數學備忘錄顯示，若舊狀態為數學備忘錄則重置為基本）
    var showModeMenu by rememberSaveable { mutableStateOf(false) }
    var selectedMode by rememberSaveable { mutableStateOf("基本") }
    var manualModeInPhoneLandscape by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (selectedMode == "數學備忘錄") {
            selectedMode = "基本"
        }
    }
    var isConvertEnabled by rememberSaveable { mutableStateOf(false) }
    var showCopyBubble by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = !showFirstLaunchDialog && (showSettingsScreen || showHistorySheet || showModeMenu)) {
        when {
            showSettingsScreen -> showSettingsScreen = false
            showHistorySheet -> showHistorySheet = false
            showModeMenu -> showModeMenu = false
        }
    }

    val basicOperators = CalculatorEngine.basicOperators
    val allBinaryOperators = CalculatorEngine.allBinaryOperators
    val scrollState = rememberScrollState()

    LaunchedEffect(expression) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // 根據手冊規則決定顯示 C 還是 AC
    val clearText = if (
        (expression == "0" && historyExpression.isEmpty()) ||
        expression.lastOrNull() in allBinaryOperators ||
        expression.endsWith("logᵧ(") ||
        isResultShown
    ) "AC" else "C"

    fun formatDoublePlain(value: Double): String = CalculatorEngine.formatDoublePlain(value)
    fun formatExpressionForDisplay(expr: String): String =
        if (styleSettings.useThousandSeparator) CalculatorEngine.formatExpressionForDisplay(expr) else expr
    fun evaluateExpression(rawExpr: String): String = CalculatorEngine.evaluateExpression(rawExpr, isRadians)

    fun recordHistory(formattedExpr: String, rawRes: String) {
        if (rawRes == "錯誤" || formattedExpr.isEmpty()) return
        val displayRes = formatExpressionForDisplay(rawRes)
        if (formattedExpr == displayRes) return
        val newItem = CalculationHistoryItem(
            id = System.nanoTime(),
            expression = formattedExpr,
            rawResult = rawRes,
            displayResult = displayRes,
            timestamp = System.currentTimeMillis()
        )
        val updated = listOf(newItem) + historyItems
        historyItems = updated
        saveHistoryToPrefs(context, updated)
    }

    fun extractLastOperation(expr: String): LastOperation? {
        val clean = expr.trim()
        val lastOpIdx = clean.indexOfLast { it in basicOperators }
        if (lastOpIdx <= 0 || lastOpIdx == clean.lastIndex) return null
        val op = clean[lastOpIdx]
        val rightOperand = clean.substring(lastOpIdx + 1)
        return if (rightOperand.isNotEmpty()) LastOperation(op, rightOperand) else null
    }

    // 輔助函式：往算式附加常數或前綴函數 (若當前是 "0" 或剛算完結果則直接替換)
    fun appendTokenOrFunction(token: String) {
        if (isResultShown || expression == "0") {
            expression = token
            if (isResultShown) historyExpression = ""
            isResultShown = false
        } else {
            expression += token
        }
    }

    fun onAction(btn: String) {
        if (styleSettings.hapticFeedbackEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (styleSettings.soundEffectEnabled) {
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
        if (btn == "MODE") {
            showCopyBubble = false
            showHistorySheet = false
            showModeMenu = !showModeMenu
            return
        }
        showModeMenu = false
        showCopyBubble = false
        showHistorySheet = false

        if (expression == "錯誤") {
            expression = "0"
            historyExpression = ""
            isResultShown = false
        }

        when (btn) {
            // ==================== 基本計算機按鍵 ====================
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
                if (isResultShown) {
                    expression = btn
                    historyExpression = ""
                    isResultShown = false
                } else if (expression.endsWith("%") || expression.endsWith("!") || expression.endsWith("²") || expression.endsWith("³")) {
                    expression += "×$btn"
                } else if (expression.endsWith("(-0)")) {
                    expression = expression.dropLast(2) + "$btn)"
                } else {
                    val lastOpIndex = expression.indexOfLast { it in allBinaryOperators || it == '(' || it == 'E' }
                    val currentNumberSegment = if (lastOpIndex == -1) expression else expression.substring(lastOpIndex + 1)

                    if (currentNumberSegment == "0") {
                        expression = expression.dropLast(1) + btn
                    } else if (currentNumberSegment.count { it.isDigit() } < 15) {
                        expression += btn
                    }
                }
            }
            "." -> {
                if (isResultShown) {
                    expression = "0."
                    historyExpression = ""
                    isResultShown = false
                } else {
                    val lastOpIndex = expression.indexOfLast { it in allBinaryOperators || it == '(' || it == 'E' }
                    val currentNumberSegment = if (lastOpIndex == -1) expression else expression.substring(lastOpIndex + 1)
                    if (currentNumberSegment.isEmpty() || currentNumberSegment in setOf("π", "ℯ")) {
                        expression += "0."
                    } else if (!currentNumberSegment.contains(".") && (lastOpIndex == -1 || expression[lastOpIndex] != 'E')) {
                        expression += "."
                    }
                }
            }
            "DEL" -> {
                if (isResultShown) {
                    historyExpression = ""
                    isResultShown = false
                }
                val matchedPrefix = CalculatorEngine.funcPrefixes.firstOrNull { expression.endsWith(it) }
                expression = when {
                    matchedPrefix != null -> {
                        val dropped = expression.dropLast(matchedPrefix.length)
                        if (dropped.isEmpty()) "0" else dropped
                    }
                    expression.length > 1 -> {
                        val dropped = expression.dropLast(1)
                        if (dropped == "-" || dropped.isEmpty()) "0" else dropped
                    }
                    else -> "0"
                }
            }
            "AC" -> {
                expression = "0"
                historyExpression = ""
                isResultShown = false
                lastOpChar = ""
                lastOpOperand = ""
            }
            "C" -> {
                val lastOpIndex = expression.indexOfLast { it in allBinaryOperators || it == '(' }
                expression = if (lastOpIndex != -1) {
                    expression.substring(0, lastOpIndex + 1)
                } else {
                    "0"
                }
            }
            "÷", "×", "−", "+" -> {
                isResultShown = false
                val opChar = btn[0]
                if (expression.isNotEmpty()) {
                    val lastChar = expression.last()
                    if (lastChar in allBinaryOperators) {
                        expression = expression.dropLast(1) + opChar
                    } else if (expression.endsWith("logᵧ(")) {
                        expression = expression.dropLast("logᵧ(".length) + opChar
                    } else {
                        expression += opChar
                    }
                }
            }
            "%" -> {
                isResultShown = false
                if (expression.isNotEmpty() && !expression.endsWith("%") && expression.last() !in allBinaryOperators) {
                    expression += "%"
                }
            }
            "+/-" -> {
                isResultShown = false
                // 支援科學記號 EE 後直接切換正負指數 (例如 5E -> 5E- 或 5E3 -> 5E-3)
                val lastEIndex = expression.lastIndexOf('E')
                val lastOpIndex = expression.indexOfLast { it in allBinaryOperators }
                if (lastEIndex > lastOpIndex) {
                    val afterE = expression.substring(lastEIndex + 1)
                    expression = if (afterE.startsWith("-")) {
                        expression.substring(0, lastEIndex + 1) + afterE.drop(1)
                    } else {
                        expression.substring(0, lastEIndex + 1) + "-" + afterE
                    }
                } else if (lastOpIndex == -1) {
                    if (expression != "0") {
                        expression = if (expression.startsWith("-")) expression.drop(1) else "-$expression"
                    }
                } else {
                    val prefix = expression.substring(0, lastOpIndex + 1)
                    val currentNumStr = expression.substring(lastOpIndex + 1)
                    if (currentNumStr.startsWith("(-") && currentNumStr.endsWith(")")) {
                        expression = prefix + currentNumStr.substring(2, currentNumStr.length - 1)
                    } else if (currentNumStr.isNotEmpty() && currentNumStr != "0") {
                        expression = "$prefix(-$currentNumStr)"
                    } else if (currentNumStr.isEmpty()) {
                        expression = "$prefix(-0)"
                    }
                }
            }
            "=" -> {
                val cleanedForCheck = expression.replace(Regex("""\(-\d+(?:\.\d+)?\)"""), "0")
                val isComplexScientific = cleanedForCheck.any {
                    it in setOf('^', '√', '(', ')', '!', '²', '³', 'π', 'ℯ', 'E', '§') || it.isLetter()
                }
                val hasCurrentOperator = expression.drop(1).any { it in basicOperators }

                if (isComplexScientific || hasCurrentOperator) {
                    if (!isComplexScientific) {
                        val extracted = extractLastOperation(expression)
                        if (extracted != null) {
                            lastOpChar = extracted.operator.toString()
                            lastOpOperand = extracted.operandText
                        }
                    }
                    val formattedCurrent = formatExpressionForDisplay(expression)
                    val result = evaluateExpression(expression)
                    historyExpression = formattedCurrent
                    expression = result
                    isResultShown = true
                    recordHistory(formattedCurrent, result)
                } else if (lastOperation != null) {
                    val op = lastOperation
                    val repeatedExpr = "${expression}${op.operator}${op.operandText}"
                    val formattedCurrent = formatExpressionForDisplay(repeatedExpr)
                    val result = evaluateExpression(repeatedExpr)
                    historyExpression = formattedCurrent
                    expression = result
                    isResultShown = true
                    recordHistory(formattedCurrent, result)
                } else if (expression.endsWith("%")) {
                    val formattedCurrent = formatExpressionForDisplay(expression)
                    val result = evaluateExpression(expression)
                    historyExpression = formattedCurrent
                    expression = result
                    isResultShown = true
                    recordHistory(formattedCurrent, result)
                }
            }

            // ==================== 科學計算機按鍵 ====================
            "(" -> appendTokenOrFunction("(")
            ")" -> {
                if (expression.count { it == '(' } > expression.count { it == ')' }) {
                    expression += ")"
                }
            }
            // 記憶功能 (mc, m+, m-, mr)
            "mc" -> {
                memoryValue = 0.0
                hasMemory = false
            }
            "m+" -> {
                val currentVal = evaluateExpression(expression).toDoubleOrNull()
                if (currentVal != null) {
                    memoryValue += currentVal
                    hasMemory = true
                    isResultShown = true
                }
            }
            "m-" -> {
                val currentVal = evaluateExpression(expression).toDoubleOrNull()
                if (currentVal != null) {
                    memoryValue -= currentVal
                    hasMemory = true
                    isResultShown = true
                }
            }
            "mr" -> {
                val memStr = formatDoublePlain(memoryValue)
                if (isResultShown || expression == "0") {
                    expression = memStr
                    isResultShown = false
                } else if (expression.last() in allBinaryOperators || expression.endsWith("(")) {
                    expression += memStr
                } else {
                    expression = memStr
                }
            }
            // 2nd 切換鍵
            "2nd" -> {
                isSecondMode = !isSecondMode
            }
            // 指數與次方
            "x²" -> {
                isResultShown = false
                expression += "²"
            }
            "x³" -> {
                isResultShown = false
                expression += "³"
            }
            "xʸ" -> {
                isResultShown = false
                expression += "^"
            }
            "eˣ" -> appendTokenOrFunction("ℯ^(")
            "yˣ" -> {
                isResultShown = false
                expression += "^"
            }
            "10ˣ" -> appendTokenOrFunction("10^(")
            "2ˣ" -> appendTokenOrFunction("2^(")
            // 倒數、開根號、對數
            "1/x" -> {
                if (isResultShown) {
                    historyExpression = ""
                    isResultShown = false
                    expression = "1÷($expression)"
                } else if (expression == "0") {
                    expression = "1÷("
                } else if (expression.last() in allBinaryOperators || expression.endsWith("(")) {
                    expression += "1÷("
                } else {
                    val lastOpIdx = expression.indexOfLast { it in allBinaryOperators }
                    expression = if (lastOpIdx == -1) {
                        "1÷($expression)"
                    } else {
                        val prefix = expression.substring(0, lastOpIdx + 1)
                        val operand = expression.substring(lastOpIdx + 1)
                        "${prefix}1÷($operand)"
                    }
                }
            }
            "²√x" -> appendTokenOrFunction("²√(")
            "³√x" -> appendTokenOrFunction("³√(")
            "ʸ√x" -> {
                isResultShown = false
                expression += "√"
            }
            "ln" -> appendTokenOrFunction("ln(")
            "logᵧ" -> {
                // 以 y 為底的對數：先輸入真數 x，再按 logᵧ，再輸入底數 y (例如 8 logᵧ(2) = 3)
                isResultShown = false
                if (!expression.endsWith("logᵧ(") && expression.last() !in allBinaryOperators) {
                    expression += "logᵧ("
                }
            }
            "log₁₀" -> appendTokenOrFunction("log₁₀(")
            "log₂" -> appendTokenOrFunction("log₂(")
            // 階乘、三角函數、常數 e、科學記號 EE
            "x!" -> {
                isResultShown = false
                expression += "!"
            }
            "sin" -> appendTokenOrFunction("sin(")
            "cos" -> appendTokenOrFunction("cos(")
            "tan" -> appendTokenOrFunction("tan(")
            "sin⁻¹" -> appendTokenOrFunction("sin⁻¹(")
            "cos⁻¹" -> appendTokenOrFunction("cos⁻¹(")
            "tan⁻¹" -> appendTokenOrFunction("tan⁻¹(")
            "e" -> appendTokenOrFunction("ℯ")
            "EE" -> {
                isResultShown = false
                if (expression.last().isDigit()) {
                    expression += "E"
                }
            }
            // 隨機數、雙曲函數、圓周率 π、弧度/角度切換
            "Rand" -> {
                val randStr = formatDoublePlain(Math.random())
                if (isResultShown || expression == "0") {
                    expression = randStr
                    isResultShown = false
                } else if (expression.last() in allBinaryOperators || expression.endsWith("(")) {
                    expression += randStr
                } else {
                    expression = randStr
                }
            }
            "sinh" -> appendTokenOrFunction("sinh(")
            "cosh" -> appendTokenOrFunction("cosh(")
            "tanh" -> appendTokenOrFunction("tanh(")
            "sinh⁻¹" -> appendTokenOrFunction("sinh⁻¹(")
            "cosh⁻¹" -> appendTokenOrFunction("cosh⁻¹(")
            "tanh⁻¹" -> appendTokenOrFunction("tanh⁻¹(")
            "π" -> appendTokenOrFunction("π")
            "Rad", "Deg" -> {
                isRadians = !isRadians
            }
        }
    }

    val generation = resolved.generation

    // 經典 iOS 12~17 或關閉算式過程列時採用單行數值顯示：僅顯示當前正在輸入或運算結果的數值（保留小數點與負號狀態）
    val rawDisplayExpr = remember(expression, isResultShown, resolved.showHistoryLine) {
        if (resolved.showHistoryLine || isResultShown || expression == "錯誤") {
            expression
        } else {
            val cleaned = expression.replace(Regex("""\(-\d+(?:\.\d+)?\)"""), "0")
            val hasSciToken = cleaned.any { it in setOf('(', ')', 'π', 'ℯ') || it.isLetter() }
            if (hasSciToken) {
                expression
            } else {
                val baseExpr = if (expression.lastOrNull() in allBinaryOperators) {
                    expression.dropLast(1)
                } else {
                    expression
                }
                val lastOpIdx = baseExpr.indexOfLast { it in allBinaryOperators }
                val segment = if (lastOpIdx == -1) baseExpr else baseExpr.substring(lastOpIdx + 1)
                when {
                    segment.startsWith("(-") && segment.endsWith(")") -> "-" + segment.substring(2, segment.length - 1)
                    segment.isEmpty() -> "0"
                    else -> segment
                }
            }
        }
    }
    val displayString = if (rawDisplayExpr == "錯誤") {
        tr(lang, "錯誤", "错误", "Error")
    } else {
        formatExpressionForDisplay(rawDisplayExpr)
    }

    // iOS 18 第一排第 1 顆按鍵動態切換 AC（清空/剛算完）與 ⌫（輸入中）
    val ios18TopLeftButton = if (
        (expression == "0" && historyExpression.isEmpty()) || isResultShown
    ) "AC" else "DEL"

    val activeOperatorChar: Char? = if (!isResultShown && expression.lastOrNull() in basicOperators) {
        expression.lastOrNull()
    } else null
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 36.dp.toPx() }

    // 使用 BoxWithConstraints 直接排版（移除多餘 Material3 Scaffold SubcomposeLayout 降低首幀開銷）
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(resolved.backgroundColor)
            .systemBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showModeMenu = false
                showCopyBubble = false
            }
    ) {
            val isLandscape = maxWidth > maxHeight
            val isTablet = minOf(maxWidth, maxHeight) >= 600.dp
            val isCompactLandscape = isLandscape && !isTablet
            val isPhoneLandscape = isLandscape && !isTablet

            // 手機橫向時可自動切為科學計算機（受設定開關控制），轉回直向恢復原本直向模式；平板橫向預設維持基本計算機
            val shouldAutoSci = isPhoneLandscape && styleSettings.autoScientificOnPhoneLandscape
            LaunchedEffect(shouldAutoSci) {
                if (!shouldAutoSci) {
                    manualModeInPhoneLandscape = null
                }
            }
            val effectiveMode = if (shouldAutoSci) {
                manualModeInPhoneLandscape ?: "科學"
            } else {
                selectedMode
            }
            val isScientific = effectiveMode == "科學"

            // 平板左側滑出歷程側邊欄寬度（約佔螢幕 1/5 寬度）
            val sidebarWidth = if (isLandscape) {
                (maxWidth * 0.20f).coerceIn(200.dp, 280.dp)
            } else {
                (maxWidth * 0.28f).coerceIn(210.dp, 280.dp)
            }
            val paneWidth = if (isTablet && showHistorySheet) {
                (maxWidth - sidebarWidth).coerceAtLeast(320.dp)
            } else {
                maxWidth
            }

            // ==================== 基本與科學計算機模式（精準對應 iOS 27 / 26 / 18 / 17~12） ====================
            val useSideBySideScientific = isScientific && (isLandscape || isTablet)

            val dynamicFontSize = when {
                isCompactLandscape -> when {
                    isConvertEnabled -> if (displayString.length > 12) 22.sp else 28.sp
                    displayString.length > 16 -> 26.sp
                    displayString.length > 10 -> 32.sp
                    else -> 40.sp
                }
                isTablet -> when {
                    isConvertEnabled -> when {
                        isLandscape -> if (displayString.length > 14) 32.sp else 42.sp
                        else -> if (displayString.length > 14) 42.sp else 54.sp
                    }
                    displayString.length > 16 -> 46.sp
                    displayString.length > 11 -> 58.sp
                    else -> 72.sp
                }
                isScientific -> when {
                    displayString.length > 14 -> 34.sp
                    displayString.length > 10 -> 42.sp
                    else -> 52.sp
                }
                else -> when {
                    displayString.length > 14 -> 40.sp
                    displayString.length > 10 -> 52.sp
                    displayString.length > 7 -> 66.sp
                    else -> 82.sp
                }
            } * resolved.displayFontScaleMultiplier

            val sciRows = remember(isScientific, isSecondMode, isRadians, generation) {
                if (!isScientific) {
                    emptyList()
                } else {
                    val radDegButton = if (isRadians) "Deg" else "Rad"
                    val sciFifthRow = if (generation == IosLayoutGeneration.CLASSIC_12_17) {
                        listOf(radDegButton, if (isSecondMode) "sinh⁻¹" else "sinh", if (isSecondMode) "cosh⁻¹" else "cosh", if (isSecondMode) "tanh⁻¹" else "tanh", "π", "Rand")
                    } else {
                        listOf("Rand", if (isSecondMode) "sinh⁻¹" else "sinh", if (isSecondMode) "cosh⁻¹" else "cosh", if (isSecondMode) "tanh⁻¹" else "tanh", "π", radDegButton)
                    }
                    listOf(
                        listOf("(", ")", "mc", "m+", "m-", "mr"),
                        listOf("2nd", "x²", "x³", "xʸ", if (isSecondMode) "yˣ" else "eˣ", if (isSecondMode) "2ˣ" else "10ˣ"),
                        listOf("1/x", "²√x", "³√x", "ʸ√x", if (isSecondMode) "logᵧ" else "ln", if (isSecondMode) "log₂" else "log₁₀"),
                        listOf("x!", if (isSecondMode) "sin⁻¹" else "sin", if (isSecondMode) "cos⁻¹" else "cos", if (isSecondMode) "tan⁻¹" else "tan", "e", "EE"),
                        sciFifthRow
                    )
                }
            }

            fun copyHistoryItemResult(text: String) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Calculator History", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    context,
                    tr(lang, "已拷貝：$text", "已拷贝：$text", "Copied: $text"),
                    Toast.LENGTH_SHORT
                ).show()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 平板專屬：從左側滑出、約佔螢幕 1/5 寬度的歷程記錄側邊欄（科學與基本模式皆適用）
                    if (isTablet) {
                        AnimatedVisibility(
                            visible = showHistorySheet,
                            enter = slideInHorizontally(tween(250)) { -it } +
                                expandHorizontally(tween(250), expandFrom = Alignment.Start),
                            exit = slideOutHorizontally(tween(220)) { -it } +
                                shrinkHorizontally(tween(220), shrinkTowards = Alignment.Start)
                        ) {
                            IosHistorySidebar(
                                historyItems = historyItems,
                                resolved = resolved,
                                width = sidebarWidth,
                                onSelectItem = { item ->
                                    historyExpression = item.expression
                                    expression = item.rawResult
                                    isResultShown = true
                                },
                                onCopyItem = { item -> copyHistoryItemResult(item.displayResult) },
                                onDeleteItem = { item ->
                                    val updated = historyItems.filterNot { it.id == item.id }
                                    historyItems = updated
                                    saveHistoryToPrefs(context, updated)
                                },
                                onClearAll = {
                                    historyItems = emptyList()
                                    saveHistoryToPrefs(context, emptyList())
                                },
                                onDismiss = { showHistorySheet = false }
                            )
                        }
                    }

                    // 右側（或全螢幕）主計算機工作區
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 1. 頂部導覽列：橫跨主工作區全寬，按鈕固定於左右角落（不顯示「iOS 27 · 科學計算機」標題文字）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = if (isTablet) 16.dp else 12.dp,
                                        vertical = if (isCompactLandscape) 2.dp else 6.dp
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val topBtnSize = if (isCompactLandscape) 34.dp else 40.dp
                                val isIos26Style = currentStyle.id == IosCalculatorStyle.IOS_26.id
                                val topBarBorderColor = if (
                                    styleSettings.glassBorderMode == "on" ||
                                    (styleSettings.glassBorderMode == "auto" && isIos26Style)
                                ) resolved.buttonBorderColor else null

                                // 左上角歷程按鈕
                                if (generation == IosLayoutGeneration.MODERN_18) {
                                    Box(
                                        modifier = Modifier
                                            .size(topBtnSize)
                                            .clip(CircleShape)
                                            .clickable {
                                                showModeMenu = false
                                                showCopyBubble = false
                                                showHistorySheet = !showHistorySheet
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_calc_history_ios18),
                                            contentDescription = "歷程記錄",
                                            tint = resolved.operatorColor,
                                            modifier = Modifier.size(if (isCompactLandscape) 20.dp else 24.dp)
                                        )
                                    }
                                } else {
                                    IosTopBarCircleButton(
                                        size = topBtnSize,
                                        backgroundColor = if (showHistorySheet) resolved.sciButtonPressedColor else resolved.topBarCircleColor,
                                        borderColor = topBarBorderColor,
                                        iconResId = if (isIos26Style) R.drawable.ic_calc_history_ios18 else R.drawable.ic_calc_history,
                                        iconTint = if (showHistorySheet) resolved.operatorColor else resolved.topBarIconColor,
                                        iconSize = if (isCompactLandscape) {
                                            if (isIos26Style) 18.dp else 20.dp
                                        } else {
                                            if (isIos26Style) 21.dp else 26.dp
                                        },
                                        contentDescription = "歷程記錄",
                                        onClick = {
                                            showModeMenu = false
                                            showCopyBubble = false
                                            showHistorySheet = !showHistorySheet
                                        }
                                    )
                                }

                                // 右上角模式選單按鈕：iOS 18 模式切換固定在鍵盤左下角，右上角永遠不顯示圖示
                                if (generation == IosLayoutGeneration.MODERN_18) {
                                    Spacer(modifier = Modifier.size(topBtnSize))
                                } else {
                                    IosTopBarCircleButton(
                                        size = topBtnSize,
                                        backgroundColor = resolved.topBarCircleColor,
                                        borderColor = topBarBorderColor,
                                        iconResId = if (isIos26Style) R.drawable.ic_calc_mode_ios26 else R.drawable.ic_calc_mode_ios27,
                                        iconTint = resolved.topBarIconColor,
                                        iconSize = if (isCompactLandscape) 18.dp else 22.dp,
                                        contentDescription = "切換模式與設定",
                                        onClick = {
                                            showCopyBubble = false
                                            if (!isTablet) {
                                                showHistorySheet = false
                                            }
                                            showModeMenu = !showModeMenu
                                        }
                                    )
                                }
                            }

                            // 2 & 3. 滿寬自適應顯示區 + 鍵盤區（iPadOS / 橫向全寬拉長填滿工作區）
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(
                                        horizontal = if (isTablet) 16.dp else 10.dp,
                                        vertical = if (isCompactLandscape) 2.dp else 4.dp
                                    ),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                val displayAreaWeight = when {
                                    isCompactLandscape -> if (isConvertEnabled) 0.38f else 0.24f
                                    isTablet && isLandscape -> if (isConvertEnabled) 0.36f else 0.22f
                                    isScientific && !useSideBySideScientific -> if (isConvertEnabled) 0.32f else 0.24f
                                    else -> if (isConvertEnabled) 0.36f else 0.30f
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(displayAreaWeight)
                                        .pointerInput(effectiveIsClassicLayout, scrollState.maxValue) {
                                            if (effectiveIsClassicLayout || scrollState.maxValue == 0) {
                                                var totalDrag = 0f
                                                var triggered = false
                                                detectHorizontalDragGestures(
                                                    onDragStart = {
                                                        totalDrag = 0f
                                                        triggered = false
                                                    },
                                                    onHorizontalDrag = { _, dragAmount ->
                                                        totalDrag += dragAmount
                                                        if (!triggered && abs(totalDrag) >= swipeThresholdPx) {
                                                            triggered = true
                                                            onAction("DEL")
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = if (isCompactLandscape) 2.dp else 4.dp),
                                    verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    if (isConvertEnabled) {
                                        val currentVal = remember(expression) {
                                            evaluateExpression(expression).toDoubleOrNull() ?: 0.0
                                        }
                                        IosUnitConverterPanel(
                                            currentInputValue = currentVal,
                                            fontFamily = InterDisplayFont,
                                            isCompact = isCompactLandscape || (isTablet && isLandscape),
                                            language = lang,
                                            modifier = Modifier.padding(bottom = if (isCompactLandscape) 2.dp else 6.dp)
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = showCopyBubble,
                                        enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.85f),
                                        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(resolved.menuBackgroundColor)
                                                .border(0.5.dp, resolved.menuDividerColor, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Calculator Result", displayString)
                                                    clipboard.setPrimaryClip(clip)
                                                    showCopyBubble = false
                                                    Toast.makeText(
                                                        context,
                                                        tr(lang, "已拷貝：$displayString", "已拷贝：$displayString", "Copied: $displayString"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = tr(lang, "拷貝", "拷贝", "Copy"),
                                                color = resolved.menuTextColor,
                                                fontSize = 14.sp,
                                                fontFamily = InterDisplayFont,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // iOS 12~17 經典模式預設隱藏上方歷史算式列，或依設定決定是否顯示
                                    if (historyExpression.isNotEmpty() && resolved.showHistoryLine) {
                                        Text(
                                            text = historyExpression,
                                            color = resolved.historyTextColor,
                                            fontSize = when {
                                                isCompactLandscape -> 16.sp
                                                isScientific -> 22.sp
                                                else -> 28.sp
                                            } * resolved.displayFontScaleMultiplier,
                                            fontFamily = InterDisplayFont,
                                            fontWeight = FontWeight.Normal,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .padding(bottom = 2.dp)
                                                .combinedClickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = { showCopyBubble = false },
                                                    onLongClick = { showCopyBubble = true }
                                                )
                                        )
                                    }

                                    // 手冊規則：「使用弧度而非度數時，螢幕會顯示標籤 Rad」
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (isScientific && isRadians) {
                                            Text(
                                                text = "Rad",
                                                color = resolved.historyTextColor,
                                                fontSize = if (isCompactLandscape) 13.sp else 16.sp,
                                                fontFamily = InterDisplayFont,
                                                fontWeight = FontWeight.Normal,
                                                modifier = Modifier.padding(bottom = if (isCompactLandscape) 4.dp else 10.dp, start = 4.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.width(1.dp))
                                        }

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .horizontalScroll(scrollState, enabled = scrollState.maxValue > 0)
                                                .combinedClickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = { showCopyBubble = false },
                                                    onLongClick = { showCopyBubble = true }
                                                ),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = displayString,
                                                color = resolved.displayTextColor,
                                                fontSize = dynamicFontSize,
                                                fontFamily = InterDisplayFont,
                                                fontWeight = resolved.displayFontWeight,
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                // 3. 按鈕網格區（每一列皆使用 Modifier.weight(1f) 動態自適應高度，絕不在橫向或平板溢位）
                                val keypadAreaWeight = 1f - displayAreaWeight
                                val rowSpacing = when {
                                    isCompactLandscape -> resolved.compactLandscapeSpacingDp
                                    isTablet -> resolved.tabletSpacingDp
                                    isScientific -> resolved.sciSpacingDp
                                    else -> resolved.basicSpacingDp
                                }
                                val colSpacing = rowSpacing
                                val isSideBySideCompact = isCompactLandscape || (isTablet && (!isLandscape || paneWidth < 760.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(keypadAreaWeight)
                                        .padding(bottom = if (isCompactLandscape) 4.dp else 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(rowSpacing)
                                ) {
                                    if (useSideBySideScientific) {
                                        // ========== 橫向與平板科學模式：iOS 經典 5 列 × 10 欄（左 6 欄科學鍵 + 右 4 欄基本鍵）==========
                                        for (rowIdx in 0 until 5) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(colSpacing)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .weight(5.7f)
                                                        .fillMaxHeight(),
                                                    horizontalArrangement = Arrangement.spacedBy(colSpacing)
                                                ) {
                                                    sciRows[rowIdx].forEach { symbol ->
                                                        val isHighlighted = (symbol == "2nd" && isSecondMode) || (symbol == "mr" && hasMemory)
                                                        IosScientificButton(
                                                            symbol = symbol,
                                                            isHighlighted = isHighlighted,
                                                            resolved = resolved,
                                                            isTablet = isTablet,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight(),
                                                            onClick = { onAction(symbol) }
                                                        )
                                                    }
                                                }

                                                IosBasicKeypadRow(
                                                    rowIdx = rowIdx,
                                                    clearText = clearText,
                                                    ios18TopLeftButton = ios18TopLeftButton,
                                                    generation = generation,
                                                    activeOperatorChar = activeOperatorChar,
                                                    classicOperatorHighlight = styleSettings.classicOperatorHighlight,
                                                    resolved = resolved,
                                                    isCompactScientific = isSideBySideCompact,
                                                    colSpacing = colSpacing,
                                                    onAction = { onAction(it) },
                                                    modifier = Modifier
                                                        .weight(4.4f)
                                                        .fillMaxHeight()
                                                )
                                            }
                                        }
                                    } else if (isScientific) {
                                        // ========== 手機直向科學模式：上方 5×6 科學鍵 + 下方 5×4 基本鍵（動態比例高度） ==========
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(0.44f),
                                            verticalArrangement = Arrangement.spacedBy(rowSpacing)
                                        ) {
                                            sciRows.forEach { row ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f),
                                                    horizontalArrangement = Arrangement.spacedBy(colSpacing)
                                                ) {
                                                    row.forEach { symbol ->
                                                        val isHighlighted = (symbol == "2nd" && isSecondMode) || (symbol == "mr" && hasMemory)
                                                        IosScientificButton(
                                                            symbol = symbol,
                                                            isHighlighted = isHighlighted,
                                                            resolved = resolved,
                                                            isTablet = false,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight(),
                                                            onClick = { onAction(symbol) }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(0.56f),
                                            verticalArrangement = Arrangement.spacedBy(rowSpacing)
                                        ) {
                                            for (rowIdx in 0 until 5) {
                                                IosBasicKeypadRow(
                                                    rowIdx = rowIdx,
                                                    clearText = clearText,
                                                    ios18TopLeftButton = ios18TopLeftButton,
                                                    generation = generation,
                                                    activeOperatorChar = activeOperatorChar,
                                                    classicOperatorHighlight = styleSettings.classicOperatorHighlight,
                                                    resolved = resolved,
                                                    isCompactScientific = true,
                                                    colSpacing = colSpacing,
                                                    onAction = { onAction(it) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f)
                                                )
                                            }
                                        }
                                    } else {
                                        // ========== 基本計算機模式（自動依 iOS 27/26、iOS 18、iOS 17~12 切換按鍵與圖示） ==========
                                        for (rowIdx in 0 until 5) {
                                            IosBasicKeypadRow(
                                                rowIdx = rowIdx,
                                                clearText = clearText,
                                                ios18TopLeftButton = ios18TopLeftButton,
                                                generation = generation,
                                                activeOperatorChar = activeOperatorChar,
                                                classicOperatorHighlight = styleSettings.classicOperatorHighlight,
                                                resolved = resolved,
                                                isCompactScientific = isCompactLandscape,
                                                colSpacing = colSpacing,
                                                onAction = { onAction(it) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 模式與設定彈出選單：iOS 18 固定對齊鍵盤左下角計算機按鍵上方，其餘版本對齊右上角模式按鈕下方
                        val isIos18ModeMenu = generation == IosLayoutGeneration.MODERN_18
                        val modeMenuAlignment = when {
                            isIos18ModeMenu && useSideBySideScientific -> Alignment.BottomCenter
                            isIos18ModeMenu -> Alignment.BottomStart
                            else -> Alignment.TopEnd
                        }
                        val modeMenuPadding = when {
                            isIos18ModeMenu && useSideBySideScientific -> PaddingValues(
                                start = 80.dp,
                                bottom = if (isCompactLandscape) 54.dp else 78.dp
                            )
                            isIos18ModeMenu -> PaddingValues(
                                start = 16.dp,
                                bottom = if (isCompactLandscape) 54.dp else 84.dp
                            )
                            else -> PaddingValues(
                                top = if (isCompactLandscape) 46.dp else 56.dp,
                                end = if (isTablet) 20.dp else 16.dp
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showModeMenu,
                            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
                            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f),
                            modifier = Modifier
                                .align(modeMenuAlignment)
                                .padding(modeMenuPadding)
                        ) {
                            IosModePopupMenu(
                                selectedMode = effectiveMode,
                                isConvertEnabled = isConvertEnabled,
                                resolved = resolved,
                                onSelectMode = { mode ->
                                    if (shouldAutoSci) {
                                        manualModeInPhoneLandscape = mode
                                    } else {
                                        selectedMode = mode
                                    }
                                    showModeMenu = false
                                },
                                onToggleConvert = { enabled ->
                                    isConvertEnabled = enabled
                                },
                                onOpenSettings = {
                                    showModeMenu = false
                                    showSettingsScreen = true
                                }
                            )
                        }
                    }
                }

                // 手機專屬：點擊左上角時鐘/清單按鈕彈出的 iOS 風格歷程記錄底部抽屜
                if (!isTablet) {
                    AnimatedVisibility(
                        visible = showHistorySheet,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(200)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showHistorySheet = false
                                }
                        )
                    }

                    AnimatedVisibility(
                        visible = showHistorySheet,
                        enter = slideInVertically(tween(260)) { fullHeight -> fullHeight },
                        exit = slideOutVertically(tween(220)) { fullHeight -> fullHeight },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        IosHistorySheet(
                            historyItems = historyItems,
                            resolved = resolved,
                            isCompactHeight = isCompactLandscape,
                            onSelectItem = { item ->
                                historyExpression = item.expression
                                expression = item.rawResult
                                isResultShown = true
                                showHistorySheet = false
                            },
                            onCopyItem = { item -> copyHistoryItemResult(item.displayResult) },
                            onDeleteItem = { item ->
                                val updated = historyItems.filterNot { it.id == item.id }
                                historyItems = updated
                                saveHistoryToPrefs(context, updated)
                            },
                            onClearAll = {
                                historyItems = emptyList()
                                saveHistoryToPrefs(context, emptyList())
                            },
                            onDismiss = {
                                showHistorySheet = false
                            }
                        )
                    }
                }

                // 右上角選單開啟之「設定頁面」（滿寬／雙欄自適應 + 淺色白晝主題 + 右上角編輯按鈕）
                AnimatedVisibility(
                    visible = showSettingsScreen,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { fullHeight -> fullHeight / 8 },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { fullHeight -> fullHeight / 8 },
                    modifier = Modifier.fillMaxSize()
                ) {
                    IosSettingsScreen(
                        settingsData = styleSettings,
                        resolved = resolved,
                        isTablet = isTablet,
                        fontFamily = InterDisplayFont,
                        onUpdateSettings = { newData ->
                            updateStyleSettings(newData)
                        },
                        onResetDefaults = {
                            updateStyleSettings(IosStyleSettingsData())
                        },
                        onOpenFirstLaunchPicker = {
                            showSettingsScreen = false
                            showFirstLaunchDialog = true
                        },
                        onClose = {
                            showSettingsScreen = false
                        }
                    )
                }

                // 首次安裝啟動時顯示的 iOS 版本選擇畫面（預設推薦 iOS 27 最新主題）
                AnimatedVisibility(
                    visible = showFirstLaunchDialog,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(180)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    IosFirstLaunchVersionDialog(
                        currentSettings = styleSettings,
                        fontFamily = InterDisplayFont,
                        onSelectLanguage = { langCode ->
                            updateStyleSettings(styleSettings.copy(language = langCode))
                        },
                        onConfirmStyle = { chosenStyle ->
                            updateStyleSettings(styleSettings.copy(styleId = chosenStyle.id, layoutOverride = "auto"))
                            IosStylePreferences.setFirstLaunchCompleted(context, true)
                            showFirstLaunchDialog = false
                        }
                    )
                }
            }
    }
}

@Composable
private fun IosTopBarCircleButton(
    size: Dp,
    backgroundColor: Color,
    borderColor: Color?,
    iconResId: Int,
    iconTint: Color,
    iconSize: Dp,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (borderColor != null) Modifier.border(0.6.dp, borderColor, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun IosBasicKeypadRow(
    rowIdx: Int,
    clearText: String,
    ios18TopLeftButton: String,
    generation: IosLayoutGeneration,
    activeOperatorChar: Char?,
    classicOperatorHighlight: Boolean,
    resolved: ResolvedIosAppearance,
    isCompactScientific: Boolean,
    colSpacing: Dp,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isClassicLayout = generation == IosLayoutGeneration.CLASSIC_12_17
    if (rowIdx == 4 && isClassicLayout) {
        // iOS 12 ~ iOS 17 經典最後一排：[雙倍寬 0 鍵] | [.] | [=]（完美對齊上方 4 欄寬度）
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(colSpacing)
        ) {
            IosCalculatorButton(
                symbol = "0",
                isOperator = false,
                isTopFunction = false,
                isActiveOperator = false,
                isDoubleWidthZero = true,
                useClassicIcons = true,
                resolved = resolved,
                isCompactScientific = isCompactScientific,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = { onAction("0") }
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(colSpacing)
            ) {
                IosCalculatorButton(
                    symbol = ".",
                    isOperator = false,
                    isTopFunction = false,
                    isActiveOperator = false,
                    isDoubleWidthZero = false,
                    useClassicIcons = true,
                    resolved = resolved,
                    isCompactScientific = isCompactScientific,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onAction(".") }
                )
                IosCalculatorButton(
                    symbol = "=",
                    isOperator = true,
                    isTopFunction = false,
                    isActiveOperator = false,
                    isDoubleWidthZero = false,
                    useClassicIcons = true,
                    resolved = resolved,
                    isCompactScientific = isCompactScientific,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onAction("=") }
                )
            }
        }
    } else {
        val rowSymbols = when (generation) {
            IosLayoutGeneration.CLASSIC_12_17 -> when (rowIdx) {
                0 -> listOf(clearText, "+/-", "%", "÷")
                1 -> listOf("7", "8", "9", "×")
                2 -> listOf("4", "5", "6", "−")
                3 -> listOf("1", "2", "3", "+")
                else -> listOf("0", ".", "=")
            }
            IosLayoutGeneration.MODERN_18 -> when (rowIdx) {
                0 -> listOf(ios18TopLeftButton, "+/-", "%", "÷")
                1 -> listOf("7", "8", "9", "×")
                2 -> listOf("4", "5", "6", "−")
                3 -> listOf("1", "2", "3", "+")
                else -> listOf("MODE", "0", ".", "=")
            }
            IosLayoutGeneration.MODERN_26_27 -> when (rowIdx) {
                0 -> listOf("DEL", clearText, "%", "÷")
                1 -> listOf("7", "8", "9", "×")
                2 -> listOf("4", "5", "6", "−")
                3 -> listOf("1", "2", "3", "+")
                else -> listOf("+/-", "0", ".", "=")
            }
        }

        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(colSpacing)
        ) {
            rowSymbols.forEach { symbol ->
                val isOperator = symbol in BasicOperatorSymbols
                val isTopFunction = symbol in TopFunctionSymbols ||
                    ((generation == IosLayoutGeneration.CLASSIC_12_17 || generation == IosLayoutGeneration.MODERN_18) && symbol == "+/-")
                val isActiveOperator = classicOperatorHighlight && isClassicLayout &&
                    symbol in ActiveOperatorSymbols && activeOperatorChar == symbol[0]

                IosCalculatorButton(
                    symbol = symbol,
                    isOperator = isOperator,
                    isTopFunction = isTopFunction,
                    isActiveOperator = isActiveOperator,
                    isDoubleWidthZero = false,
                    useClassicIcons = isClassicLayout,
                    resolved = resolved,
                    isCompactScientific = isCompactScientific,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { onAction(symbol) },
                    onLongClick = {
                        if (symbol == "DEL" || symbol == "C") {
                            onAction("AC")
                        }
                    }
                )
            }
        }
    }
}

/**
 * 延遲初始化顏色動畫：首幀未互動前不啟動 Animatable 與協程，首次按下或高亮後再維持平滑漸變回饋。
 */
@Composable
private fun rememberLazyAnimatedColor(
    targetColor: Color,
    isPressed: Boolean,
    isHighlightedOrActive: Boolean,
    label: String
): Color {
    var hasInteracted by remember { mutableStateOf(false) }
    if (isPressed || isHighlightedOrActive) {
        hasInteracted = true
    }
    return if (hasInteracted) {
        animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(durationMillis = if (isPressed) 50 else 200),
            label = label
        ).value
    } else {
        targetColor
    }
}

// 科學計算機上方 5×6 專屬按鈕（支援上標 x²、下標 log₁₀、根號 ²√x 與斜體 e 排版）
@Composable
private fun IosScientificButton(
    symbol: String,
    isHighlighted: Boolean,
    resolved: ResolvedIosAppearance,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetBackgroundColor = when {
        isHighlighted -> resolved.sciButtonHighlightedColor
        isPressed -> resolved.sciButtonPressedColor
        else -> resolved.sciButtonColor
    }

    val backgroundColor = rememberLazyAnimatedColor(
        targetColor = targetBackgroundColor,
        isPressed = isPressed,
        isHighlightedOrActive = isHighlighted,
        label = "SciButtonBg"
    )

    // 將科學符號排版為上下標格式，100% 還原 iOS 截圖視覺
    val formattedText = remember(symbol) {
        val superStyle = SpanStyle(fontSize = 11.sp, baselineShift = BaselineShift.Superscript)
        val subStyle = SpanStyle(fontSize = 11.sp, baselineShift = BaselineShift.Subscript)
        buildAnnotatedString {
            when (symbol) {
                "2nd" -> { append("2"); withStyle(superStyle) { append("nd") } }
                "x²" -> { append("x"); withStyle(superStyle) { append("2") } }
                "x³" -> { append("x"); withStyle(superStyle) { append("3") } }
                "xʸ" -> { append("x"); withStyle(superStyle) { append("y") } }
                "eˣ" -> { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("e") }; withStyle(superStyle) { append("x") } }
                "yˣ" -> { append("y"); withStyle(superStyle) { append("x") } }
                "10ˣ" -> { append("10"); withStyle(superStyle) { append("x") } }
                "2ˣ" -> { append("2"); withStyle(superStyle) { append("x") } }
                "1/x" -> { withStyle(superStyle) { append("1") }; append("⁄"); withStyle(subStyle) { append("x") } }
                "²√x" -> { withStyle(superStyle) { append("2") }; append("√x") }
                "³√x" -> { withStyle(superStyle) { append("3") }; append("√x") }
                "ʸ√x" -> { withStyle(superStyle) { append("y") }; append("√x") }
                "log₁₀" -> { append("log"); withStyle(subStyle) { append("10") } }
                "log₂" -> { append("log"); withStyle(subStyle) { append("2") } }
                "logᵧ" -> { append("log"); withStyle(subStyle) { append("y") } }
                "sin⁻¹" -> { append("sin"); withStyle(superStyle) { append("−1") } }
                "cos⁻¹" -> { append("cos"); withStyle(superStyle) { append("−1") } }
                "tan⁻¹" -> { append("tan"); withStyle(superStyle) { append("−1") } }
                "sinh⁻¹" -> { append("sinh"); withStyle(superStyle) { append("−1") } }
                "cosh⁻¹" -> { append("cosh"); withStyle(superStyle) { append("−1") } }
                "tanh⁻¹" -> { append("tanh"); withStyle(superStyle) { append("−1") } }
                "e" -> { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("e") } }
                else -> append(symbol)
            }
        }
    }

    val borderColor = resolved.buttonBorderColor

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(resolved.sciButtonShape)
            .background(backgroundColor)
            .then(
                if (borderColor != null) Modifier.border(0.6.dp, borderColor, resolved.sciButtonShape)
                else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = formattedText,
            color = resolved.sciButtonTextColor,
            fontSize = (if (isTablet) {
                if (symbol.length >= 5) 15.sp else 18.sp
            } else {
                if (symbol.length >= 5) 14.sp else 16.sp
            }) * resolved.buttonContentScaleMultiplier,
            fontFamily = InterDisplayFont,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IosCalculatorButton(
    symbol: String,
    isOperator: Boolean,
    isTopFunction: Boolean,
    isActiveOperator: Boolean = false,
    isDoubleWidthZero: Boolean = false,
    useClassicIcons: Boolean = false,
    resolved: ResolvedIosAppearance,
    isCompactScientific: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val currentStyle = resolved.style
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetBackgroundColor = when {
        isActiveOperator -> if (isPressed) Color(0xFFE5E5EA) else Color.White
        isOperator -> if (isPressed) resolved.operatorPressedColor else resolved.operatorColor
        isTopFunction -> if (isPressed) resolved.topFuncButtonPressedColor else resolved.topFuncButtonColor
        else -> if (isPressed) resolved.digitButtonPressedColor else resolved.digitButtonColor
    }

    val contentColor = when {
        isActiveOperator -> resolved.operatorColor
        isOperator -> currentStyle.operatorButtonTextColor
        isTopFunction -> resolved.topFuncButtonTextColor
        else -> resolved.digitButtonTextColor
    }

    val backgroundColor = rememberLazyAnimatedColor(
        targetColor = targetBackgroundColor,
        isPressed = isPressed,
        isHighlightedOrActive = isActiveOperator,
        label = "ButtonBgAnimation"
    )

    val iconResId: Int? = when (symbol) {
        "DEL" -> R.drawable.ic_calc_backspace
        "+/-" -> if (useClassicIcons) R.drawable.ic_calc_plus_minus_classic else R.drawable.ic_calc_plus_minus
        "%" -> R.drawable.ic_op_percent
        "÷" -> R.drawable.ic_op_divide
        "×" -> R.drawable.ic_op_multiply
        "−" -> R.drawable.ic_op_minus
        "+" -> R.drawable.ic_op_plus
        "=" -> R.drawable.ic_op_equal
        "MODE" -> R.drawable.ic_calc_mode_ios27
        else -> null
    }

    val borderColor = resolved.buttonBorderColor

    Box(
        contentAlignment = if (isDoubleWidthZero) Alignment.CenterStart else Alignment.Center,
        modifier = modifier
            .clip(resolved.buttonShape)
            .background(backgroundColor)
            .then(
                if (borderColor != null) Modifier.border(0.6.dp, borderColor, resolved.buttonShape)
                else Modifier
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (iconResId != null) {
            val baseIconSize = if (isCompactScientific) {
                when (symbol) {
                    "+/-" -> if (useClassicIcons) 22.dp else 26.dp
                    "DEL", "MODE" -> 24.dp
                    else -> 22.dp
                }
            } else {
                when (symbol) {
                    "+/-" -> if (useClassicIcons) 28.dp else 36.dp
                    "DEL", "MODE" -> 32.dp
                    else -> 28.dp
                }
            }
            val iconSize = baseIconSize * resolved.buttonContentScaleMultiplier
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = if (symbol == "MODE") "切換模式與設定" else symbol,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        } else {
            val baseFontSize = if (isCompactScientific) {
                if (symbol == "AC" || symbol == "C") 22.sp else 26.sp
            } else {
                if (symbol == "AC" || symbol == "C") 34.sp else 40.sp
            }
            val textFontSize = baseFontSize * resolved.buttonContentScaleMultiplier

            if (isDoubleWidthZero) {
                // 經典 iOS 12~17 雙倍寬 0 鍵：數字 0 對齊左側第一欄中央
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.48f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = symbol,
                        color = contentColor,
                        fontSize = textFontSize,
                        fontFamily = InterDisplayFont,
                        fontWeight = FontWeight.Normal
                    )
                }
            } else {
                Text(
                    text = symbol,
                    color = contentColor,
                    fontSize = textFontSize,
                    fontFamily = InterDisplayFont,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun IosModePopupMenu(
    selectedMode: String,
    isConvertEnabled: Boolean,
    resolved: ResolvedIosAppearance,
    onSelectMode: (String) -> Unit,
    onToggleConvert: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    // 暫時移除「數學備忘錄」選項顯示，僅保留「基本」、「科學」、「轉換」與「設定」
    val lang = resolved.language
    val modes = listOf(
        "基本" to R.drawable.ic_menu_basic,
        "科學" to R.drawable.ic_menu_scientific
    )
    val accentColor = resolved.operatorColor

    Column(
        modifier = Modifier
            .width(235.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(resolved.menuBackgroundColor)
            .border(0.5.dp, resolved.menuDividerColor, RoundedCornerShape(16.dp))
    ) {
        modes.forEach { (mode, modeIconRes) ->
            val isSelected = selectedMode == mode
            val itemColor = if (isSelected) accentColor else resolved.menuTextColor
            val displayMode = when (mode) {
                "科學" -> tr(lang, "科學", "科学", "Scientific")
                else -> tr(lang, "基本", "基本", "Basic")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_menu_check),
                                contentDescription = tr(lang, "已選取", "已选取", "Selected"),
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = displayMode,
                        color = itemColor,
                        fontSize = 16.sp,
                        fontFamily = InterDisplayFont,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    painter = painterResource(id = modeIconRes),
                    contentDescription = displayMode,
                    tint = itemColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            HorizontalDivider(color = resolved.menuDividerColor, thickness = 0.5.dp)
        }

        // 「轉換」開關列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleConvert(!isConvertEnabled) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_menu_convert),
                    contentDescription = tr(lang, "轉換", "转换", "Convert"),
                    tint = if (isConvertEnabled) accentColor else resolved.menuTextColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tr(lang, "轉換", "转换", "Convert"),
                    color = if (isConvertEnabled) accentColor else resolved.menuTextColor,
                    fontSize = 16.sp,
                    fontFamily = InterDisplayFont,
                    fontWeight = FontWeight.Medium
                )
            }
            Switch(
                checked = isConvertEnabled,
                onCheckedChange = onToggleConvert,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = if (resolved.isLightTheme) Color(0xFFD1D1D6) else Color(0xFF48484A)
                )
            )
        }

        HorizontalDivider(color = resolved.menuDividerColor, thickness = 0.5.dp)

        // 底部「設定」入口列（iOS 27~12 主題樣式與自定義設定）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSettings() }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_menu_settings),
                    contentDescription = tr(lang, "設定", "设置", "Settings"),
                    tint = resolved.menuTextColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tr(lang, "設定", "设置", "Settings"),
                    color = resolved.menuTextColor,
                    fontSize = 16.sp,
                    fontFamily = InterDisplayFont,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "›",
                color = resolved.historyTextColor,
                fontSize = 18.sp,
                fontFamily = InterDisplayFont,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatHistoryDateHeader(timestamp: Long, lang: AppLanguage): String {
    val nowCal = Calendar.getInstance()
    val itemCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = nowCal.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR)
    val dayDiff = nowCal.get(Calendar.DAY_OF_YEAR) - itemCal.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> tr(lang, "今天", "今天", "Today")
        sameYear && dayDiff == 1 -> tr(lang, "昨天", "昨天", "Yesterday")
        sameYear -> when (lang) {
            AppLanguage.ZH_TW -> SimpleDateFormat("M月d日", Locale.TAIWAN).format(Date(timestamp))
            AppLanguage.ZH_CN -> SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE).format(Date(timestamp))
            AppLanguage.EN -> SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(timestamp))
        }
        else -> when (lang) {
            AppLanguage.ZH_TW -> SimpleDateFormat("yyyy年M月d日", Locale.TAIWAN).format(Date(timestamp))
            AppLanguage.ZH_CN -> SimpleDateFormat("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE).format(Date(timestamp))
            AppLanguage.EN -> SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(Date(timestamp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IosHistorySidebar(
    historyItems: List<CalculationHistoryItem>,
    resolved: ResolvedIosAppearance,
    width: Dp,
    onSelectItem: (CalculationHistoryItem) -> Unit,
    onCopyItem: (CalculationHistoryItem) -> Unit,
    onDeleteItem: (CalculationHistoryItem) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(resolved.sheetBackgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* 攔截點擊避免誤觸關閉 */ }
    ) {
        IosHistoryPanelContent(
            historyItems = historyItems,
            resolved = resolved,
            isCompactSidebar = true,
            onSelectItem = onSelectItem,
            onCopyItem = onCopyItem,
            onDeleteItem = onDeleteItem,
            onClearAll = onClearAll,
            onDismiss = onDismiss,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .fillMaxHeight()
                .background(resolved.menuDividerColor)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IosHistorySheet(
    historyItems: List<CalculationHistoryItem>,
    resolved: ResolvedIosAppearance,
    isCompactHeight: Boolean = false,
    onSelectItem: (CalculationHistoryItem) -> Unit,
    onCopyItem: (CalculationHistoryItem) -> Unit,
    onDeleteItem: (CalculationHistoryItem) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetBg = resolved.sheetBackgroundColor
    val disabledColor = if (resolved.isLightTheme) Color(0xFFC7C7CC) else Color(0xFF48484A)

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .fillMaxHeight(if (isCompactHeight) 0.86f else 0.64f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(sheetBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* 攔截點擊避免穿透關閉 */ }
    ) {
        // 頂部拖拽指示橫條 (Grabber)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(disabledColor)
            )
        }

        IosHistoryPanelContent(
            historyItems = historyItems,
            resolved = resolved,
            isCompactSidebar = false,
            onSelectItem = onSelectItem,
            onCopyItem = onCopyItem,
            onDeleteItem = onDeleteItem,
            onClearAll = onClearAll,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IosHistoryPanelContent(
    historyItems: List<CalculationHistoryItem>,
    resolved: ResolvedIosAppearance,
    isCompactSidebar: Boolean,
    onSelectItem: (CalculationHistoryItem) -> Unit,
    onCopyItem: (CalculationHistoryItem) -> Unit,
    onDeleteItem: (CalculationHistoryItem) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(historyItems.isEmpty()) {
        if (historyItems.isEmpty()) {
            isEditing = false
        }
    }

    val lang = resolved.language
    val groupedHistory = remember(historyItems, lang) {
        historyItems.groupBy { formatHistoryDateHeader(it.timestamp, lang) }
    }

    val accentColor = resolved.operatorColor
    val primaryText = resolved.displayTextColor
    val secondaryText = resolved.historyTextColor
    val dividerColor = resolved.menuDividerColor
    val disabledColor = if (resolved.isLightTheme) Color(0xFFC7C7CC) else Color(0xFF48484A)
    val contentHorizontalPadding = if (isCompactSidebar) 14.dp else 20.dp

    Column(modifier = modifier) {
        // 頂部標題列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompactSidebar) 14.dp else 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr(lang, "歷程記錄", "历史记录", "History"),
                color = primaryText,
                fontSize = if (isCompactSidebar) 16.sp else 18.sp,
                fontFamily = InterDisplayFont,
                fontWeight = FontWeight.Medium
            )

            Box(
                modifier = Modifier
                    .size(if (isCompactSidebar) 26.dp else 30.dp)
                    .clip(CircleShape)
                    .background(resolved.menuBackgroundColor)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = secondaryText,
                    fontSize = if (isCompactSidebar) 12.sp else 14.sp,
                    fontFamily = InterDisplayFont,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

        // 中間清單區
        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_calc_history),
                        contentDescription = null,
                        tint = disabledColor,
                        modifier = Modifier.size(if (isCompactSidebar) 34.dp else 44.dp)
                    )
                    Text(
                        text = tr(lang, "沒有歷程記錄", "没有历史记录", "No History"),
                        color = secondaryText,
                        fontSize = if (isCompactSidebar) 14.sp else 17.sp,
                        fontFamily = InterDisplayFont,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                groupedHistory.forEach { (dateHeader, itemsInGroup) ->
                    item(key = "header_$dateHeader") {
                        Text(
                            text = dateHeader,
                            color = secondaryText,
                            fontSize = if (isCompactSidebar) 12.sp else 13.sp,
                            fontFamily = InterDisplayFont,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = contentHorizontalPadding, vertical = 6.dp)
                        )
                    }

                    items(itemsInGroup, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (!isEditing) {
                                            onSelectItem(item)
                                        }
                                    },
                                    onLongClick = {
                                        onCopyItem(item)
                                    }
                                )
                                .padding(horizontal = contentHorizontalPadding, vertical = if (isCompactSidebar) 8.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AnimatedVisibility(visible = isEditing) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .size(if (isCompactSidebar) 20.dp else 24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF453A))
                                        .clickable { onDeleteItem(item) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(if (isCompactSidebar) 9.dp else 11.dp)
                                            .height(2.dp)
                                            .background(Color.White)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = item.expression,
                                    color = secondaryText,
                                    fontSize = if (isCompactSidebar) 13.sp else 16.sp,
                                    fontFamily = InterDisplayFont,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.displayResult,
                                    color = primaryText,
                                    fontSize = if (isCompactSidebar) 20.sp else 26.sp,
                                    fontFamily = InterDisplayFont,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(
                            color = dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = contentHorizontalPadding)
                        )
                    }
                }
            }
        }

        // 底部操作列（iOS 原廠「編輯 / 完成」與「清除」）
        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentHorizontalPadding, vertical = if (isCompactSidebar) 12.dp else 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) tr(lang, "完成", "完成", "Done") else tr(lang, "編輯", "编辑", "Edit"),
                color = if (historyItems.isNotEmpty()) accentColor else disabledColor,
                fontSize = if (isCompactSidebar) 15.sp else 16.sp,
                fontFamily = InterDisplayFont,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(enabled = historyItems.isNotEmpty()) {
                    isEditing = !isEditing
                }
            )

            Text(
                text = tr(lang, "清除", "清除", "Clear"),
                color = if (historyItems.isNotEmpty()) Color(0xFFFF453A) else disabledColor,
                fontSize = if (isCompactSidebar) 15.sp else 16.sp,
                fontFamily = InterDisplayFont,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(enabled = historyItems.isNotEmpty()) {
                    onClearAll()
                }
            )
        }
    }
}
