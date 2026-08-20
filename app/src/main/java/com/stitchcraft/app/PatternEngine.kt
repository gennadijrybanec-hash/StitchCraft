package com.stitchcraft.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

data class ThreadColor(val code: String, val name: String, val rgb: Int)
data class PatternCell(
    val colorIndex: Int,
    val symbol: String,
    val completed: Boolean = false,
    val erased: Boolean = false
)

data class StitchPattern(
    val width: Int,
    val height: Int,
    val palette: List<ThreadColor>,
    val cells: List<PatternCell>
) {
    fun cell(x: Int, y: Int) = cells[y * width + x]
    fun counts(): Map<Int, Int> = cells.filterNot { it.erased }.groupingBy { it.colorIndex }.eachCount()
    fun stitchCount(): Int = cells.count { !it.erased }
    fun completedCount(): Int = cells.count { !it.erased && it.completed }
    fun progressPercent(): Int {
        val total = stitchCount()
        return if (total == 0) 0 else ((completedCount() * 100.0) / total).roundToInt()
    }

    fun updateCell(x: Int, y: Int, transform: (PatternCell) -> PatternCell): StitchPattern {
        if (x !in 0 until width || y !in 0 until height) return this
        val index = y * width + x
        val updated = cells.toMutableList()
        updated[index] = transform(updated[index])
        return copy(cells = updated)
    }
}

data class PatternOptions(
    val cleanupIsolatedStitches: Boolean = true
)

object ThreadPalette {
    // Approximate screen RGB values. A store release should keep a documented source/version
    // for the physical-thread reference chart and should not claim spectrophotometric accuracy.
    val dmcApprox = listOf(
        ThreadColor("B5200", "Snow White", Color.rgb(255,255,255)),
        ThreadColor("310", "Black", Color.rgb(0,0,0)),
        ThreadColor("321", "Red", Color.rgb(199,43,59)),
        ThreadColor("666", "Bright Red", Color.rgb(227,29,66)),
        ThreadColor("498", "Dark Red", Color.rgb(167,19,43)),
        ThreadColor("742", "Tangerine Light", Color.rgb(255,191,87)),
        ThreadColor("740", "Tangerine", Color.rgb(255,131,19)),
        ThreadColor("444", "Lemon Dark", Color.rgb(255,214,0)),
        ThreadColor("307", "Lemon", Color.rgb(253,237,84)),
        ThreadColor("700", "Christmas Green", Color.rgb(7,115,27)),
        ThreadColor("702", "Kelly Green", Color.rgb(71,167,47)),
        ThreadColor("704", "Chartreuse Bright", Color.rgb(158,207,52)),
        ThreadColor("3846", "Turquoise Bright", Color.rgb(6,227,230)),
        ThreadColor("995", "Electric Blue Dark", Color.rgb(38,150,182)),
        ThreadColor("996", "Electric Blue Medium", Color.rgb(48,194,236)),
        ThreadColor("820", "Royal Blue Very Dark", Color.rgb(14,54,92)),
        ThreadColor("797", "Royal Blue", Color.rgb(19,71,125)),
        ThreadColor("798", "Delft Blue Dark", Color.rgb(70,106,142)),
        ThreadColor("799", "Delft Blue Medium", Color.rgb(116,142,182)),
        ThreadColor("208", "Lavender Very Dark", Color.rgb(131,91,139)),
        ThreadColor("209", "Lavender Dark", Color.rgb(163,123,167)),
        ThreadColor("210", "Lavender Medium", Color.rgb(195,159,195)),
        ThreadColor("211", "Lavender Light", Color.rgb(227,203,227)),
        ThreadColor("550", "Violet Very Dark", Color.rgb(92,24,78)),
        ThreadColor("552", "Violet Medium", Color.rgb(128,58,107)),
        ThreadColor("554", "Violet Light", Color.rgb(219,179,203)),
        ThreadColor("600", "Cranberry Very Dark", Color.rgb(205,47,99)),
        ThreadColor("602", "Cranberry Medium", Color.rgb(226,72,116)),
        ThreadColor("604", "Cranberry Light", Color.rgb(234,107,146)),
        ThreadColor("818", "Baby Pink", Color.rgb(255,223,217)),
        ThreadColor("938", "Coffee Brown Ultra Dark", Color.rgb(54,31,14)),
        ThreadColor("801", "Coffee Brown Dark", Color.rgb(101,57,25)),
        ThreadColor("434", "Brown Light", Color.rgb(152,94,51)),
        ThreadColor("436", "Tan", Color.rgb(203,144,81)),
        ThreadColor("437", "Tan Light", Color.rgb(228,187,142)),
        ThreadColor("3865", "Winter White", Color.rgb(249,247,241)),
        ThreadColor("762", "Pearl Gray Very Light", Color.rgb(236,236,236)),
        ThreadColor("415", "Pearl Gray", Color.rgb(211,211,214)),
        ThreadColor("318", "Steel Gray Light", Color.rgb(171,171,171)),
        ThreadColor("414", "Steel Gray Dark", Color.rgb(140,140,140)),
        ThreadColor("3799", "Pewter Gray Very Dark", Color.rgb(66,66,66)),
        ThreadColor("934", "Avocado Green Black", Color.rgb(49,57,25)),
        ThreadColor("936", "Avocado Green Very Dark", Color.rgb(76,88,38)),
        ThreadColor("937", "Avocado Green Medium", Color.rgb(98,113,51)),
        ThreadColor("3345", "Hunter Green Dark", Color.rgb(27,89,21)),
        ThreadColor("3347", "Yellow Green Medium", Color.rgb(113,147,92)),
        ThreadColor("3348", "Yellow Green Light", Color.rgb(204,217,177)),
        ThreadColor("3852", "Straw Very Dark", Color.rgb(205,157,55)),
        ThreadColor("3854", "Autumn Gold Medium", Color.rgb(242,175,104)),
        ThreadColor("3776", "Mahogany Light", Color.rgb(207,121,57)),
        ThreadColor("3777", "Terra Cotta Very Dark", Color.rgb(134,48,34)),
        ThreadColor("758", "Terra Cotta Very Light", Color.rgb(238,170,155)),
        ThreadColor("945", "Tawny", Color.rgb(251,213,187)),
        ThreadColor("948", "Peach Very Light", Color.rgb(254,231,218)),
        ThreadColor("3841", "Baby Blue Pale", Color.rgb(205,223,237)),
        ThreadColor("3755", "Baby Blue", Color.rgb(147,180,206)),
        ThreadColor("3756", "Baby Blue Ultra Very Light", Color.rgb(238,252,252)),
        ThreadColor("932", "Antique Blue Light", Color.rgb(162,181,198)),
        ThreadColor("931", "Antique Blue Medium", Color.rgb(106,133,158)),
        ThreadColor("930", "Antique Blue Dark", Color.rgb(69,92,113)),
        ThreadColor("3768", "Gray Green Dark", Color.rgb(101,127,127)),
        ThreadColor("927", "Gray Green Light", Color.rgb(189,203,203)),
        ThreadColor("928", "Gray Green Very Light", Color.rgb(221,227,227))
    )
}

private data class Lab(val l: Double, val a: Double, val b: Double)

object PatternEngine {
    private val symbols = listOf("●","■","▲","◆","✚","✖","○","□","△","◇","+","×","♠","♥","♣","★","☆","☀","☂","☘","A","B","C","D","E","F","G","H","J","K","L","M","N","P","Q","R","S","T","U","V","W","X","Y","Z","1","2","3","4","5","6","7","8","9","@","#","%","&","?","!","=","~","/")
    private val paletteLabs by lazy { ThreadPalette.dmcApprox.associateWith { rgbToLab(it.rgb) } }

    val symbolForIndex: (Int) -> String = { index -> symbols[index.coerceIn(0, symbols.lastIndex)] }

    fun generate(
        bitmap: Bitmap,
        targetWidth: Int,
        maxColors: Int,
        options: PatternOptions = PatternOptions()
    ): StitchPattern {
        require(targetWidth in 10..300)
        require(maxColors in 2..symbols.size)
        val aspect = bitmap.height.toDouble() / bitmap.width.toDouble()
        val targetHeight = (targetWidth * aspect).roundToInt().coerceIn(10, 300)
        // Downsample in two stages. Rendering first to 2× and then averaging 2×2 pixels
        // suppresses single-pixel photo noise while keeping edges noticeably cleaner than a
        // direct resize to the stitch grid.
        val pixels = downsampleForStitches(bitmap, targetWidth, targetHeight)

        val candidatePalette = selectPalette(pixels, maxColors)
        val labs = candidatePalette.map { paletteLabs.getValue(it) }
        var indices = IntArray(pixels.size) { i -> nearestLab(rgbToLab(pixels[i]), labs) }
        if (options.cleanupIsolatedStitches) {
            // Two conservative passes remove isolated speckles without aggressively blurring
            // boundaries. The second pass only sees changes accepted by the first pass.
            repeat(2) {
                indices = cleanupIsolated(indices, targetWidth, targetHeight, pixels, labs)
            }
        }
        return compactPattern(targetWidth, targetHeight, candidatePalette, indices)
    }


    private fun downsampleForStitches(bitmap: Bitmap, width: Int, height: Int): IntArray {
        val workWidth = (width * 2).coerceAtMost(bitmap.width.coerceAtLeast(width))
        val workHeight = (height * 2).coerceAtMost(bitmap.height.coerceAtLeast(height))
        val work = Bitmap.createScaledBitmap(bitmap, workWidth, workHeight, true)

        // If the source is already close to the requested grid, Android's filtered resize is
        // the safer choice and avoids manufacturing detail that is not present in the source.
        if (workWidth < width * 2 || workHeight < height * 2) {
            val scaled = Bitmap.createScaledBitmap(work, width, height, true)
            return IntArray(width * height).also {
                scaled.getPixels(it, 0, width, 0, 0, width, height)
            }
        }

        val src = IntArray(workWidth * workHeight)
        work.getPixels(src, 0, workWidth, 0, 0, workWidth, workHeight)
        val out = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            var r = 0; var g = 0; var b = 0
            for (dy in 0..1) for (dx in 0..1) {
                val c = src[(y * 2 + dy) * workWidth + (x * 2 + dx)]
                r += Color.red(c); g += Color.green(c); b += Color.blue(c)
            }
            out[y * width + x] = Color.rgb(r / 4, g / 4, b / 4)
        }
        return out
    }

    private fun selectPalette(pixels: IntArray, maxColors: Int): List<ThreadColor> {
        val sampleStep = (pixels.size / 1600).coerceAtLeast(1)
        val samples = pixels.filterIndexed { index, _ -> index % sampleStep == 0 }.map(::rgbToLab)
        val all = ThreadPalette.dmcApprox

        val selected = mutableListOf<ThreadColor>()
        val first = all.minBy { thread ->
            val lab = paletteLabs.getValue(thread)
            samples.sumOf { cieDe2000(it, lab) }
        }
        selected += first

        while (selected.size < maxColors) {
            val selectedLabs = selected.map { paletteLabs.getValue(it) }
            val best = all.asSequence().filter { it !in selected }.maxByOrNull { candidate ->
                val candidateLab = paletteLabs.getValue(candidate)
                samples.sumOf { sample ->
                    val old = selectedLabs.minOf { cieDe2000(sample, it) }
                    val newer = min(old, cieDe2000(sample, candidateLab))
                    old - newer
                }
            } ?: break
            selected += best
        }
        return selected
    }

    private fun cleanupIsolated(
        source: IntArray,
        width: Int,
        height: Int,
        pixels: IntArray,
        paletteLabs: List<Lab>
    ): IntArray {
        val out = source.copyOf()
        for (y in 0 until height) for (x in 0 until width) {
            val pos = y * width + x
            val current = source[pos]
            val neighbors = mutableListOf<Int>()
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until width && ny in 0 until height) neighbors += source[ny * width + nx]
            }
            val sameCount = neighbors.count { it == current }
            if (sameCount >= 2) continue
            val dominant = neighbors.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            if (dominant.key == current) continue

            // A true isolated speck needs a clear local majority. If one matching neighbour
            // exists, demand an even stronger majority so thin intentional details survive.
            val requiredMajority = if (sameCount == 0) 4 else 5
            if (dominant.value < requiredMajority) continue

            // Only simplify when the replacement is still a plausible perceptual match.
            // CIEDE2000 keeps this much safer than comparing raw RGB distances.
            val pxLab = rgbToLab(pixels[pos])
            val oldD = cieDe2000(pxLab, paletteLabs[current])
            val newD = cieDe2000(pxLab, paletteLabs[dominant.key])
            val tolerance = if (sameCount == 0) 4.0 else 2.5
            if (newD <= oldD + tolerance) out[pos] = dominant.key
        }
        return out
    }

    private fun compactPattern(width: Int, height: Int, palette: List<ThreadColor>, indices: IntArray): StitchPattern {
        val used = indices.toSet().sorted()
        val remap = used.withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }
        val compactPalette = used.map { palette[it] }
        val cells = indices.map { old ->
            val i = remap.getValue(old)
            PatternCell(i, symbols[i])
        }
        return StitchPattern(width, height, compactPalette, cells)
    }

    private fun nearestLab(color: Lab, palette: List<Lab>): Int =
        palette.indices.minBy { cieDe2000(color, palette[it]) }

    private fun rgbToLab(rgb: Int): Lab {
        fun linear(v: Int): Double {
            val n = v / 255.0
            return if (n <= 0.04045) n / 12.92 else ((n + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(Color.red(rgb)); val g = linear(Color.green(rgb)); val b = linear(Color.blue(rgb))
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750)
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
        fun f(t: Double) = if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0
        val fx = f(x); val fy = f(y); val fz = f(z)
        return Lab(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    // CIEDE2000 perceptual color difference (Sharma et al.).
    private fun cieDe2000(x: Lab, y: Lab): Double {
        val c1 = hypot(x.a, x.b); val c2 = hypot(y.a, y.b); val cBar = (c1 + c2) / 2.0
        val c7 = cBar.pow(7.0)
        val g = 0.5 * (1.0 - sqrt(c7 / (c7 + 25.0.pow(7.0))))
        val a1p = (1.0 + g) * x.a; val a2p = (1.0 + g) * y.a
        val c1p = hypot(a1p, x.b); val c2p = hypot(a2p, y.b)
        fun hp(a: Double, b: Double): Double {
            var h = Math.toDegrees(atan2(b, a)); if (h < 0) h += 360.0; return h
        }
        val h1p = if (c1p == 0.0) 0.0 else hp(a1p, x.b)
        val h2p = if (c2p == 0.0) 0.0 else hp(a2p, y.b)
        val dLp = y.l - x.l; val dCp = c2p - c1p
        var dhp = h2p - h1p
        if (c1p * c2p == 0.0) dhp = 0.0
        else if (dhp > 180.0) dhp -= 360.0
        else if (dhp < -180.0) dhp += 360.0
        val dHp = 2.0 * sqrt(c1p * c2p) * sin(Math.toRadians(dhp / 2.0))
        val lBar = (x.l + y.l) / 2.0; val cpBar = (c1p + c2p) / 2.0
        val hpBar = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180.0 -> (h1p + h2p) / 2.0
            h1p + h2p < 360.0 -> (h1p + h2p + 360.0) / 2.0
            else -> (h1p + h2p - 360.0) / 2.0
        }
        val t = 1.0 - 0.17 * cos(Math.toRadians(hpBar - 30.0)) + 0.24 * cos(Math.toRadians(2.0 * hpBar)) +
                0.32 * cos(Math.toRadians(3.0 * hpBar + 6.0)) - 0.20 * cos(Math.toRadians(4.0 * hpBar - 63.0))
        val dTheta = 30.0 * exp(-((hpBar - 275.0) / 25.0).pow(2.0))
        val rc = 2.0 * sqrt(cpBar.pow(7.0) / (cpBar.pow(7.0) + 25.0.pow(7.0)))
        val sl = 1.0 + 0.015 * (lBar - 50.0).pow(2.0) / sqrt(20.0 + (lBar - 50.0).pow(2.0))
        val sc = 1.0 + 0.045 * cpBar; val sh = 1.0 + 0.015 * cpBar * t
        val rt = -sin(Math.toRadians(2.0 * dTheta)) * rc
        val l = dLp / sl; val c = dCp / sc; val h = dHp / sh
        return sqrt(l*l + c*c + h*h + rt*c*h)
    }
}
