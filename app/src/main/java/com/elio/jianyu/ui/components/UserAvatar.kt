package com.elio.jianyu.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.elio.jianyu.R

/** 用户头像的唯一当前来源；产品尚未定义头像切换目录，因此不暴露伪造的切换能力。 */
@Composable
internal fun UserAvatar(
    modifier: Modifier = Modifier,
    contentDescription: String = "用户头像",
) {
    Image(
        painter = painterResource(R.drawable.avatar_user),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
