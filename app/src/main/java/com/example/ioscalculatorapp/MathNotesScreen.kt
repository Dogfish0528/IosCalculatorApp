package com.example.ioscalculatorapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val MathNoteBg = Color(0xFF000000)
private val MathNoteCardBg = Color(0xFF1C1C1E)
private val MathNoteElevatedBg = Color(0xFF2C2C2E)
private val MathNoteOrange = Color(0xFFF08800)
private val MathNoteResultAmber = Color(0xFFFF9F0A)
private val MathNoteGrayText = Color(0xFF8E8E93)
private val MathNoteDivider = Color(0xFF38383A)

@Composable
fun IosMathNotesScreen(
    isTablet: Boolean,
    isLandscape: Boolean,
    isRadians: Boolean,
    fontFamily: FontFamily,
    onOpenModeMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(MathNotesEngine.loadNotes(context)) }
    var selectedNoteId by rememberSaveable {
        mutableStateOf(notes.firstOrNull()?.id ?: -1L)
    }

    val isTwoPane = isTablet || isLandscape
    var showSidebarInTwoPane by rememberSaveable { mutableStateOf(true) }
    var showListOnPhone by rememberSaveable { mutableStateOf(false) }
    var isSketchMode by rememberSaveable { mutableStateOf(false) }

    val selectedNote = notes.firstOrNull { it.id == selectedNoteId } ?: notes.firstOrNull()
    LaunchedEffect(selectedNote?.id) {
        if (selectedNote != null && selectedNoteId != selectedNote.id) {
            selectedNoteId = selectedNote.id
        }
    }

    fun updateNote(updated: MathNote) {
        val newList = notes.map { if (it.id == updated.id) updated else it }
        notes = newList
        MathNotesEngine.saveNotes(context, newList)
    }

    fun createNewNote() {
        val now = System.currentTimeMillis()
        val newNote = MathNote(
            id = now,
            title = "新數學備忘錄",
            content = "# 新數學備忘錄\nx = 10\ny = 25\nx + y =",
            updatedAt = now,
            showGraph = false
        )
        val newList = listOf(newNote) + notes
        notes = newList
        selectedNoteId = newNote.id
        showListOnPhone = false
        MathNotesEngine.saveNotes(context, newList)
    }

    fun deleteNote(noteId: Long) {
        val remaining = notes.filterNot { it.id == noteId }
        if (remaining.isEmpty()) {
            val now = System.currentTimeMillis()
            val fallback = MathNote(
                id = now,
                title = "新數學備忘錄",
                content = "# 新數學備忘錄\n",
                updatedAt = now
            )
            notes = listOf(fallback)
            selectedNoteId = fallback.id
            MathNotesEngine.saveNotes(context, notes)
        } else {
            notes = remaining
            if (selectedNoteId == noteId) {
                selectedNoteId = remaining.first().id
            }
            MathNotesEngine.saveNotes(context, remaining)
        }
    }

    if (isTwoPane) {
        // 平板與橫向模式：iPadOS 雙欄分割畫面 (左側備忘錄清單 + 右側即時算式/圖表/手寫工作區)
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(MathNoteBg)
        ) {
            AnimatedVisibility(visible = showSidebarInTwoPane) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    MathNotesListPane(
                        notes = notes,
                        selectedNoteId = selectedNote?.id ?: -1L,
                        fontFamily = fontFamily,
                        isCompactPane = true,
                        onSelectNote = { note ->
                            selectedNoteId = note.id
                        },
                        onCreateNote = { createNewNote() },
                        onDeleteNote = { id -> deleteNote(id) },
                        onOpenModeMenu = onOpenModeMenu,
                        modifier = Modifier
                            .width(if (isTablet) 300.dp else 250.dp)
                            .fillMaxHeight()
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MathNoteDivider)
                    )
                }
            }

            if (selectedNote != null) {
                MathNoteDetailEditorPane(
                    note = selectedNote,
                    isTwoPane = true,
                    isTablet = isTablet,
                    isLandscape = isLandscape,
                    isRadians = isRadians,
                    isSidebarVisible = showSidebarInTwoPane,
                    isSketchMode = isSketchMode,
                    fontFamily = fontFamily,
                    onToggleSidebar = { showSidebarInTwoPane = !showSidebarInTwoPane },
                    onBackToList = {},
                    onToggleSketchMode = { isSketchMode = !isSketchMode },
                    onCreateNote = { createNewNote() },
                    onUpdateNote = { updateNote(it) },
                    onOpenModeMenu = onOpenModeMenu,
                    context = context,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    } else {
        // 手機直向模式：清單與編輯器流暢切換
        if (showListOnPhone || selectedNote == null) {
            MathNotesListPane(
                notes = notes,
                selectedNoteId = selectedNote?.id ?: -1L,
                fontFamily = fontFamily,
                isCompactPane = false,
                onSelectNote = { note ->
                    selectedNoteId = note.id
                    showListOnPhone = false
                },
                onCreateNote = { createNewNote() },
                onDeleteNote = { id -> deleteNote(id) },
                onOpenModeMenu = onOpenModeMenu,
                modifier = modifier
                    .fillMaxSize()
                    .background(MathNoteBg)
            )
        } else {
            MathNoteDetailEditorPane(
                note = selectedNote,
                isTwoPane = false,
                isTablet = false,
                isLandscape = false,
                isRadians = isRadians,
                isSidebarVisible = false,
                isSketchMode = isSketchMode,
                fontFamily = fontFamily,
                onToggleSidebar = {},
                onBackToList = { showListOnPhone = true },
                onToggleSketchMode = { isSketchMode = !isSketchMode },
                onCreateNote = { createNewNote() },
                onUpdateNote = { updateNote(it) },
                onOpenModeMenu = onOpenModeMenu,
                context = context,
                modifier = modifier
                    .fillMaxSize()
                    .background(MathNoteBg)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MathNotesListPane(
    notes: List<MathNote>,
    selectedNoteId: Long,
    fontFamily: FontFamily,
    isCompactPane: Boolean,
    onSelectNote: (MathNote) -> Unit,
    onCreateNote: () -> Unit,
    onDeleteNote: (Long) -> Unit,
    onOpenModeMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("M月d日 HH:mm", Locale.TAIWAN) }

    Column(
        modifier = modifier
            .background( Color(0xFF101012) )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // 頂部導覽列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "數學備忘錄",
                color = Color.White,
                fontSize = if (isCompactPane) 20.sp else 26.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MathNoteElevatedBg)
                        .clickable { onCreateNote() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "＋",
                        color = MathNoteOrange,
                        fontSize = 20.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!isCompactPane) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MathNoteElevatedBg)
                            .clickable { onOpenModeMenu() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_calc_mode),
                            contentDescription = "切換模式",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = "共 ${notes.size} 則備忘錄 · 支援變數、自動算出 = 與函數圖形",
            color = MathNoteGrayText,
            fontSize = 12.sp,
            fontFamily = fontFamily,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                val isSelected = isCompactPane && note.id == selectedNoteId
                val previewLine = remember(note.content) {
                    note.content.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .firstOrNull() ?: "空白數學備忘錄"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) Color(0xFF3A2810) else MathNoteCardBg)
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) MathNoteOrange else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .combinedClickable(
                            onClick = { onSelectNote(note) }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = note.title,
                            color = if (isSelected) MathNoteOrange else Color.White,
                            fontSize = 16.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = previewLine,
                            color = Color(0xFFD1D1D6),
                            fontSize = 13.sp,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateFormat.format(Date(note.updatedAt)),
                                color = MathNoteGrayText,
                                fontSize = 11.sp,
                                fontFamily = fontFamily
                            )
                            if (note.strokes.isNotEmpty()) {
                                Text(
                                    text = "✏️ 含手寫筆跡",
                                    color = MathNoteOrange,
                                    fontSize = 11.sp,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E))
                            .clickable { onDeleteNote(note.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Color(0xFFFF453A),
                            fontSize = 12.sp,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MathNoteDetailEditorPane(
    note: MathNote,
    isTwoPane: Boolean,
    isTablet: Boolean,
    isLandscape: Boolean,
    isRadians: Boolean,
    isSidebarVisible: Boolean,
    isSketchMode: Boolean,
    fontFamily: FontFamily,
    onToggleSidebar: () -> Unit,
    onBackToList: () -> Unit,
    onToggleSketchMode: () -> Unit,
    onCreateNote: () -> Unit,
    onUpdateNote: (MathNote) -> Unit,
    onOpenModeMenu: () -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    // 綁定當前編輯中的文字狀態與游標位置
    var textFieldValue by remember(note.id) {
        mutableStateOf(
            TextFieldValue(
                text = note.content,
                selection = TextRange(note.content.length)
            )
        )
    }

    // 即時解析整篇數學備忘錄（變數表、每行計算結果、可繪圖函數）
    val evalSummary = remember(textFieldValue.text, isRadians) {
        MathNotesEngine.evaluateNote(textFieldValue.text, isRadians)
    }

    val hasGraphFunctions = evalSummary.graphFunctions.isNotEmpty()
    val showGraphCard = note.showGraph || hasGraphFunctions

    fun commitTextChange(newValue: TextFieldValue) {
        textFieldValue = newValue
        val newTitle = MathNotesEngine.deriveTitle(newValue.text)
        onUpdateNote(
            note.copy(
                title = newTitle,
                content = newValue.text,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun insertTokenAtCursor(token: String) {
        val currentText = textFieldValue.text
        val start = textFieldValue.selection.start.coerceIn(0, currentText.length)
        val end = textFieldValue.selection.end.coerceIn(0, currentText.length)
        val minPos = minOf(start, end)
        val maxPos = maxOf(start, end)
        val updatedText = currentText.substring(0, minPos) + token + currentText.substring(maxPos)
        val newCursor = minPos + token.length
        commitTextChange(
            TextFieldValue(
                text = updatedText,
                selection = TextRange(newCursor)
            )
        )
    }

    fun backspaceAtCursor() {
        val currentText = textFieldValue.text
        val start = textFieldValue.selection.start.coerceIn(0, currentText.length)
        val end = textFieldValue.selection.end.coerceIn(0, currentText.length)
        if (start != end) {
            val minPos = minOf(start, end)
            val maxPos = maxOf(start, end)
            val updatedText = currentText.substring(0, minPos) + currentText.substring(maxPos)
            commitTextChange(TextFieldValue(text = updatedText, selection = TextRange(minPos)))
        } else if (start > 0) {
            val updatedText = currentText.substring(0, start - 1) + currentText.substring(start)
            commitTextChange(TextFieldValue(text = updatedText, selection = TextRange(start - 1)))
        }
    }

    Column(
        modifier = modifier
            .background(MathNoteBg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // 1. 頂部工具列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isTwoPane) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSidebarVisible) Color(0xFF3A2810) else MathNoteCardBg)
                            .clickable { onToggleSidebar() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isSidebarVisible) "◁ 隱藏清單" else "☰ 備忘錄 (${evalSummary.variables.size}變數)",
                            color = MathNoteOrange,
                            fontSize = 13.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MathNoteCardBg)
                            .clickable { onBackToList() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "‹ 備忘錄清單",
                            color = MathNoteOrange,
                            fontSize = 14.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 切換函數圖形顯示
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (showGraphCard) Color(0xFF3A2810) else MathNoteCardBg)
                        .clickable {
                            onUpdateNote(note.copy(showGraph = !showGraphCard))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "📈 圖表",
                        color = if (showGraphCard) MathNoteOrange else Color.White,
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 切換手寫計算畫布
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSketchMode) Color(0xFF3A2810) else MathNoteCardBg)
                        .clickable { onToggleSketchMode() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isSketchMode) "⌨️ 算式" else "✏️ 手寫",
                        color = if (isSketchMode) MathNoteOrange else Color.White,
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 新增備忘錄按鈕
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MathNoteCardBg)
                        .clickable { onCreateNote() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "＋",
                        color = MathNoteOrange,
                        fontSize = 18.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 右上角計算機模式選單切換鈕
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MathNoteCardBg)
                        .clickable { onOpenModeMenu() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_calc_mode),
                        contentDescription = "切換模式",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // 2. 即時偵測到的變數膠囊列（點擊即可插入該變數名稱到游標處）
        if (evalSummary.variables.isNotEmpty() && !isSketchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "變數：",
                    color = MathNoteGrayText,
                    fontSize = 12.sp,
                    fontFamily = fontFamily
                )
                evalSummary.variables.forEach { (varName, varVal) ->
                    val formattedVal = CalculatorEngine.formatExpressionForDisplay(
                        CalculatorEngine.formatDoublePlain(varVal)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF262018))
                            .border(0.5.dp, MathNoteOrange.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable { insertTokenAtCursor(varName) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "$varName = $formattedVal",
                            color = MathNoteResultAmber,
                            fontSize = 12.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. 主工作區：手寫塗鴉模式 vs 算式編輯與即時解答模式
        if (isSketchMode) {
            MathNoteDrawingCanvas(
                strokes = note.strokes,
                fontFamily = fontFamily,
                onStrokesChange = { newStrokes ->
                    onUpdateNote(
                        note.copy(
                            strokes = newStrokes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp)
            )
        } else {
            val useHorizontalSplitForEditorAndPreview = (isTablet && isLandscape) || (isLandscape && showGraphCard)

            if (useHorizontalSplitForEditorAndPreview) {
                // 橫向或平板寬螢幕：左欄輸入算式，右欄即時顯示 iOS 橘字解答與 2D 互動函數圖形
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MathNoteTextEditorCard(
                        textFieldValue = textFieldValue,
                        onValueChange = { commitTextChange(it) },
                        fontFamily = fontFamily,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MathNoteLiveEvaluationCard(
                            evalSummary = evalSummary,
                            fontFamily = fontFamily,
                            onCopyResult = { res ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Math Note Result", res))
                                Toast.makeText(context, "已拷貝結果：$res", Toast.LENGTH_SHORT).show()
                            },
                            onAppendEqualAtLine = { lineIdx ->
                                val lines = textFieldValue.text.split("\n").toMutableList()
                                if (lineIdx in lines.indices && !lines[lineIdx].trimEnd().endsWith("=")) {
                                    lines[lineIdx] = lines[lineIdx].trimEnd() + " ="
                                    val joined = lines.joinToString("\n")
                                    commitTextChange(TextFieldValue(text = joined, selection = TextRange(joined.length)))
                                }
                            }
                        )

                        if (showGraphCard) {
                            MathNoteGraphCard(
                                graphFunctions = evalSummary.graphFunctions,
                                variables = evalSummary.variables,
                                fontFamily = fontFamily,
                                onAddSampleFunction = {
                                    val suffix = if (textFieldValue.text.endsWith("\n") || textFieldValue.text.isEmpty()) {
                                        "y = sin(x)"
                                    } else {
                                        "\ny = sin(x)"
                                    }
                                    val newText = textFieldValue.text + suffix
                                    commitTextChange(TextFieldValue(text = newText, selection = TextRange(newText.length)))
                                }
                            )
                        }
                    }
                }
            } else {
                // 直向螢幕：上方多行算式編輯器 + 下方即時 iOS 橘色解答與 2D 互動函數圖形
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MathNoteTextEditorCard(
                        textFieldValue = textFieldValue,
                        onValueChange = { commitTextChange(it) },
                        fontFamily = fontFamily,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 165.dp, max = 280.dp)
                    )

                    MathNoteLiveEvaluationCard(
                        evalSummary = evalSummary,
                        fontFamily = fontFamily,
                        onCopyResult = { res ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Math Note Result", res))
                            Toast.makeText(context, "已拷貝結果：$res", Toast.LENGTH_SHORT).show()
                        },
                        onAppendEqualAtLine = { lineIdx ->
                            val lines = textFieldValue.text.split("\n").toMutableList()
                            if (lineIdx in lines.indices && !lines[lineIdx].trimEnd().endsWith("=")) {
                                lines[lineIdx] = lines[lineIdx].trimEnd() + " ="
                                val joined = lines.joinToString("\n")
                                commitTextChange(TextFieldValue(text = joined, selection = TextRange(joined.length)))
                            }
                        }
                    )

                    if (showGraphCard) {
                        MathNoteGraphCard(
                            graphFunctions = evalSummary.graphFunctions,
                            variables = evalSummary.variables,
                            fontFamily = fontFamily,
                            onAddSampleFunction = {
                                val suffix = if (textFieldValue.text.endsWith("\n") || textFieldValue.text.isEmpty()) {
                                    "y = sin(x)"
                                } else {
                                    "\ny = sin(x)"
                                }
                                val newText = textFieldValue.text + suffix
                                commitTextChange(TextFieldValue(text = newText, selection = TextRange(newText.length)))
                            }
                        )
                    }
                }
            }

            // 4. 底部 iOS 數學符號快捷工具列（方便一鍵輸入 =, ×, ÷, π, ²√(, 變數 x, y 等）
            MathQuickSymbolBar(
                fontFamily = fontFamily,
                onInsertToken = { token -> insertTokenAtCursor(token) },
                onBackspace = { backspaceAtCursor() }
            )
        }
    }
}

@Composable
private fun MathNoteTextEditorCard(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MathNoteCardBg)
            .border(0.5.dp, MathNoteDivider, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "備忘錄算式編輯（寫下 = 即時計算結果）",
                color = MathNoteGrayText,
                fontSize = 12.sp,
                fontFamily = fontFamily
            )
        }

        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 18.sp,
                fontFamily = fontFamily,
                lineHeight = 26.sp
            ),
            cursorBrush = SolidColor(MathNoteOrange),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        )
    }
}

@Composable
private fun MathNoteLiveEvaluationCard(
    evalSummary: MathNoteEvaluationSummary,
    fontFamily: FontFamily,
    onCopyResult: (String) -> Unit,
    onAppendEqualAtLine: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161618))
            .border(0.5.dp, MathNoteDivider, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✨ iOS 數學備忘錄即時解答（點橘字可拷貝）",
                color = MathNoteOrange,
                fontSize = 12.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium
            )
        }

        evalSummary.lines.forEach { line ->
            val trimmed = line.rawText.trim()
            if (trimmed.isEmpty()) return@forEach

            if (trimmed.startsWith("#")) {
                Text(
                    text = trimmed.removePrefix("#").trim(),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF202024))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val annotatedLine = buildAnnotatedString {
                        append(trimmed)
                        if (line.evaluatedResult != null) {
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = if (line.isError) Color(0xFFFF453A) else MathNoteResultAmber,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = if (line.isError) TextDecoration.None else TextDecoration.Underline
                                )
                            ) {
                                append(line.evaluatedResult)
                            }
                        }
                    }

                    Text(
                        text = annotatedLine,
                        color = Color(0xFFE5E5EA),
                        fontSize = 17.sp,
                        fontFamily = fontFamily,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = line.evaluatedResult != null && !line.isError) {
                                line.evaluatedResult?.let { onCopyResult(it) }
                            }
                    )

                    when {
                        line.isGraphFunction -> {
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E3A2F))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "函數圖形",
                                    color = Color(0xFF30D158),
                                    fontSize = 11.sp,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                        line.assignedVariable != null && line.evaluatedResult == null -> {
                            val formattedVar = CalculatorEngine.formatExpressionForDisplay(
                                CalculatorEngine.formatDoublePlain(line.assignedVariable.second)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2C2C2E))
                                    .clickable { onAppendEqualAtLine(line.lineIndex) }
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "已設為 $formattedVar",
                                    color = MathNoteGrayText,
                                    fontSize = 11.sp,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                        line.evaluatedResult == null && !trimmed.endsWith("=") -> {
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF3A2810))
                                    .clickable { onAppendEqualAtLine(line.lineIndex) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "算出 =",
                                    color = MathNoteOrange,
                                    fontSize = 12.sp,
                                    fontFamily = fontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MathNoteGraphCard(
    graphFunctions: List<GraphFunction>,
    variables: Map<String, Double>,
    fontFamily: FontFamily,
    onAddSampleFunction: () -> Unit
) {
    var xDomainRange by rememberSaveable { mutableStateOf(10f) }
    var inspectXFraction by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MathNoteCardBg)
            .border(0.5.dp, MathNoteDivider, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📈 互動式 2D 函數圖表 (支援拖曳查值)",
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MathNoteElevatedBg)
                        .clickable { xDomainRange = (xDomainRange * 0.75f).coerceAtLeast(2f) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("放大 ＋", color = Color.White, fontSize = 11.sp, fontFamily = fontFamily)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MathNoteElevatedBg)
                        .clickable { xDomainRange = (xDomainRange * 1.35f).coerceAtMost(50f) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("縮小 −", color = Color.White, fontSize = 11.sp, fontFamily = fontFamily)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MathNoteElevatedBg)
                        .clickable {
                            xDomainRange = 10f
                            inspectXFraction = null
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("重置", color = MathNoteOrange, fontSize = 11.sp, fontFamily = fontFamily)
                }
            }
        }

        if (graphFunctions.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "在算式中輸入如 y = sin(x) 或 y = x² - 4 即可自動繪圖",
                    color = MathNoteGrayText,
                    fontSize = 13.sp,
                    fontFamily = fontFamily,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MathNoteOrange)
                        .clickable { onAddSampleFunction() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "+ 插入 y = sin(x)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // 函數圖例與查值顯示
            val inspectedX = inspectXFraction?.let { frac ->
                -xDomainRange + frac * (2f * xDomainRange)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                graphFunctions.forEach { fn ->
                    val fnColor = Color(fn.colorHex)
                    val inspectedYStr = if (inspectedX != null) {
                        val yVal = MathNotesEngine.evaluateFunctionAt(fn.expressionRhs, inspectedX.toDouble(), variables)
                        if (yVal != null) {
                            val xFmt = (inspectedX * 100f).roundToInt() / 100.0
                            val yFmt = (yVal * 100.0).roundToInt() / 100.0
                            "  (x=$xFmt, y=$yFmt)"
                        } else ""
                    } else ""

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(fnColor)
                        )
                        Text(
                            text = "${fn.label}$inspectedYStr",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = fontFamily
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF101012))
                    .border(0.5.dp, Color(0xFF2C2C2E), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            inspectXFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            inspectXFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val centerX = w / 2f
                    val centerY = h / 2f
                    val yDomainRange = xDomainRange * (h / w.coerceAtLeast(1f))

                    // 1. 繪製背景網格線
                    val gridSteps = 4
                    for (i in -gridSteps..gridSteps) {
                        val gx = centerX + (i.toFloat() / gridSteps) * (w / 2f)
                        val gy = centerY + (i.toFloat() / gridSteps) * (h / 2f)
                        drawLine(
                            color = Color(0xFF222226),
                            start = Offset(gx, 0f),
                            end = Offset(gx, h),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color(0xFF222226),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1f
                        )
                    }

                    // 2. 繪製 X / Y 座標軸
                    drawLine(
                        color = Color(0xFF636366),
                        start = Offset(0f, centerY),
                        end = Offset(w, centerY),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color(0xFF636366),
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, h),
                        strokeWidth = 2f
                    )

                    // 3. 繪製每一條函數曲線
                    val sampleCount = 320
                    graphFunctions.forEach { fn ->
                        val path = Path()
                        var penDown = false
                        var prevScreenY = 0f

                        for (step in 0..sampleCount) {
                            val frac = step.toFloat() / sampleCount
                            val mathX = -xDomainRange + frac * (2f * xDomainRange)
                            val mathY = MathNotesEngine.evaluateFunctionAt(
                                rhsExpr = fn.expressionRhs,
                                xValue = mathX.toDouble(),
                                variables = variables
                            )?.toFloat()

                            if (mathY != null && mathY.isFinite()) {
                                val screenX = frac * w
                                val screenY = centerY - (mathY / yDomainRange) * (h / 2f)

                                if (screenY in -h..(2f * h)) {
                                    if (!penDown || abs(screenY - prevScreenY) > h * 0.8f) {
                                        path.moveTo(screenX, screenY)
                                        penDown = true
                                    } else {
                                        path.lineTo(screenX, screenY)
                                    }
                                    prevScreenY = screenY
                                } else {
                                    penDown = false
                                }
                            } else {
                                penDown = false
                            }
                        }

                        drawPath(
                            path = path,
                            color = Color(fn.colorHex),
                            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // 4. 繪製手指拖曳查值十字準線與交點
                    val frac = inspectXFraction
                    if (frac != null) {
                        val cursorX = frac * w
                        val mathX = -xDomainRange + frac * (2f * xDomainRange)
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(cursorX, 0f),
                            end = Offset(cursorX, h),
                            strokeWidth = 1.5f
                        )
                        graphFunctions.forEach { fn ->
                            val mathY = MathNotesEngine.evaluateFunctionAt(
                                rhsExpr = fn.expressionRhs,
                                xValue = mathX.toDouble(),
                                variables = variables
                            )?.toFloat()
                            if (mathY != null) {
                                val cursorY = centerY - (mathY / yDomainRange) * (h / 2f)
                                if (cursorY in 0f..h) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = 7f,
                                        center = Offset(cursorX, cursorY)
                                    )
                                    drawCircle(
                                        color = Color(fn.colorHex),
                                        radius = 5f,
                                        center = Offset(cursorX, cursorY)
                                    )
                                }
                            }
                        }
                    }
                }

                // 座標軸刻度標籤
                Text(
                    text = "x ∈ [${-xDomainRange.roundToInt()}, ${xDomainRange.roundToInt()}]",
                    color = MathNoteGrayText,
                    fontSize = 10.sp,
                    fontFamily = fontFamily,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun MathNoteDrawingCanvas(
    strokes: List<DrawingStroke>,
    fontFamily: FontFamily,
    onStrokesChange: (List<DrawingStroke>) -> Unit,
    modifier: Modifier = Modifier
) {
    val penColors = listOf(
        0xFFF08800L to "橘色",
        0xFFFFFFFFL to "白色",
        0xFF0A84FFL to "藍色",
        0xFF30D158L to "綠色",
        0xFFFFD60AL to "黃色"
    )
    var selectedColorHex by rememberSaveable { mutableStateOf(0xFFF08800L) }
    var selectedStrokeWidth by rememberSaveable { mutableStateOf(7f) }
    var currentPoints by remember { mutableStateOf<List<OffsetPoint>>(emptyList()) }

    val latestStrokes by rememberUpdatedState(strokes)
    val latestColor by rememberUpdatedState(selectedColorHex)
    val latestWidth by rememberUpdatedState(selectedStrokeWidth)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141416))
            .border(0.5.dp, MathNoteDivider, RoundedCornerShape(16.dp))
    ) {
        // 畫布頂部筆刷工具列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MathNoteCardBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                penColors.forEach { (hex, _) ->
                    val isSelected = selectedColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 28.dp else 22.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                            .clickable { selectedColorHex = hex }
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                listOf(4f to "細", 7f to "中", 12f to "粗").forEach { (w, label) ->
                    val active = selectedStrokeWidth == w
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) MathNoteOrange else MathNoteElevatedBg)
                            .clickable { selectedStrokeWidth = w }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = fontFamily
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MathNoteElevatedBg)
                        .clickable(enabled = strokes.isNotEmpty()) {
                            if (strokes.isNotEmpty()) {
                                onStrokesChange(strokes.dropLast(1))
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "↩ 復原",
                        color = if (strokes.isNotEmpty()) Color.White else MathNoteGrayText,
                        fontSize = 12.sp,
                        fontFamily = fontFamily
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MathNoteElevatedBg)
                        .clickable(enabled = strokes.isNotEmpty()) {
                            onStrokesChange(emptyList())
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "清除手寫",
                        color = if (strokes.isNotEmpty()) Color(0xFFFF453A) else MathNoteGrayText,
                        fontSize = 12.sp,
                        fontFamily = fontFamily
                    )
                }
            }
        }

        HorizontalDivider(color = MathNoteDivider, thickness = 0.5.dp)

        // 手寫計算方格紙畫布
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints = listOf(OffsetPoint(offset.x, offset.y))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints = currentPoints + OffsetPoint(change.position.x, change.position.y)
                        },
                        onDragEnd = {
                            if (currentPoints.size > 1) {
                                onStrokesChange(
                                    latestStrokes + DrawingStroke(
                                        points = currentPoints,
                                        colorHex = latestColor,
                                        strokeWidth = latestWidth
                                    )
                                )
                            }
                            currentPoints = emptyList()
                        },
                        onDragCancel = {
                            currentPoints = emptyList()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 繪製 iOS 備忘錄暗色點陣/方格紙底紋
                val step = 36.dp.toPx()
                var gx = step
                while (gx < size.width) {
                    drawLine(
                        color = Color(0xFF1F1F24),
                        start = Offset(gx, 0f),
                        end = Offset(gx, size.height),
                        strokeWidth = 1f
                    )
                    gx += step
                }
                var gy = step
                while (gy < size.height) {
                    drawLine(
                        color = Color(0xFF1F1F24),
                        start = Offset(0f, gy),
                        end = Offset(size.width, gy),
                        strokeWidth = 1f
                    )
                    gy += step
                }

                fun drawStrokePath(pts: List<OffsetPoint>, colorHex: Long, width: Float) {
                    if (pts.size < 2) return
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 1 until pts.size) {
                            lineTo(pts[i].x, pts[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(colorHex),
                        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                strokes.forEach { s ->
                    drawStrokePath(s.points, s.colorHex, s.strokeWidth)
                }
                if (currentPoints.size >= 2) {
                    drawStrokePath(currentPoints, selectedColorHex, selectedStrokeWidth)
                }
            }

            if (strokes.isEmpty() && currentPoints.isEmpty()) {
                Text(
                    text = "在此處使用手指或觸控筆自由手寫草稿與算式…",
                    color = MathNoteGrayText,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun MathQuickSymbolBar(
    fontFamily: FontFamily,
    onInsertToken: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val quickTokens = listOf(
        "=" to " = ",
        "+" to " + ",
        "−" to " − ",
        "×" to " × ",
        "÷" to " ÷ ",
        "(" to "(",
        ")" to ")",
        "x" to "x",
        "y" to "y",
        "²" to "²",
        "³" to "³",
        "^" to "^",
        "²√(" to "²√(",
        "π" to "π",
        "sin(" to "sin(",
        "cos(" to "cos(",
        "ln(" to "ln(",
        "換行" to "\n"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MathNoteCardBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            quickTokens.forEach { (label, token) ->
                val isPrimary = label in listOf("=", "+", "−", "×", "÷")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPrimary) MathNoteOrange else MathNoteElevatedBg)
                        .clickable { onInsertToken(token) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background( Color(0xFF5C5C5E) )
                .clickable { onBackspace() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⌫",
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
