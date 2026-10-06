package com.kiite.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/** 连接 PlaybackService 的 MediaController。 */
class PlayerConnection(private val context: Context) {

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    suspend fun connect(): MediaController {
        _controller.value?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                {
                    try {
                        val controller = future.get()
                        _controller.value = controller
                        cont.resume(controller)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
    }

    fun release() {
        _controller.value?.release()
        _controller.value = null
    }
}
