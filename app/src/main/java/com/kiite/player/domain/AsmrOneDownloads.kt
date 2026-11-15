package com.kiite.player.domain

import android.app.Application
import com.kiite.player.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * asmr.one 下载的**应用级单例**。
 *
 * 之前下载挂在 ViewModel 的 viewModelScope 上，一旦退出曲库页/文件选择页（ViewModel 被清），
 * 下载就被连带取消——这就是「切到后台就断」的根因。现在控制器由 Application 持有、
 * 跑在独立作用域里，页面只是观察 [AsmrOneController.state]，
 * 前台通知由 [com.kiite.player.download.DownloadService] 负责。
 *
 * 曲库根目录从设置里持续同步（下载可能发生在用户改过设置之后）。
 */
object AsmrOneDownloads {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var controllerRef: AsmrOneController? = null

    @Volatile
    private var root: String? = null

    /** 由 [com.kiite.player.AsmrApplication] 在进程启动时调用。 */
    fun init(app: Application) {
        if (controllerRef != null) return
        synchronized(this) {
            if (controllerRef != null) return
            scope.launch {
                SettingsStore(app).settings.collect { root = it.rootPath }
            }
            controllerRef = AsmrOneController(
                app = app,
                scope = scope,
                rootPath = { root },
            )
        }
    }

    fun get(app: Application): AsmrOneController {
        init(app)
        return controllerRef!!
    }
}
