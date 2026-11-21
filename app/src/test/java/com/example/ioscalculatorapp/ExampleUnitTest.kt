package com.example.ioscalculatorapp

import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

class ExampleUnitTest {
    @Test
    fun basicArithmetic_isCorrect() {
        assertEquals("4", CalculatorEngine.evaluateExpression("2+2"))
        assertEquals("14", CalculatorEngine.evaluateExpression("2+3×4"))
        assertEquals("20", CalculatorEngine.evaluateExpression("(2+3)×4"))
        assertEquals("0.3", CalculatorEngine.evaluateExpression("0.1+0.2"))
        assertEquals("115", CalculatorEngine.evaluateExpression("100+15%"))
        assertEquals("85", CalculatorEngine.evaluateExpression("100−15%"))
    }

    @Test
    fun unaryMinusAndPowerPrecedence_isCorrect() {
        // -2² = -4, (-2)² = 4, 2^-3 = 0.125
        assertEquals("-4", CalculatorEngine.evaluateExpression("-2²"))
        assertEquals("4", CalculatorEngine.evaluateExpression("(-2)²"))
        assertEquals("-4", CalculatorEngine.evaluateExpression("-2^2"))
        assertEquals("4", CalculatorEngine.evaluateExpression("(-2)^2"))
        assertEquals("0.125", CalculatorEngine.evaluateExpression("2^-3"))
        assertEquals("-6", CalculatorEngine.evaluateExpression("-3!"))
        assertEquals("錯誤", CalculatorEngine.evaluateExpression("(-3)!"))
    }

    @Test
    fun logYAndRoots_isCorrect() {
        // logᵧ: 8 logᵧ(2) = 3, 1000 logᵧ(10) = 3
        assertEquals("3", CalculatorEngine.evaluateExpression("8logᵧ(2"))
        assertEquals("3", CalculatorEngine.evaluateExpression("8logᵧ(2)"))
        assertEquals("3", CalculatorEngine.evaluateExpression("1000logᵧ(10)"))
        assertEquals("錯誤", CalculatorEngine.evaluateExpression("8logᵧ(1)"))
        assertEquals("錯誤", CalculatorEngine.evaluateExpression("8logᵧ(-2)"))

        // 負數開奇數次方根：3√(-8) = -2, 2√(-4) = 錯誤
        assertEquals("-2", CalculatorEngine.evaluateExpression("3√(-8)"))
        assertEquals("-2", CalculatorEngine.evaluateExpression("3√-8"))
        assertEquals("錯誤", CalculatorEngine.evaluateExpression("2√(-4)"))
        assertEquals("-2", CalculatorEngine.evaluateExpression("³√(-8)"))
    }

    @Test
    fun trigPrecisionAndDomain_isCorrect() {
        // 度數模式 (isRadians = false)
        assertEquals("0.5", CalculatorEngine.evaluateExpression("sin(30)", isRadians = false))
        assertEquals("0.5", CalculatorEngine.evaluateExpression("cos(60)", isRadians = false))
        assertEquals("1", CalculatorEngine.evaluateExpression("tan(45)", isRadians = false))
        assertEquals("0", CalculatorEngine.evaluateExpression("sin(180)", isRadians = false))
        assertEquals("0", CalculatorEngine.evaluateExpression("cos(90)", isRadians = false))
        assertEquals("錯誤", CalculatorEngine.evaluateExpression("tan(90)", isRadians = false))
        assertEquals("錯誤", CalculatorEngine.evaluateExpression("tan(270)", isRadians = false))

        // 弧度模式 (isRadians = true)
        assertEquals("0", CalculatorEngine.evaluateExpression("sin(π)", isRadians = true))
        assertEquals("錯誤", CalculatorEngine.evaluateExpression("tan(π÷2)", isRadians = true))
    }

    @Test
    fun extremeNumbersAndFormatting_isCorrect() {
        // 極小值不被截斷為 0
        assertEquals("1E-11", CalculatorEngine.evaluateExpression("10^-11"))
        assertEquals("5E-12", CalculatorEngine.evaluateExpression("5E-12"))
        // 科學記號指數部分不應加上千分位逗號
        assertEquals("1E123", CalculatorEngine.formatExpressionForDisplay("1E123"))
        assertEquals("1,234,567.89", CalculatorEngine.formatExpressionForDisplay("1234567.89"))
        assertEquals("log₁₀(1,000)", CalculatorEngine.formatExpressionForDisplay("log₁₀(1000)"))
    }

    @Test
    fun mathNotesEngine_variablesAndEvaluation_isCorrect() {
        val noteContent = """
            # 測試備忘錄
            x = 12
            y = 5
            總和 = x + y =
            x × y =
        """.trimIndent()

        val summary = MathNotesEngine.evaluateNote(noteContent, isRadians = false)
        assertEquals(12.0, summary.variables["x"] ?: 0.0, 1e-6)
        assertEquals(5.0, summary.variables["y"] ?: 0.0, 1e-6)
        assertEquals(17.0, summary.variables["總和"] ?: 0.0, 1e-6)
        assertEquals("17", summary.lines[3].evaluatedResult)
        assertEquals("60", summary.lines[4].evaluatedResult)
    }

    @Test
    fun mathNotesEngine_graphFunctions_isCorrect() {
        val noteContent = """
            a = 2
            y = a × x² - 3
        """.trimIndent()

        val summary = MathNotesEngine.evaluateNote(noteContent)
        assertEquals(1, summary.graphFunctions.size)
        val fn = summary.graphFunctions.first()
        val yAtZero = MathNotesEngine.evaluateFunctionAt(fn.expressionRhs, 0.0, summary.variables)
        val yAtTwo = MathNotesEngine.evaluateFunctionAt(fn.expressionRhs, 2.0, summary.variables)
        assertNotNull(yAtZero)
        assertNotNull(yAtTwo)
        assertEquals(-3.0, yAtZero!!, 1e-6)
        assertEquals(5.0, yAtTwo!!, 1e-6)
    }

    @Test
    fun iosStyles_officialVersionsAndGenerations_isCorrect() {
        val versions = IosCalculatorStyle.entries.map { it.versionNumber }
        assertEquals(listOf(27, 26, 18, 17, 16, 15, 14, 13, 12), versions)

        // 驗證三大世代分類
        assertEquals(IosLayoutGeneration.MODERN_26_27, IosCalculatorStyle.IOS_27.generation)
        assertEquals(IosLayoutGeneration.MODERN_26_27, IosCalculatorStyle.IOS_26.generation)
        assertEquals(IosLayoutGeneration.MODERN_18, IosCalculatorStyle.IOS_18.generation)
        for (style in listOf(
            IosCalculatorStyle.IOS_17,
            IosCalculatorStyle.IOS_16,
            IosCalculatorStyle.IOS_15,
            IosCalculatorStyle.IOS_14,
            IosCalculatorStyle.IOS_13,
            IosCalculatorStyle.IOS_12
        )) {
            assertEquals(IosLayoutGeneration.CLASSIC_12_17, style.generation)
            assertTrue(style.isClassicLayout)
        }

        // 驗證佈局覆寫與強調色覆寫
        val modernInIos14 = resolveIosAppearance(
            IosStyleSettingsData(
                styleId = IosCalculatorStyle.IOS_14.id,
                layoutOverride = "modern",
                accentColorId = IosAccentColorOption.ELECTRIC_BLUE.id
            )
        )
        assertFalse(modernInIos14.isClassicLayout)
        assertEquals(IosLayoutGeneration.MODERN_26_27, modernInIos14.generation)
        assertEquals(IosAccentColorOption.ELECTRIC_BLUE.color, modernInIos14.operatorColor)

        val classicInIos27 = resolveIosAppearance(
            IosStyleSettingsData(
                styleId = IosCalculatorStyle.IOS_27.id,
                layoutOverride = "classic"
            )
        )
        assertTrue(classicInIos27.isClassicLayout)
        assertEquals(IosLayoutGeneration.CLASSIC_12_17, classicInIos27.generation)

        // 驗證舊版 ID 向後相容映射
        assertEquals(IosCalculatorStyle.IOS_27, IosCalculatorStyle.fromId("ios_27_default"))
        assertEquals(IosCalculatorStyle.IOS_26, IosCalculatorStyle.fromId("ios_25"))
        assertEquals(IosCalculatorStyle.IOS_18, IosCalculatorStyle.fromId("ios_19"))
        assertEquals(IosCalculatorStyle.IOS_17, IosCalculatorStyle.fromId("ios_17_classic"))
    }

    @Test
    fun appLanguageResolution_andTranslations_isCorrect() {
        assertEquals(AppLanguage.ZH_TW, resolveAppLanguage("zh_TW", Locale.US))
        assertEquals(AppLanguage.ZH_CN, resolveAppLanguage("zh_CN", Locale.US))
        assertEquals(AppLanguage.EN, resolveAppLanguage("en", Locale.TAIWAN))

        assertEquals(AppLanguage.ZH_TW, resolveAppLanguage("auto", Locale.TAIWAN))
        assertEquals(AppLanguage.ZH_CN, resolveAppLanguage("auto", Locale.SIMPLIFIED_CHINESE))
        assertEquals(AppLanguage.EN, resolveAppLanguage("auto", Locale.US))

        assertEquals("設定", tr(AppLanguage.ZH_TW, "設定", "设置", "Settings"))
        assertEquals("设置", tr(AppLanguage.ZH_CN, "設定", "设置", "Settings"))
        assertEquals("Settings", tr(AppLanguage.EN, "設定", "设置", "Settings"))
    }
}
