package net.integr.kai.driver.impl.groq

enum class GroqModel(val modelName: String) {
    QWEN_QWQ_32B("qwen-qwq-32b"),
    QWEN_QWEN3_32B("qwen/qwen3-32b"),
    DEEPSEEK_LLAMA_70B("deepseek-r1-distill-llama-70b"),
    GEMMA2_9B_IT("gemma2-9b-it"),
    COMPOUND_BETA("compound-beta"),
    COMPOUND_BETA_MINI("compound-beta-mini"),
    DISTIL_WHISPER_LARGE_V3_EN("distil-whisper-large-v3-en"),
    LLAMA_3_1_8B_INSTANT("llama-3.1-8b-instant"),
    LLAMA_3_3_70B_VERSATILE("llama-3.3-70b-versatile"),
    LLAMA3_70B_8192("llama3-70b-8192"),
    LLAMA3_8B_8192("llama3-8b-8192"),
    LLAMA_4_MAVERICK_17B("meta-llama/llama-4-maverick-17b-128e-instruct"),
    LLAMA_4_SCOUT_17B("meta-llama/llama-4-scout-17b-16e-instruct"),
    LLAMA_GUARD_4_12B("meta-llama/llama-guard-4-12b"),
    LLAMA_PROMPT_GUARD_2_22M("meta-llama/llama-prompt-guard-2-22m"),
    LLAMA_PROMPT_GUARD_2_86M("meta-llama/llama-prompt-guard-2-86m"),
    MISTRAL_SABA_24B("mistral-saba-24b"),
    WHISPER_LARGE_V3("whisper-large-v3"),
    WHISPER_LARGE_V3_TURBO("whisper-large-v3-turbo"),
    PLAYAI_TTS("playai-tts"),
    PLAYAI_TTS_ARABIC("playai-tts-arabic");
}