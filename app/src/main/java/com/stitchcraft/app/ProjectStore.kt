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
    val progress: Int = 0
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
                    progress = o.optInt("progress", 0)
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun save(name: String, pattern: StitchPattern, existingId: String? = null): SavedProject {
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
            progress = pattern.progressPercent()
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
            )
        }
        indexFile.writeText(arr.toString())
    }
}
