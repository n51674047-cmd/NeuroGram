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

private val Bg = Color(0xFF09090D)
private val Panel = Color(0xFF171720)
private val Red = Color(0xFFFF334F)
private val Muted = Color(0xFF92929F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Bg, surface = Panel, primary = Red,
                onBackground = Color.White, onSurface = Color.White
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
        val profile = mapOf(
            "uid" to userId,
            "email" to userEmail.lowercase(),
            "nickname" to safeName
        )
        db.collection("users").document(userId).set(profile)
            .addOnSuccessListener {
                db.collection("publicProfiles").document(userId).set(profile)
                    .addOnSuccessListener { onDone() }
                    .addOnFailureListener { loading = false; error = "Аккаунт создан, но профиль не сохранился: ${it.localizedMessage}" }
            }
            .addOnFailureListener { loading = false; error = "Не удалось сохранить профиль: ${it.localizedMessage}" }
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
        if (isRegister && nickname.isBlank()) {
            error = "Введи имя пользователя"
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
            saveProfile(user.uid, cleanEmail, nickname.trim().ifBlank { cleanEmail.substringBefore("@") }) {
                loading = false
                error = ""
                page = "chats"
            }
        }.addOnFailureListener {
            loading = false
            error = it.localizedMessage ?: "Не удалось выполнить вход"
        }
    }

    fun startChat(targetEmail: String) {
        val me = currentUser ?: return
        val cleanTarget = targetEmail.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanTarget).matches()) {
            error = "Введи корректный email собеседника"
            return
        }
        if (cleanTarget == me.email?.lowercase()) {
            error = "Нельзя начать диалог с самим собой"
            return
        }
        loading = true
        db.collection("publicProfiles").whereEqualTo("email", cleanTarget).limit(1).get()
            .addOnSuccessListener { result ->
                val target = result.documents.firstOrNull()
                if (target == null) {
                    loading = false
                    error = "Пользователь с таким email пока не зарегистрирован"
                    return@addOnSuccessListener
                }
                val otherId = target.id
                val otherName = target.getString("nickname") ?: cleanTarget.substringBefore("@")
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
        Box(Modifier.size(46.dp).background(Red, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Text("N", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("NeuroGram", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("ТВОЁ ОБЩЕНИЕ. ТВОИ ПРАВИЛА.", color = Muted, fontSize = 9.sp, letterSpacing = 1.3.sp)
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
    Column(Modifier.fillMaxSize().padding(horizontal = 25.dp), verticalArrangement = Arrangement.Center) {
        BrandHeader()
        Spacer(Modifier.height(42.dp))
        Text(if (register) "Создать аккаунт" else "С возвращением", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Вход по электронной почте", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 7.dp, bottom = 25.dp))
        if (register) {
            AuthField(nickname, onNickname, "Имя пользователя")
            Spacer(Modifier.height(12.dp))
        }
        AuthField(email, onEmail, "Gmail или email", KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField(password, onPassword, "Пароль", password = true)
        if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(22.dp))
        Button(onClick = onSubmit, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
            Text(if (loading) "Подождите…" else if (register) "Зарегистрироваться" else "Войти", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(17.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(if (register) "Уже есть аккаунт? " else "Нет аккаунта? ", color = Muted, fontSize = 13.sp)
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
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(15.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Red, unfocusedBorderColor = Color(0xFF353541),
            focusedLabelColor = Red, unfocusedLabelColor = Muted,
            cursorColor = Red, focusedTextColor = Color.White, unfocusedTextColor = Color.White
        )
    )
}

@Composable
private fun ChatList(
    userLabel: String, chats: List<ChatItem>, error: String, loading: Boolean,
    onAddChat: (String) -> Unit, onOpenChat: (ChatItem) -> Unit, onProfile: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var targetEmail by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandHeader()
            Spacer(Modifier.weight(1f))
            Text("●", color = Red, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onProfile).padding(10.dp))
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Сообщения", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text(userLabel, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = Red), shape = RoundedCornerShape(13.dp)) {
                Text("+ Чат")
            }
        }
        if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Red)
        Spacer(Modifier.height(18.dp))
        if (chats.isEmpty()) {
            Text("Пока нет переписок. Нажми «+ Чат» и найди собеседника по email.", color = Muted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(18.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(chats, key = { it.id }) { chat ->
                    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).clickable { onOpenChat(chat) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(Red, CircleShape), contentAlignment = Alignment.Center) {
                            Text(chat.title.take(1).uppercase(), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(chat.lastMessage.ifBlank { "Начните переписку" }, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Новый диалог") },
            text = {
                Column {
                    Text("Введи email человека, который уже зарегистрирован в NeuroGram.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = targetEmail, onValueChange = { targetEmail = it }, label = { Text("Email собеседника") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdd = false; onAddChat(targetEmail) }) { Text("Создать", color = Red) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
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
                if (exception != null) {
                    error = "Не удалось загрузить сообщения: ${exception.localizedMessage}"
                } else {
                    messages = snapshot?.documents?.map { doc ->
                        MessageItem(
                            doc.id,
                            doc.getString("senderId") ?: "",
                            doc.getString("senderName") ?: "Пользователь",
                            doc.getString("text") ?: ""
                        )
                    } ?: emptyList()
                }
            }
        onDispose { listener.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Red, fontSize = 30.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            Column {
                Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("NeuroGram", color = Muted, fontSize = 11.sp)
            }
        }
        if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == myUid
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                ) {
                    Text(
                        if (mine) "Вы" else message.senderName,
                        color = Muted, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Text(
                        message.text, color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.widthIn(max = 290.dp)
                            .background(if (mine) Color(0xFF7B2636) else Panel, RoundedCornerShape(15.dp))
                            .padding(horizontal = 13.dp, vertical = 10.dp)
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Сообщение…") },
                shape = RoundedCornerShape(16.dp), maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        draft = ""
                        val chatRef = db.collection("chats").document(chatId)
                        chatRef.collection("messages").add(mapOf(
                            "senderId" to myUid,
                            "senderName" to myName,
                            "text" to text,
                            "createdAt" to FieldValue.serverTimestamp()
                        )).addOnFailureListener { error = "Не отправлено: ${it.localizedMessage}" }
                        chatRef.update(
                            "lastMessage", text,
                            "updatedAt", FieldValue.serverTimestamp()
                        )
                    }
                },
                enabled = draft.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) { Text("➤") }
        }
    }
}

@Composable
private fun ProfileScreen(email: String, onBack: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(35.dp))
        Text("Профиль", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(email, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(25.dp))
        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Выйти из аккаунта") }
    }
}
