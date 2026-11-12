package uk.co.tomosmaps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
fun NavigationPreview(
    currentLatitude: Double?,
    currentLongitude: Double?,
    onEndPreview: () -> Unit,
    onOverview: () -> Unit,
) {
    val hasLocation = currentLatitude != null && currentLongitude != null

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
                    if (hasLocation) "●" else "◎",
                    color = NavigationCyan,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        if (hasLocation) "GPS location acquired" else "Waiting for GPS location",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (hasLocation) {
                        Text(
                            "Lat %.5f · Lon %.5f".format(currentLatitude, currentLongitude),
                            color = NavigationMuted,
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            "Allow location access and wait for a device fix.",
                            color = NavigationMuted,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        "LIVE LOCATION · ROUTE NOT STARTED",
                        color = NavigationCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NavigationPanel,
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 22.dp,
                bottomEnd = 22.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, NavigationPanelBorder),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .size(width = 48.dp, height = 5.dp)
                        .background(Color(0xFF58677D), CircleShape)
                )
                Text(
                    "Live route guidance is not ready yet",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Your GPS position is available to the app. A destination and a configured routing service are still needed before TomOS Maps can calculate turns, distance remaining or arrival time.",
                    color = NavigationMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp)
                        .background(NavigationPanelBorder)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onEndPreview) {
                        Text(
                            "✕  End navigation",
                            color = Color(0xFFFF737B),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = onOverview) {
                        Text("⌕ Overview", color = NavigationMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
