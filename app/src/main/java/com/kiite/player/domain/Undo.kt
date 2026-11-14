package com.kiite.player.domain

/** 一条可撤销操作的凭据：AppRoot 底部弹出 SnackBar，点「撤销」执行 [undo]。 */
data class UndoRequest(val text: String, val undo: () -> Unit)
