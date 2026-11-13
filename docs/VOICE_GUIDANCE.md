# Turn-by-turn voice guidance

The shared `VoiceGuidancePlanner` converts live route progress into deduplicated
spoken prompts. It announces at configurable approach thresholds (800 m,
250 m and 80 m by default), resets thresholds for each new manoeuvre, and
announces arrival once per session.

The planner is platform-neutral and returns text only. A platform text-to-speech
adapter must speak that text, respect audio focus and mute settings, stop promptly
when navigation ends, and avoid speaking while GPS data is stale. Background
navigation, audio-focus behaviour, language/voice selection and physical-device
testing remain release requirements; the planner alone is not proof of working
voice output.

Shared tests cover threshold ordering, de-duplication, reset and arrival.
