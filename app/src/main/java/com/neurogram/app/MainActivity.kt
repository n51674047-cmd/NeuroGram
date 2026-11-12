package com.neurogram.app

import android.os.Bundle
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.app.Activity
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

private const val VERIFICATION_API_URL = "https://script.google.com/macros/s/AKfycby3kL3K_xlha2I2V-NATi9PktRdqnVQCILY8iK7ED_XdVnBG1iIm2ic_Ynivih32FRaAQ/exec"

private var appDarkTheme by mutableStateOf(false)
private var appMessageScale by mutableFloatStateOf(1f)

private val Bg: Color get() = if (appDarkTheme) Color(0xFF0B1220) else Color(0xFFF3F6FC)
private val Panel: Color get() = if (appDarkTheme) Color(0xFF151F30) else Color(0xFFFFFFFF)
private val Red: Color get() = if (appDarkTheme) Color(0xFF82AAFF) else Color(0xFF2878E7)
private val Muted: Color get() = if (appDarkTheme) Color(0xFF9AAAC0) else Color(0xFF718096)
private val Ink: Color get() = if (appDarkTheme) Color(0xFFF0F5FF) else Color(0xFF17243A)
private val Divider: Color get() = if (appDarkTheme) Color(0xFF29364A) else Color(0xFFE3EAF4)

private fun Modifier.glassSurface(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    this.background(
        Brush.verticalGradient(listOf(
            Panel.copy(alpha = if (appDarkTheme) 0.94f else 0.88f),
            Panel.copy(alpha = if (appDarkTheme) 0.78f else 0.72f)
        )), shape
    ).border(1.dp, Color.White.copy(alpha = if (appDarkTheme) 0.10f else 0.78f), shape)

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile var isAppVisible: Boolean = false
            private set
    }

    private val presenceHandler = Handler(Looper.getMainLooper())
    private var presenceRunnable: Runnable? = null
    private var authPresenceListener: FirebaseAuth.AuthStateListener? = null

    // Heartbeat instead of a one-time onStart write: an active session stays
    // visible, while a closed app naturally expires after the freshness window.
    // We deliberately do not write online=false onStop because another device
    // may still be using the same account.
    private fun updatePresence() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val data = mapOf("online" to true, "lastSeen" to FieldValue.serverTimestamp())
        val db = FirebaseFirestore.getInstance()
        db.runBatch { batch ->
            batch.set(db.collection("publicProfiles").document(uid), data, com.google.firebase.firestore.SetOptions.merge())
            batch.set(db.collection("users").document(uid), data, com.google.firebase.firestore.SetOptions.merge())
        }
    }

    private fun startPresenceHeartbeat() {
        stopPresenceHeartbeat()
        if (FirebaseAuth.getInstance().currentUser == null) return
        updatePresence()
        val task = object : Runnable {
            override fun run() {
                if (FirebaseAuth.getInstance().currentUser != null) {
                    updatePresence()
                    presenceHandler.postDelayed(this, 30_000L)
                }
            }
        }
        presenceRunnable = task
        presenceHandler.postDelayed(task, 30_000L)
    }

    private fun stopPresenceHeartbeat() {
        presenceRunnable?.let { presenceHandler.removeCallbacks(it) }
        presenceRunnable = null
    }

    override fun onStart() { super.onStart(); isAppVisible = true; startPresenceHeartbeat() }
    override fun onStop() { isAppVisible = false; stopPresenceHeartbeat(); super.onStop() }

    override fun onDestroy() {
        stopPresenceHeartbeat()
        authPresenceListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authPresenceListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) startPresenceHeartbeat() else stopPresenceHeartbeat()
        }.also { FirebaseAuth.getInstance().addAuthStateListener(it) }
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

private data class UpdateInfo(val version: String, val apkUrl: String)

internal fun showIncomingMessageNotification(
    context: android.content.Context,
    chatId: String,
    messageId: String,
    senderName: String,
    messageText: String
) {
    val prefs = context.getSharedPreferences("neurogram_settings", android.content.Context.MODE_PRIVATE)
    if (!prefs.getBoolean("notify_enabled", true)) return
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val soundEnabled = prefs.getBoolean("notify_sound", true)
    val vibrationEnabled = prefs.getBoolean("notify_vibration", true)
    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "messages_s${if (soundEnabled) 1 else 0}_v${if (vibrationEnabled) 1 else 0}"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
        val channel = NotificationChannel(channelId, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Новые сообщения NovixGram"
            enableVibration(vibrationEnabled)
            if (soundEnabled) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                setSound(null, null)
            }
        }
        manager.createNotificationChannel(channel)
    }

    val launchIntent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra("open_chat_id", chatId)
    }
    val pendingIntent = PendingIntent.getActivity(
        context, messageId.hashCode(), launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val preview = prefs.getBoolean("notify_preview", true)
    val text = if (preview) messageText.take(140).ifBlank { "Новое сообщение" } else "Новое сообщение"
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(context, channelId)
    } else {
        Notification.Builder(context).apply {
            setPriority(Notification.PRIORITY_HIGH)
            if (soundEnabled) setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            if (vibrationEnabled) setVibrate(longArrayOf(0, 250, 120, 250))
        }
    }
    val notification = builder
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(senderName.ifBlank { "Новое сообщение" })
        .setContentText(text)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    manager.notify(messageId.hashCode(), notification)
}

private fun isNewerVersion(remote: String, current: String): Boolean {
    val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val c = current.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(r.size, c.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = c.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private data class ChatItem(
    val id: String,
    val title: String,
    val lastMessage: String,
    val otherUid: String,
    val isMuted: Boolean = false,
    val isBlocked: Boolean = false,
    val isGroup: Boolean = false,
    val memberCount: Int = 2
)

private data class MessageItem(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val delivered: Boolean,
    val read: Boolean,
    val pending: Boolean = false,
    val createdAt: com.google.firebase.Timestamp? = null,
    val replyToId: String = "",
    val replyToText: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "",
    val edited: Boolean = false,
    val reactions: Map<String, List<String>> = emptyMap()
)

@Composable
private fun NeuroGramSplash(onFinished: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (exiting) 0f else 1f,
        animationSpec = tween(520),
        label = "splashAlpha",
        finishedListener = { if (exiting && it == 0f) onFinished() }
    )
    LaunchedEffect(Unit) {
        delay(420)
        expanded = true
        delay(1050)
        exiting = true
    }
    Box(
        Modifier.fillMaxSize().background(Bg).graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateContentSize()) {
                Text("N", color = Red, fontSize = 76.sp, fontWeight = FontWeight.Black)
                AnimatedVisibility(visible = expanded, enter = expandHorizontally(tween(620)) + fadeIn(tween(500))) {
                    Text("ovixGram", color = Ink, fontSize = 37.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn(tween(450))) {
                Text("МЕССЕНДЖЕР", color = Muted, fontSize = 10.sp, letterSpacing = 4.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun NeuroGramApp() {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) {
        NeuroGramSplash(onFinished = { showSplash = false })
        return
    }

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
    var verificationCode by remember { mutableStateOf("") }
    var awaitingEmailCode by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var selectedChatId by remember { mutableStateOf("") }
    var selectedChatTitle by remember { mutableStateOf("") }
    var selectedOtherUid by remember { mutableStateOf("") }
    var chats by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var downloadedUpdateId by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(Unit) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.github.com/repos/n51674047-cmd/NeuroGram/releases/latest").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "NovixGram-Android")
                }
                if (connection.responseCode in 200..299) {
                    val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val version = release.optString("tag_name").removePrefix("v")
                    val apk = release.optJSONArray("assets")
                    var apkUrl = ""
                    if (apk != null) for (i in 0 until apk.length()) {
                        val asset = apk.optJSONObject(i) ?: continue
                        if (asset.optString("name").endsWith(".apk", true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                    if (version.isNotBlank() && apkUrl.isNotBlank() && isNewerVersion(version, BuildConfig.VERSION_NAME)) {
                        Handler(Looper.getMainLooper()).post { updateInfo = UpdateInfo(version, apkUrl) }
                    }
                }
            } catch (_: Exception) {
            } finally { connection?.disconnect() }
        }
    }

    fun installOrDownloadUpdate() {
        val info = updateInfo ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
        if (downloadedUpdateId >= 0L) {
            val query = DownloadManager.Query().setFilterById(downloadedUpdateId)
            manager.query(query).use { cursor ->
                if (cursor != null && cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                    val apkUri = manager.getUriForDownloadedFile(downloadedUpdateId)
                    if (apkUri != null) {
                        val installIntent = Intent(Intent.ACTION_VIEW).setDataAndType(apkUri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(installIntent)
                        return
                    }
                }
            }
            downloadedUpdateId = -1L
        }
        if (!updateDownloading) {
            updateDownloading = true
            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("NovixGram ${info.version}")
                .setDescription("Загрузка обновления")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "NovixGram-${info.version}.apk")
            val queuedDownloadId = manager.enqueue(request)
            downloadedUpdateId = queuedDownloadId
            thread {
                var finished = false
                while (!finished) {
                    Thread.sleep(1000)
                    val q = DownloadManager.Query().setFilterById(queuedDownloadId)
                    manager.query(q).use { cursor ->
                        if (cursor != null && cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                                finished = true
                                Handler(Looper.getMainLooper()).post {
                                    updateDownloading = false
                                    if (status == DownloadManager.STATUS_SUCCESSFUL) installOrDownloadUpdate()
                                }
                            }
                        } else finished = true
                    }
                }
                if (finished) Handler(Looper.getMainLooper()).post { updateDownloading = false }
            }
        }
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    // After opening an authenticated session, always land on the chat list
    // with the navigation/settings drawer closed.
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            page = "chats"
            drawerState.close()
            val notificationPrefs = context.getSharedPreferences("neurogram_settings", android.content.Context.MODE_PRIVATE)
            if (notificationPrefs.getBoolean("notify_enabled", true) &&
                Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                activity?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
            }
        }
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
            firebaseAuth.currentUser?.let { syncNovixGramFcmToken(it.uid) }
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
                        chats = snapshot?.documents?.mapNotNull { doc ->
                            val members = doc.get("members") as? List<*> ?: emptyList<Any>()
                            val otherId = members.firstOrNull { it?.toString() != uid }?.toString()
                            val names = doc.get("memberNames") as? Map<*, *> ?: emptyMap<Any, Any>()
                            val isGroup = doc.getBoolean("isGroup") == true
                            val title = if (isGroup) doc.getString("title") ?: "Группа NovixGram" else names[otherId]?.toString() ?: "Диалог NovixGram"
                            val hiddenFor = doc.get("hiddenFor") as? List<*> ?: emptyList<Any>()
                            if (hiddenFor.contains(uid)) return@mapNotNull null
                            val mutedFor = doc.get("mutedFor") as? List<*> ?: emptyList<Any>()
                            val blockedBy = doc.get("blockedBy") as? List<*> ?: emptyList<Any>()
                            ChatItem(doc.id, title, doc.getString("lastMessage") ?: "Нет сообщений",
                                if (isGroup) "" else otherId ?: "", mutedFor.contains(uid), blockedBy.contains(uid),
                                isGroup, members.size)
                        }?.sortedByDescending { it.id } ?: emptyList()
                    }
                }
            onDispose { registration.remove() }
        }
    }

    // Mark incoming messages as delivered as soon as this account receives
    // them in its active chat list. Read receipts remain separate and are set
    // only by ChatScreen while the conversation is open.
    val activeChatIds = chats.map { it.id }
    DisposableEffect(currentUser?.uid, activeChatIds) {
        val uid = currentUser?.uid
        if (uid == null || activeChatIds.isEmpty()) {
            onDispose { }
        } else {
            val registrations = activeChatIds.map { chatId ->
                var firstSnapshot = true
                db.collection("chats").document(chatId).collection("messages")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(100)
                    .addSnapshotListener { snapshot, exception ->
                        if (exception == null && snapshot != null) {
                            // Ignore the initial history load; notify only for messages
                            // added after the listener is already watching this chat.
                            if (firstSnapshot) {
                                firstSnapshot = false
                            } else if (chats.firstOrNull { it.id == chatId }?.isMuted != true) {
                                snapshot.documentChanges
                                    .filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                                    .forEach { change ->
                                        val message = change.document
                                        if (message.getString("senderId") != uid && !message.metadata.hasPendingWrites() &&
                                            !(page == "chat" && selectedChatId == chatId)) {
                                            showIncomingMessageNotification(
                                                context = context,
                                                chatId = chatId,
                                                messageId = message.id,
                                                senderName = message.getString("senderName") ?: chats.firstOrNull { it.id == chatId }?.title ?: "NovixGram",
                                                messageText = message.getString("text") ?: ""
                                            )
                                        }
                                    }
                            }
                            val undelivered = snapshot.documents.filter { message ->
                                message.getString("senderId") != uid &&
                                    message.getTimestamp("deliveredAt") == null
                            }
                            if (undelivered.isNotEmpty()) {
                                val batch = db.batch()
                                undelivered.forEach { message ->
                                    batch.update(message.reference, "deliveredAt", FieldValue.serverTimestamp())
                                }
                                batch.commit()
                            }
                        }
                    }
            }
            onDispose { registrations.forEach { it.remove() } }
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
        if (isRegister) {
            if (!awaitingEmailCode) {
                loading = true
                postVerificationRequest(JSONObject().put("action", "sendCode").put("email", cleanEmail)) { ok, message ->
                    loading = false
                    if (ok) {
                        awaitingEmailCode = true
                        error = "Код отправлен на $cleanEmail"
                    } else error = message
                }
                return
            }
            if (!Regex("^\\d{4}$").matches(verificationCode)) {
                error = "Введи четырёхзначный код из письма"
                return
            }
            loading = true
            val payload = JSONObject().put("action", "verifyAndCreate")
                .put("email", cleanEmail).put("code", verificationCode).put("password", password)
            postVerificationRequest(payload) { ok, message ->
                if (!ok) {
                    loading = false
                    error = message
                } else {
                    auth.signInWithEmailAndPassword(cleanEmail, password)
                        .addOnSuccessListener { result ->
                            val user = result.user
                            if (user == null) {
                                loading = false
                                error = "Аккаунт создан, но не удалось войти. Попробуй войти вручную."
                            } else {
                                saveProfile(user.uid, cleanEmail, cleanUsername) {
                                    loading = false
                                    error = ""
                                    nickname = cleanUsername
                                    verificationCode = ""
                                    awaitingEmailCode = false
                                    page = "chats"
                                }
                            }
                        }
                        .addOnFailureListener {
                            loading = false
                            error = "Почта подтверждена и аккаунт создан. Войди вручную: ${it.localizedMessage}"
                        }
                }
            }
            return
        }
        loading = true
        auth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    loading = false
                    error = "Firebase не вернул аккаунт"
                    return@addOnSuccessListener
                }
                db.collection("users").document(user.uid).get().addOnSuccessListener { profile ->
                    val existingUsername = profile.getString("username") ?: profile.getString("nickname")
                        ?: cleanEmail.substringBefore("@")
                    saveProfile(user.uid, cleanEmail, existingUsername) {
                        loading = false; error = ""; nickname = existingUsername; page = "chats"
                    }
                }.addOnFailureListener { loading = false; error = "Не удалось загрузить профиль: ${it.localizedMessage}" }
            }.addOnFailureListener {
                loading = false
                error = it.localizedMessage ?: "Не удалось выполнить вход"
            }
    }

    fun resendVerificationCode() {
        val cleanEmail = email.trim().lowercase()
        loading = true
        postVerificationRequest(JSONObject().put("action", "sendCode").put("email", cleanEmail)) { ok, message ->
            loading = false
            error = if (ok) "Новый код отправлен на $cleanEmail" else message
        }
    }

    fun createGroup(groupTitle: String, rawUsernames: String) {
        val me = currentUser ?: return
        val cleanTitle = groupTitle.trim().take(40)
        val requested = rawUsernames.split(Regex("[,\\s;]+"))
            .map { it.trim().removePrefix("@").lowercase() }
            .filter { it.isNotBlank() && it != nickname.trim().removePrefix("@").lowercase() }
            .distinct().take(49)
        if (cleanTitle.isBlank()) { error = "Введи название группы"; return }
        if (requested.isEmpty()) { error = "Добавь хотя бы одного участника по username"; return }
        if (requested.any { !Regex("^[a-z0-9_]{5,20}$").matches(it) }) {
            error = "Проверь username участников"; return
        }
        loading = true
        val resolved = mutableListOf<Pair<String, String>>()
        fun resolveNext(index: Int) {
            if (index >= requested.size) {
                val members = (listOf(me.uid) + resolved.map { it.first }).distinct()
                if (members.size < 2) { loading = false; error = "В группе должно быть минимум два человека"; return }
                val names = mutableMapOf<String, String>()
                names[me.uid] = nickname.ifBlank { me.email?.substringBefore("@") ?: "Я" }
                resolved.forEach { names[it.first] = it.second }
                val chatRef = db.collection("chats").document()
                val data = mapOf(
                    "members" to members, "memberNames" to names,
                    "title" to cleanTitle, "isGroup" to true,
                    "createdBy" to me.uid, "admins" to listOf(me.uid),
                    "lastMessage" to "Группа создана",
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                chatRef.set(data).addOnSuccessListener {
                    loading = false; error = ""
                    selectedChatId = chatRef.id; selectedChatTitle = cleanTitle
                    selectedOtherUid = ""; page = "chat"
                }.addOnFailureListener {
                    loading = false; error = "Не удалось создать группу: ${it.localizedMessage}"
                }
                return
            }
            db.collection("publicProfiles").whereEqualTo("usernameLower", requested[index]).limit(1).get()
                .addOnSuccessListener { result ->
                    val profile = result.documents.firstOrNull()
                    if (profile == null) {
                        loading = false; error = "Не найден пользователь @${requested[index]}"; return@addOnSuccessListener
                    }
                    resolved.add(profile.id to (profile.getString("displayName") ?: profile.getString("username") ?: requested[index]))
                    resolveNext(index + 1)
                }.addOnFailureListener {
                    loading = false; error = "Ошибка поиска участников: ${it.localizedMessage}"
                }
        }
        resolveNext(0)
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
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "hiddenFor" to FieldValue.arrayRemove(me.uid)
                )
                db.collection("chats").document(chatId).set(data, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        loading = false
                        error = ""
                        selectedChatId = chatId
                        selectedChatTitle = otherName
                        selectedOtherUid = otherId
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
            "user_profile" -> previousPage
            "privacy", "notifications", "chat_settings", "appearance", "about" -> previousPage
            "chats" -> { activity?.finish(); "chats" }
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
                            Text("NovixGram", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
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
                    Text("NovixGram • версия ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(20.dp))
                }
            }
        }
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                targetState = page,
                label = "screen",
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(360)) { it / 18 } + fadeIn(tween(360))) togetherWith
                        (slideOutVertically(animationSpec = tween(260)) { -it / 24 } + fadeOut(tween(260)))
                }
            ) { target ->
                when (target) {
                    "login", "register" -> AuthScreen(
                        register = target == "register",
                        email = email, onEmail = { email = it },
                        password = password, onPassword = { password = it },
                        nickname = nickname, onNickname = { nickname = it },
                        error = error, loading = loading,
                        verificationCode = verificationCode, onVerificationCode = { verificationCode = it.filter(Char::isDigit).take(4) },
                        awaitingEmailCode = awaitingEmailCode,
                        onSubmit = { isRegister = target == "register"; submitAuth() },
                        onResendCode = { resendVerificationCode() },
                        onCancelCode = { awaitingEmailCode = false; verificationCode = ""; error = "" },
                        onSwitch = {
                            error = ""
                            awaitingEmailCode = false
                            verificationCode = ""
                            page = if (target == "register") "login" else "register"
                        }
                    )
                    "chats" -> ChatList(
                        userLabel = currentUser?.email ?: "Аккаунт NovixGram",
                        chats = chats, error = error, loading = loading,
                        updateInfo = updateInfo, updateDownloading = updateDownloading,
                        onUpdate = { installOrDownloadUpdate() },
                        onAddChat = { startChat(it) },
                        onCreateGroup = { title, usernames -> createGroup(title, usernames) },
                        onOpenChat = { chat ->
                            selectedChatId = chat.id
                            selectedChatTitle = chat.title
                            selectedOtherUid = chat.otherUid
                            page = "chat"
                        },
                        onProfile = { page = "profile" },
                        onMenu = { drawerScope.launch { drawerState.open() } }
                    )
                    "privacy", "notifications", "chat_settings", "appearance", "about" ->
                        SettingsScreen(section = target, onBack = { page = previousPage })
                    "chat" -> ChatScreen(
                        chatId = selectedChatId, title = selectedChatTitle,
                        myUid = currentUser?.uid ?: "", otherUid = selectedOtherUid,
                        myName = nickname.ifBlank { currentUser?.email?.substringBefore("@") ?: "Я" },
                        db = db, storage = storage, allChats = chats,
                        onBack = { page = "chats" },
                        onViewProfile = { if (selectedOtherUid.isNotBlank()) { previousPage = "chat"; page = "user_profile" } }
                    )
                    "user_profile" -> PublicProfileScreen(
                        uid = selectedOtherUid, db = db,
                        onBack = { page = previousPage }
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
                AnimatedVisibility(
                    visible = updateInfo != null,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth().padding(horizontal = 16.dp)
                        .statusBarsPadding().padding(top = 8.dp),
                    enter = fadeIn(tween(220)) + slideInVertically(tween(220)),
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(180))
                ) {
                    Row(
                        Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(16.dp))
                            .clickable(enabled = !updateDownloading, onClick = { installOrDownloadUpdate() })
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⬆", color = Red, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Доступно обновление ${updateInfo?.version}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(if (updateDownloading) "Загружаем APK…" else "Нажми, чтобы скачать и установить", color = Muted, fontSize = 11.sp)
                        }
                        Text(if (updateDownloading) "…" else "Обновить", color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun postVerificationRequest(payload: JSONObject, callback: (Boolean, String) -> Unit) {
    if (VERIFICATION_API_URL.startsWith("PASTE_")) {
        callback(false, "Сервер подтверждения ещё не подключён. Сначала разверни Apps Script и добавь его URL в приложение.")
        return
    }
    thread {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(VERIFICATION_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                connectTimeout = 20000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            // Apps Script ContentService responds with a redirect to a one-time
            // googleusercontent.com URL. Follow it explicitly as GET.
            if (connection.responseCode in 300..399) {
                val redirectUrl = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("Сервер не вернул адрес ответа")
                connection.disconnect()
                connection = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 30000
                }
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = JSONObject(responseText.ifBlank { "{}" })
            val ok = response.optBoolean("ok", false)
            val message = response.optString("message").ifBlank { if (ok) "Готово" else "Сервер не смог выполнить запрос" }
            Handler(Looper.getMainLooper()).post { callback(ok, message) }
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                callback(false, "Не удалось связаться с сервером подтверждения: ${e.localizedMessage ?: "ошибка сети"}")
            }
        } finally {
            connection?.disconnect()
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
            Text("NovixGram", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("ОБЩАЙСЯ БЕЗ ГРАНИЦ", color = Muted, fontSize = 9.sp, letterSpacing = 1.1.sp)
        }
    }
}

@Composable
private fun AuthScreen(
    register: Boolean, email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    nickname: String, onNickname: (String) -> Unit,
    verificationCode: String, onVerificationCode: (String) -> Unit,
    awaitingEmailCode: Boolean, onResendCode: () -> Unit, onCancelCode: () -> Unit,
    error: String, loading: Boolean, onSubmit: () -> Unit, onSwitch: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp), verticalArrangement = Arrangement.Center) {
        BrandHeader()
        Spacer(Modifier.height(48.dp))
        Text(if (register && awaitingEmailCode) "Подтверди почту" else if (register) "Создать аккаунт" else "С возвращением", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(if (register && awaitingEmailCode) "Мы отправили четырёхзначный код на $email" else if (register) "Присоединяйся к NovixGram" else "Войди, чтобы продолжить общение", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 25.dp))
        if (register && !awaitingEmailCode) {
            AuthField(nickname, onNickname, "Username (5–20 символов)")
            Spacer(Modifier.height(12.dp))
        }
        AuthField(email, onEmail, "Электронная почта", KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField(password, onPassword, "Пароль", password = true)
        if (register && awaitingEmailCode) {
            Spacer(Modifier.height(12.dp))
            AuthField(verificationCode, onVerificationCode, "Код из письма (4 цифры)", KeyboardType.Number)
            TextButton(onClick = onResendCode, enabled = !loading) { Text("Отправить код ещё раз", color = Red) }
            TextButton(onClick = onCancelCode, enabled = !loading) { Text("Изменить данные регистрации", color = Muted) }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFE05260), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(22.dp))
        Button(onClick = onSubmit, enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
            Text(if (loading) "Подождите…" else if (register && awaitingEmailCode) "Подтвердить и создать аккаунт" else if (register) "Получить код" else "Войти", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    updateInfo: UpdateInfo?, updateDownloading: Boolean, onUpdate: () -> Unit,
    onAddChat: (String) -> Unit, onCreateGroup: (String, String) -> Unit,
    onOpenChat: (ChatItem) -> Unit,
    onProfile: () -> Unit, onMenu: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var targetUsername by remember { mutableStateOf("") }
    var foundUid by remember { mutableStateOf("") }
    var foundName by remember { mutableStateOf("") }
    var foundUsername by remember { mutableStateOf("") }
    var foundAvatar by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var groupTitle by remember { mutableStateOf("") }
    var groupUsernames by remember { mutableStateOf("") }
    LaunchedEffect(targetUsername, showAdd) {
        foundUid = ""; foundName = ""; foundUsername = ""; foundAvatar = ""
        val clean = targetUsername.trim().removePrefix("@").lowercase()
        if (!showAdd || clean.length < 2 || !Regex("^[a-z0-9_]{2,20}$").matches(clean)) {
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        FirebaseFirestore.getInstance().collection("publicProfiles")
            .whereEqualTo("usernameLower", clean).limit(1).get()
            .addOnSuccessListener { result ->
                if (showAdd && targetUsername.trim().removePrefix("@").lowercase() == clean) {
                    val doc = result.documents.firstOrNull()
                    foundUid = doc?.id ?: ""
                    foundName = doc?.getString("displayName") ?: doc?.getString("username") ?: ""
                    foundUsername = doc?.getString("username") ?: clean
                    foundAvatar = doc?.getString("avatarUrl") ?: ""
                    searching = false
                }
            }.addOnFailureListener { searching = false }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp).padding(top = 12.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).glassSurface(RoundedCornerShape(14.dp)).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
                    Text("☰", color = Red, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                BrandHeader()
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(42.dp).glassSurface(CircleShape).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
                    Text("●", color = Red, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(30.dp))
            Column {
                Text("Чаты", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(userLabel, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Поиск по чатам и сообщениям", color = Muted, fontSize = 14.sp) },
                leadingIcon = { Text("⌕", color = Red, fontSize = 23.sp) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        Text("×", color = Muted, fontSize = 22.sp,
                            modifier = Modifier.clickable { searchQuery = "" }.padding(horizontal = 8.dp))
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Red, unfocusedBorderColor = Divider,
                    focusedTextColor = Ink, unfocusedTextColor = Ink,
                    focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                    cursorColor = Red
                )
            )
            AnimatedVisibility(visible = showAdd, enter = fadeIn(tween(220)) + expandHorizontally(tween(260))) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).glassSurface(RoundedCornerShape(18.dp)).padding(13.dp).animateContentSize()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Новое сообщение", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showGroupDialog = true }) { Text("＋ Группа", color = Red) }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetUsername, onValueChange = { targetUsername = it.removePrefix("@") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("@username", color = Muted) },
                        prefix = { Text("@", color = Red) },
                        shape = RoundedCornerShape(13.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Red, unfocusedBorderColor = Divider,
                            focusedTextColor = Ink, unfocusedTextColor = Ink,
                            focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                            cursorColor = Red
                        )
                    )
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Red)
                    AnimatedVisibility(visible = foundUid.isNotBlank(), enter = fadeIn(tween(180))) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp).clickable {
                                val chosen = targetUsername
                                showAdd = false
                                onAddChat(chosen)
                            }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp).background(Color(0xFFDCEEFF), CircleShape), contentAlignment = Alignment.Center) {
                                if (foundAvatar.isNotBlank()) AsyncImage(model = foundAvatar, contentDescription = "Аватар", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Text(foundName.take(1).uppercase().ifBlank { "?" }, color = Red, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(foundName.ifBlank { foundUsername }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("@$foundUsername", color = Muted, fontSize = 12.sp)
                            }
                            Text("›", color = Red, fontSize = 24.sp)
                        }
                    }
                    if (!searching && targetUsername.trim().removePrefix("@").length >= 2 && foundUid.isBlank()) {
                        Text("Пользователь не найден", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFE05260), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Red)
            Spacer(Modifier.height(17.dp))
            val filteredChats = chats.filter { chat ->
                searchQuery.isBlank() ||
                    chat.title.contains(searchQuery.trim(), ignoreCase = true) ||
                    chat.lastMessage.contains(searchQuery.trim(), ignoreCase = true)
            }
            if (filteredChats.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(58.dp).background(Panel, CircleShape), contentAlignment = Alignment.Center) {
                        Text("✉", color = Red, fontSize = 27.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank()) "Ничего не найдено" else "Пока нет сообщений",
                        color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (searchQuery.isNotBlank()) "Попробуй изменить запрос." else "Найди человека по username и начни диалог.",
                        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                    items(filteredChats, key = { it.id }) { chat ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(17.dp)).clickable { onOpenChat(chat) }
                                .background(Panel.copy(alpha = 0.78f))
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(52.dp).background(Red.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                Text(if (chat.isGroup) "👥" else chat.title.take(1).uppercase(), color = Red, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chat.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text((if (chat.isGroup) "${chat.memberCount} участников · " else "") + chat.lastMessage.ifBlank { "Начните переписку" }, color = Muted, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (chat.isMuted) Text("🔕", fontSize = 12.sp)
                                if (chat.isBlocked) Text("Заблокирован", color = Color(0xFFE05260), fontSize = 9.sp)
                                Text("›", color = Muted, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }
        if (showGroupDialog) AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("Новая группа", color = Ink) },
            text = {
                Column {
                    OutlinedTextField(value = groupTitle, onValueChange = { groupTitle = it.take(40) },
                        label = { Text("Название группы") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = groupUsernames, onValueChange = { groupUsernames = it },
                        label = { Text("Username участников") }, placeholder = { Text("@user1, @user2") },
                        minLines = 2, maxLines = 4)
                    Text("Перечисли username через запятую или пробел.", color = Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 7.dp))
                }
            },
            confirmButton = { TextButton(onClick = {
                showGroupDialog = false
                onCreateGroup(groupTitle, groupUsernames)
                groupTitle = ""; groupUsernames = ""
            }) { Text("Создать", color = Red) } },
            dismissButton = { TextButton(onClick = { showGroupDialog = false }) { Text("Отмена", color = Muted) } },
            containerColor = Panel
        )
        FloatingActionButton(
            onClick = { showAdd = !showAdd; if (!showAdd) targetUsername = "" },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(22.dp),
            containerColor = Red, contentColor = Color.White, shape = CircleShape
        ) { Text(if (showAdd) "×" else "+", fontSize = 28.sp, fontWeight = FontWeight.Normal) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatScreen(
    chatId: String, title: String, myUid: String, otherUid: String, myName: String,
    db: FirebaseFirestore, storage: FirebaseStorage, allChats: List<ChatItem>,
    onBack: () -> Unit, onViewProfile: () -> Unit
) {
    var messages by remember(chatId) { mutableStateOf<List<MessageItem>>(emptyList()) }
    var draft by remember(chatId) { mutableStateOf("") }
    var error by remember(chatId) { mutableStateOf("") }
    var otherAvatar by remember(otherUid) { mutableStateOf("") }
    var otherUsername by remember(otherUid) { mutableStateOf("") }
    var otherDisplayName by remember(otherUid) { mutableStateOf("") }
    var presenceOnline by remember(otherUid) { mutableStateOf(false) }
    var presenceLastSeen by remember(otherUid) { mutableStateOf<com.google.firebase.Timestamp?>(null) }
    var otherTyping by remember(otherUid) { mutableStateOf(false) }
    var isGroupChat by remember(chatId) { mutableStateOf(otherUid.isBlank()) }
    var groupMemberCount by remember(chatId) { mutableIntStateOf(2) }
    var groupMembers by remember(chatId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var groupAdmins by remember(chatId) { mutableStateOf<List<String>>(emptyList()) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var newMemberUsername by remember { mutableStateOf("") }
    var pinnedMessageText by remember(chatId) { mutableStateOf("") }
    var pinnedMessageId by remember(chatId) { mutableStateOf("") }
    var showMembersDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MessageItem?>(null) }
    var replyingTo by remember(chatId) { mutableStateOf<MessageItem?>(null) }
    var editingMessageId by remember(chatId) { mutableStateOf("") }
    var showForwardDialog by remember { mutableStateOf(false) }
    var forwardingMessage by remember { mutableStateOf<MessageItem?>(null) }
    var isMuted by remember(chatId) { mutableStateOf(false) }
    var blockedBy by remember(chatId) { mutableStateOf<List<String>>(emptyList()) }
    val isBlockedByMe = blockedBy.contains(myUid)
    val isBlockedByOther = blockedBy.contains(otherUid)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val context = LocalContext.current
    var uploadingMedia by remember(chatId) { mutableStateOf(false) }
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadingMedia = true
            val ref = storage.reference.child("chats/$chatId/media/image_${System.currentTimeMillis()}.jpg")
            ref.putFile(uri).continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Ошибка загрузки")
                ref.downloadUrl
            }.addOnSuccessListener { url ->
                sendMessageContent("", url.toString(), "image")
                uploadingMedia = false
            }.addOnFailureListener {
                uploadingMedia = false
                error = "Не удалось загрузить фото: ${it.localizedMessage}"
            }
        }
    }
    val videoPicker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadingMedia = true
            val ref = storage.reference.child("chats/$chatId/media/video_${System.currentTimeMillis()}.mp4")
            ref.putFile(uri).continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Ошибка загрузки")
                ref.downloadUrl
            }.addOnSuccessListener { url ->
                sendMessageContent("", url.toString(), "video")
                uploadingMedia = false
            }.addOnFailureListener {
                uploadingMedia = false
                error = "Не удалось загрузить видео: ${it.localizedMessage}"
            }
        }
    }

    fun sendMessageContent(text: String, mediaUrl: String = "", mediaType: String = "") {
        val chatRef = db.collection("chats").document(chatId)
        val messageRef = chatRef.collection("messages").document()
        val reply = replyingTo
        val preview = when {
            mediaType == "image" -> "📷 Фото"
            mediaType == "video" -> "▶ Видео"
            else -> text
        }
        val batch = db.batch()
        batch.set(messageRef, mapOf(
            "senderId" to myUid, "senderName" to myName,
            "text" to text, "mediaUrl" to mediaUrl, "mediaType" to mediaType,
            "replyToId" to (reply?.id ?: ""), "replyToText" to (reply?.text ?: reply?.let { if (it.mediaType == "image") "Фото" else if (it.mediaType == "video") "Видео" else "" } ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "clientCreatedAt" to com.google.firebase.Timestamp.now(),
            "deliveredAt" to null, "readAt" to null,
            "edited" to false
        ))
        batch.update(chatRef, "lastMessage", preview, "updatedAt", FieldValue.serverTimestamp())
        batch.commit().addOnFailureListener {
            error = "Не удалось отправить сообщение: ${it.localizedMessage}"
        }.addOnSuccessListener {
            replyingTo = null
        }
    }

    DisposableEffect(otherUid) {
        if (otherUid.isBlank()) {
            onDispose { }
        } else {
        val otherListener = db.collection("publicProfiles").document(otherUid)
            .addSnapshotListener { doc, exception ->
                if (exception == null && doc != null && doc.exists()) {
                    otherAvatar = doc.getString("avatarUrl") ?: ""
                    otherUsername = doc.getString("username") ?: doc.getString("nickname") ?: ""
                    otherDisplayName = doc.getString("displayName") ?: otherUsername
                    presenceLastSeen = doc.getTimestamp("lastSeen")
                    val fresh = presenceLastSeen?.toDate()?.let { System.currentTimeMillis() - it.time < 90_000L } == true
                    presenceOnline = doc.getBoolean("online") == true && fresh
                }
            }
        onDispose { otherListener.remove() }
        }
    }

    DisposableEffect(chatId) {
        val chatRef = db.collection("chats").document(chatId)
        val chatListener = chatRef.addSnapshotListener { doc, exception ->
            if (exception == null && doc != null && doc.exists()) {
                isGroupChat = doc.getBoolean("isGroup") == true
                groupMemberCount = (doc.get("members") as? List<*>)?.size ?: 2
                val names = doc.get("memberNames") as? Map<*, *> ?: emptyMap<Any, Any>()
                groupMembers = names.mapNotNull { (key, value) -> key?.toString()?.let { it to (value?.toString() ?: it) } }.toMap()
                groupAdmins = (doc.get("admins") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                pinnedMessageText = doc.getString("pinnedMessageText") ?: ""
                pinnedMessageId = doc.getString("pinnedMessageId") ?: ""
                val mutedFor = doc.get("mutedFor") as? List<*> ?: emptyList<Any>()
                isMuted = mutedFor.contains(myUid)
                blockedBy = (doc.get("blockedBy") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val typingAt = doc.get("typingAt") as? Map<*, *> ?: emptyMap<Any, Any>()
                val typingTime = typingAt[otherUid] as? com.google.firebase.Timestamp
                otherTyping = typingTime?.toDate()?.let { System.currentTimeMillis() - it.time < 6500L } == true
            }
        }
        val listener = chatRef.collection("messages").orderBy("createdAt")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, messageException ->
                if (messageException != null) error = "Не удалось загрузить сообщения: ${messageException.localizedMessage}"
                else {
                    val docs = snapshot?.documents ?: emptyList()
                    messages = docs.map { doc ->
                        val reactionsRaw = doc.get("reactions") as? Map<*, *> ?: emptyMap<Any, Any>()
                        val reactions = reactionsRaw.mapNotNull { (key, value) ->
                            val users = (value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                            key?.toString()?.let { it to users }
                        }.toMap()
                        MessageItem(doc.id, doc.getString("senderId") ?: "", doc.getString("senderName") ?: "Пользователь", doc.getString("text") ?: "", doc.getTimestamp("deliveredAt") != null, doc.getTimestamp("readAt") != null, doc.metadata.hasPendingWrites(), doc.getTimestamp("createdAt") ?: doc.getTimestamp("clientCreatedAt"), doc.getString("replyToId") ?: "", doc.getString("replyToText") ?: "", doc.getString("mediaUrl") ?: "", doc.getString("mediaType") ?: "", doc.getBoolean("edited") == true, reactions)
                    }
                    val batch = db.batch()
                    var changed = false
                    // Opening/keeping this chat visible means incoming messages are
                    // both delivered and read. Apply both timestamps from the live
                    // snapshot so receipts also work for messages arriving later.
                    docs.filter {
                        it.getString("senderId") != myUid &&
                            (it.getTimestamp("deliveredAt") == null || it.getTimestamp("readAt") == null)
                    }.forEach { doc ->
                        val updates = mutableMapOf<String, Any>()
                        if (doc.getTimestamp("deliveredAt") == null) updates["deliveredAt"] = FieldValue.serverTimestamp()
                        if (doc.getTimestamp("readAt") == null) updates["readAt"] = FieldValue.serverTimestamp()
                        if (updates.isNotEmpty()) { batch.update(doc.reference, updates); changed = true }
                    }
                    if (changed) batch.commit()
                }
            }
        onDispose {
            chatListener.remove()
            listener.remove()
            chatRef.update("typingAt.$myUid", FieldValue.delete())
        }
    }

    LaunchedEffect(draft.isNotBlank(), chatId) {
        val chatRef = db.collection("chats").document(chatId)
        if (draft.isBlank()) {
            chatRef.update("typingAt.$myUid", FieldValue.delete())
        } else {
            while (true) {
                chatRef.update("typingAt.$myUid", FieldValue.serverTimestamp())
                delay(2500)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val stickyDateCount = messages.map { message ->
                message.createdAt?.toDate()?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(it) } ?: "unknown"
            }.distinct().size
            listState.animateScrollToItem((messages.size + stickyDateCount - 1).coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Red, fontSize = 27.sp, modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 5.dp))
            Box(Modifier.size(38.dp).background(Color(0xFFDCEEFF), CircleShape), contentAlignment = Alignment.Center) {
                if (!isGroupChat && otherAvatar.isNotBlank()) AsyncImage(model = otherAvatar, contentDescription = "Аватар собеседника", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(if (isGroupChat) "👥" else (otherUsername.ifBlank { title }).take(1).uppercase(), color = Red, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f).clickable(enabled = !isGroupChat, onClick = onViewProfile)) {
                Text(if (isGroupChat) title else otherDisplayName.ifBlank { otherUsername.ifBlank { title } }, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                val status = if (presenceOnline) "В сети" else presenceLastSeen?.toDate()?.let { date ->
                    val now = java.util.Calendar.getInstance()
                    val then = java.util.Calendar.getInstance().apply { time = date }
                    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
                    if (now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
                        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR))
                        "Был(а) сегодня в " + time
                    else "Был(а) " + java.text.SimpleDateFormat("dd.MM.yyyy в HH:mm", java.util.Locale.getDefault()).format(date)
                } ?: "Не в сети"
                Text(
                    if (isGroupChat) "Группа · $groupMemberCount участников" else if (otherTyping) "••• печатает" else status,
                    color = if (otherTyping) Red else if (presenceOnline) Color(0xFF2EAD68) else Muted,
                    fontSize = 10.sp, maxLines = 1
                )
            }
            Box {
                Text("⋮", color = Red, fontSize = 22.sp, modifier = Modifier.clickable { menuExpanded = true }.padding(horizontal = 7.dp, vertical = 4.dp))
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isMuted) "Включить уведомления" else "Выключить уведомления") },
                        onClick = {
                            menuExpanded = false
                            val field = if (isMuted) FieldValue.arrayRemove(myUid) else FieldValue.arrayUnion(myUid)
                            db.collection("chats").document(chatId).update("mutedFor", field)
                                .addOnFailureListener { error = "Не удалось изменить уведомления: ${it.localizedMessage}" }
                        }
                    )
                    if (isGroupChat) DropdownMenuItem(
                        text = { Text("Участники группы") },
                        onClick = { menuExpanded = false; showMembersDialog = true }
                    )
                    if (isGroupChat) DropdownMenuItem(
                        text = { Text("Покинуть группу", color = Color(0xFFE05260)) },
                        onClick = {
                            menuExpanded = false
                            db.collection("chats").document(chatId).update("members", FieldValue.arrayRemove(myUid), "hiddenFor", FieldValue.arrayUnion(myUid))
                                .addOnSuccessListener { onBack() }
                                .addOnFailureListener { error = "Не удалось покинуть группу: ${it.localizedMessage}" }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить чат", color = Color(0xFFE05260)) },
                        onClick = { menuExpanded = false; showDeleteConfirm = true }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBlockedByMe) "Разблокировать" else "Заблокировать", color = if (isBlockedByMe) Ink else Color(0xFFE05260)) },
                        onClick = { menuExpanded = false; if (isBlockedByMe) db.collection("chats").document(chatId).update("blockedBy", FieldValue.arrayRemove(myUid)) else showBlockConfirm = true }
                    )
                }
            }
        }
        if (isBlockedByMe) {
            Text("Вы заблокировали этого пользователя. Отправка сообщений отключена.", color = Color(0xFFE05260), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(10.dp))
        } else if (isBlockedByOther) {
            Text("Этот пользователь ограничил переписку.", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(10.dp))
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFE05260), fontSize = 12.sp)
        if (pinnedMessageText.isNotBlank()) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable {
                val index = messages.indexOfFirst { it.id == pinnedMessageId }
                if (index >= 0) listState.animateScrollToItem(index)
            }.padding(11.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📌", color = Red)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Закреплённое сообщение", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(pinnedMessageText, color = Ink, fontSize = 12.sp, maxLines = 1)
            }
        }
        val groupedMessages = messages.groupBy { message ->
            message.createdAt?.toDate()?.let {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(it)
            } ?: "unknown"
        }.toList()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            groupedMessages.forEach { (dateKey, dayMessages) ->
                stickyHeader(key = "date-$dateKey") {
                    Box(
                        Modifier.fillMaxWidth().background(Bg.copy(alpha = 0.96f)).padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (dateKey == "unknown") "Дата неизвестна" else
                                java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("ru")).format(
                                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateKey)!!
                                ),
                            color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.background(Panel, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                items(dayMessages, key = { it.id }) { message ->
                    val mine = message.senderId == myUid
                    val timeLabel = message.createdAt?.toDate()?.let {
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(it)
                    } ?: if (message.pending) "…" else "--:--"
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 }
                    ) {
                        Column(
                            Modifier.fillMaxWidth().animateContentSize(),
                            horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                        ) {
                            if (isGroupChat && !mine) Text(message.senderName, color = Red, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            Row(
                                modifier = Modifier.widthIn(max = 310.dp)
                                    .then(if (mine) Modifier.background(Red, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp)) else Modifier.glassSurface(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 5.dp, bottomEnd = 18.dp)))
                                    .combinedClickable(onClick = {}, onLongClick = { selectedMessage = message })
                                    .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 7.dp)
                                    .animateContentSize(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column(Modifier.weight(1f, fill = false)) {
                                    if (message.replyToText.isNotBlank()) {
                                        Text("↪ ${message.replyToText.take(90)}", color = if (mine) Color.White.copy(alpha = 0.78f) else Red,
                                            fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(bottom = 4.dp))
                                    }
                                    if (message.mediaType == "image" && message.mediaUrl.isNotBlank()) {
                                        AsyncImage(model = message.mediaUrl, contentDescription = "Фото в сообщении",
                                            modifier = Modifier.widthIn(max = 230.dp).heightIn(max = 230.dp).clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop)
                                    } else if (message.mediaType == "video" && message.mediaUrl.isNotBlank()) {
                                        Text("▶ Открыть видео", color = if (mine) Color.White else Red,
                                            modifier = Modifier.clickable {
                                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl))) }
                                                catch (_: Exception) { error = "Не удалось открыть видео" }
                                            }.padding(vertical = 8.dp))
                                    }
                                    if (message.text.isNotBlank()) Text(
                                        message.text + if (message.edited) "  (изменено)" else "",
                                        color = if (mine) Color.White else Ink,
                                        fontSize = 14.sp * appMessageScale
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    timeLabel,
                                    color = if (mine) Color.White.copy(alpha = 0.72f) else Muted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(bottom = 1.dp)
                                )
                                if (mine) {
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        when {
                                            message.pending -> "◷"
                                            message.read || message.delivered -> "✓✓"
                                            else -> "✓"
                                        },
                                        color = when {
                                            message.pending -> Color.White.copy(alpha = 0.72f)
                                            message.read -> Color(0xFF64B5F6)
                                            else -> Color.White.copy(alpha = 0.78f)
                                        },
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            if (message.reactions.isNotEmpty()) {
                                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    message.reactions.forEach { (emoji, users) ->
                                        Text("$emoji ${users.size}", color = Ink, fontSize = 11.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Panel).clickable {
                                                db.collection("chats").document(chatId).collection("messages").document(message.id)
                                                    .update("reactions.$emoji", FieldValue.arrayUnion(myUid))
                                            }.padding(horizontal = 7.dp, vertical = 3.dp))
                                    }
                                }
                            }
                            Text("⋯", color = Muted, fontSize = 13.sp,
                                modifier = Modifier.clickable { selectedMessage = message }.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
        if (replyingTo != null || editingMessageId.isNotBlank()) Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(Panel).padding(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (editingMessageId.isNotBlank()) "Редактирование" else "Ответ на сообщение", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(if (editingMessageId.isNotBlank()) "Измени текст и отправь" else replyingTo?.text?.ifBlank { "Медиа" } ?: "",
                    color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            Text("×", color = Muted, modifier = Modifier.clickable {
                replyingTo = null; editingMessageId = ""; draft = ""
            }.padding(6.dp))
        }
        Row(
            Modifier.fillMaxWidth().align(Alignment.CenterHorizontally)
                .glassSurface(RoundedCornerShape(24.dp)).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("▧", color = Red, fontSize = 21.sp, modifier = Modifier.clickable(enabled = !uploadingMedia) { imagePicker.launch("image/*") }.padding(horizontal = 7.dp))
            Text("▶", color = Red, fontSize = 17.sp, modifier = Modifier.clickable(enabled = !uploadingMedia) { videoPicker.launch("video/*") }.padding(horizontal = 5.dp))
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f), placeholder = { Text(if (editingMessageId.isNotBlank()) "Изменить сообщение…" else "Сообщение…", color = Muted) },
                shape = RoundedCornerShape(17.dp), maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Red,
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    if (editingMessageId.isNotBlank()) {
                        db.collection("chats").document(chatId).collection("messages").document(editingMessageId)
                            .update(mapOf("text" to text, "edited" to true, "editedAt" to FieldValue.serverTimestamp()))
                            .addOnFailureListener { error = "Не удалось изменить сообщение: ${it.localizedMessage}" }
                        editingMessageId = ""; draft = ""
                    } else if (text.isNotEmpty() && !isBlockedByMe && !isBlockedByOther) {
                        draft = ""
                        sendMessageContent(text)
                    }
                },
                enabled = (draft.isNotBlank() || editingMessageId.isNotBlank()) && !isBlockedByMe && !isBlockedByOther && !uploadingMedia,
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                shape = CircleShape, contentPadding = PaddingValues(14.dp)
            ) { Text(if (editingMessageId.isNotBlank()) "✓" else if (uploadingMedia) "…" else "➤", color = Color.White) }
        }
    }

    if (showDeleteConfirm) AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text("Удалить чат?", color = Ink) },
        text = { Text("Чат исчезнет из вашего списка. У собеседника он останется.", color = Muted) },
        confirmButton = {
            TextButton(onClick = {
                showDeleteConfirm = false
                db.collection("chats").document(chatId).update("hiddenFor", FieldValue.arrayUnion(myUid))
                    .addOnSuccessListener { onBack() }
                    .addOnFailureListener { error = "Не удалось удалить чат: ${it.localizedMessage}" }
            }) { Text("Удалить", color = Color(0xFFE05260)) }
        },
        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена", color = Muted) } },
        containerColor = Panel
    )

    selectedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { selectedMessage = null },
            title = { Text("Действия с сообщением", color = Ink) },
            text = {
                Column {
                    TextButton(onClick = { replyingTo = message; selectedMessage = null }) { Text("↩ Ответить", color = Red) }
                    TextButton(onClick = {
                        db.collection("chats").document(chatId).collection("messages").document(message.id)
                            .update("reactions.👍", FieldValue.arrayUnion(myUid))
                        selectedMessage = null
                    }) { Text("👍 Поставить реакцию", color = Red) }
                    TextButton(onClick = {
                        db.collection("chats").document(chatId).update(
                            mapOf("pinnedMessageId" to message.id,
                                "pinnedMessageText" to (message.text.ifBlank { if (message.mediaType == "image") "Фото" else "Видео" }))
                        )
                        selectedMessage = null
                    }) { Text("📌 Закрепить", color = Red) }
                    TextButton(onClick = {
                        forwardingMessage = message
                        showForwardDialog = true
                        selectedMessage = null
                    }) { Text("↗ Переслать", color = Red) }
                    if (message.senderId == myUid) {
                        TextButton(onClick = {
                            draft = message.text
                            editingMessageId = message.id
                            selectedMessage = null
                        }) { Text("✎ Изменить", color = Red) }
                        TextButton(onClick = {
                            db.collection("chats").document(chatId).collection("messages").document(message.id).delete()
                                .addOnFailureListener { error = "Не удалось удалить сообщение: ${it.localizedMessage}" }
                            selectedMessage = null
                        }) { Text("Удалить сообщение", color = Color(0xFFE05260)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedMessage = null }) { Text("Закрыть", color = Muted) } },
            containerColor = Panel
        )
    }

    if (showForwardDialog && forwardingMessage != null) AlertDialog(
        onDismissRequest = { showForwardDialog = false; forwardingMessage = null },
        title = { Text("Переслать в чат", color = Ink) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                val targets = allChats.filter { it.id != chatId }
                if (targets.isEmpty()) Text("Нет других чатов для пересылки.", color = Muted)
                targets.forEach { target ->
                    TextButton(onClick = {
                        val source = forwardingMessage!!
                        val ref = db.collection("chats").document(target.id)
                        val messageRef = ref.collection("messages").document()
                        val preview = source.text.ifBlank { if (source.mediaType == "image") "📷 Фото" else "▶ Видео" }
                        messageRef.set(mapOf(
                            "senderId" to myUid, "senderName" to myName, "text" to source.text,
                            "mediaUrl" to source.mediaUrl, "mediaType" to source.mediaType,
                            "forwardedFrom" to source.senderName,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "clientCreatedAt" to com.google.firebase.Timestamp.now(),
                            "deliveredAt" to null, "readAt" to null
                        )).addOnSuccessListener {
                            ref.update("lastMessage", "↗ $preview", "updatedAt", FieldValue.serverTimestamp())
                        }.addOnFailureListener { error = "Не удалось переслать: ${it.localizedMessage}" }
                        showForwardDialog = false; forwardingMessage = null
                    }) { Text(target.title, color = Ink) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showForwardDialog = false; forwardingMessage = null }) { Text("Отмена", color = Muted) } },
        containerColor = Panel
    )

    if (showMembersDialog && isGroupChat) AlertDialog(
        onDismissRequest = { showMembersDialog = false },
        title = { Text("Участники группы · ${groupMembers.size}", color = Ink) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                groupMembers.forEach { (memberUid, memberName) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(memberName, color = Ink, modifier = Modifier.weight(1f))
                        if (myUid in groupAdmins && memberUid != myUid) TextButton(onClick = {
                            db.collection("chats").document(chatId).update(
                                "members", FieldValue.arrayRemove(memberUid),
                                "memberNames.$memberUid", FieldValue.delete()
                            ).addOnFailureListener { error = "Не удалось удалить участника: ${it.localizedMessage}" }
                        }) { Text("Удалить", color = Color(0xFFE05260)) }
                    }
                }
                if (myUid in groupAdmins) TextButton(onClick = { showMembersDialog = false; showAddMemberDialog = true }) {
                    Text("＋ Добавить участника", color = Red)
                }
            }
        },
        confirmButton = { TextButton(onClick = { showMembersDialog = false }) { Text("Готово", color = Red) } },
        containerColor = Panel
    )

    if (showAddMemberDialog) AlertDialog(
        onDismissRequest = { showAddMemberDialog = false },
        title = { Text("Добавить участника", color = Ink) },
        text = { OutlinedTextField(value = newMemberUsername, onValueChange = { newMemberUsername = it.removePrefix("@") },
            label = { Text("Username") }, prefix = { Text("@") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val clean = newMemberUsername.trim().lowercase()
            if (!Regex("^[a-z0-9_]{5,20}$").matches(clean)) { error = "Проверь username" }
            else db.collection("publicProfiles").whereEqualTo("usernameLower", clean).limit(1).get()
                .addOnSuccessListener { result ->
                    val profile = result.documents.firstOrNull()
                    if (profile == null) error = "Пользователь не найден"
                    else {
                        val uid = profile.id
                        val name = profile.getString("displayName") ?: profile.getString("username") ?: clean
                        db.collection("chats").document(chatId).update(
                            "members", FieldValue.arrayUnion(uid), "memberNames.$uid", name
                        ).addOnSuccessListener { newMemberUsername = ""; showAddMemberDialog = false }
                            .addOnFailureListener { error = "Не удалось добавить участника: ${it.localizedMessage}" }
                    }
                }.addOnFailureListener { error = "Ошибка поиска: ${it.localizedMessage}" }
        }) { Text("Добавить", color = Red) } },
        dismissButton = { TextButton(onClick = { showAddMemberDialog = false }) { Text("Отмена", color = Muted) } },
        containerColor = Panel
    )

    if (showBlockConfirm) AlertDialog(
        onDismissRequest = { showBlockConfirm = false },
        title = { Text("Заблокировать пользователя?", color = Ink) },
        text = { Text("Вы не сможете отправлять сообщения в этом чате, пока не снимете блокировку.", color = Muted) },
        confirmButton = {
            TextButton(onClick = {
                showBlockConfirm = false
                db.collection("chats").document(chatId).update("blockedBy", FieldValue.arrayUnion(myUid))
                    .addOnFailureListener { error = "Не удалось заблокировать: ${it.localizedMessage}" }
            }) { Text("Заблокировать", color = Color(0xFFE05260)) }
        },
        dismissButton = { TextButton(onClick = { showBlockConfirm = false }) { Text("Отмена", color = Muted) } },
        containerColor = Panel
    )
}

@Composable
private fun PublicProfileScreen(uid: String, db: FirebaseFirestore, onBack: () -> Unit) {
    var username by remember(uid) { mutableStateOf("") }
    var displayName by remember(uid) { mutableStateOf("") }
    var editDisplayName by remember(uid) { mutableStateOf("") }
    var bio by remember(uid) { mutableStateOf("") }
    var avatarUrl by remember(uid) { mutableStateOf("") }
    var loading by remember(uid) { mutableStateOf(true) }
    var error by remember(uid) { mutableStateOf("") }

    DisposableEffect(uid) {
        if (uid.isBlank()) {
            loading = false
            error = "Профиль не найден"
            onDispose { }
        } else {
            val registration = db.collection("publicProfiles").document(uid)
                .addSnapshotListener { doc, exception ->
                    if (exception != null) {
                        error = "Не удалось загрузить профиль: " + exception.localizedMessage
                    } else if (doc != null && doc.exists()) {
                        username = doc.getString("username") ?: doc.getString("nickname") ?: "Пользователь"
                        bio = doc.getString("bio") ?: ""
                        avatarUrl = doc.getString("avatarUrl") ?: ""
                    } else error = "Профиль не найден"
                    loading = false
                }
            onDispose { registration.remove() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(30.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(112.dp).background(Color(0xFFDCEEFF), CircleShape), contentAlignment = Alignment.Center) {
                if (avatarUrl.isNotBlank()) AsyncImage(
                    model = avatarUrl, contentDescription = "Аватар пользователя",
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                ) else Text(username.take(1).uppercase().ifBlank { "?" }, color = Red, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("@${username.ifBlank { "Пользователь" }}", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red)
        if (error.isNotBlank()) Text(error, color = Color(0xFFE05260), fontSize = 13.sp)
        Column(Modifier.fillMaxWidth().padding(top = 18.dp).background(Panel, RoundedCornerShape(18.dp)).padding(18.dp)) {
            Text("О себе", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(bio.ifBlank { "Пользователь пока не добавил описание." }, color = Ink, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ProfileScreen(
    uid: String, email: String, db: FirebaseFirestore, storage: FirebaseStorage,
    onBack: () -> Unit, onSettings: () -> Unit, onUsernameChanged: (String) -> Unit, onLogout: () -> Unit
) {
    var username by remember(uid) { mutableStateOf("") }
    var displayName by remember(uid) { mutableStateOf("") }
    var editDisplayName by remember(uid) { mutableStateOf("") }
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
            displayName = doc.getString("displayName") ?: username
            bio = doc.getString("bio") ?: ""
            avatarUrl = doc.getString("avatarUrl") ?: ""
            editUsername = username
            editDisplayName = displayName
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
                db.runBatch { batch ->
                    batch.set(db.collection("users").document(uid), data, com.google.firebase.firestore.SetOptions.merge())
                    batch.set(db.collection("publicProfiles").document(uid), data, com.google.firebase.firestore.SetOptions.merge())
                }.addOnSuccessListener {
                    avatarUrl = url; loading = false; message = "Аватар обновлён"
                }.addOnFailureListener {
                    loading = false; message = "Фото загружено, но профиль не обновлён: " + it.localizedMessage
                }
            }.addOnFailureListener { loading = false; message = "Не удалось загрузить аватар: ${it.localizedMessage}" }
        }
    }

    fun saveProfileChanges() {
        val nextUsername = editUsername.trim().removePrefix("@").lowercase()
        val nextBio = editBio.trim()
        val nextDisplayName = editDisplayName.trim()
        if (nextDisplayName.isBlank() || nextDisplayName.length > 40) { message = "Имя: от 1 до 40 символов"; return }
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
                "nickname" to nextUsername, "displayName" to nextDisplayName, "bio" to nextBio, "avatarUrl" to avatarUrl)
            transaction.set(db.collection("users").document(uid), common, com.google.firebase.firestore.SetOptions.merge())
            transaction.set(db.collection("publicProfiles").document(uid), common, com.google.firebase.firestore.SetOptions.merge())
            null
        }.addOnSuccessListener {
            username = nextUsername; displayName = nextDisplayName; bio = nextBio; editUsername = nextUsername; editDisplayName = nextDisplayName; editBio = nextBio
            onUsernameChanged(nextUsername); loading = false; showEdit = false; message = "Профиль сохранён"
        }.addOnFailureListener { loading = false; message = it.localizedMessage ?: "Не удалось сохранить профиль" }
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.height(34.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp).background(Red.copy(alpha = 0.12f), CircleShape).clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
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
        Text(displayName.ifBlank { username }, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("@${username.ifBlank { "username" }}", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
        Text(email, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        Text(bio.ifBlank { "Добавь описание профиля" }, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { editUsername = username; editDisplayName = displayName; editBio = bio; showEdit = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Изменить профиль", color = Red) }
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
                OutlinedTextField(value = editDisplayName, onValueChange = { editDisplayName = it.take(40) }, label = { Text("Имя") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
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
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Red, fontSize = 28.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            Text("Настройки", color = Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(22.dp))
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
                Text("NovixGram", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Версия ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Общайся, находи людей и настраивай NovixGram под себя.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}