//
// jni_bridge.cpp —— JNI 桥接，供 Kotlin 调用
//
// 对应 Kotlin 类：com.pianokids.audio.NativeAudioEngine
//
// 导出函数：
//   - nativeDetectPitch(buffer: FloatArray, length: Int, sampleRate: Int): Float
//   - nativeDetectOnset(buffer: FloatArray, length: Int): Float
//   - nativeFreqToNoteName(freq: Float): String
//   - nativeFreqToCentsOff(freq: Float, midiNote: Int): Float
//   - nativeFreqToMidiNote(freq: Float): Int
//

#include <jni.h>
#include <android/log.h>

#include "pitch_yin.h"
#include "note_detector.h"

#define LOG_TAG "pianokids-native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 默认 YIN 阈值，与算法保持一致
static constexpr float kDefaultYinThreshold = 0.15f;

extern "C" {

JNIEXPORT jfloat JNICALL
Java_com_pianokids_audio_NativeAudioEngine_nativeDetectPitch(
        JNIEnv *env, jobject /*thiz*/,
        jfloatArray buffer, jint length, jint sample_rate) {

    // 边界检查
    if (env == nullptr || buffer == nullptr) {
        LOGW("nativeDetectPitch: env 或 buffer 为空");
        return -1.0f;
    }
    if (length <= 0 || sample_rate <= 0) {
        LOGW("nativeDetectPitch: length=%d sampleRate=%d 非法", (int) length, (int) sample_rate);
        return -1.0f;
    }

    const jsize array_len = env->GetArrayLength(buffer);
    if (array_len <= 0 || length > array_len) {
        LOGW("nativeDetectPitch: 数组长度不匹配, arrayLen=%d, requested length=%d",
             (int) array_len, (int) length);
        return -1.0f;
    }

    // 取出 float[] 内部指针（可能复制一份）
    jboolean is_copy = JNI_FALSE;
    jfloat *elements = env->GetFloatArrayElements(buffer, &is_copy);
    if (elements == nullptr) {
        LOGE("nativeDetectPitch: GetFloatArrayElements 失败 (OOM?)");
        return -1.0f;
    }

    // 调用 YIN 算法
    const float pitch = yin_detect_pitch(
            reinterpret_cast<const float *>(elements),
            (int) length,
            (int) sample_rate,
            kDefaultYinThreshold);

    // 释放数组元素，使用 JNI_ABORT 模式：我们只读不写，不需要回写 Java 端
    env->ReleaseFloatArrayElements(buffer, elements, JNI_ABORT);

    return pitch;
}

JNIEXPORT jfloat JNICALL
Java_com_pianokids_audio_NativeAudioEngine_nativeDetectOnset(
        JNIEnv *env, jobject /*thiz*/,
        jfloatArray buffer, jint length) {

    if (env == nullptr || buffer == nullptr) {
        LOGW("nativeDetectOnset: env 或 buffer 为空");
        return 0.0f;
    }
    if (length <= 0) {
        LOGW("nativeDetectOnset: length=%d 非法", (int) length);
        return 0.0f;
    }

    const jsize array_len = env->GetArrayLength(buffer);
    if (array_len <= 0 || length > array_len) {
        LOGW("nativeDetectOnset: 数组长度不匹配, arrayLen=%d, requested length=%d",
             (int) array_len, (int) length);
        return 0.0f;
    }

    jboolean is_copy = JNI_FALSE;
    jfloat *elements = env->GetFloatArrayElements(buffer, &is_copy);
    if (elements == nullptr) {
        LOGE("nativeDetectOnset: GetFloatArrayElements 失败");
        return 0.0f;
    }

    const float energy = detect_onset_energy(
            reinterpret_cast<const float *>(elements),
            (int) length);

    env->ReleaseFloatArrayElements(buffer, elements, JNI_ABORT);

    return energy;
}

JNIEXPORT jstring JNICALL
Java_com_pianokids_audio_NativeAudioEngine_nativeFreqToNoteName(
        JNIEnv *env, jobject /*thiz*/,
        jfloat freq) {

    if (env == nullptr) {
        return nullptr;
    }

    const char *name = freq_to_note_name((float) freq);
    if (name == nullptr) {
        return env->NewStringUTF("N/A");
    }
    return env->NewStringUTF(name);
}

JNIEXPORT jfloat JNICALL
Java_com_pianokids_audio_NativeAudioEngine_nativeFreqToCentsOff(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jfloat freq, jint midi_note) {

    return freq_to_cents_off((float) freq, (int) midi_note);
}

JNIEXPORT jint JNICALL
Java_com_pianokids_audio_NativeAudioEngine_nativeFreqToMidiNote(
        JNIEnv * /*env*/, jobject /*thiz*/,
        jfloat freq) {

    return (jint) freq_to_midi_note((float) freq);
}

}  // extern "C"
