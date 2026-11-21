package com.example.ioscalculatorapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class UnitItem(
    val symbol: String,
    val name: String,
    val zhCnName: String = name,
    val enName: String = name,
    val toBaseFactor: Double = 1.0
) {
    fun localizedName(lang: AppLanguage): String = tr(lang, name, zhCnName, enName)
}

enum class ConvertCategory(
    val label: String,
    val zhCnLabel: String,
    val enLabel: String,
    val units: List<UnitItem>
) {
    LENGTH(
        "長度",
        "长度",
        "Length",
        listOf(
            UnitItem("m", "公尺", "米", "Meter", 1.0),
            UnitItem("km", "公里", "千米", "Kilometer", 1000.0),
            UnitItem("cm", "公分", "厘米", "Centimeter", 0.01),
            UnitItem("in", "英吋", "英寸", "Inch", 0.0254),
            UnitItem("ft", "英尺", "英尺", "Foot", 0.3048),
            UnitItem("mi", "英里", "英里", "Mile", 1609.344)
        )
    ),
    WEIGHT(
        "重量",
        "重量",
        "Weight",
        listOf(
            UnitItem("kg", "公斤", "千克", "Kilogram", 1.0),
            UnitItem("g", "公克", "克", "Gram", 0.001),
            UnitItem("台斤", "台斤", "台斤", "Taiwan Jin", 0.6),
            UnitItem("lb", "磅", "磅", "Pound", 0.45359237),
            UnitItem("oz", "盎司", "盎司", "Ounce", 0.0283495231)
        )
    ),
    AREA(
        "面積",
        "面积",
        "Area",
        listOf(
            UnitItem("m²", "平方公尺", "平方米", "Sq Meter", 1.0),
            UnitItem("坪", "台灣坪", "台湾坪", "Ping", 3.305785),
            UnitItem("ft²", "平方英尺", "平方英尺", "Sq Foot", 0.09290304),
            UnitItem("ha", "公頃", "公顷", "Hectare", 10000.0)
        )
    ),
    TEMPERATURE(
        "溫度",
        "温度",
        "Temp",
        listOf(
            UnitItem("°C", "攝氏", "摄氏", "Celsius"),
            UnitItem("°F", "華氏", "华氏", "Fahrenheit"),
            UnitItem("K", "克氏", "开尔文", "Kelvin")
        )
    ),
    DATA(
        "資料",
        "数据",
        "Data",
        listOf(
            UnitItem("MB", "MB", "MB", "MB", 1.0),
            UnitItem("GB", "GB", "GB", "GB", 1024.0),
            UnitItem("TB", "TB", "TB", "TB", 1048576.0),
            UnitItem("KB", "KB", "KB", "KB", 1.0 / 1024.0)
        )
    );

    fun label(lang: AppLanguage): String = tr(lang, label, zhCnLabel, enLabel)
}

@Composable
fun IosUnitConverterPanel(
    currentInputValue: Double,
    fontFamily: FontFamily,
    isCompact: Boolean,
    language: AppLanguage = AppLanguage.ZH_TW,
    modifier: Modifier = Modifier
) {
    var categoryOrdinal by rememberSaveable { mutableStateOf(0) }
    val category = ConvertCategory.entries[categoryOrdinal.coerceIn(0, ConvertCategory.entries.lastIndex)]

    var fromIndex by rememberSaveable(categoryOrdinal) { mutableStateOf(0) }
    var toIndex by rememberSaveable(categoryOrdinal) { mutableStateOf(1.coerceAtMost(category.units.lastIndex)) }

    val fromUnit = category.units[fromIndex.coerceIn(0, category.units.lastIndex)]
    val toUnit = category.units[toIndex.coerceIn(0, category.units.lastIndex)]

    val convertedValue = remember(currentInputValue, category, fromUnit, toUnit) {
        if (category == ConvertCategory.TEMPERATURE) {
            val celsius = when (fromUnit.symbol) {
                "°C" -> currentInputValue
                "°F" -> (currentInputValue - 32.0) * 5.0 / 9.0
                "K" -> currentInputValue - 273.15
                else -> currentInputValue
            }
            when (toUnit.symbol) {
                "°C" -> celsius
                "°F" -> celsius * 9.0 / 5.0 + 32.0
                "K" -> celsius + 273.15
                else -> celsius
            }
        } else {
            val base = currentInputValue * fromUnit.toBaseFactor
            base / toUnit.toBaseFactor
        }
    }

    val formattedConverted = remember(convertedValue) {
        CalculatorEngine.formatExpressionForDisplay(
            CalculatorEngine.formatDoublePlain(convertedValue)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1C))
            .padding(horizontal = 12.dp, vertical = if (isCompact) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp)
    ) {
        // 類別選擇列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConvertCategory.entries.forEachIndexed { idx, cat ->
                val selected = idx == categoryOrdinal
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0xFFF08800) else Color(0xFF2C2C2E))
                        .clickable { categoryOrdinal = idx }
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = cat.label(language),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 單位轉換結果列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 來源單位選擇（點擊循環切換）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2C2C2E))
                        .clickable {
                            fromIndex = (fromIndex + 1) % category.units.size
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${fromUnit.localizedName(language)} (${fromUnit.symbol}) ▾",
                        color = Color(0xFFD1D1D6),
                        fontSize = 12.sp,
                        fontFamily = fontFamily
                    )
                }

                // 互換按鈕
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A3C))
                        .clickable {
                            val tmp = fromIndex
                            fromIndex = toIndex
                            toIndex = tmp
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⇄",
                        color = Color(0xFFF08800),
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 目標單位選擇（點擊循環切換）
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2C2C2E))
                        .clickable {
                            toIndex = (toIndex + 1) % category.units.size
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${toUnit.localizedName(language)} (${toUnit.symbol}) ▾",
                        color = Color(0xFFF08800),
                        fontSize = 12.sp,
                        fontFamily = fontFamily
                    )
                }
            }

            Text(
                text = "= $formattedConverted ${toUnit.symbol}",
                color = Color(0xFFFF9F0A),
                fontSize = if (isCompact) 16.sp else 19.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
