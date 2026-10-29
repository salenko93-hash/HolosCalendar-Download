#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <string>
#include <vector>
#include <stdexcept>
#include <chrono>

namespace {
struct Session {
    whisper_context *context = nullptr;
    std::atomic<bool> cancelled{false};
    std::atomic<bool> running{false};
    ~Session() { if (context) whisper_free(context); }
};
std::mutex sessions_mutex;
std::unordered_map<jlong, std::shared_ptr<Session>> sessions;
std::atomic<jlong> next_id{1};
std::once_flag logging;
void fail(JNIEnv *env, const char *message, bool timed_out = false) {
    jclass cls = env->FindClass(timed_out ? "java/io/InterruptedIOException" : "java/io/IOException");
    if (cls) env->ThrowNew(cls, message);
}
std::shared_ptr<Session> lookup(jlong id) {
    std::lock_guard<std::mutex> lock(sessions_mutex);
    auto found = sessions.find(id);
    return found == sessions.end() ? nullptr : found->second;
}
struct AbortState {
    Session *session;
    std::chrono::steady_clock::time_point deadline;
    std::atomic<bool> timed_out{false};
};
bool abort_inference(void *data) {
    auto *state = static_cast<AbortState *>(data);
    if (state->session->cancelled.load()) return true;
    if (std::chrono::steady_clock::now() >= state->deadline) {
        state->timed_out.store(true);
        return true;
    }
    return false;
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_ua_holoscalendar_redmi_WhisperEngine_nativeCreate(JNIEnv *env, jclass, jstring path) {
    try {
        if (!path) { fail(env, "Missing model"); return 0; }
        const char *chars = env->GetStringUTFChars(path, nullptr);
        if (!chars) return 0;
        std::string filename(chars);
        env->ReleaseStringUTFChars(path, chars);
        std::call_once(logging, [] { whisper_log_set([](ggml_log_level, const char *, void *) {}, nullptr); });
        auto session = std::make_shared<Session>();
        auto params = whisper_context_default_params();
        params.use_gpu = false;
        session->context = whisper_init_from_file_with_params(filename.c_str(), params);
        if (!session->context || !whisper_is_multilingual(session->context)) {
            fail(env, "Cannot load multilingual model"); return 0;
        }
        jlong id = next_id.fetch_add(1);
        std::lock_guard<std::mutex> lock(sessions_mutex);
        sessions[id] = session;
        return id;
    } catch (const std::exception &) { fail(env, "Cannot allocate speech model"); return 0; }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_ua_holoscalendar_redmi_WhisperEngine_nativeTranscribe(JNIEnv *env, jclass, jlong id, jfloatArray audio, jint threads, jint timeout_ms) {
    auto session = lookup(id);
    if (!session || !audio) { fail(env, "Speech session is closed"); return nullptr; }
    if (session->running.exchange(true)) { fail(env, "Speech session is busy"); return nullptr; }
    struct Release { Session *session; ~Release() { session->running.store(false); } } release{session.get()};
    try {
        jsize count = env->GetArrayLength(audio);
        if (count < 16000 || count > 480000) { fail(env, "Invalid recording length"); return nullptr; }
        std::vector<float> samples(count);
        env->GetFloatArrayRegion(audio, 0, count, samples.data());
        if (env->ExceptionCheck()) return nullptr;
        auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = threads < 1 ? 1 : threads > 4 ? 4 : threads;
        params.language = "uk";
        params.translate = false;
        params.detect_language = false;
        params.no_context = true;
        params.n_max_text_ctx = 0;
        params.no_timestamps = true;
        params.print_special = false;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.suppress_nst = true;
        params.max_tokens = 128;
        params.greedy.best_of = 1;
        if (timeout_ms < 1 || timeout_ms > 120000) { fail(env, "Invalid speech timeout"); return nullptr; }
        AbortState abort{session.get(), std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms)};
        params.abort_callback = abort_inference;
        params.abort_callback_user_data = &abort;
        if (session->cancelled.load()) { fail(env, "Cancelled"); return nullptr; }
        int result = whisper_full(session->context, params, samples.data(), count);
        if (session->cancelled.load()) { fail(env, "Cancelled"); return nullptr; }
        if (abort.timed_out.load()) { fail(env, "Speech inference timed out", true); return nullptr; }
        if (result != 0) { fail(env, "Speech inference failed"); return nullptr; }
        std::string text;
        int segments = whisper_full_n_segments(session->context);
        for (int i = 0; i < segments; i++) {
            const char *segment = whisper_full_get_segment_text(session->context, i);
            if (segment) text += segment;
            if (text.size() > 16384) { fail(env, "Speech result is too long"); return nullptr; }
        }
        // Return actual UTF-8, rather than JNI modified UTF-8, including four-byte code points.
        jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
        if (bytes) env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
        return bytes;
    } catch (const std::exception &) { fail(env, "Cannot allocate speech buffers"); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_ua_holoscalendar_redmi_WhisperEngine_nativeCancel(JNIEnv *, jclass, jlong id) {
    auto session = lookup(id);
    if (session) session->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_ua_holoscalendar_redmi_WhisperEngine_nativeClose(JNIEnv *, jclass, jlong id) {
    // A running call keeps a shared reference until it exits; cancellation cannot free live tensors.
    std::shared_ptr<Session> session;
    { std::lock_guard<std::mutex> lock(sessions_mutex);
      auto found = sessions.find(id);
      if (found != sessions.end()) { session = found->second; sessions.erase(found); }
    }
    if (session) session->cancelled.store(true);
}
