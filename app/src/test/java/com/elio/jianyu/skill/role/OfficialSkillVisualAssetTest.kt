package com.elio.jianyu.skill.role

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillVisualAssetTest {
    private val officialCatalog: OfficialSkillCatalog by lazy {
        val result = OfficialSkillCatalogParser.parse(
            assetFile("official_skill_catalog_v1.json").readText(),
            assetFile("official_skill_execution_manifest_v2.json").readText(),
        )
        require(result is OfficialSkillCatalogLoadResult.Success)
        result.catalog
    }

    @Test
    fun productionAssets_coverEveryOfficialSkillVisual() {
        val issues = officialCatalog.skills.mapNotNull { skill ->
            val path = officialSkillVisualAssetPath(skill)
            val file = assetFile(path)
            when {
                !file.isFile -> "$path: missing"
                else -> {
                    val dimensions = imageDimensions(file)
                    when {
                        dimensions == null -> "$path: decode_failed"
                        dimensions.first != dimensions.second ->
                            "$path: ${dimensions.first}x${dimensions.second} not_square"
                        dimensions.first < 512 ->
                            "$path: ${dimensions.first}x${dimensions.second} too_small"
                        else -> null
                    }
                }
            }
        }

        assertTrue(
            "正式 Skill 视觉资源不完整或不合格：" + issues.joinToString(),
            issues.isEmpty(),
        )
    }

    @Test
    fun productionVisualKinds_are38PortraitsAnd6Tools() {
        val kinds = officialCatalog.skills.groupingBy(::officialSkillVisualKind).eachCount()

        assertEquals(38, kinds[OfficialSkillVisualKind.PORTRAIT])
        assertEquals(6, kinds[OfficialSkillVisualKind.TOOL])
    }

    private fun imageDimensions(file: File): Pair<Int, Int>? {
        val bytes = file.readBytes()
        return when {
            isPng(bytes) -> pngDimensions(bytes)
            isJpeg(bytes) -> jpegDimensions(bytes)
            else -> null
        }
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 24 &&
            bytes[0].toInt() and 0xFF == 0x89 &&
            bytes[1].toInt() and 0xFF == 0x50 &&
            bytes[2].toInt() and 0xFF == 0x4E &&
            bytes[3].toInt() and 0xFF == 0x47

    private fun pngDimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 24) return null
        val width = readInt32Be(bytes, 16)
        val height = readInt32Be(bytes, 20)
        return if (width > 0 && height > 0) width to height else null
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0].toInt() and 0xFF == 0xFF &&
            bytes[1].toInt() and 0xFF == 0xD8

    private fun jpegDimensions(bytes: ByteArray): Pair<Int, Int>? {
        var index = 2
        while (index + 8 < bytes.size) {
            if (bytes[index].toInt() and 0xFF != 0xFF) {
                index += 1
                continue
            }

            while (index < bytes.size && bytes[index].toInt() and 0xFF == 0xFF) {
                index += 1
            }
            if (index >= bytes.size) return null

            val marker = bytes[index].toInt() and 0xFF
            index += 1

            if (
                marker == 0xD8 ||
                marker == 0xD9 ||
                marker == 0x01 ||
                marker in 0xD0..0xD7
            ) {
                continue
            }

            if (index + 1 >= bytes.size) return null
            val segmentLength = readUInt16Be(bytes, index)
            if (segmentLength < 2 || index + segmentLength > bytes.size) return null

            if (marker in SOF_MARKERS) {
                if (segmentLength < 7 || index + 6 >= bytes.size) return null
                val height = readUInt16Be(bytes, index + 3)
                val width = readUInt16Be(bytes, index + 5)
                return if (width > 0 && height > 0) width to height else null
            }

            index += segmentLength
        }
        return null
    }

    private fun readUInt16Be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)

    private fun readInt32Be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")

    private companion object {
        val SOF_MARKERS = setOf(
            0xC0, 0xC1, 0xC2, 0xC3,
            0xC5, 0xC6, 0xC7,
            0xC9, 0xCA, 0xCB,
            0xCD, 0xCE, 0xCF,
        )
    }
}
