package uk.co.tomosmaps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("TomOS Maps", style = MaterialTheme.typography.headlineLarge)
                Text("Offline-first navigation for Android and Linux", style = MaterialTheme.typography.bodyLarge)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Navigation status", style = MaterialTheme.typography.titleMedium)
                        Text("Ready for local map data")
                        Text("Core navigation is designed to work without an account or network connection.")
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Offline maps", style = MaterialTheme.typography.titleMedium)
                        Text("Downloaded regions will be stored on this device.")
                        Text("Map packages, routing graphs and search data will be managed independently.")
                        Button(onClick = { /* Connect the map package manager in the offline milestone. */ }) {
                            Text("Manage map regions")
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Shared platform core", style = MaterialTheme.typography.titleMedium)
                        Text("Android and Linux use the same navigation domain models and offline package contracts.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Android")
                            Text("•")
                            Text("Linux")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Prototype foundation — map rendering, GPS and routing engine integration are not connected yet.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
