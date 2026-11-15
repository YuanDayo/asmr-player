package com.kiite.player

import android.app.Application
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AsmrApplication : Application(), coil.ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // pdfbox-android 需要先加载资源（字体、CMap 等）才能抽取 PDF 文本
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
        // asmr.one 下载跑在应用级作用域，页面销毁/切后台都继续（前台通知见 DownloadService）
        com.kiite.player.domain.AsmrOneDownloads.init(this)
    }

    /**
     * 封面加载全局配置：
     * - 内存缓存 25% 堆：曲库列表快速滑动时封面不重解码；
     * - 磁盘缓存 256MB：封面文件二次进入不再读盘解码；
     * - 内嵌封面另有按 path+size+mtime 键控的文件缓存（LibraryRepository.artworkFile），
     *   两层合起来：列表永远只解码目标尺寸的缩略图，不会直接解大图。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(180)
            .build()
}
