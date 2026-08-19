package com.stitchcraft.app
import android.content.Context
class AppPreferences(context:Context) {
    private val p=context.getSharedPreferences("stitchcraft_settings",Context.MODE_PRIVATE)
    var showSymbols:Boolean get()=p.getBoolean("show_symbols",true) set(v){p.edit().putBoolean("show_symbols",v).apply()}
    var showColors:Boolean get()=p.getBoolean("show_colors",true) set(v){p.edit().putBoolean("show_colors",v).apply()}
    var cleanNoise:Boolean get()=p.getBoolean("clean_noise",true) set(v){p.edit().putBoolean("clean_noise",v).apply()}
    var keepAspectRatio:Boolean get()=p.getBoolean("keep_aspect_ratio",true) set(v){p.edit().putBoolean("keep_aspect_ratio",v).apply()}
    var lastWidth:Int get()=p.getInt("last_width",100) set(v){p.edit().putInt("last_width",v.coerceIn(20,300)).apply()}
    var lastHeight:Int get()=p.getInt("last_height",100) set(v){p.edit().putInt("last_height",v.coerceIn(20,300)).apply()}
    var lastColors:Int get()=p.getInt("last_colors",32) set(v){p.edit().putInt("last_colors",v.coerceIn(2,200)).apply()}
}
