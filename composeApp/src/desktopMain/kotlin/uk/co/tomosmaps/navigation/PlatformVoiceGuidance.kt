package uk.co.tomosmaps.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlatformVoiceGuidance(): VoiceGuidance {
    val voice = remember { EspeakVoiceGuidance() }
    DisposableEffect(voice) {
        onDispose { voice.dispose() }
    }
    return voice
}

/** Linux desktop speech adapter. Install espeak-ng (recommended) or espeak. */
private class EspeakVoiceGuidance : VoiceGuidance {
    private var process: Process? = null

    @Synchronized
    override suspend fun announce(instruction: ManeuverInstruction) {
        if (instruction.text.isBlank()) return
        stop()
        for (command in listOf("espeak-ng", "espeak")) {
            try {
                process = ProcessBuilder(command, instruction.text)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                return
            } catch (_: Exception) {
                // Try the other commonly installed eSpeak executable.
            }
        }
    }

    @Synchronized
    override suspend fun stop() {
        process?.destroy()
        process = null
    }

    fun dispose() {
        process?.destroy()
        process = null
    }
}
