package com.example.skillroundtable

import android.os.Bundle
import android.widget.Toast
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
import com.example.skillroundtable.data.Character
import com.example.skillroundtable.data.ChatSession
import com.example.skillroundtable.data.Message
import com.example.skillroundtable.viewmodel.RoundtableViewModel

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
    val typingCharacterId by viewModel.typingCharacterId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddCharacterDialog by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<Character?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Automatically trigger API key dialog if not set on first start
    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) {
            showApiKeyDialog = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = CardBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "圆桌脑暴") },
                    label = { Text("圆桌脑暴") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "智囊大厅") },
                    label = { Text("智囊大厅") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
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
                        typingCharacterId = typingCharacterId,
                        apiKey = apiKey,
                        onOpenApiKeyConfig = { showApiKeyDialog = true },
                        onToggleDrawer = { showDrawer = !showDrawer }
                    )
                }
                1 -> {
                    CharacterHallScreen(
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
                            text = "🧠 脑暴会议历史",
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
                                .testTag("new_session_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "新建会议")
                            Spacer(Modifier.width(8.dp))
                            Text("开启全新圆桌脑暴")
                        }

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(allSessions) { session ->
                                val isSelected = session.id == currentSessionId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectSession(session.id)
                                            showDrawer = false
                                        }
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
            if (showApiKeyDialog) {
                ApiKeyConfigDialog(
                    currentKey = apiKey,
                    onDismiss = { showApiKeyDialog = false },
                    onSave = { newKey ->
                        viewModel.setApiKey(newKey)
                        showApiKeyDialog = false
                        Toast.makeText(context, "API Key 配置成功", Toast.LENGTH_SHORT).show()
                    }
                )
            }

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
    typingCharacterId: String?,
    apiKey: String,
    onOpenApiKeyConfig: () -> Unit,
    onToggleDrawer: () -> Unit
) {
    val listState = rememberLazyListState()
    var userQuestionText by remember { mutableStateOf("") }

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "历史会议", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = currentSession?.title ?: "AI 智囊圆桌",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isRoundtableRunning) "🧠 诸位智囊连环论证中..." else "🔮 虚左以待，请君提问",
                        fontSize = 12.sp,
                        color = if (isRoundtableRunning) SecondaryAccent else TextSecondary
                    )
                }
            }

            IconButton(onClick = onOpenApiKeyConfig) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "密钥设置",
                    tint = if (apiKey.isNotBlank()) SecondaryAccent else GoldAccent
                )
            }
        }

        // Seating Roundtable Diagram Row
        RoundtableSeatingDiagram(
            characters = allCharacters.filter { it.isActive },
            typingCharacterId = typingCharacterId,
            currentMessages = currentMessages
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
                    Text(
                        "🧠",
                        fontSize = 64.sp,
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
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                    ) {
                        Text("开启首个圆桌会议")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                items(currentMessages) { message ->
                    MessageBubble(message = message)
                }

                if (isRoundtableRunning && typingCharacterId != null) {
                    item {
                        val typingChar = allCharacters.find { it.id == typingCharacterId }
                        if (typingChar != null) {
                            TypingIndicatorBubble(character = typingChar)
                        }
                    }
                }
            }
        }

        // Action Toolbar
        AnimatedVisibility(visible = currentSession != null && !isRoundtableRunning && currentMessages.isNotEmpty()) {
            val hasActiveChars = allCharacters.any { it.isActive }
            if (hasActiveChars) {
                val lastQuestionIndex = currentMessages.indexOfLast { it.senderId == "user" }
                val messagesSinceQuestion = if (lastQuestionIndex != -1) currentMessages.subList(lastQuestionIndex + 1, currentMessages.size) else emptyList()
                val activeCount = allCharacters.count { it.isActive }
                val repliedCount = messagesSinceQuestion.map { it.senderId }.distinct().count()

                if (repliedCount < activeCount) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { viewModel.triggerNextCharacterManual() },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("催促剩余智囊作答 (${repliedCount}/${activeCount} 已言)")
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
                    .background(CardBg)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = userQuestionText,
                    onValueChange = { userQuestionText = it },
                    placeholder = { Text("向诸位智囊提问...", color = TextSecondary) },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("chat_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SlateBg,
                        unfocusedContainerColor = SlateBg,
                        disabledContainerColor = SlateBg,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 4,
                    enabled = !isRoundtableRunning
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (userQuestionText.isNotBlank()) {
                            viewModel.askQuestion(userQuestionText)
                            userQuestionText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isRoundtableRunning || userQuestionText.isBlank()) TextSecondary.copy(
                                alpha = 0.3f
                            ) else PrimaryAccent, CircleShape
                        )
                        .testTag("send_button"),
                    enabled = !isRoundtableRunning && userQuestionText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun RoundtableSeatingDiagram(
    characters: List<Character>,
    typingCharacterId: String?,
    currentMessages: List<Message>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateBg)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🪵 智囊群英圆桌会议席位",
            fontSize = 11.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .border(1.dp, PrimaryAccent.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(characters) { char ->
                val isTyping = char.id == typingCharacterId

                // Determine speaker state for current round
                val lastQuestionIndex = currentMessages.indexOfLast { it.senderId == "user" }
                val messagesSinceQuestion = if (lastQuestionIndex != -1) currentMessages.subList(lastQuestionIndex + 1, currentMessages.size) else emptyList()
                val hasReplied = messagesSinceQuestion.any { it.senderId == char.id }

                // Pulse scale animation for typing
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

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .scale(if (isTyping) pulseScale else 1.0f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTyping) PrimaryAccent.copy(alpha = 0.3f)
                                else if (hasReplied) SecondaryAccent.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isTyping) 2.dp else 1.dp,
                                color = if (isTyping) PrimaryAccent
                                else if (hasReplied) SecondaryAccent
                                else TextSecondary.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            text = char.avatar,
                            fontSize = 28.sp
                        )

                        // Action bubble or tick badge
                        if (hasReplied) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryAccent)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp).align(Alignment.Center)
                                )
                            }
                        } else if (isTyping) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryAccent)
                                    .align(Alignment.BottomEnd)
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.fillMaxSize().padding(2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = char.name,
                        fontSize = 12.sp,
                        color = if (isTyping) PrimaryAccent else if (hasReplied) TextPrimary else TextSecondary,
                        fontWeight = if (isTyping || hasReplied) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
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
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(PrimaryAccent.copy(alpha = 0.1f))
                    .border(1.dp, PrimaryAccent.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(message.avatar, fontSize = 24.sp)
            }
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
                    .clickable {
                        clipboardManager.setText(AnnotatedString(message.text))
                        Toast.makeText(context, "已复制至剪贴板", Toast.LENGTH_SHORT).show()
                    }
                    .padding(14.dp)
            ) {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(PrimaryAccent.copy(alpha = 0.2f))
                    .border(1.dp, PrimaryAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(message.avatar, fontSize = 24.sp)
            }
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
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(PrimaryAccent.copy(alpha = 0.1f))
                .border(1.dp, PrimaryAccent.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(character.avatar, fontSize = 24.sp)
        }
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
fun CharacterHallScreen(
    characters: List<Character>,
    onToggleActive: (Character) -> Unit,
    onEditCharacter: (Character) -> Unit,
    onAddCharacter: () -> Unit,
    onDeleteCharacter: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "💼 智囊设定殿堂",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "在此设定参会角色，可支持随时自定义扩充智囊！",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Button(
                onClick = onAddCharacter,
                colors = ButtonDefaults.buttonColors(containerColor = SecondaryAccent)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添客")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(characters) { char ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryAccent.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(char.avatar, fontSize = 28.sp)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = char.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = TextPrimary
                                        )
                                        if (char.id == "zhang_xuefeng") {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(GoldAccent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("首发Skill", color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(
                                        text = char.tagline,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Switch(
                                checked = char.isActive,
                                onCheckedChange = { onToggleActive(char) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SecondaryAccent,
                                    checkedTrackColor = SecondaryAccent.copy(alpha = 0.4f)
                                )
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Divider(color = TextSecondary.copy(alpha = 0.1f))
                        Spacer(Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { onEditCharacter(char) }) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("修改Prompt设定")
                            }

                            // Do not allow deleting the core first skill zhang_xuefeng, but allow others
                            if (char.id != "zhang_xuefeng") {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { onDeleteCharacter(char.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red.copy(alpha = 0.8f))
                                    Spacer(Modifier.width(6.dp))
                                    Text("请离会议", color = Color.Red.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyConfigDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyText by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = GoldAccent)
                Spacer(Modifier.width(8.dp))
                Text("配置 Gemini API 密钥")
            }
        },
        text = {
            Column {
                Text(
                    text = "请在下方填入你的 Gemini API Key。圆桌会议脑暴由 Gemini 提供底层大语言能力支持。",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    placeholder = { Text("AI Studio 密钥或标准 API Key...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "提示：API 密钥将保存在运行内存中。如不需要本地存储，可在此进行配置。",
                    fontSize = 11.sp,
                    color = GoldAccent
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyText) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Text("保存并连接")
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
                text = if (character == null) "🖋️ 录入新智囊入席" else "🖋️ 修改智囊 [${character.name}] 设定"
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
                    Text("智囊头像 (单个Emoji)", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = avatar,
                        onValueChange = { avatar = it.take(2) }, // Limit to 1-2 chars for single Emoji
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：🎓") }
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
                                isActive = character?.isActive ?: true
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
