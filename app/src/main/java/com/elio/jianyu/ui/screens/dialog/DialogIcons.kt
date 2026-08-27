package com.elio.jianyu.ui.screens.dialog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 见域「对话」页面专用自包含矢量图标集合
 * 1:1 像素级还原设计规范
 */
object DialogIcons {
    val Add = Icons.Default.Add
    val Close = Icons.Default.Close
    val Check = Icons.Default.Check
    val Menu = Icons.Default.Menu
    val MoreVert = Icons.Default.MoreVert
    val Edit = Icons.Default.Edit
    val Search = Icons.Default.Search
    val Share = Icons.Default.Share
    val Star = Icons.Default.Star
    val Person = Icons.Default.Person

    // 1. 顶部栏右上角：方形带笔编辑/新建图标 (EditNote / NewSession)
    val EditNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogEditNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zM5 19V5h9v4h5v10H5zm10-12V5.5L18.5 9H15z",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).addPath(
            pathData = PathParser().parsePathString("M8 12h8M8 15h5").toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ).build()
    }

    // 2. 双勾送达图标 (DoneAll)
    val DoneAll: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogDoneAll",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString("M17.5 7.5l-6.5 6.5-3-3M21.5 7.5l-6.5 6.5M3 13.5l3.5 3.5").toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }

    // 3. 点赞手势 / 复制 (ThumbUp / Copy)
    val ThumbUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogThumbUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M2 20h4V9H2v11zm19-8.5c0-.8-.7-1.5-1.5-1.5h-4.8l.7-3.5.02-.2c0-.4-.2-.8-.4-1.1L14 4.2 8.6 9.6c-.4.4-.6.9-.6 1.4v7.5c0 1.1.9 2 2 2h7.6c.8 0 1.5-.5 1.8-1.2l2.8-6.5c.1-.2.2-.5.2-.8v-1.5z",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }

    // 4. 书签 / 保存为成果 (Bookmark)
    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogBookmark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M17 3H7a2 2 0 0 0-2 2v16l7-3 7 3V5a2 2 0 0 0-2-2z",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }

    // 5. 装饰四角星光 (Sparkle)
    val Sparkle: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogSparkle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M12 2c.5 4.5 4.5 8.5 9 9-4.5.5-8.5 4.5-9 9-.5-4.5-4.5-8.5-9-9 4.5-.5 8.5-4.5 9-9z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
    }

    // 6. 水平更多 (MoreHoriz)
    val MoreHoriz: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogMoreHoriz",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M6 12a1.8 1.8 0 1 1-3.6 0 1.8 1.8 0 0 1 3.6 0zm7.8 0a1.8 1.8 0 1 1-3.6 0 1.8 1.8 0 0 1 3.6 0zm7.8 0a1.8 1.8 0 1 1-3.6 0 1.8 1.8 0 0 1 3.6 0z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
    }

    // 7. 地球/联网搜索 (Globe)
    val Language: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogLanguage",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 0c-3 3-4.5 6.5-4.5 10s1.5 7 4.5 10m0-20c3 3 4.5 6.5 4.5 10s-1.5 7-4.5 10M2.5 9h19M2.5 15h19",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }

    // 8. 纸飞机发送 (SendPlane)
    val SendPlane: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogSendPlane",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z",
            ).toNodes(),
            fill = SolidColor(Color.White),
        ).build()
    }

    // 9. @ 符号 (AlternateEmail)
    val AlternateEmail: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogAlternateEmail",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M12 2a10 10 0 1 0 10 10c0-2.5-1.5-4-3.5-4s-3.5 1.5-3.5 4v2a1 1 0 0 1-2 0v-4a3 3 0 1 0-3 3 3 3 0 0 0 2.2-1c.5 1.2 1.8 2 3.3 2 3 0 5-2 5-5A8 8 0 1 0 6 12",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }

    // 10. 底部导航实心对话气泡 (ChatBubble)
    val ChatBubble: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogChatBubble",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
    }

    // 11. 底部导航线性文件夹 (Folder)
    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogFolder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6z",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }

    // 12. 底部导航线性成果星标 (StarOutline)
    val StarOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogStarOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }

    // 13. 底部导航线性人物 (PersonOutline)
    val PersonOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "DialogPersonOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(
                "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-4.4 0-8 2.2-8 5v1h16v-1c0-2.8-3.6-5-8-5z",
            ).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }

    // 14. 其它业务图标
    val AttachFile = DialogIconsLegacy.AttachFile
    val Description = DialogIconsLegacy.Description
    val GraphicEq = DialogIconsLegacy.GraphicEq
    val Forum = DialogIconsLegacy.Forum
    val Archive = DialogIconsLegacy.Archive
    val Cube = DialogIconsLegacy.Cube
    val Network = DialogIconsLegacy.Network
    val Chat = DialogIconsLegacy.Chat
    val Groups = DialogIconsLegacy.Groups
    val AccessTime = DialogIconsLegacy.AccessTime
    val PersonAdd = DialogIconsLegacy.PersonAdd
}

private object DialogIconsLegacy {
    val AttachFile: ImageVector by lazy {
        ImageVector.Builder(name = "AttachFile", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M16.5 6.5l-8.4 8.4a3 3 0 0 0 4.2 4.2l8.4-8.4a5 5 0 0 0-7-7L5.3 12.1a7 7 0 0 0 9.9 9.9l7.8-7.8").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    val Description: ImageVector by lazy {
        ImageVector.Builder(name = "Description", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M4 4a2 2 0 0 1 2-2h8l6 6v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4zm10-2v6h6M8 12h8M8 16h5").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    val GraphicEq: ImageVector by lazy {
        ImageVector.Builder(name = "GraphicEq", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M4 10v4M8 6v12M12 3v18M16 7v10M20 11v2").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round).build()
    }
    val Forum: ImageVector by lazy {
        ImageVector.Builder(name = "Forum", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M7 8h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H11l-4 3v-3H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM4 15V6a2 2 0 0 1 2-2h10").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    val Archive: ImageVector by lazy {
        ImageVector.Builder(name = "Archive", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M3 4h18v4H3V4zm2 4v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8H5zm5 4h4").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    val Cube: ImageVector by lazy {
        ImageVector.Builder(name = "Cube", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M12 2l8 4.5v9L12 20l-8-4.5v-9L12 2zM12 2v9m8-4.5l-8 4.5m0 0L4 6.5").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    val Network: ImageVector by lazy {
        ImageVector.Builder(name = "Network", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M12 4a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm-7 16a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm14 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM12 4v6m0 0L6.5 16.5M12 10l5.5 6.5").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    val Chat: ImageVector by lazy {
        ImageVector.Builder(name = "Chat", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M4 5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-5 4v-4H6a2 2 0 0 1-2-2V5z").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    val Groups: ImageVector by lazy {
        ImageVector.Builder(name = "Groups", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M12 6a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm-6 3a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zm12 0a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM12 9c-3 0-6 1.5-6 4v3h12v-3c0-2.5-3-4-6-4z").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    val AccessTime: ImageVector by lazy {
        ImageVector.Builder(name = "AccessTime", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 5v5l3.5 2").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    val PersonAdd: ImageVector by lazy {
        ImageVector.Builder(name = "PersonAdd", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString("M9 6a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm0 3c-2.7 0-6 1.3-6 4v3h12v-3c0-2.7-3.3-4-6-4zm10-3v6m-3-3h6").toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
}
