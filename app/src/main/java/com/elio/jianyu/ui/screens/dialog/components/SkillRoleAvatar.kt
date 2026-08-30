package com.elio.jianyu.ui.screens.dialog.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.SkillRoleUiModel

/** 统一读取角色配置中的本地人物头像，避免把 assets 路径显示成文字。 */
@Composable
internal fun SkillRoleAvatar(
    role: SkillRoleUiModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assetBitmap = remember(role.avatarUrl) {
        role.avatarUrl
            .takeIf(String::isNotBlank)
            ?.let { path ->
                runCatching {
                    context.assets.open(path).use(BitmapFactory::decodeStream)
                }.getOrNull()
            }
            ?.asImageBitmap()
    }

    when {
        role.avatarResId != null -> Image(
            painter = androidx.compose.ui.res.painterResource(role.avatarResId),
            contentDescription = role.name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        assetBitmap != null -> Image(
            bitmap = assetBitmap,
            contentDescription = role.name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        else -> Box(
            modifier = modifier.background(role.tintBorder),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = role.avatarText.ifBlank { role.name.take(2) },
                color = role.accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
