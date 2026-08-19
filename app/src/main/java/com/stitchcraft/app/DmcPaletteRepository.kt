package com.stitchcraft.app
import android.graphics.Color
object DmcPaletteRepository {
    val starter = listOf(
        ThreadColor("B5200","Snow White",Color.rgb(255,255,255)),
        ThreadColor("White","White",Color.rgb(252,251,248)),
        ThreadColor("310","Black",Color.rgb(0,0,0)),
        ThreadColor("318","Steel Gray - Light",Color.rgb(171,171,171)),
        ThreadColor("414","Steel Gray - Dark",Color.rgb(140,140,140)),
        ThreadColor("415","Pearl Gray",Color.rgb(211,211,214)),
        ThreadColor("666","Christmas Red - Bright",Color.rgb(227,29,66)),
        ThreadColor("699","Green",Color.rgb(5,101,23)),
        ThreadColor("742","Tangerine - Light",Color.rgb(255,191,87)),
        ThreadColor("758","Terra Cotta - Very Light",Color.rgb(238,170,155)),
        ThreadColor("779","Cocoa - Dark",Color.rgb(98,75,69)),
        ThreadColor("796","Royal Blue - Dark",Color.rgb(17,65,109)),
        ThreadColor("820","Royal Blue - Very Dark",Color.rgb(14,54,92)),
        ThreadColor("838","Beige Brown - Very Dark",Color.rgb(89,73,55)),
        ThreadColor("930","Antique Blue - Dark",Color.rgb(69,92,113)),
        ThreadColor("995","Electric Blue - Dark",Color.rgb(38,150,182)),
        ThreadColor("3041","Antique Violet - Medium",Color.rgb(149,111,124)),
        ThreadColor("3064","Desert Sand",Color.rgb(196,142,112)),
        ThreadColor("3770","Tawny - Very Light",Color.rgb(255,238,227)),
        ThreadColor("3857","Rosewood - Dark",Color.rgb(104,37,26))
    )
    fun nearest(rgb:Int):ThreadColor {
        val r=Color.red(rgb); val g=Color.green(rgb); val b=Color.blue(rgb)
        return starter.minByOrNull {
            val dr=r-Color.red(it.rgb); val dg=g-Color.green(it.rgb); val db=b-Color.blue(it.rgb)
            dr*dr+dg*dg+db*db
        } ?: starter.first()
    }
}
