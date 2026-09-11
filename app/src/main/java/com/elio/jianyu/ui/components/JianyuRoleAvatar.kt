package com.elio.jianyu.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 跨【角色】与【对话】复用的稳定头像渲染器。
 *
 * 本地人物资产优先；资产不可用时退到确定性的文字身份，不生成随机颜色、emoji 或人脸。
 * 容器/文字颜色由调用方按页面语义传入，默认使用 Material 主题容器色。
 */
@Composable
internal fun JianyuRoleAvatar(
    name: String,
    modifier: Modifier = Modifier,
    assetPath: String? = null,
    avatarResId: Int? = null,
    fallbackText: String = "",
    fallbackContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    fallbackContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val context = LocalContext.current
    val assetBitmap = remember(context, assetPath, avatarResId) {
        if (avatarResId != null || assetPath.isNullOrBlank()) {
            null
        } else {
            runCatching {
                context.assets.open(assetPath).use(BitmapFactory::decodeStream)
            }.getOrNull()?.asImageBitmap()
        }
    }

    when {
        avatarResId != null -> Image(
            painter = painterResource(avatarResId),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        assetBitmap != null -> Image(
            bitmap = assetBitmap,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        else -> Box(
            modifier = modifier
                .background(fallbackContainerColor)
                .semantics { contentDescription = name },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fallbackText.ifBlank { roleAvatarFallbackLabel(name) },
                color = fallbackContentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

internal fun roleAvatarFallbackLabel(name: String): String {
    val normalized = name.trim()
    if (normalized.isEmpty()) return "AI"

    val words = normalized
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (words.size >= 2 && words.all { word -> word.first().isLetterOrDigit() }) {
        return words
            .take(2)
            .joinToString("") { word -> word.first().toString() }
            .uppercase(Locale.ROOT)
    }
    return normalized.take(2).uppercase(Locale.ROOT)
}
