package com.stitchcraft.app

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import java.net.URLEncoder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@Composable
private fun isCompactScreen(): Boolean = LocalConfiguration.current.screenWidthDp < 420

@Composable
private fun adaptivePagePadding() = when {
    LocalConfiguration.current.screenWidthDp < 360 -> 10.dp
    LocalConfiguration.current.screenWidthDp < 420 -> 12.dp
    LocalConfiguration.current.screenWidthDp < 600 -> 16.dp
    else -> 20.dp
}

@Composable
private fun adaptiveCanvasHeight() = when {
    LocalConfiguration.current.screenHeightDp < 600 -> 260.dp
    LocalConfiguration.current.screenHeightDp < 700 -> 320.dp
    LocalConfiguration.current.screenWidthDp >= 600 -> 440.dp
    else -> 360.dp
}
private const val APP_LANGUAGE_PREF = "app_language"

private fun localizedContext(base: Context): Context {
    val code = base.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        .getString(APP_LANGUAGE_PREF, "system") ?: "system"
    if (code == "system") return base
    val locale = Locale.forLanguageTag(code)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

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
        requireNotNull(input) { context.getString(R.string.image_open_failed) }
        BitmapFactory.decodeStream(input, null, bounds)
    }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { context.getString(R.string.image_size_failed) }

    var sample = 1
    while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { context.getString(R.string.image_open_failed) }
        requireNotNull(BitmapFactory.decodeStream(input, null, options)) { context.getString(R.string.image_decode_failed) }
    }
}

enum class EditTool { COLOR, ERASE, COMPLETE }

private data class PatternStats(
    val total: Int,
    val done: Int,
    val counts: IntArray,
    val completedByColor: IntArray
)

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
        message = context.getString(R.string.png_saved)
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
        message = context.getString(R.string.pdf_saved)
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
        message = context.getString(R.string.csv_saved)
    }
    pendingCsvFile = null
}
    // Never treat a locally cached flag as proof of a Play purchase.
    // Entitlement is granted only after BillingManager reports a PURCHASED product.
    var isPro by remember { mutableStateOf(false) }
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
                    .onSuccess { message = context.getString(R.string.project_exported) }
                    .onFailure { message = context.getString(R.string.project_export_failed) }
            } else message = context.getString(R.string.project_export_failed)
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
                message = context.getString(R.string.project_imported, imported.name)
            } else message = context.getString(R.string.not_stitchcraft_project)
        }
    }
    val billing = remember {
        BillingManager(
            context = context,
            onProChanged = { pro ->
                isPro = pro
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
                message = context.getString(R.string.project_opened, imported.name)
            }
        } else message = context.getString(R.string.not_stitchcraft_project)
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
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("✚") }, label = { Text(stringResource(R.string.nav_create)) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("▦") }, label = { Text(stringResource(R.string.nav_pattern)) })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("☰") }, label = { Text(stringResource(R.string.nav_projects)) })
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
                val w = width.toInt().coerceAtMost(if (isPro) ReleaseConfig.PRO_MAX_WIDTH else ReleaseConfig.FREE_MAX_WIDTH)
                val c = colors.toInt().coerceAtMost(if (isPro) ReleaseConfig.PRO_MAX_COLORS else ReleaseConfig.FREE_MAX_COLORS)

                // A stitch grid never needs the full camera/photo resolution. Decode a bounded
                // source close to the useful working size so 12-50 MP photos do not consume
                // hundreds of MB before the pattern is even generated.
                val decodeSide = (w * 4).coerceIn(768, 1600)
                val bmp = decodeBitmapForPattern(context, uri, maxSide = decodeSide)
                try {
                    PatternEngine.generate(
                        bmp,
                        w,
                        c,
                        PatternOptions(cleanupIsolatedStitches = cleanupSingles)
                    )
                } finally {
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }

            pattern = result
            activeProject = null
            editingSession++
            tab = 1

        } catch (e: Exception) {
            message = e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.image_process_failed)
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
                        message = context.getString(R.string.project_saved_progress, p.progressPercent())
                    },
                    onPdf = { p ->
                        if (isPro) {
                        val f = ExportManager.exportPdf(context, p, activeProject?.name ?: "StitchCraft_${System.currentTimeMillis()}", fabricCount)
                        pendingPdfFile = f
pdfSaveLauncher.launch(f.name)
                        } else { message = context.getString(R.string.pro_tagline) }
                    },
                    onCsv = { p ->
                        if (isPro) {
                        val f = ExportManager.exportCsv(context, p, activeProject?.name ?: "StitchCraft_${System.currentTimeMillis()}")
                        pendingCsvFile = f
csvSaveLauncher.launch(f.name)
                        } else { message = context.getString(R.string.pro_tagline) }
                    },
                    onPng = { p ->
                        if (isPro) {
                        val f = ExportManager.exportPng(context, p, activeProject?.name ?: "StitchCraft_${System.currentTimeMillis()}")
                    pendingPngFile = f
pngSaveLauncher.launch(f.name)
                        } else { message = context.getString(R.string.pro_tagline) }
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
                    isPro = isPro,
                    statusMessage = message,
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
    val compact = isCompactScreen()
    val pagePadding = adaptivePagePadding()
    Column(
        Modifier.padding(pagePadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)
    ) {
        Text(stringResource(R.string.create_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.create_description))
        Button(onClick = onPick, Modifier.fillMaxWidth()) {
            Text(if (uri == null) stringResource(R.string.choose_image) else stringResource(R.string.choose_other_image))
        }
        Text(if (uri == null) stringResource(R.string.image_not_selected) else stringResource(R.string.image_selected))
        Text(stringResource(R.string.pattern_width, width.toInt()))
        Slider(width, onValueChange = onWidth, valueRange = 20f..if (isPro) ReleaseConfig.PRO_MAX_WIDTH.toFloat() else ReleaseConfig.FREE_MAX_WIDTH.toFloat(), steps = 22)
        Text(stringResource(R.string.color_count, colors.toInt()))
        Slider(colors, onValueChange = onColors, valueRange = 4f..if (isPro) ReleaseConfig.PRO_MAX_COLORS.toFloat() else ReleaseConfig.FREE_MAX_COLORS.toFloat(), steps = 12)
        Text(stringResource(R.string.fabric_aida, fabricCount))

Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf(14, 16, 18).forEach { count ->
        FilterChip(
            selected = fabricCount == count,
            onClick = { onFabricCount(count) },
            label = { Text("$count ct") }
        )
    }
}
        if (compact) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.cleanup_singles), modifier = Modifier.weight(1f), maxLines = 2)
                    Switch(checked = cleanupSingles, onCheckedChange = onCleanup)
                }
                Text(stringResource(R.string.cleanup_singles_desc), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.cleanup_singles))
                    Text(stringResource(R.string.cleanup_singles_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = cleanupSingles, onCheckedChange = onCleanup)
            }
        }
        if (!isPro) {
            Text(stringResource(R.string.free_limits, ReleaseConfig.FREE_MAX_WIDTH, ReleaseConfig.FREE_MAX_COLORS), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onGenerate, enabled = uri != null && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) stringResource(R.string.generating) else stringResource(R.string.create_pattern))
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
        Box(Modifier.fillMaxSize().padding(24.dp)) { Text(stringResource(R.string.create_first)) }
        return
    }

    val context = LocalContext.current
    var showMaterials by remember(sessionId) { mutableStateOf(false) }
    var scale by remember(sessionId) { mutableFloatStateOf(1f) }
    var tool by remember(sessionId) { mutableStateOf(EditTool.COMPLETE) }
    var selectedColor by remember(sessionId) { mutableIntStateOf(0) }
    var focusColor by remember(sessionId) { mutableIntStateOf(-1) }
    var viewResetKey by remember(sessionId) { mutableIntStateOf(0) }
    var viewMenuExpanded by remember(sessionId) { mutableStateOf(false) }
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

    // Calculate all progress/palette counters in one pass and reuse them until the pattern changes.
    // The previous UI repeatedly scanned every cell once per palette row, which becomes expensive
    // on 40k+ stitch charts.
    val stats = remember(pattern) {
        val counts = IntArray(pattern.palette.size)
        val completedByColor = IntArray(pattern.palette.size)
        var total = 0
        var done = 0
        pattern.cells.forEach { cell ->
            if (!cell.erased) {
                total++
                if (cell.colorIndex in counts.indices) counts[cell.colorIndex]++
                if (cell.completed) {
                    done++
                    if (cell.colorIndex in completedByColor.indices) completedByColor[cell.colorIndex]++
                }
            }
        }
        PatternStats(total, done, counts, completedByColor)
    }
    val total = stats.total
    val done = stats.done
    val finishedWidthCm = pattern.width.toFloat() / fabricCount * 2.54f
    val finishedHeightCm = pattern.height.toFloat() / fabricCount * 2.54f

    // Keep the whole pattern screen vertically scrollable. The canvas has its own fixed
    // viewport for pan/zoom, while the controls, exports and full palette can scroll as a page.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(adaptivePagePadding()),
        verticalArrangement = Arrangement.spacedBy(if (isCompactScreen()) 6.dp else 8.dp)
    ) {
        Text(stringResource(R.string.pattern_summary, pattern.width, pattern.height, pattern.palette.size), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.fabric_dimensions, fabricCount, finishedWidthCm, finishedHeightCm))
        Text(stringResource(R.string.progress, done, total, pattern.progressPercent()), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )

        // Primary edit actions must always stay on-screen. Rare zoom actions live in a compact menu
        // instead of forcing the toolbar to overflow horizontally on phones such as Mi 8.
        // Compact two-row toolbar. Keep the six most-used actions visible even on narrow phones
        // and with enlarged system fonts; no horizontal scrolling is needed for commands.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = tool == EditTool.COMPLETE,
                onClick = { tool = EditTool.COMPLETE },
                label = { Text(stringResource(R.string.tool_mark), maxLines = 1, softWrap = false) },
                modifier = Modifier.weight(1f).height(48.dp)
            )
            FilterChip(
                selected = tool == EditTool.COLOR,
                onClick = { tool = EditTool.COLOR },
                label = { Text(stringResource(R.string.tool_color), maxLines = 1, softWrap = false) },
                modifier = Modifier.weight(1f).height(48.dp)
            )
            FilterChip(
                selected = tool == EditTool.ERASE,
                onClick = { tool = EditTool.ERASE },
                label = { Text(stringResource(R.string.tool_eraser), maxLines = 1, softWrap = false) },
                modifier = Modifier.weight(1f).height(48.dp)
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = ::undoEdit,
                enabled = undo.isNotEmpty(),
                modifier = Modifier.weight(1f).height(46.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { Text(stringResource(R.string.undo), maxLines = 1, softWrap = false) }
            OutlinedButton(
                onClick = ::redoEdit,
                enabled = redo.isNotEmpty(),
                modifier = Modifier.weight(1f).height(46.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { Text(stringResource(R.string.redo), maxLines = 1, softWrap = false) }

            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { viewMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(stringResource(R.string.menu), maxLines = 1, softWrap = false) }
                DropdownMenu(
                    expanded = viewMenuExpanded,
                    onDismissRequest = { viewMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.zoom_out)) },
                        onClick = {
                            scale = (scale / 2f).coerceAtLeast(.6f)
                            viewMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fit)) },
                        onClick = {
                            scale = 1f
                            viewResetKey++
                            viewMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.zoom_in)) },
                        onClick = {
                            scale = (scale * 2f).coerceAtMost(20f)
                            viewMenuExpanded = false
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.zoom_percent, (scale * 100).toInt())) },
                        onClick = { viewMenuExpanded = false }
                    )
                }
            }
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
                label = { Text(stringResource(R.string.all_colors)) }
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
            modifier = Modifier.fillMaxWidth().height(adaptiveCanvasHeight()),
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
        Text(
            stringResource(R.string.gesture_hint),
            style = MaterialTheme.typography.bodySmall
        )

        // Export actions are a fixed 2×2 grid so none can disappear beyond the right edge.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onSave(pattern) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.save), maxLines = 1, softWrap = false) }
                Button(
                    onClick = { onPdf(pattern) },
                    enabled = isPro,
                    modifier = Modifier.weight(1f)
                ) { Text("PDF") }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onCsv(pattern) },
                    enabled = isPro,
                    modifier = Modifier.weight(1f)
                ) { Text("CSV") }
                Button(
                    onClick = { onPng(pattern) },
                    enabled = isPro,
                    modifier = Modifier.weight(1f)
                ) { Text("PNG") }
            }
        }
        OutlinedButton(
            onClick = { showMaterials = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.materials)) }

        Text(stringResource(R.string.palette), fontWeight = FontWeight.Bold)
        pattern.palette.forEachIndexed { i, c ->
            val count = stats.counts.getOrElse(i) { 0 }
            val completedForColor = stats.completedByColor.getOrElse(i) { 0 }
            Text(
                "${PatternEngine.symbolForIndex(i)}  ${c.code} • ${c.name} — $completedForColor/$count",
                Modifier.padding(vertical = 2.dp)
            )
        }
    }

    if (showMaterials) {
        AlertDialog(
            onDismissRequest = { showMaterials = false },
            title = { Text(stringResource(R.string.materials_title)) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(R.string.fabric_dimensions_colon, fabricCount, finishedWidthCm, finishedHeightCm))
                    OutlinedButton(
                        onClick = { openMaterialSearch(context, "Aida $fabricCount cross stitch fabric buy") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.find_fabric)) }

                    HorizontalDivider()
                    Text(stringResource(R.string.dmc_threads), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.tap_color_shop), style = MaterialTheme.typography.bodySmall)
                    pattern.palette.forEachIndexed { index, thread ->
                        val count = stats.counts.getOrElse(index) { 0 }
                        OutlinedButton(
                            onClick = { openMaterialSearch(context, "DMC ${thread.code} embroidery floss buy") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.dmc_crosses, PatternEngine.symbolForIndex(index), thread.code, count))
                        }
                    }
                    Text(stringResource(R.string.external_shop_notice), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showMaterials = false }) { Text(stringResource(R.string.close)) } }
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
    // completed/erased/recolored cell, so reset only for a new session or explicit fit.
    val currentOnCellTap by rememberUpdatedState(onCellTap)
    val currentOnZoom by rememberUpdatedState(onZoom)
    // Pan belongs to the viewport, not to the pattern data. Reset only for a new session
    // or an explicit "fit to screen" action. This keeps editing from snapping the view.
    var pan by remember(sessionId, viewResetKey) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scale, sessionId, viewResetKey) {
        if (scale <= 1.01f && pan != Offset.Zero) pan = Offset.Zero
    }

    // Fast low-zoom preview: one bitmap pixel represents one stitch. At fit-to-screen this
    // replaces tens of thousands of individual drawRect calls with a single bitmap draw.
    val fastPreview = remember(pattern, focusColor) {
        val pixels = IntArray(pattern.width * pattern.height)
        pattern.cells.forEachIndexed { index, pc ->
            val rgb = when {
                pc.erased -> android.graphics.Color.WHITE
                pc.completed -> android.graphics.Color.rgb(46, 125, 50)
                else -> pattern.palette[pc.colorIndex].rgb
            }
            if (focusColor >= 0 && !pc.erased && pc.colorIndex != focusColor) {
                val r = android.graphics.Color.red(rgb)
                val g = android.graphics.Color.green(rgb)
                val b = android.graphics.Color.blue(rgb)
                // Match the old faded focus look without adding per-cell alpha drawing work.
                pixels[index] = android.graphics.Color.rgb(
                    (r * .16f + 255f * .84f).roundToInt(),
                    (g * .16f + 255f * .84f).roundToInt(),
                    (b * .16f + 255f * .84f).roundToInt()
                )
            } else {
                pixels[index] = rgb
            }
        }
        android.graphics.Bitmap.createBitmap(
            pixels,
            pattern.width,
            pattern.height,
            android.graphics.Bitmap.Config.ARGB_8888
        ).asImageBitmap()
    }

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
                    val offsetX = (size.width - pattern.width * cellSize) / 2f + pan.x
                    val offsetY = (size.height - pattern.height * cellSize) / 2f + pan.y
                    if (cellSize <= 0f) return@detectTapGestures
                    val x = floor((offset.x - offsetX) / cellSize).toInt()
                    val y = floor((offset.y - offsetY) / cellSize).toInt()
                    if (x in 0 until pattern.width && y in 0 until pattern.height) currentOnCellTap(x, y)
                }
            }
            .pointerInput(pattern.width, pattern.height, scale, sessionId, viewResetKey) {
                // Keep the fast two-finger zoom behaviour from v134, while retaining v135 pan.
                // One finger is consumed only when the chart is enlarged, so the page can still
                // scroll normally at fit-to-screen scale.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.size >= 2) {
                            val a = pressed[0]
                            val b = pressed[1]
                            val currentDx = a.position.x - b.position.x
                            val currentDy = a.position.y - b.position.y
                            val previousDx = a.previousPosition.x - b.previousPosition.x
                            val previousDy = a.previousPosition.y - b.previousPosition.y
                            val currentDistance = kotlin.math.sqrt(currentDx * currentDx + currentDy * currentDy)
                            val previousDistance = kotlin.math.sqrt(previousDx * previousDx + previousDy * previousDy)
                            val zoom = if (previousDistance > 0.01f) currentDistance / previousDistance else 1f
                            if (zoom.isFinite() && zoom > 0f) currentOnZoom(zoom)
                            pressed.forEach { it.consume() }
                        } else if (pressed.size == 1 && scale > 1.01f) {
                            val change = pressed[0]
                            val delta = change.position - change.previousPosition
                            if (delta != Offset.Zero) {
                                val cellSize = minOf(
                                    size.width / pattern.width,
                                    size.height / pattern.height
                                ) * scale
                                val contentWidth = pattern.width * cellSize
                                val contentHeight = pattern.height * cellSize
                                val maxPanX = ((contentWidth - size.width) / 2f).coerceAtLeast(0f)
                                val maxPanY = ((contentHeight - size.height) / 2f).coerceAtLeast(0f)
                                pan = Offset(
                                    (pan.x + delta.x).coerceIn(-maxPanX, maxPanX),
                                    (pan.y + delta.y).coerceIn(-maxPanY, maxPanY)
                                )
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        val cellSize = minOf(
            size.width / pattern.width,
            size.height / pattern.height
        ) * scale
        val offsetX = (size.width - pattern.width * cellSize) / 2f + pan.x
        val offsetY = (size.height - pattern.height * cellSize) / 2f + pan.y

        fun drawGuideLines() {
            if (cellSize < 4f) return
            val guideWidth = if (cellSize >= 14f) 2.4f else 1.7f
            val visibleStartX = floor((0f - offsetX) / cellSize).toInt().coerceIn(0, pattern.width)
            val visibleEndX = ceil((size.width - offsetX) / cellSize).toInt().coerceIn(0, pattern.width)
            val visibleStartY = floor((0f - offsetY) / cellSize).toInt().coerceIn(0, pattern.height)
            val visibleEndY = ceil((size.height - offsetY) / cellSize).toInt().coerceIn(0, pattern.height)
            val firstGuideX = ((visibleStartX + 9) / 10) * 10
            val firstGuideY = ((visibleStartY + 9) / 10) * 10
            for (x in firstGuideX..visibleEndX step 10) {
                val lineX = offsetX + x * cellSize
                drawLine(
                    Color.Black.copy(alpha = .62f),
                    Offset(lineX, maxOf(0f, offsetY)),
                    Offset(lineX, minOf(size.height, offsetY + pattern.height * cellSize)),
                    strokeWidth = guideWidth
                )
            }
            for (y in firstGuideY..visibleEndY step 10) {
                val lineY = offsetY + y * cellSize
                drawLine(
                    Color.Black.copy(alpha = .62f),
                    Offset(maxOf(0f, offsetX), lineY),
                    Offset(minOf(size.width, offsetX + pattern.width * cellSize), lineY),
                    strokeWidth = guideWidth
                )
            }
        }

        // Below this size symbols are not useful. Use the cached raster preview instead of
        // painting every stitch separately; keep 10x10 guides when they are still readable.
        if (cellSize < 8f) {
            val dstWidth = (pattern.width * cellSize).roundToInt().coerceAtLeast(1)
            val dstHeight = (pattern.height * cellSize).roundToInt().coerceAtLeast(1)
            drawImage(
                image = fastPreview,
                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                dstSize = IntSize(dstWidth, dstHeight),
                filterQuality = FilterQuality.None
            )
            drawGuideLines()
            return@Canvas
        }

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

        // Draw only cells that intersect the viewport. On zoomed large charts this cuts work
        // from the entire 40k-90k cell pattern to the small portion actually visible on screen.
        val startX = floor((0f - offsetX) / cellSize).toInt().coerceIn(0, pattern.width - 1)
        val endX = ceil((size.width - offsetX) / cellSize).toInt().coerceIn(0, pattern.width)
        val startY = floor((0f - offsetY) / cellSize).toInt().coerceIn(0, pattern.height - 1)
        val endY = ceil((size.height - offsetY) / cellSize).toInt().coerceIn(0, pattern.height)

        for (y in startY until endY) for (x in startX until endX) {
            val pc = pattern.cell(x, y)
            val left = offsetX + x * cellSize
            val top = offsetY + y * cellSize
            val baseFill = if (pc.erased) Color.White else Color(pattern.palette[pc.colorIndex].rgb)
            val isFocused = focusColor < 0 || pc.colorIndex == focusColor
            val fill = if (isFocused || pc.erased) baseFill else baseFill.copy(alpha = .16f)
            drawRect(fill, Offset(left, top), androidx.compose.ui.geometry.Size(cellSize, cellSize))

            if (pc.completed && !pc.erased) {
                drawRect(
                    Color(0xFF2E7D32).copy(alpha = .42f),
                    Offset(left, top),
                    androidx.compose.ui.geometry.Size(cellSize, cellSize)
                )
                val inset = (cellSize * .08f).coerceAtLeast(1f)
                drawRect(
                    Color(0xFF0B6B2B),
                    Offset(left + inset, top + inset),
                    androidx.compose.ui.geometry.Size(cellSize - inset * 2, cellSize - inset * 2),
                    style = Stroke((cellSize * .08f).coerceIn(1.2f, 4f))
                )
                textPaint.color = android.graphics.Color.WHITE
                textPaint.setShadowLayer((cellSize * .08f).coerceAtLeast(1f), 0f, 0f, android.graphics.Color.BLACK)
                textPaint.textSize = cellSize * .72f
                drawContext.canvas.nativeCanvas.drawText("✓", left + cellSize * .5f, top + cellSize * .74f, textPaint)
                textPaint.clearShadowLayer()
            } else if (!pc.erased && cellSize >= 11f && isFocused) {
                textPaint.color = symbolColor(pattern.palette[pc.colorIndex].rgb)
                textPaint.textSize = cellSize * .52f
                drawContext.canvas.nativeCanvas.drawText(pc.symbol, left + cellSize * .5f, top + cellSize * .70f, textPaint)
            }

            drawRect(
                Color.Black.copy(alpha = .24f),
                Offset(left, top),
                androidx.compose.ui.geometry.Size(cellSize, cellSize),
                style = Stroke(if (cellSize >= 10f) .65f else .4f)
            )
        }
        drawGuideLines()
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

    val compact = isCompactScreen()
    Column(Modifier.fillMaxSize().padding(adaptivePagePadding())) {
        if (compact) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.saved_projects), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_project)) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.saved_projects), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.import_project)) }
            }
        }
        if (projects.isEmpty()) Text(stringResource(R.string.no_projects), Modifier.padding(top = 16.dp))
        LazyColumn {
            itemsIndexed(projects) { _, p ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.project_summary, p.width, p.height, p.colors, p.fabricCount, p.progress))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = { onOpen(p) }) { Text(stringResource(R.string.open), maxLines = 1) }
                            TextButton(onClick = { renameTarget = p; renameText = p.name }) { Text(stringResource(R.string.rename), maxLines = 1) }
                            TextButton(onClick = { onExport(p) }) { Text(stringResource(R.string.export), maxLines = 1) }
                            TextButton(onClick = { deleteTarget = p }) { Text(stringResource(R.string.delete), maxLines = 1) }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_project)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(60) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = { onRename(project, renameText); renameTarget = null }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_project_q)) },
            text = { Text(stringResource(R.string.delete_project_text, project.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(project); deleteTarget = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
fun ProScreen(
    isPro: Boolean,
    statusMessage: String?,
    onBuy: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        Modifier.padding(adaptivePagePadding()).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (isCompactScreen()) 10.dp else 12.dp)
    ) {
        Text("StitchCraft Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.version, "1.0"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (isPro) stringResource(R.string.pro_active) else stringResource(R.string.pro_tagline))
        statusMessage?.let { status ->
            Card(Modifier.fillMaxWidth()) {
                Text(status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pro_includes), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pro_large_patterns, ReleaseConfig.PRO_MAX_WIDTH))
                Text(stringResource(R.string.pro_colors, ReleaseConfig.PRO_MAX_COLORS))
                Text(stringResource(R.string.pro_export))
                Text(stringResource(R.string.pro_projects))
                Text(stringResource(R.string.pro_progress))
            }
        }
        if (!isPro) Button(onClick = onBuy, Modifier.fillMaxWidth()) { Text(stringResource(R.string.get_pro)) }
        OutlinedButton(onClick = onRestore, Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_purchase)) }
        Text(stringResource(R.string.pro_purchase_info), style = MaterialTheme.typography.bodySmall)

        val context = LocalContext.current
        val activity = context as? Activity
        var showLanguageDialog by remember { mutableStateOf(false) }
        val languageCode = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString(APP_LANGUAGE_PREF, "system") ?: "system"
        val languageName = when (languageCode) {
            "ru" -> stringResource(R.string.language_russian)
            "uk" -> stringResource(R.string.language_ukrainian)
            "en" -> stringResource(R.string.language_english)
            else -> stringResource(R.string.language_system)
        }
        OutlinedButton(
            onClick = { showLanguageDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.app_language_value, languageName)) }

        if (showLanguageDialog) {
            val options = listOf(
                "system" to stringResource(R.string.language_system),
                "ru" to stringResource(R.string.language_russian),
                "uk" to stringResource(R.string.language_ukrainian),
                "en" to stringResource(R.string.language_english)
            )
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.choose_language)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        options.forEach { (code, label) ->
                            TextButton(
                                onClick = {
                                    context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                                        .edit().putString(APP_LANGUAGE_PREF, code).apply()
                                    showLanguageDialog = false
                                    activity?.recreate()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (code == languageCode) "✓ $label" else label)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.about), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.support, ReleaseConfig.SUPPORT_EMAIL), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseConfig.PRIVACY_POLICY_URL))) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.privacy_policy)) }
    }
}
