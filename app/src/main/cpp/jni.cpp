// Cầu nối Kotlin <-> whisper.cpp: nạp mô hình và chép lời âm thanh 16 kHz mono
#include <jni.h>
#include <string>
#include <cstdio>
#include "whisper.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CallNote", __VA_ARGS__)
#else
#define LOGI(...) fprintf(stderr, __VA_ARGS__)
#endif

static void fmt_ts(int64_t cs, char *buf, size_t n) {
    int64_t s = cs / 100;
    snprintf(buf, n, "%02lld:%02lld", (long long)(s / 60), (long long)(s % 60));
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_vn_thanh_callnote_WhisperLib_initContext(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    LOGI("init model %s -> %p\n", path, (void *)ctx);
    env->ReleaseStringUTFChars(jpath, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_vn_thanh_callnote_WhisperLib_freeContext(JNIEnv *, jobject, jlong ptr) {
    if (ptr != 0) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

JNIEXPORT jbyteArray JNICALL
Java_vn_thanh_callnote_WhisperLib_transcribe(JNIEnv *env, jobject, jlong ptr, jfloatArray jsamples,
                                             jstring jlang, jint threads, jstring jprompt) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx == nullptr) return env->NewByteArray(0);

    jsize n = env->GetArrayLength(jsamples);
    jfloat *samples = env->GetFloatArrayElements(jsamples, nullptr);
    const char *lang = env->GetStringUTFChars(jlang, nullptr);
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.print_realtime = false;
    p.print_progress = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.translate = false;
    p.language = lang;
    p.n_threads = threads > 0 ? threads : 4;
    p.no_context = true;
    p.single_segment = false;
    p.suppress_blank = true;
    if (prompt != nullptr && prompt[0] != '\0') p.initial_prompt = prompt;

    std::string out;
    int rc = whisper_full(ctx, p, samples, n);
    if (rc == 0) {
        int segs = whisper_full_n_segments(ctx);
        char ts[32];
        for (int i = 0; i < segs; ++i) {
            const char *t = whisper_full_get_segment_text(ctx, i);
            if (t == nullptr) continue;
            std::string s(t);
            size_t b = s.find_first_not_of(' ');
            if (b == std::string::npos) continue;
            fmt_ts(whisper_full_get_segment_t0(ctx, i), ts, sizeof(ts));
            out += "[";
            out += ts;
            out += "] ";
            out += s.substr(b);
            out += "\n";
        }
    } else {
        LOGI("whisper_full failed: %d\n", rc);
        out = "";
    }

    env->ReleaseStringUTFChars(jprompt, prompt);
    env->ReleaseStringUTFChars(jlang, lang);
    env->ReleaseFloatArrayElements(jsamples, samples, JNI_ABORT);
    jbyteArray res = env->NewByteArray((jsize)out.size());
    env->SetByteArrayRegion(res, 0, (jsize)out.size(), reinterpret_cast<const jbyte *>(out.data()));
    return res;
}

}
