package com.elio.jianyu.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.elio.jianyu.R

private const val USER_AVATAR_SOURCE_ZOOM = 1.5f

/** 用户头像的唯一当前来源；产品尚未定义头像切换目录，因此不暴露伪造的切换能力。 */
@Composable
internal fun UserAvatar(
    modifier: Modifier = Modifier,
    contentDescription: String = "用户头像",
) {
    // 当前固定 avatar_user.png 下方自带大块白底。
    // 调用方负责最终形状（Mine / Dialog 都是圆形），这里仅在形状内部统一裁掉源图留白。
    Box(modifier = modifier) {
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
