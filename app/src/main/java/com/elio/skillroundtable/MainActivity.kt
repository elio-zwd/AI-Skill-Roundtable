package com.elio.skillroundtable

import com.elio.skillroundtable.telemetry.PrivacySafeLogger
import com.elio.skillroundtable.viewmodel.SearchMode
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import dev.jeziellago.compose.markdowntext.MarkdownText
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.ChatSession
import com.elio.skillroundtable.data.Message
import com.elio.skillroundtable.viewmodel.RoundtableViewModel
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState

fun Modifier.bounceClick(): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bounceScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                isPressed = true
                tryAwaitRelease()
                isPressed = false
            }
        )
    }
}

@Composable
fun MinimalistPulseIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = PrimaryAccent.copy(alpha = alpha * 0.12f),
                radius = size.minDimension / 2 * scale
            )
            drawCircle(
                color = GoldAccent.copy(alpha = alpha * 0.35f),
                radius = size.minDimension / 2.4f * (2f - scale),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = GoldAccent.copy(alpha = 0.8f),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun CharacterAvatar(
    avatar: String,
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    textSize: androidx.compose.ui.unit.TextUnit = 24.sp
) {
    val context = LocalContext.current
    val imageBitmap = remember(avatar) {
        if (avatar.startsWith("avatars/")) {
            try {
                context.assets.open(avatar).use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                PrivacySafeLogger.e(
                    "MainActivity",
                    "角色头像读取失败",
                    e
                )
                null
            }
        } else {
            null
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            val displayChar = if (avatar.length > 2) {
                name.lastOrNull()?.toString() ?: "智"
            } else {
                avatar
            }
            Text(displayChar, fontSize = textSize, color = Color.White)
        }
    }
}

// Color scheme definitions for Slate-Dark Theme
private val SlateBg = Color(0xFF121824)
private val CardBg = Color(0xFF1E2638)
private val PrimaryAccent = Color(0xFF6366F1) // Royal Blue/Indigo
private val SecondaryAccent = Color(0xFF10B981) // Emerald Green
private val GoldAccent = Color(0xFFF59E0B) // Amber
private val TextPrimary = Color(0xFFF3F4F6)
private val TextSecondary = Color(0xFF9CA3AF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = PrimaryAccent,
                    secondary = SecondaryAccent,
                    background = SlateBg,
                    surface = CardBg,
                    onPrimary = Color.White,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SlateBg
                ) {
                    MainAppContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent() {
    val viewModel: RoundtableViewModel = viewModel()
    val allSessions by viewModel.allSessions.collectAsState()
    val allCharacters by viewModel.allCharacters.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val isRoundtableRunning by viewModel.isRoundtableRunning.collectAsState()
    val typingCharacterIds by viewModel.typingCharacterIds.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val apiKeySummaries by viewModel.apiKeySummaries.collectAsState()
    val isAutoNextEnabled by viewModel.isAutoNextEnabled.collectAsState()
    val isSemanticRoutingEnabled by viewModel.isSemanticRoutingEnabled.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val roundActionState by viewModel.roundActionState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddCharacterDialog by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<Character?>(null) }
    var showApiKeyManagerScreen by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }

    var renameSessionId by remember { mutableStateOf<Long?>(null) }
    var renameSessionTitle by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showTelemetryScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current

    if (showTelemetryScreen) {
        ApiTelemetryScreen(
            currentSessionId = currentSessionId,
            onBack = { showTelemetryScreen = false }
        )
    } else if (showApiKeyManagerScreen) {
        ApiKeyManagerScreen(
            currentSessionId = currentSessionId,
            onBack = { showApiKeyManagerScreen = false },
            onOpenTelemetry = { showTelemetryScreen = true }
        )
    } else {
        Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateBg)
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF232D42))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val tabs = listOf(
                        Triple("圆桌脑暴", Icons.Default.Home, 0),
                        Triple("智囊大厅", Icons.Default.Person, 1),
                        Triple("音频库", Icons.Default.PlayArrow, 2)
                    )
                    tabs.forEach { (label, icon, index) ->
                        val isSelected = selectedTab == index
                        val activeColor = if (isSelected) PrimaryAccent else TextSecondary.copy(alpha = 0.6f)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .bounceClick()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { selectedTab = index },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = activeColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = label,
                                color = activeColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> {
                    RoundtableBrainstormScreen(
                        viewModel = viewModel,
                        allSessions = allSessions,
                        currentSession = currentSession,
                        currentMessages = currentMessages,
                        allCharacters = allCharacters,
                        isRoundtableRunning = isRoundtableRunning,
                        typingCharacterIds = typingCharacterIds,
                        hasApiKeys = apiKeySummaries.any { it.enabled },
                        isAutoNextEnabled = isAutoNextEnabled,
                        isSemanticRoutingEnabled = isSemanticRoutingEnabled,
                        searchMode = searchMode,
                        roundActionState = roundActionState,
                        onSearchModeChange = { viewModel.setSearchMode(it) },
                        onOpenApiKeyConfig = { showApiKeyManagerScreen = true },
                        onToggleDrawer = { showDrawer = !showDrawer },
                        onRenameSession = { id, title ->
                            renameSessionId = id
                            renameSessionTitle = title
                            showRenameDialog = true
                        }
                    )
                }
                1 -> {
                    CharacterHallScreen(
                        viewModel = viewModel,
                        characters = allCharacters,
                        onToggleActive = { char ->
                            viewModel.addOrUpdateCharacter(char.copy(isActive = !char.isActive))
                        },
                        onEditCharacter = { char ->
                            editingCharacter = char
                        },
                        onAddCharacter = { showAddCharacterDialog = true },
                        onDeleteCharacter = { id ->
                            viewModel.deleteCharacter(id)
                            Toast.makeText(context, "智囊已被移出会议", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                2 -> {
                    AudioLibraryScreen(
                        viewModel = viewModel,
                        allCharacters = allCharacters
                    )
                }
            }

            // Slide out Drawer Overlay for Sessions History
            AnimatedVisibility(
                visible = showDrawer && selectedTab == 0,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                // Dimmed background click-away helper
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showDrawer = false }
                ) {
                    // Drawer panel
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(300.dp)
                            .background(CardBg)
                            .clickable(enabled = false) {} // block click through
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "脑暴会议历史",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            color = PrimaryAccent
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = TextSecondary.copy(alpha = 0.2f))

                        Button(
                            onClick = {
                                viewModel.createNewSession("关于新概念的圆桌会议 #${allSessions.size + 1}")
                                showDrawer = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .bounceClick()
                                .testTag("new_session_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "新建会议")
                            Spacer(Modifier.width(8.dp))
                            Text("开启全新圆桌脑暴")
                        }

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(
                                items = allSessions,
                                key = { it.id }
                            ) { session ->
                                val isSelected = session.id == currentSessionId
                                @OptIn(ExperimentalFoundationApi::class)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onLongClick = {
                                                renameSessionId = session.id
                                                renameSessionTitle = session.title
                                                showRenameDialog = true
                                            },
                                            onClick = {
                                                viewModel.selectSession(session.id)
                                                showDrawer = false
                                            }
                                        )
                                        .background(if (isSelected) PrimaryAccent.copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(horizontal = 24.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (isSelected) PrimaryAccent else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = TextPrimary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                        Divider(color = TextSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "圆桌脑暴设置",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("自动顺延发言 (TTS播毕)", fontSize = 12.sp, color = TextPrimary)
                                Switch(
                                    checked = isAutoNextEnabled,
                                    onCheckedChange = { viewModel.setAutoNextEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SecondaryAccent,
                                        checkedTrackColor = SecondaryAccent.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.scale(0.7f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("专家自适应排序 (余弦路由)", fontSize = 12.sp, color = TextPrimary)
                                Switch(
                                    checked = isSemanticRoutingEnabled,
                                    onCheckedChange = { viewModel.setSemanticRoutingEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SecondaryAccent,
                                        checkedTrackColor = SecondaryAccent.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.scale(0.7f)
                                )
                            }
                            Divider(color = TextSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick()
                                    .clickable {
                                        showTelemetryScreen = true
                                        showDrawer = false
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("熔断诊断与遥测日志", fontSize = 12.sp, color = TextPrimary)
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Error snackbar overlay
            if (errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("确定", color = Color.Yellow)
                        }
                    }
                ) {
                    Text(errorMessage ?: "")
                }
            }

            // Dialogs
            if (showAddCharacterDialog) {
                AddEditCharacterDialog(
                    character = null,
                    onDismiss = { showAddCharacterDialog = false },
                    onConfirm = { newChar ->
                        viewModel.addOrUpdateCharacter(newChar)
                        showAddCharacterDialog = false
                        Toast.makeText(context, "新智囊已入席", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (editingCharacter != null) {
                AddEditCharacterDialog(
                    character = editingCharacter,
                    onDismiss = { editingCharacter = null },
                    onConfirm = { updatedChar ->
                        viewModel.addOrUpdateCharacter(updatedChar)
                        editingCharacter = null
                        Toast.makeText(context, "智囊设定已修改", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("重命名会议主题", color = TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value = renameSessionTitle,
                            onValueChange = { renameSessionTitle = it },
                            label = { Text("新主题") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (renameSessionTitle.isNotBlank() && renameSessionId != null) {
                                    viewModel.renameSession(renameSessionId!!, renameSessionTitle)
                                    showRenameDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                        ) {
                            Text("保存")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("取消", color = TextSecondary)
                        }
                    }
                )
            }

            }
        }
    }
}

fun saveMarkdownToLocal(context: android.content.Context, title: String, content: String): String? {
    val resolver = context.contentResolver
    val safeTitle = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${safeTitle}_${System.currentTimeMillis()}.md")
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Documents/AI智囊圆桌")
        }
    }
    
    val uri = resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), contentValues)
    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            }
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                "Documents/AI智囊圆桌"
            } else {
                uri.path
            }
        } catch (e: Exception) {
            PrivacySafeLogger.e(
                "MainActivity",
                "保存 Markdown 失败",
                e
            )
        }
    }
    return null
}

sealed class ChatItem {
    data class UserMessage(val message: Message) : ChatItem()
    data class RoundtableRound(val roundIndex: Int, val messages: List<Message>) : ChatItem()
}

fun groupMessages(messages: List<Message>): List<ChatItem> {
    val result = mutableListOf<ChatItem>()
    var currentGroup = mutableListOf<Message>()
    
    for (msg in messages) {
        if (msg.senderId == "user") {
            if (currentGroup.isNotEmpty()) {
                val groupedByRound = currentGroup.groupBy { it.roundIndex }.entries.sortedBy { it.key }
                groupedByRound.forEach { (round, msgs) ->
                    result.add(ChatItem.RoundtableRound(round, msgs))
                }
                currentGroup.clear()
            }
            result.add(ChatItem.UserMessage(msg))
        } else {
            if (!msg.isPending) {
                currentGroup.add(msg)
            }
        }
    }
    if (currentGroup.isNotEmpty()) {
        val groupedByRound = currentGroup.groupBy { it.roundIndex }.entries.sortedBy { it.key }
        groupedByRound.forEach { (round, msgs) ->
            result.add(ChatItem.RoundtableRound(round, msgs))
        }
    }
    return result
}

@Composable
fun RoundtableRoundBubble(
    roundItem: ChatItem.RoundtableRound,
    currentPlayingId: Long?,
    allCharacters: List<Character>,
    onPlayAudio: (Message, String) -> Unit
) {
    val msgs = roundItem.messages
    if (msgs.isEmpty()) return
    
    val pagerState = rememberPagerState(pageCount = { msgs.size })
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) {
            pagerState.animateScrollToPage(msgs.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(CardBg.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .border(1.dp, PrimaryAccent.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(vertical = 12.dp)
    ) {
        // 头部：第几轮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${roundItem.roundIndex} 轮脑暴交锋",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GoldAccent
            )
            Text(
                text = "${pagerState.currentPage + 1}/${msgs.size}",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val msg = msgs[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                MessageBubble(
                    message = msg,
                    currentPlayingId = currentPlayingId,
                    allCharacters = allCharacters,
                    onPlayAudio = onPlayAudio
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 头像导航指示器 Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            msgs.forEachIndexed { index, msg ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(if (isSelected) 36.dp else 28.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) PrimaryAccent.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) GoldAccent else TextSecondary.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CharacterAvatar(
                        avatar = msg.avatar,
                        name = msg.senderName,
                        size = if (isSelected) 36.dp else 28.dp,
                        textSize = if (isSelected) 18.sp else 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RoundtableBrainstormScreen(
    viewModel: RoundtableViewModel,
    allSessions: List<ChatSession>,
    currentSession: ChatSession?,
    currentMessages: List<Message>,
    allCharacters: List<Character>,
    isRoundtableRunning: Boolean,
    typingCharacterIds: Set<String>,
    hasApiKeys: Boolean,
    isAutoNextEnabled: Boolean,
    isSemanticRoutingEnabled: Boolean,
    searchMode: SearchMode,
    roundActionState: com.elio.skillroundtable.viewmodel.RoundActionState,
    onSearchModeChange: (SearchMode) -> Unit,
    onOpenApiKeyConfig: () -> Unit,
    onToggleDrawer: () -> Unit,
    onRenameSession: (Long, String) -> Unit
) {
    val listState = rememberLazyListState()
    var userQuestionText by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val currentPlayingId by viewModel.currentPlayingMessageId.collectAsState()

    // Scroll to bottom on new messages
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // App Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "历史会议", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                @OptIn(ExperimentalFoundationApi::class)
                Column(
                    modifier = Modifier.combinedClickable(
                        enabled = currentSession != null,
                        onLongClick = {
                            currentSession?.let {
                                onRenameSession(it.id, it.title)
                            }
                        },
                        onClick = {}
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentSession?.title ?: "AI 智囊圆桌",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isRoundtableRunning) {
                            Spacer(Modifier.width(6.dp))
                            CircularProgressIndicator(
                                color = SecondaryAccent,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentSession != null && currentMessages.isNotEmpty()) {
                    var showExportMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "导出对话",
                                tint = TextPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制为 Markdown") },
                                onClick = {
                                    showExportMenu = false
                                    coroutineScope.launch {
                                        val md = viewModel.exportConversation(currentSession.id)
                                        try {
                                            clipboardManager.setText(AnnotatedString(md))
                                            Toast.makeText(context, "已复制至剪贴板", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            PrivacySafeLogger.e(
                                                "MainActivity",
                                                "复制剪贴板失败",
                                                e
                                            )
                                            Toast.makeText(context, "复制失败：剪贴板不可用", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("保存到本地文档") },
                                onClick = {
                                    showExportMenu = false
                                    coroutineScope.launch {
                                        val md = viewModel.exportConversation(currentSession.id)
                                        val saved = saveMarkdownToLocal(context, currentSession.title, md)
                                        if (saved != null) {
                                            Toast.makeText(context, "已保存到 Documents/AI智囊圆桌/", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onOpenApiKeyConfig, modifier = Modifier.bounceClick()) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "密钥设置",
                        tint = if (hasApiKeys) SecondaryAccent else GoldAccent
                    )
                }
            }
        }

        // Seating Roundtable Diagram Row
        RoundtableSeatingDiagram(
            characters = allCharacters.filter { it.isActive },
            typingCharacterIds = typingCharacterIds,
            currentMessages = currentMessages,
            searchMode = searchMode,
            onSearchModeChange = onSearchModeChange
        )

        // Chat conversation list
        if (currentSession == null) {
            // Empty state helper
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MinimalistPulseIndicator(
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "欢迎来到 AI 智囊圆桌",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "本软件支持多角色“轮流式”群聊讨论。当你输入问题，激活的智囊会顺次作答，自动携带上下文展开思想辩论！",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.createNewSession("关于新方向的圆桌脑暴")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        modifier = Modifier.bounceClick()
                    ) {
                        Text("开启首个圆桌会议")
                    }
                }
            }
        } else {
            val chatItems = remember(currentMessages) { groupMessages(currentMessages) }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                items(chatItems) { item ->
                    when (item) {
                        is ChatItem.UserMessage -> MessageBubble(
                            message = item.message,
                            currentPlayingId = currentPlayingId,
                            allCharacters = allCharacters,
                            onPlayAudio = { msg, voice ->
                                viewModel.playOrSynthesizeTts(msg, voice)
                            }
                        )
                        is ChatItem.RoundtableRound -> RoundtableRoundBubble(
                            roundItem = item,
                            currentPlayingId = currentPlayingId,
                            allCharacters = allCharacters,
                            onPlayAudio = { msg, voice ->
                                viewModel.playOrSynthesizeTts(msg, voice)
                            }
                        )
                    }
                }

                if (isRoundtableRunning && typingCharacterIds.isNotEmpty()) {
                    typingCharacterIds.forEach { charId ->
                        val typingChar = allCharacters.find { it.id == charId }
                        if (typingChar != null) {
                            item(key = "typing_$charId") {
                                TypingIndicatorBubble(character = typingChar)
                            }
                        }
                    }
                }
            }
        }

        // Action Toolbar
        AnimatedVisibility(visible = currentSession != null && !isRoundtableRunning && currentMessages.isNotEmpty()) {
            val hasActiveChars = allCharacters.any { it.isActive }
            if (hasActiveChars) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    when (roundActionState) {
                        com.elio.skillroundtable.viewmodel.RoundActionState.CONTINUE_ROUND -> {
                            Surface(
                                onClick = { viewModel.triggerNextCharacterManual() },
                                color = SecondaryAccent.copy(alpha = 0.08f),
                                contentColor = SecondaryAccent,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, SecondaryAccent.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .bounceClick()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = SecondaryAccent
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "继续本轮",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SecondaryAccent
                                    )
                                }
                            }
                        }
                        com.elio.skillroundtable.viewmodel.RoundActionState.START_NEXT_ROUND -> {
                            Surface(
                                onClick = { viewModel.triggerNextCharacterManual() },
                                color = GoldAccent.copy(alpha = 0.08f),
                                contentColor = GoldAccent,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .bounceClick()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = GoldAccent
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "开启下一轮",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GoldAccent
                                    )
                                }
                            }
                        }
                        com.elio.skillroundtable.viewmodel.RoundActionState.BUDGET_EXCEEDED -> {
                            Surface(
                                color = Color(0xFF2D3748),
                                contentColor = TextSecondary,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = TextSecondary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "本问题已达安全预算上限",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chat Input Box
        if (currentSession != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            color = if (isInputFocused) SlateBg else Color(0xFF151B27),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isInputFocused) PrimaryAccent.copy(alpha = 0.8f) else Color(0xFF232D42),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = userQuestionText,
                        onValueChange = { userQuestionText = it },
                        placeholder = { Text("向诸位智囊提问...", color = TextSecondary.copy(alpha = 0.8f), fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isInputFocused = it.isFocused }
                            .testTag("chat_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        maxLines = 4,
                        enabled = !isRoundtableRunning
                    )
                    Spacer(Modifier.width(4.dp))
                    val isSendEnabled = !isRoundtableRunning && userQuestionText.isNotBlank()
                    val isActionEnabled = isRoundtableRunning || isSendEnabled
                    IconButton(
                        onClick = {
                            if (isRoundtableRunning) {
                                viewModel.cancelRoundtable()
                            } else if (userQuestionText.isNotBlank()) {
                                viewModel.askQuestion(userQuestionText)
                                userQuestionText = ""
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = when {
                                    isRoundtableRunning -> MaterialTheme.colorScheme.error
                                    isSendEnabled -> PrimaryAccent
                                    else -> Color.Transparent
                                },
                                shape = CircleShape
                            )
                            .bounceClick()
                            .testTag(if (isRoundtableRunning) "stop_button" else "send_button"),
                        enabled = isActionEnabled
                    ) {
                        Icon(
                            imageVector = if (isRoundtableRunning) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isRoundtableRunning) "停止生成" else "发送",
                            tint = if (isActionEnabled) Color.White else TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoundtableSeatingDiagram(
    characters: List<Character>,
    typingCharacterIds: Set<String>,
    currentMessages: List<Message>,
    searchMode: SearchMode,
    onSearchModeChange: (SearchMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateBg)
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧：紧凑的席位头像流 (36.dp box)
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(characters) { char ->
                val isTyping = typingCharacterIds.contains(char.id)
                val lastQuestionIndex = currentMessages.indexOfLast { it.senderId == "user" }
                val messagesSinceQuestion = if (lastQuestionIndex != -1) currentMessages.subList(lastQuestionIndex + 1, currentMessages.size) else emptyList()
                val hasReplied = messagesSinceQuestion.any { it.senderId == char.id }

                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .scale(if (isTyping) pulseScale else 1.0f)
                        .clip(CircleShape)
                        .background(
                            if (isTyping) PrimaryAccent.copy(alpha = 0.3f)
                            else if (hasReplied) SecondaryAccent.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (isTyping) 1.5.dp else 1.dp,
                            color = if (isTyping) PrimaryAccent
                            else if (hasReplied) SecondaryAccent
                            else TextSecondary.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                ) {
                    CharacterAvatar(
                        avatar = char.avatar,
                        name = char.name,
                        size = 32.dp,
                        textSize = 16.sp
                    )

                    if (hasReplied) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(SecondaryAccent)
                                .align(Alignment.BottomEnd)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(8.dp).align(Alignment.Center)
                            )
                        }
                    } else if (isTyping) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(PrimaryAccent)
                                .align(Alignment.BottomEnd)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.fillMaxSize().padding(1.dp)
                            )
                        }
                    }
                }
            }
        }

        // 右侧：紧凑的联网搜索接地胶囊
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(CardBg)
                .border(0.5.dp, PrimaryAccent.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .padding(1.5.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            SearchMode.values().forEach { mode ->
                val isSelected = searchMode == mode
                val text = when (mode) {
                    SearchMode.SMART -> "智能"
                    SearchMode.FORCE -> "强制"
                    SearchMode.OFF -> "关闭"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) PrimaryAccent else Color.Transparent)
                        .bounceClick()
                        .clickable { onSearchModeChange(mode) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        fontSize = 9.sp,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    currentPlayingId: Long?,
    allCharacters: List<Character>,
    onPlayAudio: (Message, String) -> Unit
) {
    val isUser = message.senderId == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            CharacterAvatar(
                avatar = message.avatar,
                name = message.senderName,
                size = 42.dp,
                textSize = 20.sp
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (!isUser) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent,
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) PrimaryAccent
                        else if (message.senderId == "zhang_xuefeng") GoldAccent.copy(alpha = 0.15f)
                        else CardBg
                    )
                    .border(
                        width = 1.dp,
                        color = if (isUser) PrimaryAccent
                        else if (message.senderId == "zhang_xuefeng") GoldAccent.copy(alpha = 0.5f)
                        else PrimaryAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .bounceClick()
                    .clickable {
                        try {
                            clipboardManager.setText(AnnotatedString(message.text))
                            Toast.makeText(context, "已复制至剪贴板", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            PrivacySafeLogger.e(
                                "MainActivity",
                                "复制消息剪贴板失败",
                                e
                            )
                            Toast.makeText(context, "复制失败：剪贴板不可用", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(14.dp)
            ) {
                if (isUser) {
                    Text(
                        text = message.text,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                } else {
                    MarkdownText(
                        markdown = message.text,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!isUser) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .bounceClick()
                        .clickable {
                            val voice = allCharacters.find { it.id == message.senderId }?.voiceConfig ?: "Aoede"
                            onPlayAudio(message, voice)
                        }
                ) {
                    val isPlaying = currentPlayingId == message.id
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放TTS",
                        tint = if (isPlaying) GoldAccent else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "播音中..." else "合成语音",
                        fontSize = 11.sp,
                        color = if (isPlaying) GoldAccent else TextSecondary
                    )
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            CharacterAvatar(
                avatar = message.avatar,
                name = message.senderName,
                size = 42.dp,
                textSize = 20.sp
            )
        }
    }
}

@Composable
fun TypingIndicatorBubble(character: Character) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        CharacterAvatar(
            avatar = character.avatar,
            name = character.name,
            size = 42.dp,
            textSize = 20.sp
        )
        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = character.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryAccent,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                    .background(CardBg)
                    .border(1.dp, PrimaryAccent.copy(alpha = 0.15f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryAccent
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "正在思考如何交锋论证...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownRender(text: String) {
    val lines = text.lines()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                val content = trimmed.drop(level).trim()
                val fontSize = when (level) {
                    1 -> 22.sp
                    2 -> 19.sp
                    3 -> 17.sp
                    else -> 15.sp
                }
                Text(
                    text = content,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                val content = trimmed.drop(1).trim()
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text("• ", color = SecondaryAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = content,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            } else if (trimmed.isNotEmpty()) {
                Text(
                    text = trimmed,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CharacterHallScreen(
    viewModel: RoundtableViewModel,
    characters: List<Character>,
    onToggleActive: (Character) -> Unit,
    onEditCharacter: (Character) -> Unit,
    onAddCharacter: () -> Unit,
    onDeleteCharacter: (String) -> Unit
) {
    val context = LocalContext.current
    val groups by viewModel.allGroups.collectAsState()
    val detailContent by viewModel.currentDetailSkillContent.collectAsState()

    var showSaveGroupDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var groupDesc by remember { mutableStateOf("") }

    var groupToDelete by remember { mutableStateOf<com.elio.skillroundtable.data.CharacterGroup?>(null) }
    var detailCharacter by remember { mutableStateOf<Character?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
    ) {
        // 顶部标题和控制按钮 (Notion线性单行版)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "智囊设定殿堂",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 如果当前有激活的角色，显示另存为分组的星标
                if (characters.any { it.isActive }) {
                    IconButton(
                        onClick = {
                            groupName = ""
                            groupDesc = ""
                            showSaveGroupDialog = true
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .bounceClick()
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "存为分组",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 极简加号“添客”IconButton
                IconButton(
                    onClick = onAddCharacter,
                    modifier = Modifier
                        .size(36.dp)
                        .bounceClick()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "录入新智囊",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 滑动 Chip 分组栏 (Notion 线性化极窄版)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            items(groups) { group ->
                val bgColor = if (group.isPreset) PrimaryAccent.copy(alpha = 0.05f) else SecondaryAccent.copy(alpha = 0.05f)
                val strokeColor = if (group.isPreset) PrimaryAccent else SecondaryAccent
                Surface(
                    modifier = Modifier
                        .bounceClick()
                        .clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = {
                                viewModel.applyCharacterGroup(group)
                                Toast.makeText(context, "已应用角色预设: ${group.name}", Toast.LENGTH_SHORT).show()
                            },
                            onLongClick = {
                                if (!group.isPreset) {
                                    groupToDelete = group
                                }
                            }
                        ),
                    color = bgColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, strokeColor.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(strokeColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = group.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 智囊卡片名册 (Notion 线性 Bento 化)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = characters,
                key = { it.id }
            ) { char ->
                var showMoreMenu by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick()
                        .clickable {
                            detailCharacter = char
                            viewModel.loadDetailSkill(char, context)
                        },
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, PrimaryAccent.copy(alpha = 0.15f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            CharacterAvatar(
                                avatar = char.avatar,
                                name = char.name,
                                size = 42.dp,
                                textSize = 20.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = char.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = char.tagline,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 右侧操作区：入席/旁听胶囊状态 + 更多三点菜单
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 入席/旁听胶囊按钮
                            val isActive = char.isActive
                            val capsuleBg = if (isActive) SecondaryAccent.copy(alpha = 0.08f) else Color.Transparent
                            val capsuleBorderColor = if (isActive) SecondaryAccent.copy(alpha = 0.4f) else TextSecondary.copy(alpha = 0.3f)
                            val capsuleTextColor = if (isActive) SecondaryAccent else TextSecondary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(capsuleBg)
                                    .border(0.5.dp, capsuleBorderColor, RoundedCornerShape(6.dp))
                                    .bounceClick()
                                    .clickable { onToggleActive(char) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isActive) "入席" else "旁听",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = capsuleTextColor
                                )
                            }

                            // 更多菜单
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.size(24.dp).bounceClick()
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "更多选项",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("修改设定") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showMoreMenu = false
                                            onEditCharacter(char)
                                        }
                                    )
                                    if (char.id != "zhang_xuefeng") {
                                        DropdownMenuItem(
                                            text = { Text("请离会议", color = Color.Red.copy(alpha = 0.8f)) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red.copy(alpha = 0.8f)) },
                                            onClick = {
                                                showMoreMenu = false
                                                onDeleteCharacter(char.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 弹窗1：保存自定义分组 Dialog
    if (showSaveGroupDialog) {
        AlertDialog(
            onDismissRequest = { showSaveGroupDialog = false },
            title = { Text("保存当前勾选为自定义分组", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将当前所有被选中的智囊席位另存为一个快速启动预设组。", color = TextSecondary, fontSize = 13.sp)
                    TextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        placeholder = { Text("分组名称 (如：智能开发组)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    TextField(
                        value = groupDesc,
                        onValueChange = { groupDesc = it },
                        placeholder = { Text("描述信息 (如：精选技术和产品大佬)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            viewModel.saveCurrentActiveAsGroup(groupName.trim(), groupDesc.trim())
                            showSaveGroupDialog = false
                            Toast.makeText(context, "分组 [${groupName}] 已保存！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "请输入分组名称", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveGroupDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardBg
        )
    }

    // 弹窗2：删除自定义分组确认 Dialog
    if (groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("删除自定义预设分组", color = TextPrimary) },
            text = { Text("确定要删除 [${groupToDelete!!.name}] 吗？", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(groupToDelete!!.id)
                        Toast.makeText(context, "分组 [${groupToDelete!!.name}] 已删除", Toast.LENGTH_SHORT).show()
                        groupToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                ) {
                    Text("确定删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardBg
        )
    }

    // BottomSheet：角色画像详情 BottomSheet
    if (detailCharacter != null) {
        ModalBottomSheet(
            onDismissRequest = {
                detailCharacter = null
                viewModel.clearDetailSkill()
            },
            containerColor = CardBg,
            contentColor = TextPrimary,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary.copy(alpha = 0.5f)) }
        ) {
            val char = detailCharacter!!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CharacterAvatar(
                        avatar = char.avatar,
                        name = char.name,
                        size = 80.dp,
                        textSize = 40.sp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = char.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "席位顺序: 第 ${char.order} 位",
                            fontSize = 12.sp,
                            color = GoldAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryAccent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .border(1.dp, PrimaryAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "“ ${char.tagline} ”",
                        fontSize = 16.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        lineHeight = 24.sp
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "角色思维模型与决策DNA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (detailContent == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryAccent)
                    }
                } else {
                    MarkdownRender(text = detailContent!!)
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCharacterDialog(
    character: Character?,
    onDismiss: () -> Unit,
    onConfirm: (Character) -> Unit
) {
    var name by remember { mutableStateOf(character?.name ?: "") }
    var avatar by remember { mutableStateOf(character?.avatar ?: "🧙") }
    var tagline by remember { mutableStateOf(character?.tagline ?: "") }
    var systemPrompt by remember { mutableStateOf(character?.systemPrompt ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (character == null) "录入新智囊入席" else "修改智囊 [${character.name}] 设定"
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("智囊名称", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：考研择校官") }
                    )
                }
                item {
                    Text("智囊头像路径 (Assets)", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = avatar,
                        onValueChange = { avatar = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：avatars/elon.jpg") }
                    )
                }
                item {
                    Text("一句座右铭/一句话简介", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：用严苛的录取比劝退投机的学子") }
                    )
                }
                item {
                    Text("角色系统提示词 (System Prompt)", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("输入智囊的学术、商业观点，以及他说话的特定口吻、语气、立场规则。") },
                        maxLines = 15
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        val newId = character?.id ?: "custom_${System.currentTimeMillis()}"
                        val newOrder = character?.order ?: 10
                        onConfirm(
                            Character(
                                id = newId,
                                name = name,
                                avatar = avatar,
                                tagline = tagline,
                                systemPrompt = systemPrompt,
                                skillAssetPath = character?.skillAssetPath ?: "",
                                order = newOrder,
                                isActive = character?.isActive ?: true,
                                skillDescriptionVector = character?.skillDescriptionVector ?: ""
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Text("确定入席")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = CardBg
    )
}

@Composable
fun ApiTelemetryScreen(
    currentSessionId: Long?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.elio.skillroundtable.network.ApiKeyPool.init(context)
        com.elio.skillroundtable.telemetry.TelemetryRepository.init(context)
        com.elio.skillroundtable.telemetry.CloudInteractionSettings.init(context)
    }
    val events by com.elio.skillroundtable.telemetry.TelemetryRepository.events.collectAsState()
    val level by com.elio.skillroundtable.telemetry.TelemetryRepository.level.collectAsState()
    val storageError by com.elio.skillroundtable.telemetry.TelemetryRepository.storageError.collectAsState()
    val cloudInteractionEnabled by com.elio.skillroundtable.telemetry.CloudInteractionSettings.enabled.collectAsState()
    var expandedEventId by remember { mutableStateOf<String?>(null) }
    var showContentDebugWarning by remember { mutableStateOf(false) }
    var showCloudWarning by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val keyStatuses = remember(currentSessionId, refreshTrigger) {
        com.elio.skillroundtable.network.ApiKeyPool.getKeyStatuses(context)
    }
    val currentKeyInfo = remember(currentSessionId, refreshTrigger) {
        currentSessionId?.let { com.elio.skillroundtable.network.ApiKeyPool.getOrBindSessionKey(context, it) }
    }
    val expiresAt = com.elio.skillroundtable.telemetry.TelemetryRepository.contentDebugExpiresAt(context)
    val remainingMinutes = expiresAt?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L) }
    val estimatedBytes = com.elio.skillroundtable.telemetry.TelemetryRepository.estimatedBytes()

    Surface(modifier = Modifier.fillMaxSize(), color = SlateBg) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.bounceClick()) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Text("隐私、遥测与 API 诊断", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("遥测隐私级别", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                when (level) {
                                    com.elio.skillroundtable.telemetry.TelemetryLevel.OFF -> "关闭：不创建本地遥测事件"
                                    com.elio.skillroundtable.telemetry.TelemetryLevel.METADATA_ONLY -> "仅元数据（默认）：不读取或保存请求/回复正文"
                                    com.elio.skillroundtable.telemetry.TelemetryLevel.CONTENT_DEBUG -> "临时正文调试：本机保存脱敏、截断预览，剩余约 ${remainingMinutes ?: 0} 分钟"
                                },
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    com.elio.skillroundtable.telemetry.TelemetryRepository.setLevel(
                                        context,
                                        com.elio.skillroundtable.telemetry.TelemetryLevel.OFF
                                    )
                                }) { Text("关闭") }
                                OutlinedButton(onClick = {
                                    com.elio.skillroundtable.telemetry.TelemetryRepository.setLevel(
                                        context,
                                        com.elio.skillroundtable.telemetry.TelemetryLevel.METADATA_ONLY
                                    )
                                }) { Text("仅元数据") }
                                Button(onClick = {
                                    if (level == com.elio.skillroundtable.telemetry.TelemetryLevel.CONTENT_DEBUG) {
                                        com.elio.skillroundtable.telemetry.TelemetryRepository.disableContentDebugAndPurgePreviews(context)
                                    } else {
                                        showContentDebugWarning = true
                                    }
                                }) { Text(if (level == com.elio.skillroundtable.telemetry.TelemetryLevel.CONTENT_DEBUG) "关闭正文调试" else "临时正文调试") }
                            }
                            Divider(color = TextSecondary.copy(alpha = 0.15f))
                            Text("事件 ${events.size} 条 · 估算占用 ${estimatedBytes} bytes · Metadata 最长 7 天", fontSize = 11.sp, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val ok = com.elio.skillroundtable.telemetry.TelemetryRepository.clearAllTelemetry(context)
                                    Toast.makeText(context, if (ok) "遥测已清空" else "遥测清空失败", Toast.LENGTH_SHORT).show()
                                }) { Text("立即清空全部遥测") }
                                if (level == com.elio.skillroundtable.telemetry.TelemetryLevel.CONTENT_DEBUG) {
                                    OutlinedButton(onClick = {
                                        com.elio.skillroundtable.telemetry.TelemetryRepository.disableContentDebugAndPurgePreviews(context)
                                    }) { Text("关闭并删除预览") }
                                }
                            }
                            storageError?.let { Text(it, color = Color.Red, fontSize = 11.sp) }
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("云端会话链优化", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    "默认关闭。关闭不会阻止模型请求发送给 Gemini，只是不额外启用持久化 Interaction 链。",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = cloudInteractionEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) showCloudWarning = true
                                    else com.elio.skillroundtable.telemetry.CloudInteractionSettings.setEnabled(context, false)
                                }
                            )
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.6f))) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("当前会话与 Key", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Key ID：${currentKeyInfo?.id ?: "未分配"}", fontSize = 12.sp, color = GoldAccent)
                            Text("显示名：${currentKeyInfo?.account ?: "无"}", fontSize = 11.sp, color = TextSecondary)
                            Text("Key 状态：${keyStatuses.count { !it.isBanned && !it.isManualDisabled }} 可用 / ${keyStatuses.size} 总数", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                item {
                    Text("最近本地遥测事件", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                if (events.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("暂无遥测事件", color = TextSecondary)
                        }
                    }
                } else {
                    items(events, key = { it.id }) { event ->
                        val success = event.statusCode?.let { it in 200..299 } == true
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (event.containsContentPreview) {
                                    expandedEventId = if (expandedEventId == event.id) null else event.id
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            border = BorderStroke(1.dp, if (success) PrimaryAccent.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.25f))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("[${event.keyId ?: "none"}] ${event.model ?: event.endpoint}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${event.statusCode ?: "ERR"} · ${event.durationMs}ms", fontSize = 10.sp, color = TextSecondary)
                                }
                                Text(event.endpoint, fontSize = 10.sp, color = TextSecondary)
                                event.failureType?.let { Text("错误分类：$it", fontSize = 10.sp, color = Color.Red) }
                                if (event.hasThoughtStep) Text("响应包含 thought step（摘要未保存）", fontSize = 10.sp, color = GoldAccent)
                                if (event.containsContentPreview) {
                                    Text("含脱敏截断预览，点击展开", fontSize = 10.sp, color = GoldAccent)
                                }
                                if (expandedEventId == event.id) {
                                    event.requestPreview?.let {
                                        Text("请求预览", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GoldAccent)
                                        Text(it, fontSize = 10.sp, color = TextSecondary)
                                    }
                                    event.responsePreview?.let {
                                        Text("响应预览", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GoldAccent)
                                        Text(it, fontSize = 10.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContentDebugWarning) {
        AlertDialog(
            onDismissRequest = { showContentDebugWarning = false },
            title = { Text("开启临时正文调试？") },
            text = { Text("开启后，应用会在本机临时保存经过脱敏和截断的请求/回复预览，最长 24 小时。请勿在调试期间输入密码、私钥或其他高度敏感信息。Release 构建不允许开启。") },
            confirmButton = {
                Button(onClick = {
                    val ok = com.elio.skillroundtable.telemetry.TelemetryRepository.enableContentDebug(context)
                    Toast.makeText(context, if (ok) "正文调试已开启，24 小时后自动过期" else "当前构建不允许开启", Toast.LENGTH_SHORT).show()
                    showContentDebugWarning = false
                }) { Text("确认开启") }
            },
            dismissButton = { TextButton(onClick = { showContentDebugWarning = false }) { Text("取消") } }
        )
    }

    if (showCloudWarning) {
        AlertDialog(
            onDismissRequest = { showCloudWarning = false },
            title = { Text("启用云端会话链优化？") },
            text = { Text("开启后，请求上下文会继续发送给 Google Gemini，并允许服务商使用持久化 Interaction 链维持续写。服务商侧保留受其政策约束，本应用无法控制远端保留或保证远端删除。") },
            confirmButton = {
                Button(onClick = {
                    com.elio.skillroundtable.telemetry.CloudInteractionSettings.setEnabled(context, true)
                    showCloudWarning = false
                }) { Text("确认启用") }
            },
            dismissButton = { TextButton(onClick = { showCloudWarning = false }) { Text("取消") } }
        )
    }
}
