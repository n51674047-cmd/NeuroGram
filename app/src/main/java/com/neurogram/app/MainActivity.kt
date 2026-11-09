package com.neurogram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private data class ChatPreview(
    val name: String, val message: String, val time: String,
    val unread: Int, val color: Color
)

private val demoChats = listOf(
    ChatPreview("Александр", "Окей, договорились", "12:48", 2, Color(0xFF9B5DE5)),
    ChatPreview("Мария", "Отправила фотографию", "11:32", 0, Color(0xFF00B4D8)),
    ChatPreview("Разработка", "Ты: скоро покажу результат", "Вчера", 5, Color(0xFFFF8C42)),
    ChatPreview("Илья", "Ахах, понятно", "Пн", 0, Color(0xFF43AA8B))
)

@Composable
private fun NeuroGramApp() {
    var page by remember { mutableStateOf("login") }
    var selected by remember { mutableStateOf<ChatPreview?>(null) }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        when (page) {
            "login" -> AuthScreen(
                register = false,
                onSwitch = { page = "register" },
                onContinue = { page = "chats" }
            )
            "register" -> AuthScreen(
                register = true,
                onSwitch = { page = "login" },
                onContinue = { page = "chats" }
            )
            "chats" -> ChatList(
                onChat = { selected = it; page = "chat" },
                onProfile = { page = "profile" }
            )
            "chat" -> ChatScreen(chat = selected, onBack = { page = "chats" })
            else -> ProfileScreen(onBack = { page = "chats" })
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).background(Red, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
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
private fun AuthScreen(register: Boolean, onSwitch: () -> Unit, onContinue: () -> Unit) {
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 25.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandHeader()
        Spacer(Modifier.height(48.dp))
        Text(if (register) "Создать аккаунт" else "С возвращением", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(
            if (register) "Присоединяйся к NeuroGram" else "Войди, чтобы продолжить общение",
            color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 7.dp, bottom = 25.dp)
        )

        if (register) {
            AuthField(nickname, { nickname = it }, "Имя пользователя")
            Spacer(Modifier.height(12.dp))
        }
        AuthField(email, { email = it }, "Электронная почта", KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField(password, { password = it }, "Пароль", password = true)
        if (error.isNotBlank()) {
            Text(error, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                error = when {
                    register && nickname.isBlank() -> "Введи имя пользователя"
                    email.isBlank() -> "Введи электронную почту"
                    password.length < 6 -> "Пароль должен содержать минимум 6 символов"
                    else -> "Это пока демонстрация. Реальная регистрация будет подключена следующим этапом."
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red)
        ) {
            Text(if (register) "Создать аккаунт" else "Войти", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(17.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(if (register) "Уже есть аккаунт? " else "Нет аккаунта? ", color = Muted, fontSize = 13.sp)
            Text(
                if (register) "Войти" else "Регистрация",
                color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onSwitch)
            )
        }
        Spacer(Modifier.height(30.dp))
        Text(
            "ПРЕДВАРИТЕЛЬНЫЙ ИНТЕРФЕЙС • АККАУНТЫ ЕЩЁ НЕ ПОДКЛЮЧЕНЫ",
            color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp
        )
    }
}

@Composable
private fun AuthField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(15.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Red,
            unfocusedBorderColor = Color(0xFF353541),
            focusedLabelColor = Red,
            unfocusedLabelColor = Muted,
            cursorColor = Red,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}

@Composable
private fun ChatList(onChat: (ChatPreview) -> Unit, onProfile: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandHeader()
            Spacer(Modifier.weight(1f))
            Text("●", color = Red, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onProfile).padding(10.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text("Сообщения", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Твои чаты в одном месте", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp, bottom = 22.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(demoChats) { chat -> ChatRow(chat) { onChat(chat) } }
        }
        Spacer(Modifier.weight(1f))
        Text("ДЕМО-РЕЖИМ • ДАННЫЕ ПОКА ЛОКАЛЬНЫЕ", color = Muted, fontSize = 9.sp, letterSpacing = 0.8.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ChatRow(chat: ChatPreview, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).background(chat.color, CircleShape), contentAlignment = Alignment.Center) {
            Text(chat.name.take(1), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(chat.message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(chat.time, color = Muted, fontSize = 10.sp)
            if (chat.unread > 0) {
                Box(Modifier.padding(top = 7.dp).background(Red, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Text(chat.unread.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(chat: ChatPreview?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(25.dp))
        Text(chat?.name ?: "Чат", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Личный чат", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.weight(1f))
        Text(
            "Экран переписки готовится. Отправка сообщений между устройствами появится после подключения сервера.",
            color = Muted, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(18.dp)
        )
    }
}

@Composable
private fun ProfileScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(35.dp))
        Text("Профиль", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text("Гость NeuroGram", color = Color.White, fontSize = 18.sp)
        Text("После подключения регистрации здесь появятся настройки аккаунта.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
    }
}
