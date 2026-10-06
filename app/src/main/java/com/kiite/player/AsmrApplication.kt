package com.kiite.player

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AsmrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // pdfbox-android 需要先加载资源（字体、CMap 等）才能抽取 PDF 文本
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
    }
}
