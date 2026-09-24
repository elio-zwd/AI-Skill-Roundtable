package com.elio.jianyu.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class UserAvatarSnapshot(
    val bitmap: Bitmap?,
    val revision: Long,
) {
    val hasCustomAvatar: Boolean
        get() = bitmap != null
}

sealed interface UserAvatarMutationResult {
    data object Success : UserAvatarMutationResult
    data object InvalidImage : UserAvatarMutationResult
    data object SourceUnavailable : UserAvatarMutationResult
    data object StorageFailure : UserAvatarMutationResult
}

/**
 * 用户自定义头像的本地事实源。
 *
 * 外部 URI 只在导入瞬间读取；正式状态固定发布到 App 私有文件，
 * 避免长期依赖相册授权或把 URI 写入数据库/偏好。
 */
class UserAvatarRepository(context: Context) {
    private val appContext = context.applicationContext
    private val avatarFile = File(appContext.filesDir, AVATAR_RELATIVE_PATH)

    fun observeAvatar(): Flow<UserAvatarSnapshot> =
        revision.mapLatest { currentRevision ->
            val bitmap = avatarFile
                .takeIf(File::isFile)
                ?.let { file -> BitmapFactory.decodeFile(file.absolutePath) }
            UserAvatarSnapshot(
                bitmap = bitmap,
                revision = currentRevision,
            )
        }.flowOn(Dispatchers.IO)

    suspend fun importAvatar(uri: Uri): UserAvatarMutationResult =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val processed = when (val decode = decodeAndNormalize(uri)) {
                    is DecodeResult.Success -> decode.bitmap
                    DecodeResult.InvalidImage -> return@withLock UserAvatarMutationResult.InvalidImage
                    DecodeResult.SourceUnavailable -> return@withLock UserAvatarMutationResult.SourceUnavailable
                }

                try {
                    if (!avatarFile.parentFile.orEmptyDirectory().exists() &&
                        !avatarFile.parentFile.orEmptyDirectory().mkdirs()
                    ) {
                        processed.recycle()
                        return@withLock UserAvatarMutationResult.StorageFailure
                    }

                    val atomicFile = AtomicFile(avatarFile)
                    val stream = try {
                        atomicFile.startWrite()
                    } catch (_: IOException) {
                        processed.recycle()
                        return@withLock UserAvatarMutationResult.StorageFailure
                    }

                    try {
                        val compressed = processed.compress(
                            Bitmap.CompressFormat.JPEG,
                            JPEG_QUALITY,
                            stream,
                        )
                        if (!compressed) {
                            atomicFile.failWrite(stream)
                            processed.recycle()
                            return@withLock UserAvatarMutationResult.StorageFailure
                        }
                        stream.fd.sync()
                        atomicFile.finishWrite(stream)
                    } catch (_: Exception) {
                        runCatching { atomicFile.failWrite(stream) }
                        processed.recycle()
                        return@withLock UserAvatarMutationResult.StorageFailure
                    }

                    processed.recycle()
                    revision.update { it + 1L }
                    UserAvatarMutationResult.Success
                } catch (_: Exception) {
                    processed.recycle()
                    UserAvatarMutationResult.StorageFailure
                }
            }
        }

    suspend fun resetToDefault(): UserAvatarMutationResult =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val deleted = !avatarFile.exists() || avatarFile.delete()
                if (!deleted) {
                    UserAvatarMutationResult.StorageFailure
                } else {
                    revision.update { it + 1L }
                    UserAvatarMutationResult.Success
                }
            }
        }

    private fun decodeAndNormalize(uri: Uri): DecodeResult {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsRead = try {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            bounds.outWidth > 0 && bounds.outHeight > 0
        } catch (_: Exception) {
            false
        }

        if (!boundsRead) {
            return if (canOpen(uri)) {
                DecodeResult.InvalidImage
            } else {
                DecodeResult.SourceUnavailable
            }
        }

        val sampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            targetMaxDimension = MAX_DECODE_DIMENSION,
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = try {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (_: Exception) {
            null
        } ?: return DecodeResult.InvalidImage

        val orientation = readOrientation(uri)
        val oriented = applyExifOrientation(decoded, orientation)
        if (oriented !== decoded) {
            decoded.recycle()
        }

        val side = min(oriented.width, oriented.height)
        if (side <= 0) {
            oriented.recycle()
            return DecodeResult.InvalidImage
        }

        val cropX = (oriented.width - side) / 2
        val cropY = (oriented.height - side) / 2
        val square = Bitmap.createBitmap(oriented, cropX, cropY, side, side)
        if (square !== oriented) {
            oriented.recycle()
        }

        val scaled = if (square.width == OUTPUT_SIZE && square.height == OUTPUT_SIZE) {
            square
        } else {
            Bitmap.createScaledBitmap(square, OUTPUT_SIZE, OUTPUT_SIZE, true).also {
                if (it !== square) square.recycle()
            }
        }

        val flattened = Bitmap.createBitmap(
            OUTPUT_SIZE,
            OUTPUT_SIZE,
            Bitmap.Config.ARGB_8888,
        )
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(
                scaled,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
        if (scaled !== flattened) {
            scaled.recycle()
        }

        return DecodeResult.Success(flattened)
    }

    private fun canOpen(uri: Uri): Boolean =
        try {
            appContext.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }

    private fun readOrientation(uri: Uri): Int =
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        targetMaxDimension: Int,
    ): Int {
        var sampleSize = 1
        while (max(width, height) / (sampleSize * 2) >= targetMaxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private sealed interface DecodeResult {
        data class Success(val bitmap: Bitmap) : DecodeResult
        data object InvalidImage : DecodeResult
        data object SourceUnavailable : DecodeResult
    }

    private fun File?.orEmptyDirectory(): File =
        this ?: File(appContext.filesDir, USER_PROFILE_DIRECTORY)

    private companion object {
        const val USER_PROFILE_DIRECTORY = "user-profile"
        const val AVATAR_RELATIVE_PATH = "$USER_PROFILE_DIRECTORY/avatar.jpg"
        const val OUTPUT_SIZE = 512
        const val MAX_DECODE_DIMENSION = 2048
        const val JPEG_QUALITY = 90

        val revision = MutableStateFlow(0L)
        val writeMutex = Mutex()
    }
}
