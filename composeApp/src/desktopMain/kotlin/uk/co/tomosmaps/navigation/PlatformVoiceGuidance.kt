package uk.co.tomosmaps.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlatformVoiceGuidance(): VoiceGuidance {
    val voice = remember { EspeakVoiceGuidance() }
    DisposableEffect(voice) {
        onDispose { voice.close() }
    }
    return voice
}

/** Linux desktop speech adapter. Install espeak-ng (recommended) or espeak. */
private class EspeakVoiceGuidance : VoiceGuidance {
    private var process: Process? = null

    @Synchronized
    override fun announce(instruction: String) {
        if (instruction.isBlank()) return
        stop()
        for (command in listOf("espeak-ng", "espeak")) {
            try {
                process = ProcessBuilder(command, instruction)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                return
            } catch (_: Exception) {
                // Try the other commonly installed eSpeak executable.
            }
        }
    }

    @Synchronized
    override fun stop() {
        process?.destroy()
        process = null
    }

    override fun close() = stop()
}
