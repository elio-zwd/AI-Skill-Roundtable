package com.elio.jianyu.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.elio.jianyu.R

private const val USER_AVATAR_SOURCE_ZOOM = 1.5f

internal val LocalUserAvatarImage = staticCompositionLocalOf<ImageBitmap?> { null }

/**
 * 用户头像的统一显示组件。
 *
 * Route 负责提供已经解码好的自定义头像；组件只负责展示。
 * 无自定义头像时继续使用内置资源作为确定性 fallback。
 */
@Composable
internal fun UserAvatar(
    modifier: Modifier = Modifier,
    contentDescription: String = "用户头像",
) {
    val customImage = LocalUserAvatarImage.current

    Box(modifier = modifier) {
        if (customImage != null) {
            Image(
                bitmap = customImage,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.avatar_user),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = USER_AVATAR_SOURCE_ZOOM
                        scaleY = USER_AVATAR_SOURCE_ZOOM
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
            )
        }
    }
}
