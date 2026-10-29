package vn.thanh.callnote

/** Gọi thư viện whisper.cpp (chạy offline trên máy). */
object WhisperLib {
    init {
        System.loadLibrary("callnote")
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(ptr: Long)
    private external fun transcribe(
        ptr: Long, samples: FloatArray, lang: String, threads: Int, prompt: String
    ): ByteArray

    fun transcribeText(ptr: Long, samples: FloatArray, lang: String, threads: Int, prompt: String): String =
        String(transcribe(ptr, samples, lang, threads, prompt), Charsets.UTF_8).trim()
}
