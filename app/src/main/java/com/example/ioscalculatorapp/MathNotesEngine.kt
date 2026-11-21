package com.example.ioscalculatorapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class OffsetPoint(
    val x: Float,
    val y: Float
)

data class DrawingStroke(
    val points: List<OffsetPoint>,
    val colorHex: Long,
    val strokeWidth: Float
)

data class MathNote(
    val id: Long,
    val title: String,
    val content: String,
    val updatedAt: Long,
    val strokes: List<DrawingStroke> = emptyList(),
    val showGraph: Boolean = false
)

data class EvaluatedLine(
    val lineIndex: Int,
    val rawText: String,
    val evaluatedResult: String? = null,
    val assignedVariable: Pair<String, Double>? = null,
    val isGraphFunction: Boolean = false,
    val isError: Boolean = false
)

data class GraphFunction(
    val label: String,
    val expressionRhs: String,
    val colorHex: Long
)

data class MathNoteEvaluationSummary(
    val lines: List<EvaluatedLine>,
    val variables: Map<String, Double>,
    val graphFunctions: List<GraphFunction>
)

internal object MathNotesEngine {
    private const val PREFS_NAME = "ios_math_notes_prefs"
    private const val NOTES_KEY = "math_notes_json_v1"
    private const val INITIALIZED_KEY = "math_notes_initialized_v1"

    private val reservedFunctionNames = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
        "ln", "log", "logten", "logtwo", "sqrt", "cbrt", "E"
    )

    private val graphColors = listOf(
        0xFFF08800L, // iOS 橘
        0xFF30D158L, // iOS 綠
        0xFF0A84FFL, // iOS 藍
        0xFFFF375FL, // iOS 粉紅
        0xFFBF5AF2L  // iOS 紫
    )

    fun loadNotes(context: Context): List<MathNote> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialized = prefs.getBoolean(INITIALIZED_KEY, false)
        val raw = prefs.getString(NOTES_KEY, null)
        if (!initialized || raw.isNullOrEmpty()) {
            val defaults = createDefaultSampleNotes()
            saveNotes(context, defaults)
            prefs.edit().putBoolean(INITIALIZED_KEY, true).apply()
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<MathNote>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val strokesArr = obj.optJSONArray("strokes")
                val strokes = mutableListOf<DrawingStroke>()
                if (strokesArr != null) {
                    for (j in 0 until strokesArr.length()) {
                        val sObj = strokesArr.getJSONObject(j)
                        val ptsArr = sObj.optJSONArray("points")
                        val pts = mutableListOf<OffsetPoint>()
                        if (ptsArr != null) {
                            for (k in 0 until ptsArr.length()) {
                                val pObj = ptsArr.getJSONObject(k)
                                pts.add(
                                    OffsetPoint(
                                        x = pObj.optDouble("x", 0.0).toFloat(),
                                        y = pObj.optDouble("y", 0.0).toFloat()
                                    )
                                )
                            }
                        }
                        strokes.add(
                            DrawingStroke(
                                points = pts,
                                colorHex = sObj.optLong("colorHex", 0xFFF08800L),
                                strokeWidth = sObj.optDouble("strokeWidth", 6.0).toFloat()
                            )
                        )
                    }
                }
                list.add(
                    MathNote(
                        id = obj.optLong("id", i.toLong()),
                        title = obj.optString("title", "未命名備忘錄"),
                        content = obj.optString("content", ""),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        strokes = strokes,
                        showGraph = obj.optBoolean("showGraph", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            createDefaultSampleNotes()
        }
    }

    fun saveNotes(context: Context, notes: List<MathNote>) {
        try {
            val arr = JSONArray()
            notes.forEach { note ->
                val strokesArr = JSONArray()
                note.strokes.forEach { stroke ->
                    val ptsArr = JSONArray()
                    stroke.points.forEach { pt ->
                        ptsArr.put(JSONObject().apply {
                            put("x", pt.x.toDouble())
                            put("y", pt.y.toDouble())
                        })
                    }
                    strokesArr.put(JSONObject().apply {
                        put("colorHex", stroke.colorHex)
                        put("strokeWidth", stroke.strokeWidth.toDouble())
                        put("points", ptsArr)
                    })
                }
                val obj = JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("updatedAt", note.updatedAt)
                    put("showGraph", note.showGraph)
                    put("strokes", strokesArr)
                }
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(NOTES_KEY, arr.toString())
                .putBoolean(INITIALIZED_KEY, true)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun deriveTitle(content: String): String {
        val firstNonBlank = content.lineSequence()
            .map { it.trim().removePrefix("#").trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: "新數學備忘錄"
        return if (firstNonBlank.length > 24) firstNonBlank.take(24) + "…" else firstNonBlank
    }

    private fun createDefaultSampleNotes(): List<MathNote> {
        val now = System.currentTimeMillis()
        return listOf(
            MathNote(
                id = now - 3000L,
                title = "📐 圓形與幾何變數計算",
                content = buildString {
                    appendLine("# 📐 圓形與幾何變數計算")
                    appendLine("半徑 = 12")
                    appendLine("高度 = 25")
                    appendLine("圓周長 = 2 × π × 半徑 =")
                    appendLine("圓面積 = π × 半徑² =")
                    appendLine("圓柱體積 = 圓面積 × 高度 =")
                }.trimEnd(),
                updatedAt = now - 3000L,
                showGraph = false
            ),
            MathNote(
                id = now - 2000L,
                title = "📈 函數圖形：拋物線與餘弦波",
                content = buildString {
                    appendLine("# 📈 函數圖形與參數實驗")
                    appendLine("a = 2")
                    appendLine("y = a × cos(x)")
                    appendLine("y = 0.25 × x² - 3")
                    appendLine("²√(144) + 3³ =")
                }.trimEnd(),
                updatedAt = now - 2000L,
                showGraph = true
            ),
            MathNote(
                id = now - 1000L,
                title = "💰 旅遊預算與多人分攤",
                content = buildString {
                    appendLine("# 💰 旅遊預算與分攤試算")
                    appendLine("機票 = 13800")
                    appendLine("住宿 = 9600")
                    appendLine("交通與餐飲 = 6600")
                    appendLine("總預算 = 機票 + 住宿 + 交通與餐飲 =")
                    appendLine("含稅總額 = 總預算 + 5% =")
                    appendLine("每人分攤 = 含稅總額 ÷ 4 =")
                }.trimEnd(),
                updatedAt = now - 1000L,
                showGraph = false
            )
        )
    }

    // 將算式中的自訂變數替換為數值（保留內建函數名稱如 sin, cos, ln）
    fun substituteVariables(
        expr: String,
        variables: Map<String, Double>,
        freeVarName: String? = null,
        freeVarValue: Double? = null
    ): String {
        val allVars = LinkedHashMap<String, Double>(variables)
        if (freeVarName != null && freeVarValue != null) {
            allVars[freeVarName] = freeVarValue
        }
        if (allVars.isEmpty()) return expr

        // 轉換標準運算符號方便算式輸入 (例如 * -> ×, / -> ÷)
        var s = expr
            .replace('*', '×')
            .replace('/', '÷')

        // 依變數名稱長度由長到短排序，避免短變數名誤切長變數名
        val sortedVars = allVars.entries.sortedByDescending { it.key.length }

        for ((varName, varVal) in sortedVars) {
            if (varName in reservedFunctionNames) continue
            val valStr = "(${CalculatorEngine.formatDoublePlain(varVal)})"
            val result = StringBuilder()
            var i = 0
            while (i < s.length) {
                if (s.startsWith(varName, i)) {
                    val prevChar = if (i > 0) s[i - 1] else ' '
                    val afterIdx = i + varName.length
                    val nextChar = if (afterIdx < s.length) s[afterIdx] else ' '

                    // 確認邊界：若變數名是純英文字母，前後不應緊鄰其他英文字母，避免拆開 sin / cos / log
                    val varIsAsciiLetter = varName.all { it in 'a'..'z' || it in 'A'..'Z' }
                    val prevIsAsciiLetter = prevChar in 'a'..'z' || prevChar in 'A'..'Z'
                    val nextIsAsciiLetter = nextChar in 'a'..'z' || nextChar in 'A'..'Z'

                    if (varIsAsciiLetter && (prevIsAsciiLetter || nextIsAsciiLetter)) {
                        result.append(s[i])
                        i++
                        continue
                    }

                    result.append(valStr)
                    i += varName.length
                } else {
                    result.append(s[i])
                    i++
                }
            }
            s = result.toString()
        }
        return s
    }

    private fun isValidVariableName(name: String): Boolean {
        if (name.isEmpty() || name.length > 20) return false
        if (name in reservedFunctionNames || name == "π" || name == "ℯ") return false
        // 變數名必須以字母或中文字開頭，且不包含算術運算子
        val first = name.first()
        if (!(first.isLetter() || Character.UnicodeScript.of(first.code) == Character.UnicodeScript.HAN)) {
            return false
        }
        val forbidden = setOf('+', '−', '-', '×', '*', '÷', '/', '^', '√', '(', ')', '=', '!', '%')
        return name.none { it in forbidden || it.isWhitespace() }
    }

    // 逐行解析並即時計算整份數學備忘錄
    fun evaluateNote(content: String, isRadians: Boolean = false): MathNoteEvaluationSummary {
        val rawLines = content.split("\n")
        val variables = LinkedHashMap<String, Double>()
        val evaluatedLines = mutableListOf<EvaluatedLine>()
        val graphFunctions = mutableListOf<GraphFunction>()
        val recentStackValues = mutableListOf<Double>()

        rawLines.forEachIndexed { idx, rawLine ->
            val trimmed = rawLine.trim()

            // 空行或標題註解行
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                if (trimmed.isEmpty()) recentStackValues.clear()
                evaluatedLines.add(EvaluatedLine(lineIndex = idx, rawText = rawLine))
                return@forEachIndexed
            }

            // 1. 檢查是否為「直式加總」：單獨一行 "=" 且上方有連續數字
            if ((trimmed == "=" || trimmed == "合計 =" || trimmed == "總計 =") && recentStackValues.isNotEmpty()) {
                val sum = recentStackValues.sum()
                val formattedSum = CalculatorEngine.formatExpressionForDisplay(
                    CalculatorEngine.formatDoublePlain(sum)
                )
                evaluatedLines.add(
                    EvaluatedLine(
                        lineIndex = idx,
                        rawText = rawLine,
                        evaluatedResult = formattedSum
                    )
                )
                recentStackValues.clear()
                return@forEachIndexed
            }

            // 2. 檢查是否為函數圖形定義：y = f(x) 或 f(x) = ...
            val normalizedForGraph = trimmed.replace(" ", "")
            val isExplicitFuncHeader = normalizedForGraph.startsWith("y=") ||
                normalizedForGraph.startsWith("f(x)=") ||
                normalizedForGraph.startsWith("g(x)=")
            if (isExplicitFuncHeader) {
                val eqIdx = trimmed.indexOf('=')
                val lhs = trimmed.substring(0, eqIdx).trim()
                val rhs = trimmed.substring(eqIdx + 1).removeSuffix("=").trim()
                val xIsUnboundOrExplicit = !variables.containsKey("x") || lhs.contains("(x)")
                val rhsUsesX = rhs.contains("x")
                if (rhs.isNotEmpty() && (rhsUsesX || lhs.contains("(x)")) && xIsUnboundOrExplicit) {
                    val colorHex = graphColors[graphFunctions.size % graphColors.size]
                    graphFunctions.add(
                        GraphFunction(
                            label = "$lhs = $rhs",
                            expressionRhs = rhs,
                            colorHex = colorHex
                        )
                    )
                    evaluatedLines.add(
                        EvaluatedLine(
                            lineIndex = idx,
                            rawText = rawLine,
                            isGraphFunction = true
                        )
                    )
                    return@forEachIndexed
                }
            }

            // 3. 檢查是否包含變數賦值 (例如 "半徑 = 12"、"面積 = π × 半徑² =" 或 "午餐: 180")
            val endsWithEquals = trimmed.endsWith("=") || trimmed.endsWith("＝")
            val cleanLine = trimmed.replace('＝', '=').replace('：', ':')

            // 找出第一個 '=' 或 ':'
            val firstEqIdx = cleanLine.indexOf('=')
            val firstColonIdx = cleanLine.indexOf(':')
            val assignSepIdx = when {
                firstEqIdx > 0 && (!endsWithEquals || firstEqIdx < cleanLine.length - 1) -> firstEqIdx
                firstColonIdx > 0 -> firstColonIdx
                else -> -1
            }

            if (assignSepIdx > 0) {
                val candidateVar = cleanLine.substring(0, assignSepIdx).trim()
                val rhsRaw = cleanLine.substring(assignSepIdx + 1).trim().removeSuffix("=").trim()

                if (isValidVariableName(candidateVar) && rhsRaw.isNotEmpty()) {
                    val substituted = substituteVariables(rhsRaw, variables)
                    val evalStr = CalculatorEngine.evaluateExpression(substituted, isRadians)
                    val numVal = evalStr.toDoubleOrNull()

                    if (numVal != null) {
                        variables[candidateVar] = numVal
                        recentStackValues.add(numVal)
                        val displayRes = CalculatorEngine.formatExpressionForDisplay(evalStr)
                        evaluatedLines.add(
                            EvaluatedLine(
                                lineIndex = idx,
                                rawText = rawLine,
                                evaluatedResult = if (endsWithEquals) displayRes else null,
                                assignedVariable = candidateVar to numVal
                            )
                        )
                        return@forEachIndexed
                    } else if (endsWithEquals) {
                        evaluatedLines.add(
                            EvaluatedLine(
                                lineIndex = idx,
                                rawText = rawLine,
                                evaluatedResult = "錯誤",
                                isError = true
                            )
                        )
                        return@forEachIndexed
                    }
                }
            }

            // 4. 一般算式行（以 "=" 結尾時計算並顯示橘色結果，若為純數字則加入直式加總堆疊）
            if (endsWithEquals) {
                val exprBody = cleanLine.removeSuffix("=").trim()
                if (exprBody.isNotEmpty()) {
                    val substituted = substituteVariables(exprBody, variables)
                    val evalStr = CalculatorEngine.evaluateExpression(substituted, isRadians)
                    val numVal = evalStr.toDoubleOrNull()
                    if (numVal != null) {
                        recentStackValues.add(numVal)
                        val displayRes = CalculatorEngine.formatExpressionForDisplay(evalStr)
                        evaluatedLines.add(
                            EvaluatedLine(
                                lineIndex = idx,
                                rawText = rawLine,
                                evaluatedResult = displayRes
                            )
                        )
                    } else {
                        evaluatedLines.add(
                            EvaluatedLine(
                                lineIndex = idx,
                                rawText = rawLine,
                                evaluatedResult = "錯誤",
                                isError = true
                            )
                        )
                    }
                    return@forEachIndexed
                }
            } else {
                // 若整行是一個純數字或簡單數值，放入直式堆疊供後續 "=" 加總
                val substituted = substituteVariables(cleanLine, variables)
                val asNum = substituted.replace(",", "").toDoubleOrNull()
                if (asNum != null) {
                    recentStackValues.add(asNum)
                }
            }

            evaluatedLines.add(EvaluatedLine(lineIndex = idx, rawText = rawLine))
        }

        return MathNoteEvaluationSummary(
            lines = evaluatedLines,
            variables = variables,
            graphFunctions = graphFunctions
        )
    }

    // 計算函數圖形在特定 x 座標的 y 值（在函數圖形中預設以弧度計算三角函數，使波形比例自然）
    fun evaluateFunctionAt(
        rhsExpr: String,
        xValue: Double,
        variables: Map<String, Double>
    ): Double? {
        val substituted = substituteVariables(
            expr = rhsExpr,
            variables = variables,
            freeVarName = "x",
            freeVarValue = xValue
        )
        val resStr = CalculatorEngine.evaluateExpression(substituted, isRadians = true)
        val y = resStr.toDoubleOrNull() ?: return null
        return if (y.isFinite() && abs(y) < 1e6) y else null
    }
}
