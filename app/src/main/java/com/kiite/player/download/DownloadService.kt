package com.kiite.player.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.kiite.player.MainActivity
import com.kiite.player.R
import com.kiite.player.domain.AsmrOneController
import com.kiite.player.domain.AsmrOneDownloads
import com.kiite.player.domain.DlsiteDownloader
import com.kiite.player.domain.DlsiteDownloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 下载前台服务：把**应用内下载**（asmr.one 整部作品 / DLsite 单个压缩包）挂成前台任务，
 * 通知栏显示进度、可取消。
 *
 * 作用有三：
 * 1. 通知栏进度（百分比 / 文件 / 速度 / 预计剩余），锁屏和解锁后都能看；
 * 2. 进程优先级——切到后台、锁屏都不会被系统回收，下载继续；
 * 3. 下载完成/失败/取消时把通知切成结果态，点一下直接回到应用。
 *
 * 真正的下载逻辑在两个应用级控制器里（[AsmrOneController]、[DlsiteDownloader]），
 * 本服务只做「通知 + 保活」，所以界面销毁、Activity 重启都不影响下载。
 * 两个下载可能同时进行：通知里优先显示先跑起来的那个，用两个「取消」动作分别停。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var asmr: AsmrOneController
    private lateinit var dlsite: DlsiteDownloader

    override fun onCreate() {
        super.onCreate()
        createChannel()
        asmr = AsmrOneDownloads.get(application)
        dlsite = DlsiteDownloads.get(application)
        // 必须 5 秒内 startForeground，先用「准备中」占位
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification(null, null),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        }
        scope.launch {
            combine(asmr.state, dlsite.state) { a, d -> a to d }.collect { (a, d) ->
                if (a == null && d == null) {
                    // 没有下载在跑（例如下载在服务起来前就结束了）：别留个空占位通知
                    runCatching { NotificationManagerCompat.from(this@DownloadService).cancel(NOTIF_ID) }
                    stopSelfSafely()
                    return@collect
                }
                notify(buildNotification(a, d))
                if (a?.running != true && d?.running != true) {
                    // 结果通知留在通知栏，但不再占据前台
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            when (intent.getStringExtra(EXTRA_KIND)) {
                KIND_DLSITE -> dlsite.cancel()
                else -> asmr.cancel()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(n: Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, n) }
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "下载",
            NotificationManager.IMPORTANCE_LOW, // 低优先级：不响铃、不弹横幅，只安静显示进度
        ).apply {
            description = "应用内下载进度（asmr.one / DLsite）"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        mgr.createNotificationChannel(ch)
    }

    /** 把两种下载状态摊平成同一个通知模型，通知文案只在这里写一次。 */
    private data class Model(
        val kind: String,
        val title: String,
        val line: String,
        val detail: String,
        val subText: String?,
        /** null = 不知道总长，画无限进度。 */
        val percent: Int?,
        val running: Boolean,
    )

    private fun asmrModel(st: AsmrOneController.DownloadState): Model {
        val line = buildString {
            when {
                st.completed -> append("下载完成")
                st.cancelled -> append("已暂停")
                st.failed -> append("下载失败")
                st.total > 0 -> append("正在下载 ").append(st.countText)
                else -> append(st.status)
            }
            if (st.running) {
                if (st.total > 0) append(" · ").append(st.percent).append("%")
                st.speedText.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                st.etaText.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            } else if (st.okCount > 0) {
                append(" · 共 ").append(st.okCount).append(" 个文件")
            }
        }
        val detail = when {
            !st.running -> st.status
            st.fileName.isNotBlank() -> st.fileName
            else -> st.status
        }
        return Model(
            kind = KIND_ASMR,
            title = st.title.ifBlank { "asmr.one 下载" },
            line = line,
            detail = detail,
            subText = if (st.running && st.fileName.isNotBlank()) detail else null,
            percent = when {
                !st.running -> null
                st.total > 0 -> st.percent
                else -> null
            },
            running = st.running,
        )
    }

    private fun dlsiteModel(st: DlsiteDownloader.State): Model = Model(
        kind = KIND_DLSITE,
        title = "DLsite 下载" + (st.code?.let { " · " + it } ?: ""),
        line = when {
            st.running -> "正在下载 · " + st.progressText
            else -> st.status
        },
        detail = st.fileName,
        subText = if (st.running) st.fileName.ifBlank { null } else null,
        percent = if (st.running && st.hasTotal) st.percent else null,
        running = st.running,
    )

    private fun buildNotification(
        a: AsmrOneController.DownloadState?,
        d: DlsiteDownloader.State?,
    ): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val models = listOfNotNull(a?.let { asmrModel(it) }, d?.let { dlsiteModel(it) })
        if (models.isEmpty()) {
            return b.setContentTitle("下载")
                .setContentText("正在连接…")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .build()
        }

        val running = models.filter { it.running }
        // 正在跑的那个优先；都跑完了就显示最后结束的那个
        val primary = running.firstOrNull() ?: models.last()
        val extra = if (running.size > 1) {
            "（另有 " + (running.size - 1) + " 个下载在进行）"
        } else {
            ""
        }

        b.setContentTitle(primary.title)
            .setContentText(primary.line + extra)
            .setStyle(NotificationCompat.BigTextStyle().bigText(primary.line + extra + "\n" + primary.detail))
            .setOngoing(primary.running)
            .setAutoCancel(!primary.running)
            .setSubText(primary.subText)

        if (running.isNotEmpty()) {
            // 两个同时跑时按钮要能区分，否则两个「取消」不知道该停哪个
            running.forEach { m ->
                val label = if (running.size > 1) {
                    if (m.kind == KIND_DLSITE) "取消 DLsite" else "取消 asmr.one"
                } else {
                    "取消"
                }
                b.addAction(0, label, cancelIntent(m.kind))
            }
            if (primary.percent != null) {
                b.setProgress(100, primary.percent!!, false)
            } else {
                b.setProgress(0, 0, true)
            }
        } else {
            b.setProgress(0, 0, false)
        }
        return b.build()
    }

    private fun cancelIntent(kind: String): PendingIntent = PendingIntent.getBroadcast(
        this,
        if (kind == KIND_DLSITE) 2 else 1,
        // 显式指定接收器：Android 8+ 对清单注册的隐式广播有拦截，显式最稳
        Intent(this, DownloadCancelReceiver::class.java)
            .setAction(ACTION_CANCEL)
            .putExtra(EXTRA_KIND, kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "download_progress"
        private const val NOTIF_ID = 4101
        const val ACTION_CANCEL = "com.kiite.player.download.CANCEL"
        const val EXTRA_KIND = "com.kiite.player.download.KIND"
        const val KIND_ASMR = "asmr"
        const val KIND_DLSITE = "dlsite"

        /** 开始下载：把服务拉成前台（通知栏立刻出现进度）。两种下载共用这个服务。 */
        fun start(ctx: Context) {
            val i = Intent(ctx, DownloadService::class.java)
            runCatching { androidx.core.content.ContextCompat.startForegroundService(ctx, i) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, DownloadService::class.java)) }
        }
    }
}

/** 通知栏「取消」按钮：直接叫停对应的应用级下载控制器，不需要起界面。 */
class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadService.ACTION_CANCEL) return
        val app = context.applicationContext as android.app.Application
        when (intent.getStringExtra(DownloadService.EXTRA_KIND)) {
            DownloadService.KIND_DLSITE -> DlsiteDownloads.get(app).cancel()
            else -> AsmrOneDownloads.get(app).cancel()
        }
    }
}
