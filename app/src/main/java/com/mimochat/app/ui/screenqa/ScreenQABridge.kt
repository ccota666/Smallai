package com.mimochat.app.ui.screenqa

/** 手动模式：悬浮球截屏后把图片路径带给聊天界面 */
object ScreenQABridge {
    @Volatile
    var pendingImagePath: String? = null
}
