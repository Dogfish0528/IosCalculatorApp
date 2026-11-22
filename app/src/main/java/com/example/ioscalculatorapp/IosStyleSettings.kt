package com.example.ioscalculatorapp

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

enum class AppLanguage {
    ZH_TW,
    ZH_CN,
    EN
}

fun resolveAppLanguage(code: String, locale: Locale = Locale.getDefault()): AppLanguage = when (code) {
    "zh_TW" -> AppLanguage.ZH_TW
    "zh_CN" -> AppLanguage.ZH_CN
    "en" -> AppLanguage.EN
    else -> {
        val lang = locale.language.lowercase(Locale.ROOT)
        val country = locale.country.uppercase(Locale.ROOT)
        val script = locale.script
        when {
            lang == "zh" -> {
                if (script.equals("Hans", ignoreCase = true) || country in setOf("CN", "SG")) {
                    AppLanguage.ZH_CN
                } else {
                    AppLanguage.ZH_TW
                }
            }
            else -> AppLanguage.EN
        }
    }
}

fun tr(lang: AppLanguage, zhTw: String, zhCn: String, en: String): String = when (lang) {
    AppLanguage.ZH_TW -> zhTw
    AppLanguage.ZH_CN -> zhCn
    AppLanguage.EN -> en
}

/**
 * Apple 官方 iPhone 支援頁面的三大計算機介面世代：
 * - MODERN_26_27 (iOS 27 / iOS 26)：頂部左右圓鈕（左上時鐘歷程、右上模式切換）、第一排 ⌫ AC % ÷、底部 +/- 0 . =、雙行算式顯示。
 * - MODERN_18 (iOS 18)：左上無框清單圖示 (list.bullet)、第一排 AC/⌫ +/- % ÷、左下角計算機模式切換鍵 🖩 0 . =、雙行算式顯示。
 * - CLASSIC_12_17 (iOS 17 ~ iOS 12)：無左上歷程按鈕、單行數值顯示、第一排 #A5A5A5 黑字 AC +/- % ÷、底部雙倍寬 0 鍵、運算鍵白底橘字反白。
 */
enum class IosLayoutGeneration {
    MODERN_26_27,
    MODERN_18,
    CLASSIC_12_17
}

/**
 * 精準對應 Apple 官方支援頁面 (support.apple.com) 下拉清單中的 9 個 iOS 版本：
 * iOS 27, iOS 26, iOS 18, iOS 17, iOS 16, iOS 15, iOS 14, iOS 13, iOS 12
 */
enum class IosCalculatorStyle(
    val id: String,
    val versionNumber: Int,
    val displayName: String,
    val eraTag: String,
    val subtitle: String,
    val generation: IosLayoutGeneration,
    val defaultDisplayWeight: String,
    val backgroundColor: Color,
    val topBarCircleColor: Color,
    val topBarIconColor: Color,
    val displayTextColor: Color,
    val historyTextColor: Color,
    val sciButtonColor: Color,
    val sciButtonPressedColor: Color,
    val sciButtonHighlightedColor: Color,
    val sciButtonTextColor: Color,
    val digitButtonColor: Color,
    val digitButtonPressedColor: Color,
    val digitButtonTextColor: Color,
    val topFuncButtonColor: Color,
    val topFuncButtonPressedColor: Color,
    val topFuncButtonTextColor: Color,
    val operatorButtonColor: Color,
    val operatorButtonPressedColor: Color,
    val operatorButtonTextColor: Color,
    val buttonBorderColor: Color?,
    val menuBackgroundColor: Color,
    val menuTextColor: Color,
    val menuDividerColor: Color,
    val sheetBackgroundColor: Color,
    val isLightTheme: Boolean = false
) {
    IOS_27(
        id = "ios_27",
        versionNumber = 27,
        displayName = "iOS 27",
        eraTag = "最新（iOS 最新主題）",
        subtitle = "頂部時鐘與模式雙圓鈕 · ⌫ AC % ÷ 與沉穩深橘 #F08800",
        generation = IosLayoutGeneration.MODERN_26_27,
        defaultDisplayWeight = "normal",
        backgroundColor = Color(0xFF000000),
        topBarCircleColor = Color(0xFF1A1A1C),
        topBarIconColor = Color(0xFFFFFFFF),
        displayTextColor = Color(0xFFFFFFFF),
        historyTextColor = Color(0xFF8E8E93),
        sciButtonColor = Color(0xFF1C1C1E),
        sciButtonPressedColor = Color(0xFF48484A),
        sciButtonHighlightedColor = Color(0xFF636366),
        sciButtonTextColor = Color(0xFFFFFFFF),
        digitButtonColor = Color(0xFF2C2C2E),
        digitButtonPressedColor = Color(0xFF636366),
        digitButtonTextColor = Color(0xFFFFFFFF),
        topFuncButtonColor = Color(0xFF5C5C5E),
        topFuncButtonPressedColor = Color(0xFF8E8E93),
        topFuncButtonTextColor = Color(0xFFFFFFFF),
        operatorButtonColor = Color(0xFFF08800),
        operatorButtonPressedColor = Color(0xFFFFB340),
        operatorButtonTextColor = Color(0xFFFFFFFF),
        buttonBorderColor = Color(0x14FFFFFF),
        menuBackgroundColor = Color(0xFF2C2C2E),
        menuTextColor = Color(0xFFFFFFFF),
        menuDividerColor = Color(0xFF3A3A3C),
        sheetBackgroundColor = Color(0xFF1C1C1E)
    ),
    IOS_26(
        id = "ios_26",
        versionNumber = 26,
        displayName = "iOS 26",
        eraTag = "液態玻璃",
        subtitle = "Liquid Glass 液態玻璃光感邊框 · 頂部雙圓鈕與純正暖橘",
        generation = IosLayoutGeneration.MODERN_26_27,
        defaultDisplayWeight = "normal",
        backgroundColor = Color(0xFF000000),
        topBarCircleColor = Color(0xFF1C1C1F),
        topBarIconColor = Color(0xFFFFFFFF),
        displayTextColor = Color(0xFFFFFFFF),
        historyTextColor = Color(0xFF98989D),
        sciButtonColor = Color(0xFF19191C),
        sciButtonPressedColor = Color(0xFF424247),
        sciButtonHighlightedColor = Color(0xFF5E5E64),
        sciButtonTextColor = Color(0xFFFFFFFF),
        digitButtonColor = Color(0xFF27272B),
        digitButtonPressedColor = Color(0xFF5A5A60),
        digitButtonTextColor = Color(0xFFFFFFFF),
        topFuncButtonColor = Color(0xFF535358),
        topFuncButtonPressedColor = Color(0xFF86868B),
        topFuncButtonTextColor = Color(0xFFFFFFFF),
        operatorButtonColor = Color(0xFFFF9500),
        operatorButtonPressedColor = Color(0xFFFFBE5C),
        operatorButtonTextColor = Color(0xFFFFFFFF),
        buttonBorderColor = Color(0x2EFFFFFF),
        menuBackgroundColor = Color(0xFF28282C),
        menuTextColor = Color(0xFFFFFFFF),
        menuDividerColor = Color(0xFF3E3E44),
        sheetBackgroundColor = Color(0xFF1A1A1E)
    ),
    IOS_18(
        id = "ios_18",
        versionNumber = 18,
        displayName = "iOS 18",
        eraTag = "清單 + 左下切換",
        subtitle = "左上無框清單圖示 · 第一排動態 AC/⌫ · 左下計算機模式鍵",
        generation = IosLayoutGeneration.MODERN_18,
        defaultDisplayWeight = "normal",
        backgroundColor = Color(0xFF000000),
        topBarCircleColor = Color(0xFF1C1C1E),
        topBarIconColor = Color(0xFFFFFFFF),
        displayTextColor = Color(0xFFFFFFFF),
        historyTextColor = Color(0xFF8E8E93),
        sciButtonColor = Color(0xFF1C1C1E),
        sciButtonPressedColor = Color(0xFF48484A),
        sciButtonHighlightedColor = Color(0xFF636366),
        sciButtonTextColor = Color(0xFFFFFFFF),
        digitButtonColor = Color(0xFF2C2C2E),
        digitButtonPressedColor = Color(0xFF636366),
        digitButtonTextColor = Color(0xFFFFFFFF),
        topFuncButtonColor = Color(0xFF5C5C5E),
        topFuncButtonPressedColor = Color(0xFF8E8E93),
        topFuncButtonTextColor = Color(0xFFFFFFFF),
        operatorButtonColor = Color(0xFFFF9F0A),
        operatorButtonPressedColor = Color(0xFFFBC78D),
        operatorButtonTextColor = Color(0xFFFFFFFF),
        buttonBorderColor = null,
        menuBackgroundColor = Color(0xFF2C2C2E),
        menuTextColor = Color(0xFFFFFFFF),
        menuDividerColor = Color(0xFF3A3A3C),
        sheetBackgroundColor = Color(0xFF1C1C1E)
    ),
    IOS_17(
        id = "ios_17",
        versionNumber = 17,
        displayName = "iOS 17",
        eraTag = "經典世代",
        subtitle = "經典單行顯示 · #A5A5A5 黑字功能鍵 · 雙倍寬 0 鍵與反白運算鍵",
        generation = IosLayoutGeneration.CLASSIC_12_17,
        defaultDisplayWeight = "normal",
        backgroundColor = Color(0xFF000000),
        topBarCircleColor = Color(0xFF1C1C1E),
        topBarIconColor = Color(0xFFFFFFFF),
        displayTextColor = Color(0xFFFFFFFF),
        historyTextColor = Color(0xFF8E8E93),
        sciButtonColor = Color(0xFF212121),
        sciButtonPressedColor = Color(0xFF4A4A4C),
        sciButtonHighlightedColor = Color(0xFF636366),
        sciButtonTextColor = Color(0xFFFFFFFF),
        digitButtonColor = Color(0xFF333333),
        digitButtonPressedColor = Color(0xFF737373),
        digitButtonTextColor = Color(0xFFFFFFFF),
        topFuncButtonColor = Color(0xFFA5A5A5),
        topFuncButtonPressedColor = Color(0xFFD4D4D2),
        topFuncButtonTextColor = Color(0xFF000000),
        operatorButtonColor = Color(0xFFFF9F0A),
        operatorButtonPressedColor = Color(0xFFFBC78D),
        operatorButtonTextColor = Color(0xFFFFFFFF),
        buttonBorderColor = null,
        menuBackgroundColor = Color(0xFF2C2C2E),
        menuTextColor = Color(0xFFFFFFFF),
        menuDividerColor = Color(0xFF3A3A3C),
        sheetBackgroundColor = Color(0xFF1C1C1E)
    ),
    IOS_16(
        id = "ios_16",
        versionNumber = 16,
        displayName = "iOS 16",
        eraTag = "經典世代",
        subtitle = "經典純黑深灰圓鈕 · 單行顯示與雙倍寬 0 鍵（支援滑動退格）",
        generation = IosLayoutGeneration.CLASSIC_12_17,
        defaultDisplayWeight = "normal",
        backgroundColor = Color(0xFF000000),
        topBarCircleColor = Color(0xFF1C1C1E),
        topBarIconColor = Color(0xFFFFFFFF),
        displayTextColor = Color(0xFFFFFFFF),
        historyTextColor = Color(0xFF8E8E93),
        sciButtonColor = Color(0xFF212121),
        sciButtonPressedColor = Color(0xFF4A4A4C),
        sciButtonHighlightedColor = Color(0xFF636366),
        sciButtonTextColor = Color(0xFFFFFFFF),
        digitButtonColor = Color(0xFF333333),
        digitButtonPressedColor = Color(0xFF737373),
        digitButtonTextColor = Color(0xFFFFFFFF),
        topFuncButtonColor = Color(0xFFA5A5A5),
        topFuncButtonPressedColor = Color(0xFFD4D4D2),
        topFuncButtonTextColor = Color(0xFF000000),
        operatorButtonColor = Color(0xFFFF9F0A),
        operatorButtonPressedColor = Color(0xFFFBC78D),
        operatorButtonTextColor = Color(0xFFFFFFFF),
        buttonBorderColor = null,
        menuBackgroundColor = Color(0xFF2C2C2E),
        menuTextColor = Color(0xFFFFFFFF),
        menuDividerColor = Color(0xFF3A3A3C),
        sheetBackgroundColor = Color(0xFF1C1C1E)
    ),
    IOS_15(
        id = "ios_15",
        versionNumber = 15,
        displayName = "iOS 15",
        eraTag = "經典世代",
        subtitle = "純粹扁平圓鈕 · #333333 深灰搭淺銀灰功能鍵",
        generation = IosLayoutGeneration.CLASSIC_12_17,
        defaultDisplayWeight = "normal",
        backgroundColor = Color(0xFF000000),
        topBarCircleColor = Color(0xFF1A1A1A),
        topBarIconColor = Color(0xFFFFFFFF),
        displayTextColor = Color(0xFFFFFFFF),
        historyTextColor = Color(0xFF8E8E93),
        sciButtonColor = Color(0xFF1F1F1F),
        sciButtonPressedColor = Color(0xFF4A4A4A),
        sciButtonHighlightedColor = Color(0xFF636363),
        sciButtonTextColor = Color(0xFFFFFFFF),
        digitButtonColor = Color(0xFF333333),
        digitButtonPressedColor = Color(0xFF737373),
        digitButtonTextColor = Color(0xFFFFFFFF),
        topFuncButtonColor = Color(0xFFA5A5A5),
        topFuncButtonPressedColor = Color(0xFFD4D4D2),
        topFuncButtonTextColor = Color(0xFF000000),
        operatorButtonColor = Color(0xFFFF9E0D),
        operatorButtonPressedColor = Color(0xFFFBC78D),
        operatorButtonTextColor = Color(0xFFFFFFFF),
        buttonBorderColor = null,
        menuBackgroundColor = Color(0xFF2C2C2E),
        menuTextColor = Color(0xFFFFFFFF),
        menuDividerColor = Color(0xFF3A3A3C),
        sheetBackgroundColor = Color(0xFF1C1C1E)
    ),
    IOS_14(
        id = "ios_14",
        versionNumber = 14,
        displayName = "iOS 14",
        eraTag = "經典世代",
        subtitle = "經典圓潤黑橘 · 單行顯示與左右滑動螢幕退格",
        generation = IosLayoutGeneration.CLASSIC_12_17,
        defaultDisplayWeight = "normal",
        backgroundColor = Color(0xFF000000),
        topBarCircleColor = Color(0xFF1A1A1A),
        topBarIconColor = Color(0xFFFFFFFF),
        displayTextColor = Color(0xFFFFFFFF),
        historyTextColor = Color(0xFF8E8E93),
        sciButtonColor = Color(0xFF222222),
        sciButtonPressedColor = Color(0xFF4D4D4D),
        sciButtonHighlightedColor = Color(0xFF666666),
        sciButtonTextColor = Color(0xFFFFFFFF),
        digitButtonColor = Color(0xFF333333),
        digitButtonPressedColor = Color(0xFF737373),
        digitButtonTextColor = Color(0xFFFFFFFF),
        topFuncButtonColor = Color(0xFFA5A5A5),
        topFuncButtonPressedColor = Color(0xFFD4D4D2),
        topFuncButtonTextColor = Color(0xFF000000),
        operatorButtonColor = Color(0xFFFE9E0A),
        operatorButtonPressedColor = Color(0xFFFBC78D),
        operatorButtonTextColor = Color(0xFFFFFFFF),
        buttonBorderColor = null,
        menuBackgroundColor = Color(0xFF2C2C2E),
        menuTextColor = Color(0xFFFFFFFF),
        menuDividerColor = Color(0xFF3A3A3C),
        sheetBackgroundColor = Color(0xFF1C1C1E)
    ),
    IOS_13(
        id = "ios_13",
        versionNumber = 13,
        displayName = "iOS 13",
        eraTag = "經典細體",
        subtitle = "經典深色配輕盈細體顯示數字與暖橘 #FF9500",
        generation = IosLayoutGeneration.CLASSIC_12_17,
        defaultDisplayWeight = "light",
        backgroundColor = Color(0xFF000000),
        topBarCircleColor = Color(0xFF1C1C1E),
        topBarIconColor = Color(0xFFFFFFFF),
        displayTextColor = Color(0xFFFFFFFF),
        historyTextColor = Color(0xFF8E8E93),
        sciButtonColor = Color(0xFF202020),
        sciButtonPressedColor = Color(0xFF4B4B4B),
        sciButtonHighlightedColor = Color(0xFF666666),
        sciButtonTextColor = Color(0xFFFFFFFF),
        digitButtonColor = Color(0xFF333333),
        digitButtonPressedColor = Color(0xFF737373),
        digitButtonTextColor = Color(0xFFFFFFFF),
        topFuncButtonColor = Color(0xFFA5A5A5),
        topFuncButtonPressedColor = Color(0xFFD4D4D2),
        topFuncButtonTextColor = Color(0xFF000000),
        operatorButtonColor = Color(0xFFFF9500),
        operatorButtonPressedColor = Color(0xFFFFC773),
        operatorButtonTextColor = Color(0xFFFFFFFF),
        buttonBorderColor = null,
        menuBackgroundColor = Color(0xFF2C2C2E),
        menuTextColor = Color(0xFFFFFFFF),
        menuDividerColor = Color(0xFF3A3A3C),
        sheetBackgroundColor = Color(0xFF1C1C1E)
    ),
    IOS_12(
        id = "ios_12",
        versionNumber = 12,
        displayName = "iOS 12",
        eraTag = "經典元祖",
        subtitle = "懷舊初代深色計算機 · 暖橘 #FF9500 與極細數字",
        generation = IosLayoutGeneration.CLASSIC_12_17,
        defaultDisplayWeight = "light",
        backgroundColor = Color(0xFF000000),
        topBarCircleColor = Color(0xFF1A1A1A),
        topBarIconColor = Color(0xFFFFFFFF),
        displayTextColor = Color(0xFFFFFFFF),
        historyTextColor = Color(0xFF8E8E93),
        sciButtonColor = Color(0xFF222222),
        sciButtonPressedColor = Color(0xFF4E4E4E),
        sciButtonHighlightedColor = Color(0xFF686868),
        sciButtonTextColor = Color(0xFFFFFFFF),
        digitButtonColor = Color(0xFF333333),
        digitButtonPressedColor = Color(0xFF737373),
        digitButtonTextColor = Color(0xFFFFFFFF),
        topFuncButtonColor = Color(0xFFA6A6A6),
        topFuncButtonPressedColor = Color(0xFFD9D9D9),
        topFuncButtonTextColor = Color(0xFF000000),
        operatorButtonColor = Color(0xFFFF9500),
        operatorButtonPressedColor = Color(0xFFFFC773),
        operatorButtonTextColor = Color(0xFFFFFFFF),
        buttonBorderColor = null,
        menuBackgroundColor = Color(0xFF2C2C2E),
        menuTextColor = Color(0xFFFFFFFF),
        menuDividerColor = Color(0xFF3A3A3C),
        sheetBackgroundColor = Color(0xFF1C1C1E)
    );

    val isClassicLayout: Boolean
        get() = generation == IosLayoutGeneration.CLASSIC_12_17

    fun eraTag(lang: AppLanguage): String = when (this) {
        IOS_27 -> tr(lang, "最新（iOS 最新主題）", "最新（iOS 最新主题）", "Latest (iOS Latest)")
        IOS_26 -> tr(lang, "液態玻璃", "液态玻璃", "Liquid Glass")
        IOS_18 -> tr(lang, "經典切換", "经典切换", "Classic Switch")
        else -> tr(lang, "經典世代", "经典世代", "Classic Era")
    }

    fun subtitle(lang: AppLanguage): String = when (this) {
        IOS_27 -> tr(
            lang,
            subtitle,
            "顶部时钟与模式双圆钮 · ⌫ AC % ÷ 与沉稳深橙 #F08800",
            "Clock & mode top buttons · ⌫ AC % ÷ & deep orange #F08800"
        )
        IOS_26 -> tr(
            lang,
            subtitle,
            "Liquid Glass 液态玻璃光感边框 · 顶部双圆钮与纯正暖橙",
            "Liquid Glass luminous borders · Top circular buttons & warm orange"
        )
        IOS_18 -> tr(
            lang,
            subtitle,
            "左上无框橙色清单、左下计算器切换钮 · 第一排 AC/⌫ +/- % ÷",
            "Frameless list, bottom-left mode key · Top row AC/⌫ +/- % ÷"
        )
        IOS_17 -> tr(
            lang,
            subtitle,
            "单行显示 · 顶部黑字银灰功能键搭双倍宽 0 键与橙色反白",
            "Single-line display · Silver top keys, double 0 & highlight ops"
        )
        IOS_16 -> tr(
            lang,
            subtitle,
            "经典纯黑深灰圆钮 · 单行显示与双倍宽 0 键（支持滑动退格）",
            "Classic black & gray buttons · Single-line & swipe to delete"
        )
        IOS_15 -> tr(
            lang,
            subtitle,
            "纯粹扁平圆钮 · #333333 深灰搭浅银灰功能键",
            "Pure flat circles · #333333 dark gray & light silver top keys"
        )
        IOS_14 -> tr(
            lang,
            subtitle,
            "圆润按压手感 · 经典 #FF9F0A 琥珀橙运算键",
            "Crisp tap feel · Classic #FF9F0A amber orange operators"
        )
        IOS_13 -> tr(
            lang,
            subtitle,
            "深色对比质感 · Light 纤细数字显示",
            "High-contrast dark look · Light weight digit display"
        )
        IOS_12 -> tr(
            lang,
            subtitle,
            "高对比传统配色 · 经典 iPhone 计算器质感",
            "High-contrast classic palette · Classic iPhone calculator look"
        )
    }

    companion object {
        fun fromId(id: String): IosCalculatorStyle = when (id) {
            "ios_27_default", "ios_light" -> IOS_27
            "ios_25", "ios_24", "ios_23", "ios_22", "ios_21", "ios_20", "ios_liquid_glass" -> IOS_26
            "ios_19" -> IOS_18
            "ios_17_classic" -> IOS_17
            else -> entries.firstOrNull { it.id == id } ?: IOS_27
        }
    }
}

enum class IosAccentColorOption(
    val id: String,
    val label: String,
    val color: Color?,
    val pressedColor: Color?
) {
    FOLLOW_VERSION("default", "跟隨版本", null, null),
    DEEP_ORANGE("deep_orange", "深橘", Color(0xFFF08800), Color(0xFFFFB340)),
    CLASSIC_ORANGE("classic_orange", "亮橘", Color(0xFFFF9F0A), Color(0xFFFBC78D)),
    ELECTRIC_BLUE("blue", "電光藍", Color(0xFF0A84FF), Color(0xFF64B5F6)),
    EMERALD_GREEN("green", "翡翠綠", Color(0xFF30D158), Color(0xFF6EE787)),
    VIOLET("purple", "紫羅蘭", Color(0xFFBF5AF2), Color(0xFFD896FF)),
    ROSE_PINK("pink", "玫瑰粉", Color(0xFFFF375F), Color(0xFFFF758F)),
    GLACIER_CYAN("cyan", "冰川青", Color(0xFF64D2FF), Color(0xFFA0E4FF));

    fun label(lang: AppLanguage): String = when (this) {
        FOLLOW_VERSION -> tr(lang, "跟隨版本", "跟随版本", "Default")
        DEEP_ORANGE -> tr(lang, "深橘", "深橙", "Deep Orange")
        CLASSIC_ORANGE -> tr(lang, "亮橘", "亮橙", "Bright Orange")
        ELECTRIC_BLUE -> tr(lang, "電光藍", "电光蓝", "Blue")
        EMERALD_GREEN -> tr(lang, "翡翠綠", "翡翠绿", "Green")
        VIOLET -> tr(lang, "紫羅蘭", "紫罗兰", "Purple")
        ROSE_PINK -> tr(lang, "玫瑰粉", "玫瑰粉", "Pink")
        GLACIER_CYAN -> tr(lang, "冰川青", "冰川青", "Cyan")
    }

    companion object {
        fun fromId(id: String): IosAccentColorOption =
            entries.firstOrNull { it.id == id } ?: FOLLOW_VERSION
    }
}

enum class IosIconPackOption(
    val id: String,
    val title: String,
    val description: String,
    val badgeText: String
) {
    DEFAULT_VECTOR(
        id = "default_vector",
        title = "自動對應各代 iOS 原廠圖示",
        description = "iOS 27 時鐘+實心計算機 / iOS 26 清單+線框計算機 / iOS 18 橘色清單 / 舊版統一 iOS 27 圖示",
        badgeText = "已啟用"
    ),
    CUSTOM_PACK(
        id = "custom_pack",
        title = "自訂 iOS 圖示包",
        description = "預留圖示擴充插槽 · 匯入新圖示後即可切換套用",
        badgeText = "準備中"
    );

    fun title(lang: AppLanguage): String = when (this) {
        DEFAULT_VECTOR -> tr(lang, title, "自动对应各代 iOS 原厂图标", "Auto-Match Authentic iOS Icons")
        CUSTOM_PACK -> tr(lang, title, "自定义 iOS 图标包", "Custom iOS Icon Pack")
    }

    fun description(lang: AppLanguage): String = when (this) {
        DEFAULT_VECTOR -> tr(
            lang,
            description,
            "iOS 27 时钟+实心计算器 / iOS 26 列表+线框计算器 / iOS 18 橙色列表 / 旧版统一 iOS 27 图标",
            "iOS 27 clock+solid / iOS 26 list+outline / iOS 18 orange list / Legacy iOS 27"
        )
        CUSTOM_PACK -> tr(
            lang,
            description,
            "预留图标扩展插槽 · 导入新图标后即可切换应用",
            "Reserved slot · Apply custom imported icon packs here"
        )
    }

    fun badgeText(lang: AppLanguage): String = when (this) {
        DEFAULT_VECTOR -> tr(lang, badgeText, "已启用", "Active")
        CUSTOM_PACK -> tr(lang, badgeText, "准备中", "Soon")
    }
}

data class IosStyleSettingsData(
    val styleId: String = IosCalculatorStyle.IOS_27.id,
    val accentColorId: String = IosAccentColorOption.FOLLOW_VERSION.id,
    val layoutOverride: String = "auto", // "auto", "modern", "ios18", "classic"
    val buttonShapeStyle: String = "circle", // "circle", "squircle", "rounded_rect"
    val buttonSpacingMode: String = "normal", // "compact", "normal", "wide"
    val displayFontWeight: String = "auto", // "auto", "light", "normal", "medium"
    val displayFontScale: String = "normal", // "small", "normal", "large"
    val hapticFeedbackEnabled: Boolean = true,
    val useThousandSeparator: Boolean = true,
    val classicOperatorHighlight: Boolean = true,
    val iconPackId: String = IosIconPackOption.DEFAULT_VECTOR.id,
    val backgroundTheme: String = "auto", // "auto", "oled_black", "charcoal", "midnight_blue", "light"
    val glassBorderMode: String = "auto", // "auto", "on", "off"
    val buttonContentScale: String = "normal", // "small", "normal", "large"
    val showExpressionHistoryLine: String = "auto", // "auto", "always", "hide"
    val soundEffectEnabled: Boolean = false,
    val autoScientificOnPhoneLandscape: Boolean = true,
    val language: String = "auto" // "auto", "zh_TW", "zh_CN", "en"
)

data class ResolvedIosAppearance(
    val style: IosCalculatorStyle,
    val generation: IosLayoutGeneration,
    val operatorColor: Color,
    val operatorPressedColor: Color,
    val isClassicLayout: Boolean,
    val buttonShape: Shape,
    val sciButtonShape: Shape,
    val displayFontWeight: FontWeight,
    val displayFontScaleMultiplier: Float,
    val basicSpacingDp: Dp,
    val sciSpacingDp: Dp,
    val tabletSpacingDp: Dp,
    val compactLandscapeSpacingDp: Dp,
    val backgroundColor: Color,
    val digitButtonColor: Color,
    val digitButtonPressedColor: Color,
    val digitButtonTextColor: Color,
    val topFuncButtonColor: Color,
    val topFuncButtonPressedColor: Color,
    val topFuncButtonTextColor: Color,
    val sciButtonColor: Color,
    val sciButtonPressedColor: Color,
    val sciButtonHighlightedColor: Color,
    val sciButtonTextColor: Color,
    val displayTextColor: Color,
    val historyTextColor: Color,
    val topBarCircleColor: Color,
    val topBarIconColor: Color,
    val buttonBorderColor: Color?,
    val isLightTheme: Boolean,
    val buttonContentScaleMultiplier: Float,
    val showHistoryLine: Boolean,
    val menuBackgroundColor: Color,
    val menuTextColor: Color,
    val menuDividerColor: Color,
    val sheetBackgroundColor: Color,
    val language: AppLanguage
)

private data class ResolvedThemeColors(
    val backgroundColor: Color,
    val digitButtonColor: Color,
    val digitButtonPressedColor: Color,
    val digitButtonTextColor: Color,
    val topFuncButtonColor: Color,
    val topFuncButtonPressedColor: Color,
    val topFuncButtonTextColor: Color,
    val sciButtonColor: Color,
    val sciButtonPressedColor: Color,
    val sciButtonHighlightedColor: Color,
    val sciButtonTextColor: Color,
    val displayTextColor: Color,
    val historyTextColor: Color,
    val topBarCircleColor: Color,
    val topBarIconColor: Color,
    val isLightTheme: Boolean
)

internal fun resolveIosAppearance(settings: IosStyleSettingsData): ResolvedIosAppearance {
    val baseStyle = IosCalculatorStyle.fromId(settings.styleId)
    val accentOpt = IosAccentColorOption.fromId(settings.accentColorId)
    val effectiveOpColor = accentOpt.color ?: baseStyle.operatorButtonColor
    val effectiveOpPressed = accentOpt.pressedColor ?: baseStyle.operatorButtonPressedColor

    val effectiveGeneration = when (settings.layoutOverride) {
        "modern" -> IosLayoutGeneration.MODERN_26_27
        "ios18" -> IosLayoutGeneration.MODERN_18
        "classic" -> IosLayoutGeneration.CLASSIC_12_17
        else -> baseStyle.generation
    }
    val isClassicLayout = effectiveGeneration == IosLayoutGeneration.CLASSIC_12_17

    val buttonShape: Shape = when (settings.buttonShapeStyle) {
        "squircle" -> RoundedCornerShape(18.dp)
        "rounded_rect" -> RoundedCornerShape(10.dp)
        else -> CircleShape
    }
    val sciButtonShape: Shape = when (settings.buttonShapeStyle) {
        "squircle" -> RoundedCornerShape(13.dp)
        "rounded_rect" -> RoundedCornerShape(8.dp)
        else -> CircleShape
    }

    val weightKey = if (settings.displayFontWeight == "auto") baseStyle.defaultDisplayWeight else settings.displayFontWeight
    val resolvedWeight = when (weightKey) {
        "light" -> FontWeight.Light
        "medium" -> FontWeight.Medium
        else -> FontWeight.Normal
    }

    val scaleMult = when (settings.displayFontScale) {
        "small" -> 0.88f
        "large" -> 1.12f
        else -> 1.0f
    }

    val buttonContentScaleMult = when (settings.buttonContentScale) {
        "small" -> 0.88f
        "large" -> 1.15f
        else -> 1.0f
    }

    val showHistoryLine = when (settings.showExpressionHistoryLine) {
        "always" -> true
        "hide" -> false
        else -> effectiveGeneration != IosLayoutGeneration.CLASSIC_12_17
    }

    val themeColors = when (settings.backgroundTheme) {
        "oled_black" -> ResolvedThemeColors(
            backgroundColor = Color(0xFF000000),
            digitButtonColor = Color(0xFF242426),
            digitButtonPressedColor = Color(0xFF565659),
            digitButtonTextColor = Color(0xFFFFFFFF),
            topFuncButtonColor = if (isClassicLayout) Color(0xFFA5A5A5) else Color(0xFF545458),
            topFuncButtonPressedColor = if (isClassicLayout) Color(0xFFD4D4D2) else Color(0xFF86868B),
            topFuncButtonTextColor = if (isClassicLayout) Color(0xFF000000) else Color(0xFFFFFFFF),
            sciButtonColor = Color(0xFF161618),
            sciButtonPressedColor = Color(0xFF3E3E42),
            sciButtonHighlightedColor = Color(0xFF636366),
            sciButtonTextColor = Color(0xFFFFFFFF),
            displayTextColor = Color(0xFFFFFFFF),
            historyTextColor = Color(0xFF8E8E93),
            topBarCircleColor = Color(0xFF1A1A1C),
            topBarIconColor = Color(0xFFFFFFFF),
            isLightTheme = false
        )
        "charcoal" -> ResolvedThemeColors(
            backgroundColor = Color(0xFF141416),
            digitButtonColor = Color(0xFF2F2F34),
            digitButtonPressedColor = Color(0xFF5E5E66),
            digitButtonTextColor = Color(0xFFFFFFFF),
            topFuncButtonColor = if (isClassicLayout) Color(0xFFB0B0B6) else Color(0xFF606066),
            topFuncButtonPressedColor = if (isClassicLayout) Color(0xFFDADAE0) else Color(0xFF8E8E96),
            topFuncButtonTextColor = if (isClassicLayout) Color(0xFF000000) else Color(0xFFFFFFFF),
            sciButtonColor = Color(0xFF202024),
            sciButtonPressedColor = Color(0xFF48484E),
            sciButtonHighlightedColor = Color(0xFF686870),
            sciButtonTextColor = Color(0xFFFFFFFF),
            displayTextColor = Color(0xFFFFFFFF),
            historyTextColor = Color(0xFF98989F),
            topBarCircleColor = Color(0xFF28282C),
            topBarIconColor = Color(0xFFFFFFFF),
            isLightTheme = false
        )
        "midnight_blue" -> ResolvedThemeColors(
            backgroundColor = Color(0xFF070C1A),
            digitButtonColor = Color(0xFF1E2B48),
            digitButtonPressedColor = Color(0xFF3A4D78),
            digitButtonTextColor = Color(0xFFF5F8FF),
            topFuncButtonColor = Color(0xFF3B4F7A),
            topFuncButtonPressedColor = Color(0xFF5C74A8),
            topFuncButtonTextColor = Color(0xFFFFFFFF),
            sciButtonColor = Color(0xFF121C33),
            sciButtonPressedColor = Color(0xFF2E3F66),
            sciButtonHighlightedColor = Color(0xFF4D6499),
            sciButtonTextColor = Color(0xFFF5F8FF),
            displayTextColor = Color(0xFFF5F8FF),
            historyTextColor = Color(0xFF8CA0C4),
            topBarCircleColor = Color(0xFF17223B),
            topBarIconColor = Color(0xFFFFFFFF),
            isLightTheme = false
        )
        "light" -> ResolvedThemeColors(
            backgroundColor = Color(0xFFF2F2F7),
            digitButtonColor = Color(0xFFFFFFFF),
            digitButtonPressedColor = Color(0xFFD1D1D6),
            digitButtonTextColor = Color(0xFF000000),
            topFuncButtonColor = Color(0xFFD1D1D6),
            topFuncButtonPressedColor = Color(0xFFB4B4BC),
            topFuncButtonTextColor = Color(0xFF000000),
            sciButtonColor = Color(0xFFE5E5EA),
            sciButtonPressedColor = Color(0xFFC7C7CC),
            sciButtonHighlightedColor = Color(0xFFAEAEB2),
            sciButtonTextColor = Color(0xFF000000),
            displayTextColor = Color(0xFF000000),
            historyTextColor = Color(0xFF6C6C70),
            topBarCircleColor = Color(0xFFE5E5EA),
            topBarIconColor = Color(0xFF1C1C1E),
            isLightTheme = true
        )
        else -> ResolvedThemeColors(
            backgroundColor = baseStyle.backgroundColor,
            digitButtonColor = baseStyle.digitButtonColor,
            digitButtonPressedColor = baseStyle.digitButtonPressedColor,
            digitButtonTextColor = baseStyle.digitButtonTextColor,
            topFuncButtonColor = baseStyle.topFuncButtonColor,
            topFuncButtonPressedColor = baseStyle.topFuncButtonPressedColor,
            topFuncButtonTextColor = baseStyle.topFuncButtonTextColor,
            sciButtonColor = baseStyle.sciButtonColor,
            sciButtonPressedColor = baseStyle.sciButtonPressedColor,
            sciButtonHighlightedColor = baseStyle.sciButtonHighlightedColor,
            sciButtonTextColor = baseStyle.sciButtonTextColor,
            displayTextColor = baseStyle.displayTextColor,
            historyTextColor = baseStyle.historyTextColor,
            topBarCircleColor = baseStyle.topBarCircleColor,
            topBarIconColor = baseStyle.topBarIconColor,
            isLightTheme = baseStyle.isLightTheme
        )
    }

    val resolvedBorderColor: Color? = when (settings.glassBorderMode) {
        "on" -> if (themeColors.isLightTheme) Color(0x24000000) else Color(0x36FFFFFF)
        "off" -> null
        else -> if (themeColors.isLightTheme) Color(0x18000000) else baseStyle.buttonBorderColor
    }

    val (basicGap, sciGap, tabletGap, compactGap) = when (settings.buttonSpacingMode) {
        "compact" -> listOf(4.dp, 4.dp, 6.dp, 3.dp)
        "wide" -> listOf(12.dp, 9.dp, 14.dp, 8.dp)
        else -> listOf(8.dp, 6.dp, 10.dp, 5.dp)
    }

    val resolvedMenuBg: Color
    val resolvedMenuText: Color
    val resolvedMenuDivider: Color
    val resolvedSheetBg: Color
    when {
        themeColors.isLightTheme -> {
            resolvedMenuBg = Color(0xFFFFFFFF)
            resolvedMenuText = Color(0xFF1C1C1E)
            resolvedMenuDivider = Color(0xFFD1D1D6)
            resolvedSheetBg = Color(0xFFF2F2F7)
        }
        settings.backgroundTheme == "midnight_blue" -> {
            resolvedMenuBg = Color(0xFF17223B)
            resolvedMenuText = Color(0xFFF5F8FF)
            resolvedMenuDivider = Color(0xFF2B3C63)
            resolvedSheetBg = Color(0xFF0E1629)
        }
        settings.backgroundTheme == "charcoal" -> {
            resolvedMenuBg = Color(0xFF28282C)
            resolvedMenuText = Color(0xFFFFFFFF)
            resolvedMenuDivider = Color(0xFF3A3A40)
            resolvedSheetBg = Color(0xFF1C1C20)
        }
        else -> {
            resolvedMenuBg = baseStyle.menuBackgroundColor
            resolvedMenuText = baseStyle.menuTextColor
            resolvedMenuDivider = baseStyle.menuDividerColor
            resolvedSheetBg = baseStyle.sheetBackgroundColor
        }
    }

    return ResolvedIosAppearance(
        style = baseStyle,
        generation = effectiveGeneration,
        operatorColor = effectiveOpColor,
        operatorPressedColor = effectiveOpPressed,
        isClassicLayout = isClassicLayout,
        buttonShape = buttonShape,
        sciButtonShape = sciButtonShape,
        displayFontWeight = resolvedWeight,
        displayFontScaleMultiplier = scaleMult,
        basicSpacingDp = basicGap,
        sciSpacingDp = sciGap,
        tabletSpacingDp = tabletGap,
        compactLandscapeSpacingDp = compactGap,
        backgroundColor = themeColors.backgroundColor,
        digitButtonColor = themeColors.digitButtonColor,
        digitButtonPressedColor = themeColors.digitButtonPressedColor,
        digitButtonTextColor = themeColors.digitButtonTextColor,
        topFuncButtonColor = themeColors.topFuncButtonColor,
        topFuncButtonPressedColor = themeColors.topFuncButtonPressedColor,
        topFuncButtonTextColor = themeColors.topFuncButtonTextColor,
        sciButtonColor = themeColors.sciButtonColor,
        sciButtonPressedColor = themeColors.sciButtonPressedColor,
        sciButtonHighlightedColor = themeColors.sciButtonHighlightedColor,
        sciButtonTextColor = themeColors.sciButtonTextColor,
        displayTextColor = themeColors.displayTextColor,
        historyTextColor = themeColors.historyTextColor,
        topBarCircleColor = themeColors.topBarCircleColor,
        topBarIconColor = themeColors.topBarIconColor,
        buttonBorderColor = resolvedBorderColor,
        isLightTheme = themeColors.isLightTheme,
        buttonContentScaleMultiplier = buttonContentScaleMult,
        showHistoryLine = showHistoryLine,
        menuBackgroundColor = resolvedMenuBg,
        menuTextColor = resolvedMenuText,
        menuDividerColor = resolvedMenuDivider,
        sheetBackgroundColor = resolvedSheetBg,
        language = resolveAppLanguage(settings.language)
    )
}

internal object IosStylePreferences {
    private const val PREFS_NAME = "ios_calc_style_prefs"
    private const val KEY_FIRST_LAUNCH_COMPLETED = "first_launch_version_chosen_v1"
    private const val KEY_STYLE_ID = "style_id"
    private const val KEY_ACCENT_ID = "accent_color_id"
    private const val KEY_LAYOUT_OVERRIDE = "layout_override_v2"
    private const val KEY_BUTTON_SHAPE = "button_shape_style"
    private const val KEY_BUTTON_SPACING = "button_spacing_mode"
    private const val KEY_FONT_WEIGHT = "display_font_weight"
    private const val KEY_FONT_SCALE = "display_font_scale"
    private const val KEY_HAPTIC = "haptic_feedback_enabled"
    private const val KEY_THOUSAND_SEP = "use_thousand_separator"
    private const val KEY_CLASSIC_OP_HIGHLIGHT = "classic_operator_highlight"
    private const val KEY_ICON_PACK_ID = "icon_pack_id"
    private const val KEY_BACKGROUND_THEME = "background_theme"
    private const val KEY_GLASS_BORDER_MODE = "glass_border_mode"
    private const val KEY_BUTTON_CONTENT_SCALE = "button_content_scale"
    private const val KEY_SHOW_EXPRESSION_LINE = "show_expression_history_line"
    private const val KEY_SOUND_EFFECT = "sound_effect_enabled"
    private const val KEY_AUTO_SCI_PHONE_LANDSCAPE = "auto_sci_phone_landscape"
    private const val KEY_APP_LANGUAGE = "app_language"

    /**
     * Triggers asynchronous SharedPreferences XML disk loading on a background worker thread early in Activity.onCreate().
     */
    fun preload(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Reads initial style settings and first-launch completion flag from a single SharedPreferences lookup.
     */
    fun loadInitial(context: Context): Pair<IosStyleSettingsData, Boolean> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readFromPrefs(prefs) to prefs.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
    }

    fun load(context: Context): IosStyleSettingsData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return readFromPrefs(prefs)
    }

    private fun readFromPrefs(prefs: SharedPreferences): IosStyleSettingsData {
        val rawStyleId = prefs.getString(KEY_STYLE_ID, IosCalculatorStyle.IOS_27.id)
            ?: IosCalculatorStyle.IOS_27.id
        return IosStyleSettingsData(
            styleId = IosCalculatorStyle.fromId(rawStyleId).id,
            accentColorId = prefs.getString(KEY_ACCENT_ID, IosAccentColorOption.FOLLOW_VERSION.id)
                ?: IosAccentColorOption.FOLLOW_VERSION.id,
            layoutOverride = prefs.getString(KEY_LAYOUT_OVERRIDE, "auto") ?: "auto",
            buttonShapeStyle = prefs.getString(KEY_BUTTON_SHAPE, "circle") ?: "circle",
            buttonSpacingMode = prefs.getString(KEY_BUTTON_SPACING, "normal") ?: "normal",
            displayFontWeight = prefs.getString(KEY_FONT_WEIGHT, "auto") ?: "auto",
            displayFontScale = prefs.getString(KEY_FONT_SCALE, "normal") ?: "normal",
            hapticFeedbackEnabled = prefs.getBoolean(KEY_HAPTIC, true),
            useThousandSeparator = prefs.getBoolean(KEY_THOUSAND_SEP, true),
            classicOperatorHighlight = prefs.getBoolean(KEY_CLASSIC_OP_HIGHLIGHT, true),
            iconPackId = prefs.getString(KEY_ICON_PACK_ID, IosIconPackOption.DEFAULT_VECTOR.id)
                ?: IosIconPackOption.DEFAULT_VECTOR.id,
            backgroundTheme = prefs.getString(KEY_BACKGROUND_THEME, "auto") ?: "auto",
            glassBorderMode = prefs.getString(KEY_GLASS_BORDER_MODE, "auto") ?: "auto",
            buttonContentScale = prefs.getString(KEY_BUTTON_CONTENT_SCALE, "normal") ?: "normal",
            showExpressionHistoryLine = prefs.getString(KEY_SHOW_EXPRESSION_LINE, "auto") ?: "auto",
            soundEffectEnabled = prefs.getBoolean(KEY_SOUND_EFFECT, false),
            autoScientificOnPhoneLandscape = prefs.getBoolean(KEY_AUTO_SCI_PHONE_LANDSCAPE, true),
            language = prefs.getString(KEY_APP_LANGUAGE, "auto") ?: "auto"
        )
    }

    fun save(context: Context, data: IosStyleSettingsData) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE_ID, data.styleId)
            .putString(KEY_ACCENT_ID, data.accentColorId)
            .putString(KEY_LAYOUT_OVERRIDE, data.layoutOverride)
            .putString(KEY_BUTTON_SHAPE, data.buttonShapeStyle)
            .putString(KEY_BUTTON_SPACING, data.buttonSpacingMode)
            .putString(KEY_FONT_WEIGHT, data.displayFontWeight)
            .putString(KEY_FONT_SCALE, data.displayFontScale)
            .putBoolean(KEY_HAPTIC, data.hapticFeedbackEnabled)
            .putBoolean(KEY_THOUSAND_SEP, data.useThousandSeparator)
            .putBoolean(KEY_CLASSIC_OP_HIGHLIGHT, data.classicOperatorHighlight)
            .putString(KEY_ICON_PACK_ID, data.iconPackId)
            .putString(KEY_BACKGROUND_THEME, data.backgroundTheme)
            .putString(KEY_GLASS_BORDER_MODE, data.glassBorderMode)
            .putString(KEY_BUTTON_CONTENT_SCALE, data.buttonContentScale)
            .putString(KEY_SHOW_EXPRESSION_LINE, data.showExpressionHistoryLine)
            .putBoolean(KEY_SOUND_EFFECT, data.soundEffectEnabled)
            .putBoolean(KEY_AUTO_SCI_PHONE_LANDSCAPE, data.autoScientificOnPhoneLandscape)
            .putString(KEY_APP_LANGUAGE, data.language)
            .apply()
    }

    fun isFirstLaunchCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)

    fun setFirstLaunchCompleted(context: Context, completed: Boolean = true) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIRST_LAUNCH_COMPLETED, completed)
            .apply()
    }
}

@Composable
fun IosSettingsScreen(
    settingsData: IosStyleSettingsData,
    resolved: ResolvedIosAppearance,
    isTablet: Boolean,
    fontFamily: FontFamily,
    onUpdateSettings: (IosStyleSettingsData) -> Unit,
    onResetDefaults: () -> Unit,
    onOpenFirstLaunchPicker: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lang = resolved.language
    val currentStyle = resolved.style
    val accent = resolved.operatorColor
    val isLight = resolved.isLightTheme
    val groupedBg = resolved.backgroundColor
    val cardBg = if (isLight) Color.White else when (settingsData.backgroundTheme) {
        "midnight_blue" -> Color(0xFF121C33)
        "charcoal" -> Color(0xFF222226)
        else -> Color(0xFF1C1C1E)
    }
    val elevatedBg = if (isLight) Color(0xFFE5E5EA) else when (settingsData.backgroundTheme) {
        "midnight_blue" -> Color(0xFF1E2B48)
        "charcoal" -> Color(0xFF323238)
        else -> Color(0xFF2C2C2E)
    }
    val textPrimary = resolved.displayTextColor
    val textSecondary = resolved.historyTextColor
    val dividerColor = resolved.menuDividerColor

    var isEditMode by rememberSaveable { mutableStateOf(false) }
    var isVersionDropdownExpanded by remember { mutableStateOf(false) }

    val languageSection: @Composable () -> Unit = {
        SettingsSectionBlock(
            title = tr(lang, "介面語言 (LANGUAGE)", "界面语言 (LANGUAGE)", "LANGUAGE"),
            footer = tr(
                lang,
                "預設「跟隨系統」自動依手機／平板語系顯示繁體中文、简体中文或 English，亦可手動指定介面語言。",
                "默认「跟随系统」自动按设备语言显示繁體中文、简体中文或 English，也可手动切换界面语言。",
                "Defaults to System locale (繁體中文 / 简体中文 / English) or manually lock your preferred UI language."
            ),
            textSecondary = textSecondary,
            fontFamily = fontFamily
        ) {
            SegmentedChoiceCard(
                options = listOf(
                    "auto" to tr(lang, "跟隨系統", "跟随系统", "Auto"),
                    "zh_TW" to "繁體中文",
                    "zh_CN" to "简体中文",
                    "en" to "English"
                ),
                selectedId = settingsData.language,
                cardBg = cardBg,
                elevatedBg = elevatedBg,
                accent = accent,
                textPrimary = textPrimary,
                fontFamily = fontFamily,
                onSelect = { onUpdateSettings(settingsData.copy(language = it)) }
            )
        }
    }

    // 抽離設定區塊以便手機單欄與平板／橫向雙欄共用，消除重複排版
    val versionSelectorSection: @Composable () -> Unit = {
        SettingsSectionBlock(
            title = tr(lang, "選取版本 (iOS 27 ~ iOS 12)", "选取版本 (iOS 27 ~ iOS 12)", "iOS VERSION (iOS 27 ~ iOS 12)"),
            footer = tr(
                lang,
                "自動切換對應世代的 Apple 原廠介面與圖示：iOS 26/27（時鐘雙圓鈕）、iOS 18（清單＋左下計算機鍵）、iOS 12~17（雙倍寬 0 鍵＋黑字銀灰鍵）。",
                "自动切换对应世代的 Apple 原厂界面与图标：iOS 26/27（时钟双圆钮）、iOS 18（列表＋左下计算器键）、iOS 12~17（双倍宽 0 键＋黑字银灰键）。",
                "Automatically switches between authentic layouts & icons: iOS 26/27, iOS 18 (bottom-left switcher), and classic iOS 12~17."
            ),
            textSecondary = textSecondary,
            fontFamily = fontFamily
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .border(
                            width = if (isVersionDropdownExpanded) 1.5.dp else 0.8.dp,
                            color = if (isVersionDropdownExpanded) Color(0xFF0A84FF) else dividerColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { isVersionDropdownExpanded = !isVersionDropdownExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(currentStyle.backgroundColor)
                                .border(0.5.dp, dividerColor, RoundedCornerShape(7.dp))
                                .padding(horizontal = 5.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            ColorPreviewDot(currentStyle.digitButtonColor, dividerColor)
                            ColorPreviewDot(currentStyle.topFuncButtonColor, dividerColor)
                            ColorPreviewDot(currentStyle.operatorButtonColor, dividerColor)
                        }

                        Text(
                            text = currentStyle.displayName,
                            color = textPrimary,
                            fontSize = 17.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.18f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentStyle.eraTag(lang),
                                color = accent,
                                fontSize = 11.sp,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = if (isVersionDropdownExpanded) "⌃" else "⌄",
                        color = textSecondary,
                        fontSize = 18.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }

                AnimatedVisibility(visible = isVersionDropdownExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .border(0.8.dp, dividerColor, RoundedCornerShape(12.dp))
                    ) {
                        IosCalculatorStyle.entries.forEachIndexed { idx, styleOption ->
                            val isSelected = styleOption.id == currentStyle.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color(0xFF0A84FF) else Color.Transparent)
                                    .clickable {
                                        onUpdateSettings(settingsData.copy(styleId = styleOption.id, layoutOverride = "auto"))
                                        isVersionDropdownExpanded = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = styleOption.displayName,
                                        color = if (isSelected) Color.White else textPrimary,
                                        fontSize = 16.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = styleOption.eraTag(lang),
                                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else textSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = fontFamily
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ColorPreviewDot(styleOption.digitButtonColor, dividerColor)
                                    ColorPreviewDot(styleOption.topFuncButtonColor, dividerColor)
                                    ColorPreviewDot(styleOption.operatorButtonColor, dividerColor)
                                }
                            }
                            if (idx < IosCalculatorStyle.entries.lastIndex) {
                                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    val editEntryCard: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .clickable { isEditMode = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr(
                        lang,
                        "進階自定義編輯（背景 / 強調色 / 佈局 / 按鍵 / 字體）",
                        "高级自定义编辑（背景 / 强调色 / 布局 / 按键 / 字体）",
                        "Customize Appearance (Background / Colors / Layout / Fonts)"
                    ),
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tr(
                        lang,
                        "點擊此處或右上角「編輯」自訂按鍵外觀與版面細節",
                        "点击此处或右上角「编辑」自定义按键外观与版面细节",
                        "Tap here or \"Edit\" at top-right to customize appearance"
                    ),
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontFamily = fontFamily
                )
            }
            Text(
                text = tr(lang, "編輯 ›", "编辑 ›", "Edit ›"),
                color = accent,
                fontSize = 14.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium
            )
        }
    }

    val accentColorSection: @Composable () -> Unit = {
        SettingsSectionBlock(
            title = tr(lang, "自定義運算鍵強調色", "自定义运算键强调色", "OPERATOR ACCENT COLOR"),
            footer = tr(
                lang,
                "可覆蓋版本預設橘色，更換右側運算符號與 = 鍵色彩。",
                "可覆盖版本默认橙色，更换右侧运算符号与 = 键色彩。",
                "Override the default orange color on operator and = buttons."
            ),
            textSecondary = textSecondary,
            fontFamily = fontFamily
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosAccentColorOption.entries.forEach { option ->
                    val selected = settingsData.accentColorId == option.id
                    val dotColor = option.color ?: currentStyle.operatorButtonColor
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) dotColor.copy(alpha = 0.2f) else elevatedBg)
                            .border(
                                width = if (selected) 1.dp else 0.dp,
                                color = if (selected) dotColor else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onUpdateSettings(settingsData.copy(accentColorId = option.id))
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(1.dp, dividerColor, CircleShape)
                        )
                        Text(
                            text = option.label(lang),
                            color = if (selected) dotColor else textPrimary,
                            fontSize = 11.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    val backgroundThemeSection: @Composable () -> Unit = {
        SettingsSectionBlock(
            title = tr(lang, "背景基底主題", "背景基底主题", "BACKGROUND THEME"),
            footer = tr(
                lang,
                "可覆蓋版本預設底色，自由切換純黑 OLED、曜石深灰、午夜深藍或淺色白晝模式。",
                "可覆盖版本默认底色，自由切换纯黑 OLED、曜石深灰、午夜深蓝或浅色白昼模式。",
                "Switch base background between Version Default, OLED Black, Charcoal, Midnight Blue, or Light Mode."
            ),
            textSecondary = textSecondary,
            fontFamily = fontFamily
        ) {
            val bgThemes = listOf(
                "auto" to tr(lang, "版本預設", "版本默认", "Default"),
                "oled_black" to tr(lang, "純黑 OLED", "纯黑 OLED", "OLED Black"),
                "charcoal" to tr(lang, "曜石深灰", "曜石深灰", "Charcoal"),
                "midnight_blue" to tr(lang, "午夜深藍", "午夜深蓝", "Midnight Blue"),
                "light" to tr(lang, "淺色白晝", "浅色白昼", "Light Mode")
            )
            val themeSwatches = mapOf(
                "auto" to currentStyle.backgroundColor,
                "oled_black" to Color(0xFF000000),
                "charcoal" to Color(0xFF141416),
                "midnight_blue" to Color(0xFF070C1A),
                "light" to Color(0xFFF2F2F7)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bgThemes.forEach { (themeId, label) ->
                    val selected = settingsData.backgroundTheme == themeId
                    val swatch = themeSwatches[themeId] ?: Color.Black
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent.copy(alpha = 0.2f) else elevatedBg)
                            .border(
                                width = if (selected) 1.dp else 0.dp,
                                color = if (selected) accent else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onUpdateSettings(settingsData.copy(backgroundTheme = themeId))
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, dividerColor, CircleShape)
                        )
                        Text(
                            text = label,
                            color = if (selected) accent else textPrimary,
                            fontSize = 11.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    val layoutOverrideSection: @Composable () -> Unit = {
        SettingsSectionBlock(
            title = tr(lang, "按鍵排列模式", "按键排列模式", "KEYPAD LAYOUT OVERRIDE"),
            footer = tr(
                lang,
                "自由混搭：可在任何配色下切換 iOS 27 (⌫/AC)、iOS 18 (左下切換) 或經典雙倍寬 0 鍵排列。",
                "自由混搭：可在任何配色下切换 iOS 27 (⌫/AC)、iOS 18 (左下切换) 或经典双倍宽 0 键排列。",
                "Mix & match any theme colors with iOS 27 (⌫/AC), iOS 18 (bottom-left mode), or Classic double-width 0 layout."
            ),
            textSecondary = textSecondary,
            fontFamily = fontFamily
        ) {
            SegmentedChoiceCard(
                options = listOf(
                    "auto" to tr(lang, "跟隨版本", "跟随版本", "Auto"),
                    "modern" to "iOS 27",
                    "ios18" to "iOS 18",
                    "classic" to tr(lang, "經典雙倍 0", "经典双倍 0", "Classic")
                ),
                selectedId = settingsData.layoutOverride,
                cardBg = cardBg,
                elevatedBg = elevatedBg,
                accent = accent,
                textPrimary = textPrimary,
                fontFamily = fontFamily,
                onSelect = { onUpdateSettings(settingsData.copy(layoutOverride = it)) }
            )
        }
    }

    val buttonShapeSpacingBorderSection: @Composable () -> Unit = {
        SettingsSectionBlock(
            title = tr(lang, "按鍵形狀、間距與液態玻璃邊框", "按键形状、间距与液态玻璃边框", "BUTTON SHAPE, SPACING & BORDERS"),
            footer = tr(
                lang,
                "調整按鈕圓角曲率、縫隙密度，或開啟／關閉 Liquid Glass 微光細邊框。",
                "调整按钮圆角曲率、缝隙密度，或开启／关闭 Liquid Glass 微光细边框。",
                "Adjust corner radius, key spacing, and Liquid Glass luminous borders."
            ),
            textSecondary = textSecondary,
            fontFamily = fontFamily
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(tr(lang, "按鍵形狀", "按键形状", "Button Shape"), color = textSecondary, fontSize = 12.sp, fontFamily = fontFamily)
                SegmentedChoiceRow(
                    options = listOf(
                        "circle" to tr(lang, "經典圓形", "经典圆形", "Circle"),
                        "squircle" to tr(lang, "圓角矩形", "圆角矩形", "Squircle"),
                        "rounded_rect" to tr(lang, "微圓角方塊", "微圆角方块", "Rounded")
                    ),
                    selectedId = settingsData.buttonShapeStyle,
                    elevatedBg = elevatedBg,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onSelect = { onUpdateSettings(settingsData.copy(buttonShapeStyle = it)) }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                Text(tr(lang, "按鍵間距", "按键间距", "Button Spacing"), color = textSecondary, fontSize = 12.sp, fontFamily = fontFamily)
                SegmentedChoiceRow(
                    options = listOf(
                        "compact" to tr(lang, "緊湊", "紧凑", "Compact"),
                        "normal" to tr(lang, "標準", "标准", "Normal"),
                        "wide" to tr(lang, "寬鬆", "宽松", "Wide")
                    ),
                    selectedId = settingsData.buttonSpacingMode,
                    elevatedBg = elevatedBg,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onSelect = { onUpdateSettings(settingsData.copy(buttonSpacingMode = it)) }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                Text(tr(lang, "液態玻璃微光邊框", "液态玻璃微光边框", "Liquid Glass Borders"), color = textSecondary, fontSize = 12.sp, fontFamily = fontFamily)
                SegmentedChoiceRow(
                    options = listOf(
                        "auto" to tr(lang, "跟隨版本", "跟随版本", "Auto"),
                        "on" to tr(lang, "強制開啟", "强制开启", "On"),
                        "off" to tr(lang, "關閉無框", "关闭无框", "Off")
                    ),
                    selectedId = settingsData.glassBorderMode,
                    elevatedBg = elevatedBg,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onSelect = { onUpdateSettings(settingsData.copy(glassBorderMode = it)) }
                )
            }
        }
    }

    val fontAndHistoryLineSection: @Composable () -> Unit = {
        SettingsSectionBlock(
            title = tr(lang, "顯示、按鍵字體與算式列自訂", "显示、按键字体与算式列自定义", "DISPLAY, BUTTON FONTS & EXPRESSION LINE"),
            footer = tr(
                lang,
                "可獨立微調顯示結果大小、按鍵數字／圖示比例，以及是否顯示上方算式過程列。",
                "可独立微调显示结果大小、按键数字／图标比例，以及是否显示上方算式过程列。",
                "Fine-tune result typography, key text/icon scale, and expression line visibility."
            ),
            textSecondary = textSecondary,
            fontFamily = fontFamily
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(tr(lang, "數字字體粗細", "数字字体粗细", "Display Font Weight"), color = textSecondary, fontSize = 12.sp, fontFamily = fontFamily)
                SegmentedChoiceRow(
                    options = listOf(
                        "auto" to tr(lang, "版本預設", "版本默认", "Default"),
                        "light" to tr(lang, "細體", "细体", "Light"),
                        "normal" to tr(lang, "標準", "标准", "Regular"),
                        "medium" to tr(lang, "中粗", "中粗", "Medium")
                    ),
                    selectedId = settingsData.displayFontWeight,
                    elevatedBg = elevatedBg,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onSelect = { onUpdateSettings(settingsData.copy(displayFontWeight = it)) }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                Text(tr(lang, "數字大小比例", "数字大小比例", "Result Size"), color = textSecondary, fontSize = 12.sp, fontFamily = fontFamily)
                SegmentedChoiceRow(
                    options = listOf(
                        "small" to tr(lang, "小 (88%)", "小 (88%)", "Small"),
                        "normal" to tr(lang, "標準 (100%)", "标准 (100%)", "Normal"),
                        "large" to tr(lang, "大 (112%)", "大 (112%)", "Large")
                    ),
                    selectedId = settingsData.displayFontScale,
                    elevatedBg = elevatedBg,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onSelect = { onUpdateSettings(settingsData.copy(displayFontScale = it)) }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                Text(tr(lang, "按鍵文字與圖示大小", "按键文字与图标大小", "Key Label & Icon Scale"), color = textSecondary, fontSize = 12.sp, fontFamily = fontFamily)
                SegmentedChoiceRow(
                    options = listOf(
                        "small" to tr(lang, "精緻 (88%)", "精致 (88%)", "Compact"),
                        "normal" to tr(lang, "標準 (100%)", "标准 (100%)", "Normal"),
                        "large" to tr(lang, "放大 (115%)", "放大 (115%)", "Large")
                    ),
                    selectedId = settingsData.buttonContentScale,
                    elevatedBg = elevatedBg,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onSelect = { onUpdateSettings(settingsData.copy(buttonContentScale = it)) }
                )

                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

                Text(tr(lang, "上方算式過程列", "上方算式过程列", "Top Expression Line"), color = textSecondary, fontSize = 12.sp, fontFamily = fontFamily)
                SegmentedChoiceRow(
                    options = listOf(
                        "auto" to tr(lang, "跟隨版本", "跟随版本", "Auto"),
                        "always" to tr(lang, "總是顯示", "总是显示", "Show"),
                        "hide" to tr(lang, "隱藏單行", "隐藏单行", "Hide")
                    ),
                    selectedId = settingsData.showExpressionHistoryLine,
                    elevatedBg = elevatedBg,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onSelect = { onUpdateSettings(settingsData.copy(showExpressionHistoryLine = it)) }
                )
            }
        }
    }

    val interactionAndDisplaySection: @Composable () -> Unit = {
        SettingsSectionBlock(
            title = tr(lang, "互動與顯示設定", "互动与显示设置", "INTERACTION & DISPLAY"),
            footer = tr(
                lang,
                "經典運算鍵反白開啟後，在 iOS 12~17 按下 ÷ × − + 時按鍵會呈現白底橘字；左右滑動顯示區數字可退格。",
                "经典运算键反白开启后，在 iOS 12~17 按下 ÷ × − + 时按键会呈现白底橙字；左右滑动显示区数字可退格。",
                "Classic highlight turns active operators white in iOS 12~17. Swipe horizontally across digits to backspace."
            ),
            textSecondary = textSecondary,
            fontFamily = fontFamily
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
            ) {
                SettingsSwitchRow(
                    title = tr(lang, "千分位逗號分隔 (1,000)", "千分位逗号分隔 (1,000)", "Thousands Separator (1,000)"),
                    checked = settingsData.useThousandSeparator,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onCheckedChange = {
                        onUpdateSettings(settingsData.copy(useThousandSeparator = it))
                    }
                )
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                SettingsSwitchRow(
                    title = tr(lang, "按鍵觸感震動回饋", "按键触感震动反馈", "Haptic Key Feedback"),
                    checked = settingsData.hapticFeedbackEnabled,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onCheckedChange = {
                        onUpdateSettings(settingsData.copy(hapticFeedbackEnabled = it))
                    }
                )
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                SettingsSwitchRow(
                    title = tr(lang, "按鍵點擊音效回饋", "按键点击音效反馈", "Key Click Sound"),
                    checked = settingsData.soundEffectEnabled,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onCheckedChange = {
                        onUpdateSettings(settingsData.copy(soundEffectEnabled = it))
                    }
                )
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                SettingsSwitchRow(
                    title = tr(lang, "手機橫向自動切換科學計算機", "手机横向自动切换科学计算器", "Auto Scientific on Phone Landscape"),
                    checked = settingsData.autoScientificOnPhoneLandscape,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onCheckedChange = {
                        onUpdateSettings(settingsData.copy(autoScientificOnPhoneLandscape = it))
                    }
                )
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                SettingsSwitchRow(
                    title = tr(lang, "經典運算鍵反白高亮 (白底橘字)", "经典运算键反白高亮 (白底橙字)", "Classic Active Operator Highlight"),
                    checked = settingsData.classicOperatorHighlight,
                    accent = accent,
                    textPrimary = textPrimary,
                    fontFamily = fontFamily,
                    onCheckedChange = {
                        onUpdateSettings(settingsData.copy(classicOperatorHighlight = it))
                    }
                )
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFirstLaunchPicker() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "重新開啟首次版本選擇畫面", "重新开启首次版本选择界面", "Reopen Welcome iOS Version Picker"),
                        color = accent,
                        fontSize = 15.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "›",
                        color = textSecondary,
                        fontSize = 18.sp,
                        fontFamily = fontFamily
                    )
                }
            }
        }
    }

    val iconPackSection: @Composable () -> Unit = {
        SettingsSectionBlock(
            title = tr(lang, "圖示套件 (ICON PACK)", "图标套件 (ICON PACK)", "ICON PACK"),
            footer = tr(
                lang,
                "目前系統已依 iOS 26/27、iOS 18、iOS 12~17 自動套用各代專屬圖示，亦保留自訂圖示包插槽。",
                "目前系统已按 iOS 26/27、iOS 18、iOS 12~17 自动套用各代专属图标，亦保留自定义图标包插槽。",
                "Automatically applies authentic icons per iOS generation, with a reserved slot for custom packs."
            ),
            textSecondary = textSecondary,
            fontFamily = fontFamily
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
            ) {
                IosIconPackOption.entries.forEachIndexed { idx, pack ->
                    val isSelected = settingsData.iconPackId == pack.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateSettings(settingsData.copy(iconPackId = pack.id))
                                if (pack == IosIconPackOption.CUSTOM_PACK) {
                                    Toast.makeText(
                                        context,
                                        tr(lang, "已選取預留插槽，待匯入新圖示後將自動套用", "已选择预留插槽，待导入新图标后将自动应用", "Reserved slot selected"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = pack.title(lang),
                                    color = textPrimary,
                                    fontSize = 15.sp,
                                    fontFamily = fontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (pack == IosIconPackOption.DEFAULT_VECTOR)
                                                accent.copy(alpha = 0.16f)
                                            else
                                                textSecondary.copy(alpha = 0.18f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = pack.badgeText(lang),
                                        color = if (pack == IosIconPackOption.DEFAULT_VECTOR) accent else textSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = pack.description(lang),
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontFamily = fontFamily
                            )
                        }

                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_menu_check),
                                contentDescription = tr(lang, "已選取", "已选取", "Selected"),
                                tint = accent,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(18.dp)
                            )
                        }
                    }
                    if (idx < IosIconPackOption.entries.lastIndex) {
                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(groupedBg)
    ) {
        val useTwoColumns = maxWidth >= 680.dp || isTablet
        val horizontalPad = if (useTwoColumns) 24.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. iOS 設定頂部導覽列：橫跨整個螢幕寬度（左「‹ 返回」、中「設定」、右固定顯示「編輯」）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPad, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr(lang, "‹ 返回", "‹ 返回", "‹ Back"),
                    color = accent,
                    fontSize = 17.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_menu_settings),
                        contentDescription = null,
                        tint = textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = tr(lang, "設定", "设置", "Settings"),
                        color = textPrimary,
                        fontSize = 18.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isEditMode) accent else Color.Transparent)
                        .clickable { isEditMode = !isEditMode }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isEditMode) tr(lang, "完成", "完成", "Done") else tr(lang, "編輯", "编辑", "Edit"),
                        color = if (isEditMode) Color.White else accent,
                        fontSize = 16.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

            // 2. 滿寬分組設定內容：寬螢幕（平板／橫向）自動分為左右雙欄以充分利用空間
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPad, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                IosStyleLiveMiniPreview(
                    settingsData = settingsData,
                    resolved = resolved,
                    cardBg = cardBg,
                    dividerColor = dividerColor,
                    fontFamily = fontFamily
                )

                AnimatedVisibility(visible = isEditMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .border(0.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(lang, "已開啟自定義編輯模式", "已开启自定义编辑模式", "Customization Edit Mode Active"),
                                color = accent,
                                fontSize = 14.sp,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = tr(lang, "可自由混搭背景主題、強調色、按鍵佈局與顯示字體", "可自由混搭背景主题、强调色、按键布局与显示字体", "Mix backgrounds, accent colors, keypad layouts, and typography"),
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontFamily = fontFamily
                            )
                        }

                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(elevatedBg)
                                .clickable {
                                    onResetDefaults()
                                    Toast.makeText(
                                        context,
                                        tr(lang, "已恢復為 iOS 27 原廠預設", "已恢复为 iOS 27 原厂默认", "Reset to iOS 27 defaults"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tr(lang, "重設預設", "重置默认", "Reset"),
                                color = Color(0xFFFF453A),
                                fontSize = 12.sp,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (useTwoColumns) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            versionSelectorSection()
                            if (!isEditMode) {
                                editEntryCard()
                            }
                            AnimatedVisibility(visible = isEditMode) {
                                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    accentColorSection()
                                    backgroundThemeSection()
                                    layoutOverrideSection()
                                }
                            }
                            iconPackSection()
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            languageSection()
                            AnimatedVisibility(visible = isEditMode) {
                                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    buttonShapeSpacingBorderSection()
                                    fontAndHistoryLineSection()
                                }
                            }
                            interactionAndDisplaySection()
                        }
                    }
                } else {
                    versionSelectorSection()
                    languageSection()
                    if (!isEditMode) {
                        editEntryCard()
                    }
                    AnimatedVisibility(visible = isEditMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            accentColorSection()
                            backgroundThemeSection()
                            layoutOverrideSection()
                            buttonShapeSpacingBorderSection()
                            fontAndHistoryLineSection()
                        }
                    }
                    interactionAndDisplaySection()
                    iconPackSection()
                }
            }
        }
    }
}

@Composable
fun IosFirstLaunchVersionDialog(
    currentSettings: IosStyleSettingsData,
    fontFamily: FontFamily,
    onSelectLanguage: (String) -> Unit = {},
    onConfirmStyle: (IosCalculatorStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStyleId by rememberSaveable { mutableStateOf(IosCalculatorStyle.IOS_27.id) }
    val selectedStyle = IosCalculatorStyle.fromId(selectedStyleId)
    val previewSettings = remember(currentSettings, selectedStyle) {
        currentSettings.copy(styleId = selectedStyle.id, layoutOverride = "auto")
    }
    val previewResolved = remember(previewSettings) { resolveIosAppearance(previewSettings) }
    val lang = previewResolved.language
    val accent = previewResolved.operatorColor
    val isLight = previewResolved.isLightTheme
    val bgColor = previewResolved.backgroundColor
    val cardBg = if (isLight) Color.White else Color(0xFF1C1C1E)
    val elevatedBg = if (isLight) Color(0xFFE5E5EA) else Color(0xFF2C2C2E)
    val textPrimary = previewResolved.displayTextColor
    val textSecondary = previewResolved.historyTextColor
    val dividerColor = previewResolved.menuDividerColor

    @Composable
    fun VersionOptionCard(styleOption: IosCalculatorStyle, cardModifier: Modifier = Modifier) {
        val isSelected = styleOption.id == selectedStyle.id
        val isLatest = styleOption == IosCalculatorStyle.IOS_27
        Row(
            modifier = cardModifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) accent.copy(alpha = 0.14f) else cardBg)
                .border(
                    width = if (isSelected) 1.5.dp else 0.7.dp,
                    color = if (isSelected) accent else dividerColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { selectedStyleId = styleOption.id }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = styleOption.displayName,
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLatest) accent else elevatedBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isLatest) tr(lang, "最新（iOS 最新主題）", "最新（iOS 最新主题）", "Latest (iOS Latest Theme)") else styleOption.eraTag(lang),
                            color = if (isLatest) Color.White else textSecondary,
                            fontSize = 11.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = styleOption.subtitle(lang),
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                    maxLines = 2
                )
            }

            Row(
                modifier = Modifier.padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    ColorPreviewDot(styleOption.digitButtonColor, dividerColor)
                    ColorPreviewDot(styleOption.topFuncButtonColor, dividerColor)
                    ColorPreviewDot(styleOption.operatorButtonColor, dividerColor)
                }
                if (isSelected) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_menu_check),
                        contentDescription = tr(lang, "已選擇", "已选择", "Selected"),
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { /* 攔截底部點擊 */ }
    ) {
        val isWide = maxWidth >= 680.dp
        val horizontalPad = if (isWide) 28.dp else 16.dp

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPad, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 快速語言切換列（系統 / 繁體 / 简体 / English）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(elevatedBg)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf(
                            "auto" to tr(lang, "系統", "系统", "Auto"),
                            "zh_TW" to "繁體",
                            "zh_CN" to "简体",
                            "en" to "EN"
                        ).forEach { (code, label) ->
                            val selected = currentSettings.language == code
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) accent else Color.Transparent)
                                    .clickable { onSelectLanguage(code) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else textSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = fontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = tr(lang, "選擇您的 iOS 計算機版本", "选择您的 iOS 计算器版本", "Choose Your iOS Calculator Style"),
                        color = textPrimary,
                        fontSize = if (isWide) 26.sp else 22.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = tr(
                            lang,
                            "首次安裝請挑選偏好的 iOS 介面世代與原廠圖示風格，預設推薦「iOS 27 最新（iOS 最新主題）」。之後隨時可於「設定」更改或自訂背景色彩。",
                            "首次安装请挑选偏好的 iOS 界面世代与原厂图标风格，默认推荐「iOS 27 最新（iOS 最新主题）」。之后随时可在「设置」更改或自定义背景色彩。",
                            "Pick your preferred iOS calculator generation and icon style. Defaults to iOS 27 (Latest Theme). You can customize colors anytime in Settings."
                        ),
                        color = textSecondary,
                        fontSize = 13.sp,
                        fontFamily = fontFamily
                    )
                }

                IosStyleLiveMiniPreview(
                    settingsData = previewSettings,
                    resolved = previewResolved,
                    cardBg = cardBg,
                    dividerColor = dividerColor,
                    fontFamily = fontFamily
                )

                if (isWide) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IosCalculatorStyle.entries.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                VersionOptionCard(pair[0], Modifier.weight(1f))
                                if (pair.size > 1) {
                                    VersionOptionCard(pair[1], Modifier.weight(1f))
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IosCalculatorStyle.entries.forEach { styleOption ->
                            VersionOptionCard(styleOption, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = horizontalPad, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent)
                        .clickable { onConfirmStyle(selectedStyle) }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr(
                            lang,
                            "開始使用 ${selectedStyle.displayName}${if (selectedStyle == IosCalculatorStyle.IOS_27) "（iOS 最新主題）" else ""}",
                            "开始使用 ${selectedStyle.displayName}${if (selectedStyle == IosCalculatorStyle.IOS_27) "（iOS 最新主题）" else ""}",
                            "Start Using ${selectedStyle.displayName}${if (selectedStyle == IosCalculatorStyle.IOS_27) " (iOS Latest Theme)" else ""}"
                        ),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun IosStyleLiveMiniPreview(
    settingsData: IosStyleSettingsData,
    resolved: ResolvedIosAppearance,
    cardBg: Color,
    dividerColor: Color,
    fontFamily: FontFamily
) {
    val style = resolved.style
    val lang = resolved.language
    val accent = resolved.operatorColor
    val miniShape: Shape = when (settingsData.buttonShapeStyle) {
        "squircle" -> RoundedCornerShape(7.dp)
        "rounded_rect" -> RoundedCornerShape(4.dp)
        else -> CircleShape
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(0.5.dp, dividerColor, RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 依照三大世代動態繪製真實差異的迷你縮圖
        Column(
            modifier = Modifier
                .width(102.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(resolved.backgroundColor)
                .border(0.8.dp, dividerColor, RoundedCornerShape(14.dp))
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 迷你頂部導覽列
            val isIos26Style = style.id == IosCalculatorStyle.IOS_26.id
            val miniTopBorderColor = if (settingsData.glassBorderMode == "on" || (settingsData.glassBorderMode == "auto" && isIos26Style)) {
                resolved.buttonBorderColor
            } else null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (resolved.generation == IosLayoutGeneration.MODERN_18) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_calc_history_ios18),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                } else {
                    val leftIconId = if (resolved.generation == IosLayoutGeneration.MODERN_26_27 && isIos26Style) {
                        R.drawable.ic_calc_history_ios18
                    } else {
                        R.drawable.ic_calc_history
                    }
                    val rightIconId = if (resolved.generation == IosLayoutGeneration.MODERN_26_27 && isIos26Style) {
                        R.drawable.ic_calc_mode_ios26
                    } else {
                        R.drawable.ic_calc_mode_ios27
                    }
                    MiniTopBarCircleIcon(
                        circleColor = resolved.topBarCircleColor,
                        borderColor = miniTopBorderColor,
                        iconResId = leftIconId,
                        iconTint = resolved.topBarIconColor,
                        iconSize = 6.5.dp
                    )
                    MiniTopBarCircleIcon(
                        circleColor = resolved.topBarCircleColor,
                        borderColor = miniTopBorderColor,
                        iconResId = rightIconId,
                        iconTint = resolved.topBarIconColor,
                        iconSize = 6.5.dp
                    )
                }
            }

            if (resolved.showHistoryLine) {
                Text(
                    text = "6×205",
                    color = resolved.historyTextColor,
                    fontSize = 8.sp,
                    fontFamily = fontFamily,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            Text(
                text = if (settingsData.useThousandSeparator) "1,230" else "1230",
                color = resolved.displayTextColor,
                fontSize = 14.sp,
                fontFamily = fontFamily,
                fontWeight = resolved.displayFontWeight,
                modifier = Modifier.align(Alignment.End)
            )

            // 第一排預覽
            val firstRow = when (resolved.generation) {
                IosLayoutGeneration.MODERN_26_27 -> listOf("⌫", "AC", "%", "÷")
                IosLayoutGeneration.MODERN_18 -> listOf("AC", "+/-", "%", "÷")
                IosLayoutGeneration.CLASSIC_12_17 -> listOf("AC", "+/-", "%", "÷")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                firstRow.forEachIndexed { idx, label ->
                    val isOp = idx == 3
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(miniShape)
                            .background(if (isOp) accent else resolved.topFuncButtonColor)
                            .then(
                                if (resolved.buttonBorderColor != null) Modifier.border(0.4.dp, resolved.buttonBorderColor, miniShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isOp) style.operatorButtonTextColor else resolved.topFuncButtonTextColor,
                            fontSize = 7.sp,
                            fontFamily = fontFamily
                        )
                    }
                }
            }

            // 最後一排預覽
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (resolved.generation) {
                    IosLayoutGeneration.CLASSIC_12_17 -> {
                        Box(
                            modifier = Modifier
                                .weight(2f)
                                .height(16.dp)
                                .clip(miniShape)
                                .background(resolved.digitButtonColor)
                                .then(
                                    if (resolved.buttonBorderColor != null) Modifier.border(0.4.dp, resolved.buttonBorderColor, miniShape)
                                    else Modifier
                                )
                                .padding(start = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("0", color = resolved.digitButtonTextColor, fontSize = 8.sp, fontFamily = fontFamily)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(miniShape)
                                .background(resolved.digitButtonColor)
                                .then(
                                    if (resolved.buttonBorderColor != null) Modifier.border(0.4.dp, resolved.buttonBorderColor, miniShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(".", color = resolved.digitButtonTextColor, fontSize = 8.sp, fontFamily = fontFamily)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(miniShape)
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("=", color = style.operatorButtonTextColor, fontSize = 8.sp, fontFamily = fontFamily)
                        }
                    }
                    IosLayoutGeneration.MODERN_18 -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(miniShape)
                                .background(resolved.digitButtonColor)
                                .then(
                                    if (resolved.buttonBorderColor != null) Modifier.border(0.4.dp, resolved.buttonBorderColor, miniShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_calc_mode_ios27),
                                contentDescription = null,
                                tint = resolved.digitButtonTextColor,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                        listOf("0", ".", "=").forEachIndexed { idx, label ->
                            val isOp = idx == 2
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .clip(miniShape)
                                    .background(if (isOp) accent else resolved.digitButtonColor)
                                    .then(
                                        if (!isOp && resolved.buttonBorderColor != null) Modifier.border(0.4.dp, resolved.buttonBorderColor, miniShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isOp) style.operatorButtonTextColor else resolved.digitButtonTextColor,
                                    fontSize = 7.sp,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }
                    IosLayoutGeneration.MODERN_26_27 -> {
                        listOf("+/-", "0", ".", "=").forEachIndexed { idx, label ->
                            val isOp = idx == 3
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .clip(miniShape)
                                    .background(if (isOp) accent else resolved.digitButtonColor)
                                    .then(
                                        if (!isOp && resolved.buttonBorderColor != null) Modifier.border(0.4.dp, resolved.buttonBorderColor, miniShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isOp) style.operatorButtonTextColor else resolved.digitButtonTextColor,
                                    fontSize = 7.sp,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = tr(lang, "目前套用：${style.displayName}", "当前应用：${style.displayName}", "Active: ${style.displayName}"),
                color = resolved.displayTextColor,
                fontSize = 16.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when (resolved.generation) {
                    IosLayoutGeneration.MODERN_26_27 -> tr(
                        lang,
                        "介面：iOS 26/27（頂部雙圓鈕 · ⌫ AC % ÷）",
                        "界面：iOS 26/27（顶部双圆钮 · ⌫ AC % ÷）",
                        "Layout: iOS 26/27 (Top Buttons · ⌫ AC % ÷)"
                    )
                    IosLayoutGeneration.MODERN_18 -> tr(
                        lang,
                        "介面：iOS 18（無框清單 · 左下計算機切換鈕）",
                        "界面：iOS 18（无框列表 · 左下计算器切换钮）",
                        "Layout: iOS 18 (Bottom-Left Switcher)"
                    )
                    IosLayoutGeneration.CLASSIC_12_17 -> tr(
                        lang,
                        "介面：iOS 12~17 經典（單行顯示 · 雙倍寬 0 鍵）",
                        "界面：iOS 12~17 经典（单行显示 · 双倍宽 0 键）",
                        "Layout: Classic iOS 12~17 (Double-Width 0)"
                    )
                },
                color = accent,
                fontSize = 12.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = style.subtitle(lang),
                color = resolved.historyTextColor,
                fontSize = 12.sp,
                fontFamily = fontFamily
            )
        }
    }
}

@Composable
private fun MiniTopBarCircleIcon(
    circleColor: Color,
    borderColor: Color?,
    iconResId: Int,
    iconTint: Color,
    iconSize: Dp
) {
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(circleColor)
            .then(
                if (borderColor != null) Modifier.border(0.4.dp, borderColor, CircleShape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun SegmentedChoiceCard(
    options: List<Pair<String, String>>,
    selectedId: String,
    cardBg: Color,
    elevatedBg: Color,
    accent: Color,
    textPrimary: Color,
    fontFamily: FontFamily,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(10.dp)
    ) {
        SegmentedChoiceRow(
            options = options,
            selectedId = selectedId,
            elevatedBg = elevatedBg,
            accent = accent,
            textPrimary = textPrimary,
            fontFamily = fontFamily,
            onSelect = onSelect
        )
    }
}

@Composable
private fun SegmentedChoiceRow(
    options: List<Pair<String, String>>,
    selectedId: String,
    elevatedBg: Color,
    accent: Color,
    textPrimary: Color,
    fontFamily: FontFamily,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(elevatedBg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (id, label) ->
            val selected = id == selectedId
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) accent else Color.Transparent)
                    .clickable { onSelect(id) }
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else textPrimary,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    accent: Color,
    textPrimary: Color,
    fontFamily: FontFamily,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = textPrimary,
            fontSize = 15.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF48484A)
            )
        )
    }
}

@Composable
private fun SettingsSectionBlock(
    title: String,
    footer: String,
    textSecondary: Color,
    fontFamily: FontFamily,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = textSecondary,
            fontSize = 12.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        content()
        Text(
            text = footer,
            color = textSecondary,
            fontSize = 12.sp,
            fontFamily = fontFamily,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun ColorPreviewDot(color: Color, borderColor: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
            .border(0.5.dp, borderColor, CircleShape)
    )
}
