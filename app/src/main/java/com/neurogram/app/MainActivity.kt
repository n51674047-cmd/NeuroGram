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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.TimeUnit

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
            )) { NeuroGramApp(this) }
        }
    }
}

private fun normalizeRussianPhone(input: String): String? {
    val digits = input.filter { it.isDigit() }
    val russianDigits = when {
        digits.length == 10 -> "7" + digits
        digits.length == 11 && digits.startsWith("8") -> "7" + digits.drop(1)
        digits.length == 11 && digits.startsWith("7") -> digits
        else -> return null
    }
    return "+" + russianDigits
}
private data class ChatPreview(val name: String, val message: String, val color: Color)
private val demoChats = listOf(
    ChatPreview("Александр", "Окей, договорились", Color(0xFF9B5DE5)),
    ChatPreview("Мария", "Отправила фотографию", Color(0xFF00B4D8)),
    ChatPreview("Разработка", "Скоро покажу результат", Color(0xFFFF8C42)),
    ChatPreview("Илья", "Ахах, понятно", Color(0xFF43AA8B))
)

@Composable
private fun NeuroGramApp(activity: ComponentActivity) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var page by remember { mutableStateOf(if (auth.currentUser != null) "chats" else "login") }
    var isRegister by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun finishSignIn(credential: PhoneAuthCredential) {
        loading = true
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    loading = false
                    error = "Не удалось получить аккаунт. Попробуй ещё раз."
                    return@addOnSuccessListener
                }
                val data = mutableMapOf<String, Any>("phone" to (user.phoneNumber ?: phone))
                if (nickname.isNotBlank()) data["nickname"] = nickname.trim()
                db.collection("users").document(user.uid).set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        loading = false
                        error = ""
                        page = "chats"
                    }
                    .addOnFailureListener {
                        loading = false
                        error = "Вход выполнен, но профиль не сохранился: ${it.localizedMessage}"
                        page = "chats"
                    }
            }
            .addOnFailureListener {
                loading = false
                error = it.localizedMessage ?: "Не удалось подтвердить номер"
            }
    }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                finishSignIn(credential)
            }
            override fun onVerificationFailed(e: FirebaseException) {
                loading = false
                error = e.localizedMessage ?: "Firebase не смог отправить SMS"
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                resendToken = token
                loading = false
                error = ""
                info = "Код отправлен. Проверь SMS."
                page = "verify"
            }
        }
    }

    fun requestCode() {
        error = ""
        val normalizedPhone = normalizeRussianPhone(phone)
        if (normalizedPhone == null) {
            error = "Введи российский номер: 10 цифр или 11 цифр, начиная с 7 или 8"
            return
        }
        phone = normalizedPhone
        if (isRegister && nickname.isBlank()) {
            error = "Введи имя пользователя"
            return
        }
        loading = true
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(normalizedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
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
                    nickname = nickname,
                    onNickname = { nickname = it },
                    phone = phone,
                    onPhone = { phone = it },
                    error = error,
                    loading = loading,
                    onContinue = { isRegister = target == "register"; requestCode() },
                    onSwitch = {
                        error = ""
                        page = if (target == "register") "login" else "register"
                    }
                )
                "verify" -> VerifyScreen(
                    phone = phone, code = code, onCode = { code = it },
                    info = info, error = error, loading = loading,
                    onBack = { page = if (isRegister) "register" else "login"; error = "" },
                    onConfirm = {
                        val id = verificationId
                        if (code.length != 6) error = "Введи шестизначный код"
                        else if (id == null) error = "Запроси SMS-код ещё раз"
                        else {
                            error = ""
                            finishSignIn(PhoneAuthProvider.getCredential(id, code))
                        }
                    },
                    onResend = { requestCode() }
                )
                "chats" -> ChatList(
                    userLabel = auth.currentUser?.phoneNumber ?: "Аккаунт NeuroGram",
                    onProfile = { page = "profile" },
                    onChat = { page = "chat" }
                )
                "chat" -> ChatScreen(onBack = { page = "chats" })
                else -> ProfileScreen(
                    phone = auth.currentUser?.phoneNumber ?: "",
                    onBack = { page = "chats" },
                    onLogout = {
                        auth.signOut()
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
    register: Boolean, nickname: String, onNickname: (String) -> Unit,
    phone: String, onPhone: (String) -> Unit, error: String,
    loading: Boolean, onContinue: () -> Unit, onSwitch: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 25.dp), verticalArrangement = Arrangement.Center) {
        BrandHeader()
        Spacer(Modifier.height(45.dp))
        Text(if (register) "Создать аккаунт" else "С возвращением", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Авторизация по номеру телефона", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 7.dp, bottom = 25.dp))
        if (register) {
            AuthField(nickname, onNickname, "Имя пользователя")
            Spacer(Modifier.height(12.dp))
        }
        AuthField(phone, onPhone, "Российский номер телефона", KeyboardType.Phone)
        if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(22.dp))
        Button(onClick = onContinue, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
            Text(if (loading) "Отправка…" else "Получить код по SMS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(17.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(if (register) "Уже есть аккаунт? " else "Нет аккаунта? ", color = Muted, fontSize = 13.sp)
            Text(if (register) "Войти" else "Регистрация", color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onSwitch))
        }
    }
}

@Composable
private fun VerifyScreen(
    phone: String, code: String, onCode: (String) -> Unit, info: String,
    error: String, loading: Boolean, onBack: () -> Unit,
    onConfirm: () -> Unit, onResend: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 25.dp), verticalArrangement = Arrangement.Center) {
        Text("‹  Назад", color = Red, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(30.dp))
        BrandHeader()
        Spacer(Modifier.height(40.dp))
        Text("Подтверждение", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Введи код из SMS для номера $phone", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        AuthField(code, { onCode(it.filter(Char::isDigit).take(6)) }, "Код из SMS", KeyboardType.Number)
        if (info.isNotBlank()) Text(info, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
        if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onConfirm, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
            Text(if (loading) "Проверка…" else "Подтвердить номер", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text("Отправить код повторно", color = Red, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onResend).padding(16.dp))
    }
}

@Composable
private fun AuthField(value: String, onValue: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(15.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Red, unfocusedBorderColor = Color(0xFF353541),
            focusedLabelColor = Red, unfocusedLabelColor = Muted,
            cursorColor = Red, focusedTextColor = Color.White, unfocusedTextColor = Color.White
        )
    )
}

@Composable
private fun ChatList(userLabel: String, onProfile: () -> Unit, onChat: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandHeader()
            Spacer(Modifier.weight(1f))
            Text("●", color = Red, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onProfile).padding(10.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text("Сообщения", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(userLabel, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp, bottom = 22.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(demoChats) { chat ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).clickable(onClick = onChat).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(chat.color, CircleShape), contentAlignment = Alignment.Center) {
                        Text(chat.name.take(1), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column {
                        Text(chat.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(chat.message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("АККАУНТ ПОДКЛЮЧЁН К FIREBASE", color = Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ChatScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(25.dp))
        Text("Переписка", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("Авторизация уже подключена. Следующий этап — реальные диалоги и сообщения в Firestore.", color = Muted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(18.dp))
    }
}

@Composable
private fun ProfileScreen(phone: String, onBack: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(35.dp))
        Text("Профиль", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(phone, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(25.dp))
        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Выйти из аккаунта") }
    }
}
