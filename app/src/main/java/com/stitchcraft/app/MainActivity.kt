package com.stitchcraft.app

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { StitchCraftApp() } }
    }
}

enum class EditTool { COLOR, ERASE, COMPLETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StitchCraftApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var width by remember { mutableFloatStateOf(80f) }
    var colors by remember { mutableFloatStateOf(24f) }
    var fabricCount by remember { mutableIntStateOf(14) }
    var cleanupSingles by remember { mutableStateOf(true) }
    var pattern by remember { mutableStateOf<StitchPattern?>(null) }
    var editingSession by remember { mutableIntStateOf(0) }
    var activeProject by remember { mutableStateOf<SavedProject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingPngFile by remember { mutableStateOf<java.io.File?>(null) }

val pngSaveLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("image/png")
) { uri ->
    val file = pendingPngFile
    if (uri != null && file != null) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        message = "PNG сохранён"
    }
    pendingPngFile = null
}
   var pendingPdfFile by remember { mutableStateOf<java.io.File?>(null) }

val pdfSaveLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf")
) { uri ->
    val file = pendingPdfFile
    if (uri != null && file != null) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        message = "PDF сохранён"
    }
    pendingPdfFile = null
}

var pendingCsvFile by remember { mutableStateOf<java.io.File?>(null) }

val csvSaveLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/csv")
) { uri ->
    val file = pendingCsvFile
    if (uri != null && file != null) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        message = "CSV сохранён"
    }
    pendingCsvFile = null
}
    var isPro by remember { mutableStateOf(context.getSharedPreferences("prefs", 0).getBoolean("pro", false)) }
    val store = remember { ProjectStore(context) }
    var projects by remember { mutableStateOf(store.list()) }
    val billing = remember {
        BillingManager(context) { pro ->
            isPro = pro
            context.getSharedPreferences("prefs", 0).edit().putBoolean("pro", pro).apply()
        }
    }
    DisposableEffect(Unit) { billing.start(); onDispose { } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
        pattern = null
        activeProject = null
        editingSession++
    }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StitchCraft", fontWeight = FontWeight.Bold) },
                actions = { if (isPro) AssistChip(onClick = {}, label = { Text("PRO") }) }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("✚") }, label = { Text("Создать") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("▦") }, label = { Text("Схема") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("☰") }, label = { Text("Проекты") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Text("★") }, label = { Text("Pro") })
            }

            when (tab) {
                0 -> CreateScreen(
                    selectedUri, width, colors,fabricCount, cleanupSingles, isPro, busy,
                    onPick = { picker.launch("image/*") },
                    onWidth = { width = it },
                    onColors = { colors = it },
                    onFabricCount = { fabricCount = it },
                    onCleanup = { cleanupSingles = it },
                    onGenerate = {
    val uri = selectedUri ?: return@CreateScreen

    scope.launch {
        busy = true
        message = null

        try {
            val result = withContext(Dispatchers.Default) {
                val bmp = context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Не удалось открыть изображение" }
                    requireNotNull(BitmapFactory.decodeStream(input)) {
                        "Не удалось декодировать изображение"
                    }
                }

                val w = width.toInt().coerceAtMost(if (isPro) 300 else 120)
                val c = colors.toInt().coerceAtMost(if (isPro) 100 else 32)

                PatternEngine.generate(
                    bmp,
                    w,
                    c,
                    PatternOptions(cleanupIsolatedStitches = cleanupSingles)
                )
            }

            pattern = result
            activeProject = null
            editingSession++
            tab = 1

        } catch (e: Exception) {
            message = "Не удалось обработать изображение"
        } finally {
            busy = false
        }
    }
}
                )

                1 -> PatternScreen(
                    pattern = pattern,
                    sessionId = editingSession,
                    isPro = isPro,
                    onPatternChange = { pattern = it },
                    onSave = { p ->
                        val existing = activeProject
                        val name = existing?.name ?: "Pattern_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        activeProject = store.save(name, p, existing?.id)
                        projects = store.list()
                        message = "Проект сохранён • прогресс ${p.progressPercent()}%"
                    },
                    onPdf = { p ->
                        val f = ExportManager.exportPdf(context, p, activeProject?.name ?: "StitchCraft_${System.currentTimeMillis()}")
                        pendingPdfFile = f
pdfSaveLauncher.launch(f.name)
                    },
                    onCsv = { p ->
                        val f = ExportManager.exportCsv(context, p, activeProject?.name ?: "StitchCraft_${System.currentTimeMillis()}")
                        pendingCsvFile = f
csvSaveLauncher.launch(f.name)
                    },
                    onPng = { p ->
                        val f = ExportManager.exportPng(context, p, activeProject?.name ?: "StitchCraft_${System.currentTimeMillis()}")
                    pendingPngFile = f
pngSaveLauncher.launch(f.name)
                    }
                )

                2 -> ProjectsScreen(
                    projects,
                    onOpen = { saved ->
                        store.load(saved)?.let {
                            pattern = it
                            activeProject = saved
                            editingSession++
                            tab = 1
                        }
                    },
                    onDelete = { saved ->
                        store.delete(saved)
                        if (activeProject?.id == saved.id) activeProject = null
                        projects = store.list()
                    }
                )

                3 -> ProScreen(
                    isPro,
                    onBuy = { billing.purchase(context as Activity) },
                    onRestore = { billing.restore() }
                )
            }
            message?.let { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun CreateScreen(
    uri: Uri?,
    width: Float,
    colors: Float,
    fabricCount: Int,
    cleanupSingles: Boolean,
    isPro: Boolean,
    busy: Boolean,
    onPick: () -> Unit,
    onWidth: (Float) -> Unit,
    onColors: (Float) -> Unit,
    onFabricCount: (Int) -> Unit,
    onCleanup: (Boolean) -> Unit,
    onGenerate: () -> Unit
) {
    Column(
        Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Фото → схема для вышивки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Выберите изображение. StitchCraft уменьшит его до сетки, сопоставит оттенки с палитрой ниток и назначит каждому цвету символ.")
        Button(onClick = onPick, Modifier.fillMaxWidth()) {
            Text(if (uri == null) "Выбрать изображение" else "Выбрать другое изображение")
        }
        Text(if (uri == null) "Изображение не выбрано" else "Изображение выбрано ✓")
        Text("Ширина схемы: ${width.toInt()} крестиков")
        Slider(width, onValueChange = onWidth, valueRange = 20f..if (isPro) ReleaseConfig.PRO_MAX_WIDTH.toFloat() else ReleaseConfig.FREE_MAX_WIDTH.toFloat(), steps = 22)
        Text("Количество цветов: ${colors.toInt()}")
        Slider(colors, onValueChange = onColors, valueRange = 4f..if (isPro) ReleaseConfig.PRO_MAX_COLORS.toFloat() else ReleaseConfig.FREE_MAX_COLORS.toFloat(), steps = 12)
        Text("Канва: Aida $fabricCount")

Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf(14, 16, 18).forEach { count ->
        FilterChip(
            selected = fabricCount == count,
            onClick = { onFabricCount(count) },
            label = { Text("$count ct") }
        )
    }
}
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Упростить одиночные крестики")
                Text("Убирает часть цветового шума и делает схему удобнее для вышивания.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = cleanupSingles, onCheckedChange = onCleanup)
        }
        if (!isPro) {
            Text("Free: до 80 крестиков по ширине и 16 цветов. Pro снимает ограничения и включает экспорт.", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onGenerate, enabled = uri != null && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Генерация…" else "Создать схему")
        }
    }
}

@Composable
fun PatternScreen(
    pattern: StitchPattern?,
    sessionId: Int,
    isPro: Boolean,
    onPatternChange: (StitchPattern) -> Unit,
    onSave: (StitchPattern) -> Unit,
    onPdf: (StitchPattern) -> Unit,
    onCsv: (StitchPattern) -> Unit,
    onPng: (StitchPattern) -> Unit
) {
    if (pattern == null) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { Text("Сначала создайте схему из изображения.") }
        return
    }

    var scale by remember(sessionId) { mutableFloatStateOf(1f) }
    var tool by remember(sessionId) { mutableStateOf(EditTool.COMPLETE) }
    var selectedColor by remember(sessionId) { mutableIntStateOf(0) }
    val undo = remember(sessionId) { mutableStateListOf<StitchPattern>() }
    val redo = remember(sessionId) { mutableStateListOf<StitchPattern>() }

    fun applyEdit(next: StitchPattern) {
        if (next == pattern) return
        if (undo.size >= 50) undo.removeAt(0)
        undo.add(pattern)
        redo.clear()
        onPatternChange(next)
    }

    fun undoEdit() {
        if (undo.isEmpty()) return
        val previous = undo.removeAt(undo.lastIndex)
        if (redo.size >= 50) redo.removeAt(0)
        redo.add(pattern)
        onPatternChange(previous)
    }

    fun redoEdit() {
        if (redo.isEmpty()) return
        val next = redo.removeAt(redo.lastIndex)
        if (undo.size >= 50) undo.removeAt(0)
        undo.add(pattern)
        onPatternChange(next)
    }

    val total = pattern.stitchCount()
    val done = pattern.completedCount()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${pattern.width} × ${pattern.height} • ${pattern.palette.size} цветов", fontWeight = FontWeight.Bold)
        Text("Вышито: $done из $total • ${pattern.progressPercent()}%", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(selected = tool == EditTool.COMPLETE, onClick = { tool = EditTool.COMPLETE }, label = { Text("✓ Готово") })
            FilterChip(selected = tool == EditTool.COLOR, onClick = { tool = EditTool.COLOR }, label = { Text("✎ Цвет") })
            FilterChip(selected = tool == EditTool.ERASE, onClick = { tool = EditTool.ERASE }, label = { Text("⌫ Ластик") })
            OutlinedButton(onClick = ::undoEdit, enabled = undo.isNotEmpty()) { Text("↶") }
            OutlinedButton(onClick = ::redoEdit, enabled = redo.isNotEmpty()) { Text("↷") }
        }

        if (tool == EditTool.COLOR) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pattern.palette.forEachIndexed { index, thread ->
                    FilterChip(
                        selected = selectedColor == index,
                        onClick = { selectedColor = index },
                        label = { Text("${PatternEngine.symbolForIndex(index)} ${thread.code}") }
                    )
                }
            }
        }

        PatternCanvas(
            pattern = pattern,
            scale = scale,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onZoom = { zoom -> scale = (scale * zoom).coerceIn(.6f, 8f) },
            onCellTap = { x, y ->
                val cell = pattern.cell(x, y)
                val next = when (tool) {
                    EditTool.COMPLETE -> if (cell.erased) pattern else pattern.updateCell(x, y) { it.copy(completed = !it.completed) }
                    EditTool.ERASE -> pattern.updateCell(x, y) { it.copy(erased = true, completed = false) }
                    EditTool.COLOR -> pattern.updateCell(x, y) {
                        it.copy(
                            colorIndex = selectedColor.coerceIn(0, pattern.palette.lastIndex),
                            symbol = PatternEngine.symbolForIndex(selectedColor),
                            erased = false
                        )
                    }
                }
                applyEdit(next)
            }
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { onSave(pattern) }) { Text("Сохранить") }
            Button(onClick = { onPdf(pattern) }, enabled = isPro || BuildConfig.DEBUG) { Text("PDF") }
            Button(onClick = { onCsv(pattern) }, enabled = isPro || BuildConfig.DEBUG) { Text("CSV") }
            Button(onClick = { onPng(pattern) }, enabled = isPro || BuildConfig.DEBUG) { Text("PNG") }
        }

        Text("Палитра", fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.heightIn(max = 145.dp)) {
            itemsIndexed(pattern.palette) { i, c ->
                val count = pattern.counts()[i] ?: 0
                val completedForColor = pattern.cells.count { !it.erased && it.colorIndex == i && it.completed }
                Text(
                    "${PatternEngine.symbolForIndex(i)}  ${c.code} • ${c.name} — $completedForColor/$count",
                    Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun PatternCanvas(
    pattern: StitchPattern,
    scale: Float,
    modifier: Modifier,
    onZoom: (Float) -> Unit,
    onCellTap: (Int, Int) -> Unit
) {
    var panOffset by remember(pattern) { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier
            .background(Color.White)
            .clipToBounds()
            .pointerInput(pattern.width, pattern.height, scale) {
                detectTapGestures { offset ->
                    val cellSize = minOf(
    size.width / pattern.width,
    size.height / pattern.height
) * scale
                    val offsetX = (size.width - pattern.width * cellSize) / 2f + panOffset.x
val offsetY = (size.height - pattern.height * cellSize) / 2f + panOffset.y
                    if (cellSize <= 0f) return@detectTapGestures
                    val x = floor((offset.x - offsetX) / cellSize).toInt()
val y = floor((offset.y - offsetY) / cellSize).toInt()
                    
                    if (x in 0 until pattern.width && y in 0 until pattern.height) onCellTap(x, y)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
    panOffset += pan
    onZoom(zoom)
}
            }
    ) {
       val cellSize = minOf(
    size.width / pattern.width,
    size.height / pattern.height
) * scale 
        val offsetX = (size.width - pattern.width * cellSize) / 2f + panOffset.x
val offsetY = (size.height - pattern.height * cellSize) / 2f + panOffset.y
        val maxX = pattern.width
val maxY = pattern.height
        
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }

        for (y in 0 until maxY) for (x in 0 until maxX) {
            val pc = pattern.cell(x, y)
            val left = offsetX + x * cellSize
            val top = offsetY + y * cellSize
            val fill = if (pc.erased) Color.White else Color(pattern.palette[pc.colorIndex].rgb)
            drawRect(fill, Offset(left, top), androidx.compose.ui.geometry.Size(cellSize, cellSize))

            if (pc.completed && !pc.erased) {
                drawRect(
                    Color.Black.copy(alpha = .18f),
                    Offset(left, top),
                    androidx.compose.ui.geometry.Size(cellSize, cellSize)
                )
                if (cellSize >= 10f) {
                    textPaint.textSize = cellSize * .72f
                    drawContext.canvas.nativeCanvas.drawText("✓", left + cellSize * .16f, top + cellSize * .78f, textPaint)
                }
            } else if (!pc.erased && cellSize >= 12f) {
                textPaint.textSize = cellSize * .55f
                drawContext.canvas.nativeCanvas.drawText(pc.symbol, left + cellSize * .22f, top + cellSize * .72f, textPaint)
            }

            drawRect(
                Color.Black.copy(alpha = .28f),
                Offset(left, top),
                androidx.compose.ui.geometry.Size(cellSize, cellSize),
                style = Stroke(if (x % 10 == 0 || y % 10 == 0) 1.8f else .5f)
            )
        }
    }
}

@Composable
fun ProjectsScreen(projects: List<SavedProject>, onOpen: (SavedProject) -> Unit, onDelete: (SavedProject) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Сохранённые проекты", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (projects.isEmpty()) Text("Пока нет проектов.", Modifier.padding(top = 16.dp))
        LazyColumn {
            itemsIndexed(projects) { _, p ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text("${p.width}×${p.height} • ${p.colors} цветов • ${p.progress}% готово")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpen(p) }) { Text("Открыть") }
                            TextButton(onClick = { onDelete(p) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProScreen(isPro: Boolean, onBuy: () -> Unit, onRestore: () -> Unit) {
    Column(
        Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("StitchCraft Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (isPro) "Pro активирован ✓"
            else "Большие схемы, до 60 цветов, экспорт PDF/CSV/PNG и профессиональные инструменты подготовки схем."
        )
        if (!isPro) Button(onClick = onBuy, Modifier.fillMaxWidth()) { Text("Купить Pro") }
        OutlinedButton(onClick = onRestore, Modifier.fillMaxWidth()) { Text("Восстановить покупку") }
        Text("Перед публикацией товар stitchcraft_pro_lifetime нужно создать в Google Play Console.", style = MaterialTheme.typography.bodySmall)
    }
}
