package com.neurogram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

private val Bg = Color(0xFFF2F4F7)
private val Panel = Color(0xFFFFFFFF)
private val Red = Color(0xFF3390EC)
private val Muted = Color(0xFF7D8B99)
private val Ink = Color(0xFF17212B)
private val Divider = Color(0xFFE7EBEF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
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
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var page by remember { mutableStateOf(if (auth.currentUser != null) "chats" else "login") }
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var selectedChatId by remember { mutableStateOf("") }
    var selectedChatTitle by remember { mutableStateOf("") }
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }

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
                    chats = chats,
                    error = error,
                    loading = loading,
                    onAddChat = { startChat(it) },
                    onOpenChat = { chat ->
                        selectedChatId = chat.id
                        selectedChatTitle = chat.title
                        page = "chat"
                    },
                    onProfile = { page = "profile" }
                )
                "chat" -> ChatScreen(
                    chatId = selectedChatId,
                    title = selectedChatTitle,
                    myUid = currentUser?.uid ?: "",
                    myName = nickname.ifBlank { currentUser?.email?.substringBefore("@") ?: "Я" },
                    db = db,
                    onBack = { page = "chats" }
                )
                else -> ProfileScreen(
                    email = currentUser?.email ?: "",
                    onBack = { page = "chats" },
                    onLogout = {
                        auth.signOut()
                        email = ""
                        password = ""
                        nickname = ""
                        error = ""
                        page = "login"
                    }
                )
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
    onAddChat: (String) -> Unit, onOpenChat: (ChatItem) -> Unit, onProfile: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var targetUsername by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 18.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandHeader()
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(42.dp).background(Panel, CircleShape).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
                Text("●", color = Red, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Чаты", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(userLabel, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = Red), shape = RoundedCornerShape(13.dp)) {
                Text("+ Написать", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
            confirmButton = {
                TextButton(onClick = { showAdd = false; onAddChat(targetUsername) }) { Text("Найти", color = Red) }
            },
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
                        message.text, color = if (mine) Color.White else Ink, fontSize = 14.sp,
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
private fun ProfileScreen(email: String, onBack: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(20.dp)) {
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(35.dp))
        Box(Modifier.size(76.dp).background(Color(0xFFDCEEFF), CircleShape), contentAlignment = Alignment.Center) {
            Text("●", color = Red, fontSize = 30.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Профиль", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(email, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(25.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
            Text("Выйти из аккаунта", color = Color.White)
        }
    }
}

