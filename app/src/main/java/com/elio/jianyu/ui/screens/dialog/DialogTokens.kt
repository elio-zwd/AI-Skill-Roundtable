package com.elio.jianyu.ui.screens.dialog

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 见域「对话」页面设计系统 Tokens
 * 对应设计规格：docs/product/重构/UI界面/对话/jianyu-dialog-final-ui-spec.md 第 16、17 节
 */
object DialogTokens {
    // 基础表面颜色
    val PageBackground = Color(0xFFFCFBFE)     // 页面主背景（带弱冷紫雾感）
    val SurfaceWhite = Color(0xFFFFFFFF)       // 卡片、输入框、Sheet 纯白表面
    val ScrimOverlay = Color(0x66000000)       // 黑色遮罩（约 35%-40% 透明度）

    // 文字层级颜色
    val TextPrimary = Color(0xFF111116)         // 近黑：标题、正文、角色名
    val TextSecondary = Color(0xFF747B90)       // 蓝灰：时间、简介、说明、未选中导航
    val TextTertiary = Color(0xFFA1A7B7)        // 灰蓝：弱提示、占位符、禁用
    val DestructiveRed = Color(0xFFFF3B30)      // 危险操作：删除会话

    // 交互与品牌强调色
    val InteractionBlue = Color(0xFF176DFF)     // 交互蓝：发送按钮、选中导航、联网搜索
    val InteractionBlueLight = Color(0xFFE9F0FD)// 浅蓝底：用户消息气泡背景、联网状态底
    val BrandPurple = Color(0xFF6340F8)         // 品牌紫：新建会话、增加按钮、CTA 渐变
    val BrandPurpleLight = Color(0xFFF2EFFF)    // 浅紫背景：新建会话背景、+ 号按钮背景
    val StatusGreen = Color(0xFF18B96C)         // 状态绿：已开、在会话状态点、Check 标记

    // 角色专属个性弱识别色（非阵营色）
    val RoleLavenderBg = Color(0xFFF7F5FE)      // 规划教练卡片底色
    val RoleLavenderBorder = Color(0xFFE7E1FB)  // 规划教练淡紫描边
    val RoleMintBg = Color(0xFFF0FAF6)          // 系统思考者卡片底色
    val RoleMintBorder = Color(0xFFCDEDE2)      // 系统思考者淡绿描边
    val RoleMintAccent = Color(0xFF28B383)      // 系统思考者图标与星光装饰

    // 普通边框
    val NeutralBorder = Color(0xFFECECF4)       // 普通输入框、Sheet 分割线

    // 圆角体系
    val RadiusChip = 16.dp                      // 状态 Chip、小型徽章
    val RadiusButton = 18.dp                    // 普通按钮、搜索框
    val RadiusCard = 22.dp                      // 角色卡、消息卡、输入区
    val RadiusSheetTop = 26.dp                  // Bottom Sheet 顶部大圆角
    val RadiusHero = 22.dp                      // 角色详情 Hero 卡

    // 边框粗细
    val BorderThin = 1.dp
    val BorderHighlight = 1.5.dp

    // 常用字号
    val FontTitle = 21.sp
    val FontHeroTitle = 28.sp
    val FontSheetTitle = 20.sp
    val FontRoleName = 17.sp
    val FontBody = 16.sp
    val FontSubtitle = 13.sp
    val FontTime = 13.sp
    val FontNavLabel = 12.sp
    val FontAction = 13.sp
}
