package com.stitchcraft.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedProject(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val colors: Int,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
val progress: Int = 0,
val fabricCount: Int = 18

    
)

class ProjectStore(private val context: Context) {
    private val indexFile get() = File(context.filesDir, "projects.json")
    private val patternsDir get() = File(context.filesDir, "patterns").apply { mkdirs() }

    fun list(): List<SavedProject> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val created = o.optLong("createdAt", System.currentTimeMillis())
                SavedProject(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    width = o.getInt("width"),
                    height = o.getInt("height"),
                    colors = o.getInt("colors"),
                    createdAt = created,
                    updatedAt = o.optLong("updatedAt", created),
                    progress = o.optInt("progress", 0),
fabricCount = o.optInt("fabricCount", 18)
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun save(name: String, pattern: StitchPattern, existingId: String? = null, fabricCount: Int = 18): SavedProject {
        val now = System.currentTimeMillis()
        val previous = existingId?.let { id -> list().firstOrNull { it.id == id } }
        val id = previous?.id ?: "p_$now"
        val project = SavedProject(
            id = id,
            name = name,
            width = pattern.width,
            height = pattern.height,
            colors = pattern.palette.size,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            progress = pattern.progressPercent(),
fabricCount = fabricCount
        )

        val data = JSONObject()
            .put("width", pattern.width)
            .put("height", pattern.height)
            .put("palette", JSONArray(pattern.palette.map { it.code }))
            .put("cells", JSONArray(pattern.cells.map { it.colorIndex }))
            .put("completed", JSONArray(pattern.cells.map { it.completed }))
            .put("erased", JSONArray(pattern.cells.map { it.erased }))
        File(patternsDir, "$id.json").writeText(data.toString())

        val all = (list().filterNot { it.id == id } + project)
            .sortedByDescending { it.updatedAt }
            .take(50)
        writeIndex(all)
        return project
    }

    fun load(project: SavedProject): StitchPattern? = runCatching {
        val o = JSONObject(File(patternsDir, "${project.id}.json").readText())
        val paletteCodes = o.getJSONArray("palette")
        val palette = (0 until paletteCodes.length()).map { i ->
            val code = paletteCodes.getString(i)
            ThreadPalette.dmcApprox.firstOrNull { it.code == code } ?: DmcPaletteRepository.starter.firstOrNull { it.code == code } ?: ThreadPalette.dmcApprox.first()
        }
        val arr = o.getJSONArray("cells")
        val completed = o.optJSONArray("completed")
        val erased = o.optJSONArray("erased")
        val cells = (0 until arr.length()).map { i ->
            val idx = arr.getInt(i).coerceIn(0, palette.lastIndex.coerceAtLeast(0))
            PatternCell(
                colorIndex = idx,
                symbol = PatternEngine.symbolForIndex(idx),
                completed = completed?.optBoolean(i, false) ?: false,
                erased = erased?.optBoolean(i, false) ?: false
            )
        }
        StitchPattern(o.getInt("width"), o.getInt("height"), palette, cells)
    }.getOrNull()


    fun exportProject(project: SavedProject): ByteArray? = runCatching {
        val patternFile = File(patternsDir, "${project.id}.json")
        require(patternFile.exists())
        val root = JSONObject()
            .put("format", "stitchcraft-project")
            .put("formatVersion", 1)
            .put("name", project.name)
            .put("fabricCount", project.fabricCount)
            .put("pattern", JSONObject(patternFile.readText()))
        root.toString().toByteArray(Charsets.UTF_8)
    }.getOrNull()

    fun importProject(bytes: ByteArray): SavedProject? = runCatching {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("format") == "stitchcraft-project")
        require(root.optInt("formatVersion", 0) == 1)
        val data = root.getJSONObject("pattern")
        val width = data.getInt("width")
        val height = data.getInt("height")
        val palette = data.getJSONArray("palette")
        val cells = data.getJSONArray("cells")
        require(width in 1..ReleaseConfig.PRO_MAX_WIDTH && height in 1..ReleaseConfig.PRO_MAX_HEIGHT)
        require(palette.length() in 1..ReleaseConfig.PRO_MAX_COLORS)
        require(cells.length() == width * height)
        for (i in 0 until cells.length()) {
            require(cells.getInt(i) in 0 until palette.length())
        }
        data.optJSONArray("completed")?.let { require(it.length() == cells.length()) }
        data.optJSONArray("erased")?.let { require(it.length() == cells.length()) }

        val now = System.currentTimeMillis()
        val id = "p_${now}_${(1000..9999).random()}"
        val name = root.optString("name", "Импортированный проект").trim().ifBlank { "Импортированный проект" }.take(60)
        val fabric = root.optInt("fabricCount", 14).coerceIn(6, 40)
        File(patternsDir, "$id.json").writeText(data.toString())

        val completed = data.optJSONArray("completed")
        val erased = data.optJSONArray("erased")
        var total = 0
        var done = 0
        for (i in 0 until cells.length()) {
            val isErased = erased?.optBoolean(i, false) ?: false
            if (!isErased) {
                total++
                if (completed?.optBoolean(i, false) == true) done++
            }
        }
        val progress = if (total == 0) 0 else kotlin.math.round(done * 100.0 / total).toInt()
        val project = SavedProject(id, name, width, height, palette.length(), now, now, progress, fabric)
        writeIndex((list() + project).sortedByDescending { it.updatedAt }.take(50))
        project
    }.getOrNull()

    fun rename(project: SavedProject, newName: String): SavedProject? {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return null
        val all = list()
        val current = all.firstOrNull { it.id == project.id } ?: return null
        val renamed = current.copy(name = cleanName, updatedAt = System.currentTimeMillis())
        writeIndex((all.filterNot { it.id == project.id } + renamed).sortedByDescending { it.updatedAt })
        return renamed
    }

    fun delete(project: SavedProject) {
        File(patternsDir, "${project.id}.json").delete()
        writeIndex(list().filterNot { it.id == project.id })
    }

    private fun writeIndex(all: List<SavedProject>) {
        val arr = JSONArray()
        all.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("width", p.width)
                    .put("height", p.height)
                    .put("colors", p.colors)
                    .put("createdAt", p.createdAt)
                    .put("updatedAt", p.updatedAt)
                    .put("progress", p.progress)
                    .put("fabricCount", p.fabricCount)
            )
        }
        indexFile.writeText(arr.toString())
    }
}
