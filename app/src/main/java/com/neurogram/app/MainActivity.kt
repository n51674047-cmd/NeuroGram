package com.neurogram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF09090D)
private val Panel = Color(0xFF15151D)
private val Red = Color(0xFFFF334F)
private val Muted = Color(0xFF92929F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Bg,
                surface = Panel,
                primary = Red,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                NeuroGramApp()
            }
        }
    }
}

private data class ChatPreview(
    val name: String,
    val message: String,
    val time: String,
    val unread: Int,
    val color: Color
)

private val demoChats = listOf(
    ChatPreview("Александр", "Окей, договорились", "12:48", 2, Color(0xFF9B5DE5)),
    ChatPreview("Мария", "Отправила фотографию", "11:32", 0, Color(0xFF00B4D8)),
    ChatPreview("Разработка", "Ты: скоро покажу результат", "Вчера", 5, Color(0xFFFF8C42)),
    ChatPreview("Илья", "Ахах, понятно", "Пн", 0, Color(0xFF43AA8B))
)

@Composable
private fun NeuroGramApp() {
    var selected by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        if (selected == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).background(Red, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("N", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("NeuroGram", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("ТВОЁ ОБЩЕНИЕ. ТВОИ ПРАВИЛА.", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp)
                    }
                }

                Spacer(Modifier.size(30.dp))
                Text("Сообщения", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Твои чаты в одном месте", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp, bottom = 22.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(demoChats) { chat ->
                        ChatRow(chat = chat, onClick = { selected = chat.name })
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "ПЕРВАЯ ВЕРСИЯ • ДЕМО-РЕЖИМ",
                    color = Muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text("‹  Назад", color = Red, fontSize = 16.sp, modifier = Modifier.clickable { selected = null })
                Spacer(Modifier.size(28.dp))
                Text(selected ?: "", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Личный чат", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    "Это демонстрационный экран. Сообщения пока не отправляются на другие устройства.",
                    color = Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatPreview, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(chat.color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(chat.name.take(1), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(chat.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(chat.message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            Text(chat.time, color = Muted, fontSize = 10.sp)
            if (chat.unread > 0) {
                Box(
                    modifier = Modifier.padding(top = 7.dp).background(Red, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(chat.unread.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
