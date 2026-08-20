package com.stitchcraft.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object ExportManager {
    fun exportPdf(
        context: Context,
        pattern: StitchPattern,
        projectName: String,
        fabricCount: Int
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "${sanitize(projectName)}.pdf")
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val margin = 32f
        val pageWidth = 595
        val pageHeight = 842
        val cellsPerPage = 40
        val pagesX = (pattern.width + cellsPerPage - 1) / cellsPerPage
        val pagesY = (pattern.height + cellsPerPage - 1) / cellsPerPage
        val chartPages = pagesX * pagesY
        val legendRowsPerPage = 28
        val legendPages = (pattern.palette.size + legendRowsPerPage - 1) / legendRowsPerPage
        val totalPages = chartPages + legendPages
        val finishedWidthCm = pattern.width.toFloat() / fabricCount * 2.54f
        val finishedHeightCm = pattern.height.toFloat() / fabricCount * 2.54f
        var pageNo = 1

        for (py in 0 until pagesY) for (px in 0 until pagesX) {
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create()
            val page = pdf.startPage(info)
            val canvas = page.canvas
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            paint.textSize = 12f
            canvas.drawText("StitchCraft — $projectName — $pageNo/$totalPages", margin, 24f, paint)

            val startX = px * cellsPerPage
            val startY = py * cellsPerPage
            val endX = minOf(startX + cellsPerPage, pattern.width)
            val endY = minOf(startY + cellsPerPage, pattern.height)
            paint.textSize = 9f
            canvas.drawText("Cells X: ${startX + 1}–$endX, Y: ${startY + 1}–$endY", margin, 42f, paint)
            paint.textSize = 10f
            canvas.drawText("Aida $fabricCount • %.1f × %.1f cm".format(finishedWidthCm, finishedHeightCm), margin, 56f, paint)

            val cell = minOf((pageWidth - margin * 2) / (endX - startX), 600f / (endY - startY))
            val top = 70f
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            paint.textSize = 7f
            for (x in startX until endX) if ((x + 1) % 10 == 0) {
                canvas.drawText("${x + 1}", margin + (x - startX) * cell + 1f, top - 3f, paint)
            }
            for (y in startY until endY) if ((y + 1) % 10 == 0) {
                canvas.drawText("${y + 1}", 14f, top + (y - startY) * cell + cell * .72f, paint)
            }

            val usedOnPage = linkedSetOf<Int>()
            for (y in startY until endY) for (x in startX until endX) {
                val left = margin + (x - startX) * cell
                val t = top + (y - startY) * cell
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (x % 10 == 0 || y % 10 == 0) 1.2f else .5f
                paint.color = if (x % 10 == 0 || y % 10 == 0) Color.DKGRAY else Color.LTGRAY
                canvas.drawRect(left, t, left + cell, t + cell, paint)
                val pc = pattern.cell(x, y)
                if (!pc.erased) {
                    usedOnPage += pc.colorIndex
                    paint.style = Paint.Style.FILL
                    paint.color = Color.BLACK
                    paint.textSize = (cell * .55f).coerceAtLeast(5f)
                    canvas.drawText(pc.symbol, left + cell * .18f, t + cell * .72f, paint)
                }
            }

            val legendTop = 690f
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            paint.textSize = 7f
            usedOnPage.take(20).forEachIndexed { line, i ->
                val c = pattern.palette[i]
                val col = line / 5
                val row = line % 5
                canvas.drawText("${PatternEngine.symbolForIndex(i)} ${c.code}", margin + col * 130f, legendTop + row * 18f, paint)
            }
            if (usedOnPage.size > 20) {
                canvas.drawText("+ ${usedOnPage.size - 20} цветов. Полная легенда — после схемы.", margin, 792f, paint)
            } else {
                canvas.drawText("Полная легенда — после схемы.", margin, 792f, paint)
            }

            pdf.finishPage(page)
            pageNo++
        }

        val counts = pattern.counts()
        pattern.palette.chunked(legendRowsPerPage).forEachIndexed { chunkIndex, chunk ->
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create()
            val page = pdf.startPage(info)
            val canvas = page.canvas
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            paint.textSize = 14f
            canvas.drawText("StitchCraft — полная легенда — $pageNo/$totalPages", margin, 30f, paint)
            paint.textSize = 10f
            canvas.drawText("$projectName • Aida $fabricCount • ${pattern.width}×${pattern.height}", margin, 50f, paint)

            chunk.forEachIndexed { localIndex, thread ->
                val i = chunkIndex * legendRowsPerPage + localIndex
                val y = 78f + localIndex * 25f
                paint.color = thread.rgb
                canvas.drawRect(margin, y - 12f, margin + 14f, y + 2f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = .7f
                paint.color = Color.DKGRAY
                canvas.drawRect(margin, y - 12f, margin + 14f, y + 2f, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.BLACK
                paint.textSize = 9f
                canvas.drawText(
                    "${PatternEngine.symbolForIndex(i)}   ${thread.code}   ${thread.name}   — ${counts[i] ?: 0}",
                    margin + 24f,
                    y,
                    paint
                )
            }
            pdf.finishPage(page)
            pageNo++
        }

        FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }

    fun exportCsv(context: Context, pattern: StitchPattern, projectName: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "${sanitize(projectName)}_legend.csv")
        val counts = pattern.counts()
        out.bufferedWriter().use { w ->
            w.appendLine("symbol,thread_code,name,crosses")
            pattern.palette.forEachIndexed { i, c ->
                val escapedName = c.name.replace("\"", "\"\"")
                w.appendLine("${PatternEngine.symbolForIndex(i)},${c.code},\"$escapedName\",${counts[i] ?: 0}")
            }
        }
        return out
    }

    fun exportPng(context: Context, pattern: StitchPattern, projectName: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "${sanitize(projectName)}.png")
        // Keep large Pro patterns below a practical bitmap-memory ceiling on phones.
        val maxSidePx = 3072
        val cell = minOf(18, (maxSidePx - 1) / maxOf(pattern.width, pattern.height)).coerceAtLeast(4)
        val bitmap = Bitmap.createBitmap(pattern.width * cell + 1, pattern.height * cell + 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in 0 until pattern.height) for (x in 0 until pattern.width) {
            val left = (x * cell).toFloat()
            val top = (y * cell).toFloat()
            val pc = pattern.cell(x, y)
            paint.style = Paint.Style.FILL
            paint.color = if (pc.erased) Color.WHITE else pattern.palette[pc.colorIndex].rgb
            canvas.drawRect(left, top, left + cell, top + cell, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (x % 10 == 0 || y % 10 == 0) 2f else .5f
            paint.color = Color.DKGRAY
            canvas.drawRect(left, top, left + cell, top + cell, paint)
            if (!pc.erased && cell >= 7) {
                paint.style = Paint.Style.FILL
                paint.color = Color.BLACK
                paint.textSize = cell * .52f
                canvas.drawText(pc.symbol, left + cell * .22f, top + cell * .72f, paint)
            }
        }
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }

    private fun sanitize(s: String) = s.replace(Regex("[^A-Za-z0-9А-Яа-я_-]+"), "_").take(60).ifBlank { "pattern" }
}
