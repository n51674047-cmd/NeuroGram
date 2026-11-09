package com.neurogram.app

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage

private var appDarkTheme by mutableStateOf(false)
private var appMessageScale by mutableFloatStateOf(1f)

private val Bg: Color get() = if (appDarkTheme) Color(0xFF0B1220) else Color(0xFFF3F6FC)
private val Panel: Color get() = if (appDarkTheme) Color(0xFF151F30) else Color(0xFFFFFFFF)
private val Red: Color get() = if (appDarkTheme) Color(0xFF82AAFF) else Color(0xFF2878E7)
private val Muted: Color get() = if (appDarkTheme) Color(0xFF9AAAC0) else Color(0xFF718096)
private val Ink: Color get() = if (appDarkTheme) Color(0xFFF0F5FF) else Color(0xFF17243A)
private val Divider: Color get() = if (appDarkTheme) Color(0xFF29364A) else Color(0xFFE3EAF4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (appDarkTheme) darkColorScheme(
                background = Bg, surface = Panel, primary = Red,
                onBackground = Ink, onSurface = Ink
            ) else lightColorScheme(
                background = Bg, surface = Panel, primary = Red,
                onBackground = Ink, onSurface = Ink
            )) { NeuroGramApp() }
        }
    }
}

private data class ChatItem(
    val id: String,
    val title: String,
    val lastMessage: String
)

private data class MessageItem(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String
)

@Composable
private fun NeuroGramApp() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("neurogram_settings", android.content.Context.MODE_PRIVATE)
        appDarkTheme = prefs.getBoolean("dark_theme", false)
        appMessageScale = prefs.getFloat("message_scale", 1f)
    }
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var page by remember { mutableStateOf(if (auth.currentUser != null) "chats" else "login") }
    var previousPage by remember { mutableStateOf("chats") }
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var selectedChatId by remember { mutableStateOf("") }
    var selectedChatTitle by remember { mutableStateOf("") }
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
            page = if (firebaseAuth.currentUser == null) "login" else if (page == "login" || page == "register") "chats" else page
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid == null) {
            chats = emptyList()
            onDispose { }
        } else {
            val registration = db.collection("chats")
                .whereArrayContains("members", uid)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        error = "Не удалось загрузить чаты: ${exception.localizedMessage}"
                    } else {
                        chats = snapshot?.documents?.map { doc ->
                            val members = doc.get("members") as? List<*> ?: emptyList<Any>()
                            val otherId = members.firstOrNull { it?.toString() != uid }?.toString()
                            val names = doc.get("memberNames") as? Map<*, *> ?: emptyMap<Any, Any>()
                            val title = names[otherId]?.toString() ?: "Диалог NeuroGram"
                            ChatItem(doc.id, title, doc.getString("lastMessage") ?: "Нет сообщений")
                        }?.sortedByDescending { it.id } ?: emptyList()
                    }
                }
            onDispose { registration.remove() }
        }
    }

    fun saveProfile(userId: String, userEmail: String, displayName: String, onDone: () -> Unit) {
        val safeName = displayName.ifBlank { userEmail.substringBefore("@") }
        val username = safeName.removePrefix("@").lowercase()
        val profile = mapOf("uid" to userId, "email" to userEmail.lowercase(),
            "nickname" to username, "username" to username, "usernameLower" to username)
        val usernameRef = db.collection("usernames").document(username)
        db.runTransaction { transaction ->
            val existing = transaction.get(usernameRef)
            if (existing.exists() && existing.getString("uid") != userId) {
                throw IllegalStateException("Этот username уже занят")
            }
            transaction.set(usernameRef, mapOf("uid" to userId))
            transaction.set(db.collection("users").document(userId), profile)
            transaction.set(db.collection("publicProfiles").document(userId), mapOf(
                "uid" to userId, "nickname" to username, "username" to username, "usernameLower" to username))
            null
        }.addOnSuccessListener { onDone() }.addOnFailureListener {
            loading = false; error = it.localizedMessage ?: "Не удалось сохранить username"
        }
    }

    fun submitAuth() {
        error = ""
        val cleanEmail = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            error = "Введи корректный адрес Gmail или другой email"
            return
        }
        if (password.length < 6) {
            error = "Пароль должен содержать минимум 6 символов"
            return
        }
        val cleanUsername = nickname.trim().removePrefix("@").lowercase()
        if (isRegister && !Regex("^[a-z0-9_]{5,20}$").matches(cleanUsername)) {
            error = "Username: 5–20 символов, латиница, цифры и _"
            return
        }
        loading = true
        val task = if (isRegister) auth.createUserWithEmailAndPassword(cleanEmail, password)
            else auth.signInWithEmailAndPassword(cleanEmail, password)
        task.addOnSuccessListener { result ->
            val user = result.user
            if (user == null) {
                loading = false
                error = "Firebase не вернул аккаунт"
                return@addOnSuccessListener
            }
            if (isRegister) {
                saveProfile(user.uid, cleanEmail, cleanUsername) {
                    loading = false; error = ""; nickname = cleanUsername; page = "chats"
                }
            } else {
                db.collection("users").document(user.uid).get().addOnSuccessListener { profile ->
                    val existingUsername = profile.getString("username") ?: profile.getString("nickname")
                        ?: cleanEmail.substringBefore("@")
                    saveProfile(user.uid, cleanEmail, existingUsername) {
                        loading = false; error = ""; nickname = existingUsername; page = "chats"
                    }
                }.addOnFailureListener { loading = false; error = "Не удалось загрузить профиль: ${it.localizedMessage}" }
            }
        }.addOnFailureListener {
            loading = false
            error = it.localizedMessage ?: "Не удалось выполнить вход"
        }
    }

    fun startChat(targetUsername: String) {
        val me = currentUser ?: return
        val cleanTarget = targetUsername.trim().removePrefix("@").lowercase()
        if (!Regex("^[a-z0-9_]{5,20}$").matches(cleanTarget)) {
            error = "Введи username: 5–20 символов"; return
        }
        if (cleanTarget == nickname.trim().removePrefix("@").lowercase()) {
            error = "Нельзя начать диалог с самим собой"; return
        }
        loading = true
        db.collection("publicProfiles").whereEqualTo("usernameLower", cleanTarget).limit(1).get()
            .addOnSuccessListener { result ->
                val target = result.documents.firstOrNull()
                if (target == null) {
                    loading = false
                    error = "Пользователь с таким email пока не зарегистрирован"
                    return@addOnSuccessListener
                }
                val otherId = target.id
                val otherName = target.getString("username") ?: cleanTarget
                val myName = nickname.ifBlank { me.email?.substringBefore("@") ?: "Я" }
                val members = listOf(me.uid, otherId).sorted()
                val chatId = members.joinToString("_")
                val names = mapOf(me.uid to myName, otherId to otherName)
                val data = mapOf(
                    "members" to members,
                    "memberNames" to names,
                    "lastMessage" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("chats").document(chatId).set(data, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        loading = false
                        error = ""
                        selectedChatId = chatId
                        selectedChatTitle = otherName
                        page = "chat"
                    }
                    .addOnFailureListener { loading = false; error = "Не удалось создать чат: ${it.localizedMessage}" }
            }
            .addOnFailureListener { loading = false; error = "Ошибка поиска: ${it.localizedMessage}" }
    }

    BackHandler(enabled = currentUser != null) {
        if (drawerState.isOpen) drawerScope.launch { drawerState.close() }
        else page = when (page) {
            "chat", "profile" -> "chats"
            "privacy", "notifications", "chat_settings", "appearance", "about" -> previousPage
            "chats" -> "chats"
            else -> "chats"
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentUser != null,
        drawerContent = {
            if (currentUser != null) {
                ModalDrawerSheet(drawerContainerColor = Panel) {
                    Spacer(Modifier.height(30.dp))
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(Red, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Text("N", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("NeuroGram", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("МЕНЮ МЕССЕНДЖЕРА", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 10.dp))
                    Text("ОСНОВНОЕ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp))
                    NavigationDrawerItem(label = { Text("Мои чаты") }, selected = page == "chats",
                        onClick = { page = "chats"; drawerScope.launch { drawerState.close() } },
                        icon = { Text("▤", color = Red, fontSize = 19.sp) }, modifier = Modifier.padding(horizontal = 10.dp))
                    NavigationDrawerItem(label = { Text("Профиль") }, selected = page == "profile",
                        onClick = { page = "profile"; drawerScope.launch { drawerState.close() } },
                        icon = { Text("●", color = Red, fontSize = 19.sp) }, modifier = Modifier.padding(horizontal = 10.dp))
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 10.dp))
                    Text("НАСТРОЙКИ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp))
                    listOf(
                        "Приватность и безопасность" to "privacy",
                        "Уведомления" to "notifications",
                        "Чаты и данные" to "chat_settings",
                        "Оформление" to "appearance",
                        "О приложении" to "about"
                    ).forEach { (item, destination) ->
                        NavigationDrawerItem(label = { Text(item) }, selected = page == destination,
                            onClick = { previousPage = "chats"; page = destination; drawerScope.launch { drawerState.close() } },
                            icon = { Text(when(destination) { "privacy" -> "◈"; "notifications" -> "♢"; "chat_settings" -> "▣"; "appearance" -> "◐"; else -> "ⓘ" }, color = Red, fontSize = 18.sp) },
                            modifier = Modifier.padding(horizontal = 10.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("NeuroGram • версия 1.0", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(20.dp))
                }
            }
        }
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            AnimatedContent(
                targetState = page,
                label = "screen",
                transitionSpec = {
                    (slideInVertically { it / 12 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 12 } + fadeOut())
                }
            ) { target ->
                when (target) {
                    "login", "register" -> AuthScreen(
                        register = target == "register",
                        email = email, onEmail = { email = it },
                        password = password, onPassword = { password = it },
                        nickname = nickname, onNickname = { nickname = it },
                        error = error, loading = loading,
                        onSubmit = { isRegister = target == "register"; submitAuth() },
                        onSwitch = {
                            error = ""
                            page = if (target == "register") "login" else "register"
                        }
                    )
                    "chats" -> ChatList(
                        userLabel = currentUser?.email ?: "Аккаунт NeuroGram",
                        chats = chats, error = error, loading = loading,
                        onAddChat = { startChat(it) },
                        onOpenChat = { chat ->
                            selectedChatId = chat.id
                            selectedChatTitle = chat.title
                            page = "chat"
                        },
                        onProfile = { page = "profile" },
                        onMenu = { drawerScope.launch { drawerState.open() } }
                    )
                    "privacy", "notifications", "chat_settings", "appearance", "about" ->
                        SettingsScreen(section = target, onBack = { page = previousPage })
                    "chat" -> ChatScreen(
                        chatId = selectedChatId, title = selectedChatTitle,
                        myUid = currentUser?.uid ?: "",
                        myName = nickname.ifBlank { currentUser?.email?.substringBefore("@") ?: "Я" },
                        db = db, onBack = { page = "chats" }
                    )
                    else -> ProfileScreen(
                        uid = currentUser?.uid ?: "",
                        email = currentUser?.email ?: "",
                        db = db, storage = storage,
                        onBack = { page = "chats" },
                        onSettings = { previousPage = "profile"; page = "privacy" },
                        onUsernameChanged = { nickname = it },
                        onLogout = {
                            auth.signOut()
                            email = ""; password = ""; nickname = ""; error = ""
                            page = "login"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(Red, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text("N", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text("NeuroGram", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("ОБЩАЙСЯ БЕЗ ГРАНИЦ", color = Muted, fontSize = 9.sp, letterSpacing = 1.1.sp)
        }
    }
}

@Composable
private fun AuthScreen(
    register: Boolean, email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    nickname: String, onNickname: (String) -> Unit,
    error: String, loading: Boolean, onSubmit: () -> Unit, onSwitch: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 25.dp), verticalArrangement = Arrangement.Center) {
        BrandHeader()
        Spacer(Modifier.height(48.dp))
        Text(if (register) "Создать аккаунт" else "С возвращением", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(if (register) "Присоединяйся к NeuroGram" else "Войди, чтобы продолжить общение", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 25.dp))
        if (register) {
            AuthField(nickname, onNickname, "Username (5–20 символов)")
            Spacer(Modifier.height(12.dp))
        }
        AuthField(email, onEmail, "Электронная почта", KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField(password, onPassword, "Пароль", password = true)
        if (error.isNotBlank()) Text(error, color = Color(0xFFE05260), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(22.dp))
        Button(onClick = onSubmit, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
            Text(if (loading) "Подождите…" else if (register) "Создать аккаунт" else "Войти", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(if (register) "Уже есть аккаунт? " else "Впервые здесь? ", color = Muted, fontSize = 13.sp)
            Text(if (register) "Войти" else "Регистрация", color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onSwitch))
        }
    }
}

@Composable
private fun AuthField(
    value: String, onValue: (String) -> Unit, label: String,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Red, unfocusedBorderColor = Divider,
            focusedLabelColor = Red, unfocusedLabelColor = Muted,
            cursorColor = Red, focusedTextColor = Ink, unfocusedTextColor = Ink,
            focusedContainerColor = Panel, unfocusedContainerColor = Panel
        )
    )
}

@Composable
private fun ChatList(
    userLabel: String, chats: List<ChatItem>, error: String, loading: Boolean,
    onAddChat: (String) -> Unit, onOpenChat: (ChatItem) -> Unit,
    onProfile: () -> Unit, onMenu: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var targetUsername by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 36.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Panel, RoundedCornerShape(14.dp)).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
                    Text("☰", color = Red, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                BrandHeader()
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(42.dp).background(Panel, CircleShape).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
                    Text("●", color = Red, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(36.dp))
            Column {
                Text("Чаты", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(userLabel, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⌕", color = Muted, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text("Поиск по чатам", color = Muted, fontSize = 14.sp)
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFE05260), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Red)
            Spacer(Modifier.height(20.dp))
            if (chats.isEmpty()) {
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(58.dp).background(Bg, CircleShape), contentAlignment = Alignment.Center) {
                        Text("✉", color = Red, fontSize = 27.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Пока нет сообщений", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Найди человека по username и начни диалог.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                    items(chats, key = { it.id }) { chat ->
                        Row(Modifier.fillMaxWidth().background(Panel).clickable { onOpenChat(chat) }.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).background(Color(0xFFDCEEFF), CircleShape), contentAlignment = Alignment.Center) {
                                Text(chat.title.take(1).uppercase(), color = Red, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chat.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(chat.lastMessage.ifBlank { "Начните переписку" }, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
            containerColor = Red, contentColor = Color.White, shape = CircleShape
        ) { Text("+", fontSize = 30.sp, fontWeight = FontWeight.Normal) }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Новое сообщение", color = Ink) },
            text = {
                Column {
                    Text("Найди человека по его username в NeuroGram.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = targetUsername, onValueChange = { targetUsername = it }, label = { Text("@username") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false; onAddChat(targetUsername) }) { Text("Найти", color = Red) } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена", color = Muted) } },
            containerColor = Panel
        )
    }
}

@Composable
private fun ChatScreen(
    chatId: String, title: String, myUid: String, myName: String,
    db: FirebaseFirestore, onBack: () -> Unit
) {
    var messages by remember(chatId) { mutableStateOf<List<MessageItem>>(emptyList()) }
    var draft by remember(chatId) { mutableStateOf("") }
    var error by remember(chatId) { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    DisposableEffect(chatId) {
        val listener = db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) error = "Не удалось загрузить сообщения: ${exception.localizedMessage}"
                else messages = snapshot?.documents?.map { doc ->
                    MessageItem(doc.id, doc.getString("senderId") ?: "", doc.getString("senderName") ?: "Пользователь", doc.getString("text") ?: "")
                } ?: emptyList()
            }
        onDispose { listener.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Red, fontSize = 30.sp, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp))
            Box(Modifier.size(42.dp).background(Color(0xFFDCEEFF), CircleShape), contentAlignment = Alignment.Center) {
                Text(title.take(1).uppercase(), color = Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("был(а) недавно", color = Muted, fontSize = 11.sp)
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFE05260), fontSize = 12.sp)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == myUid
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Text(
                        if (mine) "Вы" else message.senderName,
                        color = Muted, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                    )
                    Text(
                        message.text, color = if (mine) Color.White else Ink, fontSize = 14.sp * appMessageScale,
                        modifier = Modifier.widthIn(max = 290.dp)
                            .background(if (mine) Red else Panel, RoundedCornerShape(17.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Сообщение…", color = Muted) },
                shape = RoundedCornerShape(15.dp), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Red,
                    focusedContainerColor = Panel, unfocusedContainerColor = Panel
                )
            )
            Spacer(Modifier.width(5.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        draft = ""
                        val chatRef = db.collection("chats").document(chatId)
                        chatRef.collection("messages").add(mapOf(
                            "senderId" to myUid, "senderName" to myName,
                            "text" to text, "createdAt" to FieldValue.serverTimestamp()
                        )).addOnFailureListener { error = "Не отправлено: ${it.localizedMessage}" }
                        chatRef.update("lastMessage", text, "updatedAt", FieldValue.serverTimestamp())
                    }
                },
                enabled = draft.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                shape = CircleShape, contentPadding = PaddingValues(14.dp)
            ) { Text("➤", color = Color.White) }
        }
    }
}

@Composable
private fun ProfileScreen(
    uid: String, email: String, db: FirebaseFirestore, storage: FirebaseStorage,
    onBack: () -> Unit, onSettings: () -> Unit, onUsernameChanged: (String) -> Unit, onLogout: () -> Unit
) {
    var username by remember(uid) { mutableStateOf("") }
    var bio by remember(uid) { mutableStateOf("") }
    var avatarUrl by remember(uid) { mutableStateOf("") }
    var editUsername by remember(uid) { mutableStateOf("") }
    var editBio by remember(uid) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showEdit by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            username = doc.getString("username") ?: doc.getString("nickname") ?: ""
            bio = doc.getString("bio") ?: ""
            avatarUrl = doc.getString("avatarUrl") ?: ""
            editUsername = username
            editBio = bio
        }.addOnFailureListener { message = "Не удалось загрузить профиль: ${it.localizedMessage}" }
    }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            loading = true
            val ref = storage.reference.child("avatars/$uid/profile_${System.currentTimeMillis()}.jpg")
            ref.putFile(uri).continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Ошибка загрузки")
                ref.downloadUrl
            }.addOnSuccessListener { download ->
                val url = download.toString()
                val data = mapOf("avatarUrl" to url)
                db.collection("users").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
                db.collection("publicProfiles").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
                avatarUrl = url; loading = false; message = "Аватар обновлён"
            }.addOnFailureListener { loading = false; message = "Не удалось загрузить аватар: ${it.localizedMessage}" }
        }
    }

    fun saveProfileChanges() {
        val nextUsername = editUsername.trim().removePrefix("@").lowercase()
        val nextBio = editBio.trim()
        if (!Regex("^[a-z0-9_]{5,20}$").matches(nextUsername)) { message = "Username: 5–20 символов, латиница, цифры и _"; return }
        if (nextBio.length > 160) { message = "Описание не должно быть длиннее 160 символов"; return }
        loading = true
        val oldUsername = username
        val newRef = db.collection("usernames").document(nextUsername)
        db.runTransaction { transaction ->
            val existing = transaction.get(newRef)
            if (existing.exists() && existing.getString("uid") != uid) throw IllegalStateException("Этот username уже занят")
            val oldRef = if (oldUsername.isNotBlank() && oldUsername != nextUsername)
                db.collection("usernames").document(oldUsername) else null
            val oldDoc = if (oldRef != null) transaction.get(oldRef) else null
            transaction.set(newRef, mapOf("uid" to uid))
            if (oldRef != null && oldDoc?.exists() == true && oldDoc.getString("uid") == uid) transaction.delete(oldRef)
            val common = mapOf("uid" to uid, "username" to nextUsername, "usernameLower" to nextUsername,
                "nickname" to nextUsername, "bio" to nextBio, "avatarUrl" to avatarUrl)
            transaction.set(db.collection("users").document(uid), common, com.google.firebase.firestore.SetOptions.merge())
            transaction.set(db.collection("publicProfiles").document(uid), common, com.google.firebase.firestore.SetOptions.merge())
            null
        }.addOnSuccessListener {
            username = nextUsername; bio = nextBio; editUsername = nextUsername; editBio = nextBio
            onUsernameChanged(nextUsername); loading = false; showEdit = false; message = "Профиль сохранён"
        }.addOnFailureListener { loading = false; message = it.localizedMessage ?: "Не удалось сохранить профиль" }
    }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(46.dp))
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(34.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(94.dp).background(Color(0xFFDCEEFF), CircleShape).clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (avatarUrl.isNotBlank()) AsyncImage(model = avatarUrl, contentDescription = "Аватар", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("＋", color = Red, fontSize = 34.sp)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Профиль", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Нажми на фото, чтобы поставить аватар", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("@${username.ifBlank { "username" }}", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(email, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        Text(bio.ifBlank { "Добавь описание профиля" }, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = { editUsername = username; editBio = bio; showEdit = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Изменить профиль", color = Red) }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("⚙  Настройки", color = Red) }
        if (message.isNotBlank()) Text(message, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Red)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Выйти из аккаунта", color = Color.White) }
        Spacer(Modifier.height(30.dp))
    }
    if (showEdit) AlertDialog(
        onDismissRequest = { if (!loading) showEdit = false },
        title = { Text("Редактировать профиль", color = Ink) },
        text = {
            Column {
                OutlinedTextField(value = editUsername, onValueChange = { editUsername = it }, label = { Text("Username") }, prefix = { Text("@") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = editBio, onValueChange = { editBio = it.take(160) }, label = { Text("Описание") }, minLines = 3, maxLines = 4)
                Text("${editBio.length}/160", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
            }
        },
        confirmButton = { TextButton(onClick = { saveProfileChanges() }, enabled = !loading) { Text("Сохранить", color = Red) } },
        dismissButton = { TextButton(onClick = { showEdit = false }, enabled = !loading) { Text("Отмена", color = Muted) } },
        containerColor = Panel
    )
}

@Composable
private fun SettingToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsScreen(section: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("neurogram_settings", android.content.Context.MODE_PRIVATE) }
    val defaults = mapOf("privacy_online" to true, "privacy_read_receipts" to true, "privacy_photo" to true, "privacy_phone" to false,
        "notify_enabled" to true, "notify_preview" to true, "notify_sound" to true, "notify_vibration" to true,
        "chat_enter_send" to false, "chat_media_auto" to false)
    val options = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply { defaults.forEach { (k, v) -> put(k, prefs.getBoolean(k, v)) } } }
    fun setOption(key: String, value: Boolean) { options[key] = value; prefs.edit().putBoolean(key, value).apply() }
    fun saveTheme(value: Boolean) { appDarkTheme = value; prefs.edit().putBoolean("dark_theme", value).apply() }
    fun saveScale(value: Float) { appMessageScale = value; prefs.edit().putFloat("message_scale", value).apply() }
    val title = when(section) {
        "privacy" -> "Приватность и безопасность"
        "notifications" -> "Уведомления"
        "chat_settings" -> "Чаты и данные"
        "appearance" -> "Оформление"
        else -> "О приложении"
    }
    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(46.dp))
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(28.dp))
        Text(title, color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        when(section) {
            "privacy" -> Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(17.dp)) {
                SettingToggleRow("Показывать статус в сети", "Видимость статуса активности", options["privacy_online"] == true) { setOption("privacy_online", it) }
                SettingToggleRow("Отчёты о прочтении", "Разрешить отметки о прочтении сообщений", options["privacy_read_receipts"] == true) { setOption("privacy_read_receipts", it) }
                SettingToggleRow("Фото профиля всем", "Разрешить другим пользователям видеть фото", options["privacy_photo"] == true) { setOption("privacy_photo", it) }
                SettingToggleRow("Разрешить поиск по телефону", "Поиск аккаунта по номеру телефона", options["privacy_phone"] == true) { setOption("privacy_phone", it) }
                Text("Настройки приватности пока сохраняются локально; серверное применение требует отдельной реализации.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
            "notifications" -> Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(17.dp)) {
                SettingToggleRow("Уведомления", "Общий переключатель уведомлений", options["notify_enabled"] == true) { setOption("notify_enabled", it) }
                SettingToggleRow("Предпросмотр сообщений", "Показывать текст в уведомлениях", options["notify_preview"] == true) { setOption("notify_preview", it) }
                SettingToggleRow("Звук", "Звуковой сигнал новых сообщений", options["notify_sound"] == true) { setOption("notify_sound", it) }
                SettingToggleRow("Вибрация", "Вибрация при новых сообщениях", options["notify_vibration"] == true) { setOption("notify_vibration", it) }
                Text("Реальные push-уведомления будут подключены отдельно.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
            "chat_settings" -> Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(17.dp)) {
                SettingToggleRow("Отправка клавишей Enter", "Отправлять сообщение нажатием Enter", options["chat_enter_send"] == true) { setOption("chat_enter_send", it) }
                SettingToggleRow("Автозагрузка медиа", "Автоматически загружать фото и видео", options["chat_media_auto"] == true) { setOption("chat_media_auto", it) }
                Text("Размер текста сообщений", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = appMessageScale, onValueChange = { saveScale(it) }, valueRange = 0.85f..1.3f, steps = 8)
                Text(when { appMessageScale < 0.98f -> "Маленький"; appMessageScale > 1.12f -> "Крупный"; else -> "Стандартный" }, color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            "appearance" -> Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(17.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (appDarkTheme) "Тёмная тема" else "Светлая тема", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Переключение светлого и тёмного режима", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = appDarkTheme, onCheckedChange = { saveTheme(it) })
                }
            }
            else -> Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(17.dp)) {
                Text("NeuroGram", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Версия 1.0", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Мессенджер находится в разработке.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}
