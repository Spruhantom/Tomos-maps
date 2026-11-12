package uk.co.tomosmaps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NavigationPanel = Color(0xEE182235)
private val NavigationPanelBorder = Color(0xFF40516B)
private val NavigationCyan = Color(0xFF42F3FF)
private val NavigationMuted = Color(0xFF9AA9BD)
private val NavigationGreen = Color(0xFF42E9AE)

@Composable
fun NavigationPreview(
    onEndPreview: () -> Unit,
    onOverview: () -> Unit,
    onSettings: () -> Unit,
) {
    var voiceEnabled by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NavigationPanel,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NavigationPanelBorder),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "↱",
                    color = NavigationCyan,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Turn right in 200 m",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("onto High Street", color = NavigationMuted, fontSize = 17.sp)
                    Text(
                        "DEMO PREVIEW · SAMPLE GUIDANCE",
                        color = NavigationCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }
                Text("◈", color = NavigationCyan, fontSize = 30.sp)
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NavigationPanel,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NavigationPanelBorder),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .size(width = 48.dp, height = 5.dp)
                        .background(Color(0xFF58677D), CircleShape)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("12", color = NavigationGreen, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                            Text(" min", color = NavigationGreen, fontSize = 19.sp)
                        }
                        Text("remaining", color = NavigationMuted, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("4.2 mi", color = Color(0xFFD5DCE7), fontSize = 27.sp, fontWeight = FontWeight.Medium)
                        Text("distance", color = NavigationMuted, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("11:45", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Medium)
                        Text("arrival · sample", color = NavigationMuted, fontSize = 11.sp)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().size(width = 1.dp, height = 1.dp)
                        .background(NavigationPanelBorder)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onEndPreview) {
                        Text("✕  End preview", color = Color(0xFFFF737B), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { voiceEnabled = !voiceEnabled }) {
                            Text(if (voiceEnabled) "♫ Voice on" else "♫ Muted", color = NavigationMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = onOverview) {
                            Text("⌕ Overview", color = NavigationMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            showSettings = !showSettings
                            onSettings()
                        }) {
                            Text("⚙", color = NavigationMuted, fontSize = 20.sp)
                        }
                    }
                }
                if (showSettings) {
                    Text(
                        "Navigation settings will be connected to route guidance in a later milestone.",
                        color = NavigationMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
