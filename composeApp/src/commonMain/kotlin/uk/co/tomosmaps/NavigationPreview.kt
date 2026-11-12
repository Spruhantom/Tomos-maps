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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.tomosmaps.navigation.LiveNavigationSnapshot
import uk.co.tomosmaps.navigation.NavigationStatus

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
    navigation: LiveNavigationSnapshot,
    gpsIsStale: Boolean,
    routeMessage: String?,
    isCalculatingRoute: Boolean,
    onCalculateRoute: (String, Double, Double) -> Unit,
) {
    val hasLocation = currentLatitude != null && currentLongitude != null
    var endpoint by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    val progress = navigation.progress

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
                        if (gpsIsStale) "GPS SIGNAL STALE · GUIDANCE PAUSED" else if (navigation.status == NavigationStatus.ACTIVE) "LIVE GPS · ROUTE ACTIVE" else if (navigation.status == NavigationStatus.ARRIVED) "DESTINATION REACHED" else "LIVE LOCATION · ROUTE NOT STARTED",
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
                    when (navigation.status) {
                        NavigationStatus.ACTIVE -> "Live route guidance"
                        NavigationStatus.ARRIVED -> "You have arrived"
                        else -> "Plan a route"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (gpsIsStale) Text("GPS fix is over 10 seconds old. Navigation updates are paused until a fresh fix arrives.", color = Color(0xFFFFC36A), style = MaterialTheme.typography.bodySmall)
                if (progress != null) {
                    Text(progress.nextManeuver?.instruction ?: "Continue along the route",
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("%.0f m to next manoeuvre · %.0f m remaining".format(
                        progress.distanceToManeuverMeters ?: 0.0,
                        progress.distanceRemainingMeters
                    ), color = NavigationMuted, style = MaterialTheme.typography.bodySmall)
                    if (!gpsIsStale && progress.estimatedSecondsRemaining > 0) {
                        Text("Estimated time remaining: ${progress.estimatedSecondsRemaining / 60} min",
                            color = NavigationMuted, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("Enter your Valhalla service URL and destination coordinates (latitude, longitude).",
                        color = NavigationMuted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = endpoint, onValueChange = { endpoint = it },
                        label = { Text("Valhalla HTTPS endpoint") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = destination, onValueChange = { destination = it },
                        label = { Text("Destination: latitude, longitude") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    val coordinates = destination.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                    Button(
                        enabled = hasLocation && !isCalculatingRoute && coordinates.size == 2 &&
                            coordinates[0] in -90.0..90.0 && coordinates[1] in -180.0..180.0 &&
                            endpoint.isNotBlank(),
                        onClick = { onCalculateRoute(endpoint, coordinates[0], coordinates[1]) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isCalculatingRoute) "Calculating route…" else "Calculate route") }
                    routeMessage?.let { Text(it, color = NavigationMuted, style = MaterialTheme.typography.bodySmall) }
                }
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
