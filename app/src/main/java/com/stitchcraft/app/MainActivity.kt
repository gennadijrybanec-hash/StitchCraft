package com.stitchcraft.app

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import java.net.URLEncoder
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
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StitchCraftTheme {
                StitchCraftApp(initialImportUri = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Recreate only for an externally opened StitchCraft project so the incoming URI is
        // consumed by the same import path as a cold start.
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) recreate()
    }
}

private fun decodeBitmapForPattern(context: android.content.Context, uri: Uri, maxSide: Int = 2048): android.graphics.Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Не удалось открыть изображение" }
        BitmapFactory.decodeStream(input, null, bounds)
    }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Не удалось прочитать размер изображения" }

    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Не удалось открыть изображение" }
        requireNotNull(BitmapFactory.decodeStream(input, null, options)) { "Не удалось декодировать изображение" }
    }
}

enum class EditTool { COLOR, ERASE, COMPLETE }

private val StitchCraftWarmColors = lightColorScheme(
    primary = Color(0xFF8A3F5D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E4),
    onPrimaryContainer = Color(0xFF3A071D),
    secondary = Color(0xFF765B65),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF2C151E),
    tertiary = Color(0xFF6D5D3F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7E0B2),
    onTertiaryContainer = Color(0xFF251A04),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF23191D),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF23191D),
    surfaceVariant = Color(0xFFF2E2E6),
    onSurfaceVariant = Color(0xFF514348),
    outline = Color(0xFF837378),
    outlineVariant = Color(0xFFD5C2C7)
)

@Composable
private fun StitchCraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StitchCraftWarmColors,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StitchCraftApp(initialImportUri: Uri? = null) {
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
    var pendingProjectExport by remember { mutableStateOf<SavedProject?>(null) }

    val projectExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.stitchcraft.project+json")
    ) { uri ->
        val project = pendingProjectExport
        if (uri != null && project != null) {
            val bytes = store.exportProject(project)
            if (bytes != null) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                    .onSuccess { message = "Проект экспортирован" }
                    .onFailure { message = "Не удалось экспортировать проект" }
            } else message = "Не удалось экспортировать проект"
        }
        pendingProjectExport = null
    }

    val projectImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val imported = runCatching {
                context.contentResolver.openInputStream(uri)?.use { store.importProject(it.readBytes()) }
            }.getOrNull()
            if (imported != null) {
                projects = store.list()
                message = "Проект «${imported.name}» импортирован"
            } else message = "Файл не является проектом StitchCraft"
        }
    }
    val billing = remember {
        BillingManager(
            context = context,
            onProChanged = { pro ->
                isPro = pro
                context.getSharedPreferences("prefs", 0).edit().putBoolean("pro", pro).apply()
            },
            onMessage = { message = it }
        )
    }
    DisposableEffect(Unit) { billing.start(); onDispose { billing.stop() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
        pattern = null
        activeProject = null
        editingSession++
    }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(initialImportUri) {
        val uri = initialImportUri ?: return@LaunchedEffect
        val imported = runCatching {
            context.contentResolver.openInputStream(uri)?.use { store.importProject(it.readBytes()) }
        }.getOrNull()
        if (imported != null) {
            projects = store.list()
            val loaded = store.load(imported)
            if (loaded != null) {
                pattern = loaded
                activeProject = imported
                editingSession++
                tab = 1
                message = "Проект «${imported.name}» открыт"
            }
        } else message = "Файл не является проектом StitchCraft"
    }

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

                val w = width.toInt().coerceAtMost(if (isPro) ReleaseConfig.PRO_MAX_WIDTH else ReleaseConfig.FREE_MAX_WIDTH)
                val c = colors.toInt().coerceAtMost(if (isPro) ReleaseConfig.PRO_MAX_COLORS else ReleaseConfig.FREE_MAX_COLORS)

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
            message = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось обработать изображение"
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
                    fabricCount = fabricCount,
                    onPatternChange = { pattern = it },
                    onSave = { p ->
                        val existing = activeProject
                        val name = existing?.name ?: "Pattern_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        activeProject = store.save(name, p, existing?.id, fabricCount)
                        projects = store.list()
                        message = "Проект сохранён • прогресс ${p.progressPercent()}%"
                    },
                    onPdf = { p ->
                        val f = ExportManager.exportPdf(context, p, activeProject?.name ?: "StitchCraft_${System.currentTimeMillis()}", fabricCount)
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
                            fabricCount = saved.fabricCount
                            editingSession++
                            tab = 1
                        }
                    },
                    onRename = { saved, newName ->
                        val renamed = store.rename(saved, newName)
                        if (renamed != null && activeProject?.id == saved.id) activeProject = renamed
                        projects = store.list()
                    },
                    onDelete = { saved ->
                        store.delete(saved)
                        if (activeProject?.id == saved.id) activeProject = null
                        projects = store.list()
                    },
                    onExport = { saved ->
                        pendingProjectExport = saved
                        val safeName = saved.name.replace(Regex("[^A-Za-zА-Яа-я0-9._ -]"), "_").ifBlank { "StitchCraft_project" }
                        projectExportLauncher.launch("$safeName.stitchcraft")
                    },
                    onImport = { projectImportLauncher.launch(arrayOf("application/vnd.stitchcraft.project+json", "application/json", "application/octet-stream", "text/plain", "*/*")) }
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
            Text("Free: до ${ReleaseConfig.FREE_MAX_WIDTH} крестиков по ширине и ${ReleaseConfig.FREE_MAX_COLORS} цветов. Pro снимает ограничения и включает экспорт.", style = MaterialTheme.typography.bodySmall)
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
    fabricCount: Int,
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

    val context = LocalContext.current
    var showMaterials by remember(sessionId) { mutableStateOf(false) }
    var scale by remember(sessionId) { mutableFloatStateOf(1f) }
    var tool by remember(sessionId) { mutableStateOf(EditTool.COMPLETE) }
    var selectedColor by remember(sessionId) { mutableIntStateOf(0) }
    var focusColor by remember(sessionId) { mutableIntStateOf(-1) }
    var viewResetKey by remember(sessionId) { mutableIntStateOf(0) }
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
    val finishedWidthCm = pattern.width.toFloat() / fabricCount * 2.54f
val finishedHeightCm = pattern.height.toFloat() / fabricCount * 2.54f

    // Keep the whole pattern screen vertically scrollable. The canvas has its own fixed
    // viewport for pan/zoom, while the controls, exports and full palette can scroll as a page.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${pattern.width} × ${pattern.height} • ${pattern.palette.size} цветов", fontWeight = FontWeight.Bold)
        Text(
    "Канва Aida $fabricCount • %.1f × %.1f см".format(
        finishedWidthCm,
        finishedHeightCm
    )
)
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
            OutlinedButton(onClick = { scale = (scale / 2f).coerceAtLeast(.6f) }) { Text("−") }
            OutlinedButton(onClick = { scale = 1f; viewResetKey++ }) { Text("По размеру") }
            OutlinedButton(onClick = { scale = (scale * 2f).coerceAtMost(20f) }) { Text("+") }
            Text("${(scale * 100).toInt()}%", modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp))
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

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = focusColor < 0,
                onClick = { focusColor = -1 },
                label = { Text("Все цвета") }
            )
            pattern.palette.forEachIndexed { index, thread ->
                FilterChip(
                    selected = focusColor == index,
                    onClick = { focusColor = if (focusColor == index) -1 else index },
                    label = { Text("${PatternEngine.symbolForIndex(index)} ${thread.code}") }
                )
            }
        }

        PatternCanvas(
            pattern = pattern,
            sessionId = sessionId,
            scale = scale,
            modifier = Modifier.fillMaxWidth().height(360.dp),
            viewResetKey = viewResetKey,
            focusColor = focusColor,
            onZoom = { zoom ->
                // Make pinch zoom responsive enough for large embroidery charts.
                // Faster two-finger zoom: closer to DiamondCraft while keeping it smooth.
                val acceleratedZoom = zoom.toDouble().pow(3.0).toFloat()
                scale = (scale * acceleratedZoom).coerceIn(.6f, 20f)
            },
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
        OutlinedButton(
            onClick = { showMaterials = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Материалы") }

        Text("Палитра", fontWeight = FontWeight.Bold)
        pattern.palette.forEachIndexed { i, c ->
            val count = pattern.counts()[i] ?: 0
            val completedForColor = pattern.cells.count { !it.erased && it.colorIndex == i && it.completed }
            Text(
                "${PatternEngine.symbolForIndex(i)}  ${c.code} • ${c.name} — $completedForColor/$count",
                Modifier.padding(vertical = 2.dp)
            )
        }
    }

    if (showMaterials) {
        AlertDialog(
            onDismissRequest = { showMaterials = false },
            title = { Text("Материалы для схемы") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Канва: Aida $fabricCount • %.1f × %.1f см".format(finishedWidthCm, finishedHeightCm))
                    OutlinedButton(
                        onClick = { openMaterialSearch(context, "Aida $fabricCount cross stitch fabric buy") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Найти канву в магазинах") }

                    HorizontalDivider()
                    Text("Нитки DMC", fontWeight = FontWeight.Bold)
                    Text("Нажмите на цвет, чтобы найти подходящие предложения в интернет-магазинах.", style = MaterialTheme.typography.bodySmall)
                    pattern.palette.forEachIndexed { index, thread ->
                        val count = pattern.counts()[index] ?: 0
                        OutlinedButton(
                            onClick = { openMaterialSearch(context, "DMC ${thread.code} embroidery floss buy") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${PatternEngine.symbolForIndex(index)}  DMC ${thread.code} • $count крестиков")
                        }
                    }
                    Text("Покупка открывается во внешнем браузере. StitchCraft не передаёт изображения или проекты магазинам.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showMaterials = false }) { Text("Закрыть") } }
        )
    }
}

private fun openMaterialSearch(context: android.content.Context, query: String) {
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val uri = Uri.parse("https://www.google.com/search?q=$encoded")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

@Composable
fun PatternCanvas(
    pattern: StitchPattern,
    sessionId: Int,
    scale: Float,
    modifier: Modifier,
    viewResetKey: Int,
    focusColor: Int,
    onZoom: (Float) -> Unit,
    onCellTap: (Int, Int) -> Unit
) {
    // Keep the viewport stable while cells are edited. The pattern object changes on every
    // completed/erased/recolored cell, so keying panOffset by `pattern` would reset the view
    // after every tap. Reset only when a new editing session starts or the user requests fit.
    var panOffset by remember(sessionId, viewResetKey) { mutableStateOf(Offset.Zero) }
    val currentOnCellTap by rememberUpdatedState(onCellTap)
    val currentOnZoom by rememberUpdatedState(onZoom)
    Canvas(
        modifier
            .background(Color.White)
            .clipToBounds()
            .pointerInput(pattern.width, pattern.height, scale, sessionId, viewResetKey) {
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
                    
                    if (x in 0 until pattern.width && y in 0 until pattern.height) currentOnCellTap(x, y)
                }
            }
            .pointerInput(pattern.width, pattern.height, scale, sessionId, viewResetKey) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val cellSize = minOf(
                        size.width / pattern.width,
                        size.height / pattern.height
                    ) * scale
                    val contentWidth = pattern.width * cellSize
                    val contentHeight = pattern.height * cellSize
                    val maxPanX = maxOf(0f, (contentWidth - size.width) / 2f) + size.width * .45f
                    val maxPanY = maxOf(0f, (contentHeight - size.height) / 2f) + size.height * .45f
                    val nextPan = panOffset + pan
                    panOffset = Offset(
                        nextPan.x.coerceIn(-maxPanX, maxPanX),
                        nextPan.y.coerceIn(-maxPanY, maxPanY)
                    )
                    currentOnZoom(zoom)
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
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        fun symbolColor(rgb: Int): Int {
            val r = android.graphics.Color.red(rgb)
            val g = android.graphics.Color.green(rgb)
            val b = android.graphics.Color.blue(rgb)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
            return if (luminance < 145) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        }

        for (y in 0 until maxY) for (x in 0 until maxX) {
            val pc = pattern.cell(x, y)
            val left = offsetX + x * cellSize
            val top = offsetY + y * cellSize
            val baseFill = if (pc.erased) Color.White else Color(pattern.palette[pc.colorIndex].rgb)
            val isFocused = focusColor < 0 || pc.colorIndex == focusColor
            val fill = if (isFocused || pc.erased) baseFill else baseFill.copy(alpha = .16f)
            drawRect(fill, Offset(left, top), androidx.compose.ui.geometry.Size(cellSize, cellSize))

            if (pc.completed && !pc.erased) {
                // A completed stitch must remain unmistakably visible after tapping other cells.
                drawRect(
                    Color(0xFF2E7D32).copy(alpha = .42f),
                    Offset(left, top),
                    androidx.compose.ui.geometry.Size(cellSize, cellSize)
                )
                if (cellSize >= 6f) {
                    val inset = (cellSize * .08f).coerceAtLeast(1f)
                    drawRect(
                        Color(0xFF0B6B2B),
                        Offset(left + inset, top + inset),
                        androidx.compose.ui.geometry.Size(cellSize - inset * 2, cellSize - inset * 2),
                        style = Stroke((cellSize * .08f).coerceIn(1.2f, 4f))
                    )
                }
                if (cellSize >= 8f) {
                    textPaint.color = android.graphics.Color.WHITE
                    textPaint.setShadowLayer((cellSize * .08f).coerceAtLeast(1f), 0f, 0f, android.graphics.Color.BLACK)
                    textPaint.textSize = cellSize * .72f
                    drawContext.canvas.nativeCanvas.drawText("✓", left + cellSize * .5f, top + cellSize * .74f, textPaint)
                    textPaint.clearShadowLayer()
                }
            } else if (!pc.erased && cellSize >= 11f && isFocused) {
                textPaint.color = symbolColor(pattern.palette[pc.colorIndex].rgb)
                textPaint.textSize = cellSize * .52f
                drawContext.canvas.nativeCanvas.drawText(pc.symbol, left + cellSize * .5f, top + cellSize * .70f, textPaint)
            }

            if (cellSize >= 2.5f) {
                drawRect(
                    Color.Black.copy(alpha = .24f),
                    Offset(left, top),
                    androidx.compose.ui.geometry.Size(cellSize, cellSize),
                    style = Stroke(if (cellSize >= 10f) .65f else .4f)
                )
            }
        }

        // Bold 10×10 guide lines make large patterns easier to count while stitching.
        if (cellSize >= 4f) {
            val guideWidth = if (cellSize >= 14f) 2.4f else 1.7f
            for (x in 0..pattern.width step 10) {
                val lineX = offsetX + x * cellSize
                drawLine(
                    Color.Black.copy(alpha = .62f),
                    Offset(lineX, offsetY),
                    Offset(lineX, offsetY + pattern.height * cellSize),
                    strokeWidth = guideWidth
                )
            }
            for (y in 0..pattern.height step 10) {
                val lineY = offsetY + y * cellSize
                drawLine(
                    Color.Black.copy(alpha = .62f),
                    Offset(offsetX, lineY),
                    Offset(offsetX + pattern.width * cellSize, lineY),
                    strokeWidth = guideWidth
                )
            }
        }
    }
}

@Composable
fun ProjectsScreen(
    projects: List<SavedProject>,
    onOpen: (SavedProject) -> Unit,
    onRename: (SavedProject, String) -> Unit,
    onDelete: (SavedProject) -> Unit,
    onExport: (SavedProject) -> Unit,
    onImport: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<SavedProject?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SavedProject?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Сохранённые проекты", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onImport) { Text("Импорт") }
        }
        if (projects.isEmpty()) Text("Пока нет проектов.", Modifier.padding(top = 16.dp))
        LazyColumn {
            itemsIndexed(projects) { _, p ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text("${p.width}×${p.height} • ${p.colors} цветов • Aida ${p.fabricCount} • ${p.progress}% готово")
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = { onOpen(p) }) { Text("Открыть", maxLines = 1) }
                            TextButton(onClick = { renameTarget = p; renameText = p.name }) { Text("Переименовать", maxLines = 1) }
                            TextButton(onClick = { onExport(p) }) { Text("Экспорт", maxLines = 1) }
                            TextButton(onClick = { deleteTarget = p }) { Text("Удалить", maxLines = 1) }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать проект") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(60) },
                    label = { Text("Название") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = { onRename(project, renameText); renameTarget = null }
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Отмена") } }
        )
    }

    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить проект?") },
            text = { Text("Проект «${project.name}» и сохранённый прогресс будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = { onDelete(project); deleteTarget = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } }
        )
    }
}

@Composable
fun ProScreen(isPro: Boolean, onBuy: () -> Unit, onRestore: () -> Unit) {
    Column(
        Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("StitchCraft Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (isPro) "Pro активирован ✓" else "Полная версия для больших и детальных схем")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Что входит в Pro", fontWeight = FontWeight.Bold)
                Text("✓ Большие схемы до ${ReleaseConfig.PRO_MAX_WIDTH} крестиков по ширине")
                Text("✓ До ${ReleaseConfig.PRO_MAX_COLORS} цветов DMC")
                Text("✓ Экспорт PDF, PNG и CSV")
                Text("✓ Сохранение, импорт и резервные копии проектов")
                Text("✓ Отслеживание прогресса вышивки")
            }
        }
        if (!isPro) Button(onClick = onBuy, Modifier.fillMaxWidth()) { Text("Получить StitchCraft Pro") }
        OutlinedButton(onClick = onRestore, Modifier.fillMaxWidth()) { Text("Восстановить покупку") }
        Text("Разовая покупка Pro через Google Play. После покупки доступ можно восстановить на другом устройстве с тем же аккаунтом Google.", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("О приложении", fontWeight = FontWeight.Bold)
        Text("StitchCraft превращает изображения в схемы для вышивки крестиком и помогает вести прогресс проекта.", style = MaterialTheme.typography.bodySmall)
        Text("Поддержка: ${ReleaseConfig.SUPPORT_EMAIL}", style = MaterialTheme.typography.bodySmall)
        val context = LocalContext.current
        OutlinedButton(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseConfig.PRIVACY_POLICY_URL))) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Политика конфиденциальности") }
    }
}
