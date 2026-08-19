package com.stitchcraft.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportNaming {
    private fun clean(value: String): String =
        value.trim().replace(Regex("[^A-Za-zА-Яа-я0-9_-]+"), "_").take(64).ifBlank { "pattern" }

    fun baseName(projectName: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "${clean(projectName)}_$stamp"
    }
}
