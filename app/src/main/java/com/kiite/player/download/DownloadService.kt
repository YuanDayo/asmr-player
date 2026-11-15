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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 下载前台服务：把 asmr.one 下载挂成前台任务，通知栏显示进度、可取消。
 *
 * 作用有三：
 * 1. 通知栏进度（百分比 / 第几个文件 / 速度 / 预计剩余），锁屏和解锁后都能看；
 * 2. 进程优先级——切到后台、锁屏都不会被系统回收，下载继续；
 * 3. 下载完成/失败/取消时把通知切成结果态，点一下直接回到应用。
 *
 * 真正的下载逻辑在 [AsmrOneController]（应用级作用域），本服务只做「通知 + 保活」，
 * 所以界面销毁、Activity 重启都不影响下载。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: AsmrOneController

    override fun onCreate() {
        super.onCreate()
        createChannel()
        controller = AsmrOneDownloads.get(application)
        // 必须 5 秒内 startForeground，先用「准备中」占位
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification(null),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        }
        scope.launch {
            controller.state.collect { st ->
                if (st == null) {
                    // 没有下载在跑（例如下载在服务起来前就结束了）：别留个空占位通知
                    runCatching { NotificationManagerCompat.from(this@DownloadService).cancel(NOTIF_ID) }
                    stopSelfSafely()
                    return@collect
                }
                notify(buildNotification(st))
                if (!st.running) {
                    // 结果通知留在通知栏，但不再占据前台
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            controller.cancel()
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
            description = "asmr.one 作品下载进度"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        mgr.createNotificationChannel(ch)
    }

    private fun buildNotification(st: AsmrOneController.DownloadState?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getBroadcast(
            this,
            1,
            // 显式指定接收器：Android 8+ 对清单注册的隐式广播有拦截，显式最稳
            Intent(this, DownloadCancelReceiver::class.java).setAction(ACTION_CANCEL),
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

        if (st == null) {
            return b.setContentTitle("asmr.one 下载")
                .setContentText("正在连接…")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .build()
        }

        val title = st.title.ifBlank { "asmr.one 下载" }
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

        b.setContentTitle(title)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$line\n$detail"))
            .setOngoing(st.running)
            .setAutoCancel(!st.running)
            .setSubText(if (st.running && st.fileName.isNotBlank()) detail else null)

        if (st.running) {
            b.addAction(0, "取消", cancel)
            if (st.total > 0) {
                b.setProgress(100, st.percent, false)
            } else {
                b.setProgress(0, 0, true)
            }
        } else {
            b.setProgress(0, 0, false)
        }
        return b.build()
    }

    companion object {
        private const val CHANNEL_ID = "download_progress"
        private const val NOTIF_ID = 4101
        const val ACTION_CANCEL = "com.kiite.player.download.CANCEL"

        /** 开始下载：把服务拉成前台（通知栏立刻出现进度）。 */
        fun start(ctx: Context) {
            val i = Intent(ctx, DownloadService::class.java)
            runCatching { androidx.core.content.ContextCompat.startForegroundService(ctx, i) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, DownloadService::class.java)) }
        }
    }
}

/** 通知栏「取消」按钮：直接叫停应用级下载控制器，不需要起服务。 */
class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadService.ACTION_CANCEL) return
        val app = context.applicationContext as android.app.Application
        AsmrOneDownloads.get(app).cancel()
    }
}
