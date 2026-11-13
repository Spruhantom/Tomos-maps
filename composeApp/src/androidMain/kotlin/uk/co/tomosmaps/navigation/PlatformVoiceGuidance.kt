package uk.co.tomosmaps.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
actual fun rememberPlatformVoiceGuidance(): VoiceGuidance {
    val context = LocalContext.current.applicationContext
    val voice = remember(context) { AndroidVoiceGuidance(context) }
    DisposableEffect(voice) {
        onDispose { voice.dispose() }
    }
    return voice
}

private class AndroidVoiceGuidance(context: Context) :
    VoiceGuidance,
    TextToSpeech.OnInitListener {

    private var engine: TextToSpeech? = null
    @Volatile private var ready = false

    init {
        engine = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        val textToSpeech = engine ?: return
        ready = status == TextToSpeech.SUCCESS &&
            textToSpeech.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE
        if (ready) textToSpeech.language = Locale.UK
    }

    override suspend fun announce(instruction: ManeuverInstruction) {
        if (!ready || instruction.text.isBlank()) return
        engine?.speak(instruction.text, TextToSpeech.QUEUE_FLUSH, null, "tomos-navigation")
    }

    override suspend fun stop() {
        engine?.stop()
    }

    fun dispose() {
        ready = false
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
