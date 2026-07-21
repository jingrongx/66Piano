//
// note_detector.cpp —— 音符 onset 检测与音名转换实现
//

#include "note_detector.h"

#include <cmath>
#include <cstdio>
#include <cstring>

#ifndef M_LN2
#define M_LN2 0.69314718055994530942
#endif

// 标准音 A4 = 440 Hz，对应 MIDI 编号 69
static constexpr float A4_FREQ = 440.0f;
static constexpr int A4_MIDI = 69;

// 升号表示的音名表（按 MIDI % 12 索引）
// 0=C, 1=C#, 2=D, 3=D#, 4=E, 5=F, 6=F#, 7=G, 8=G#, 9=A, 10=A#, 11=B
static const char *const kNoteNames[12] = {
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B"
};

float detect_onset_energy(const float *buffer, int length) {
    // 边界检查
    if (buffer == nullptr || length <= 0) {
        return 0.0f;
    }

    // 计算平方和（使用 double 累加以避免长 buffer 累加误差）
    double sum_sq = 0.0;
    for (int i = 0; i < length; ++i) {
        const double s = (double) buffer[i];
        sum_sq += s * s;
    }

    // RMS = sqrt(mean(sum_sq))
    const double mean_sq = sum_sq / (double) length;
    if (mean_sq < 0.0) {
        return 0.0f;
    }
    return (float) sqrt(mean_sq);
}

int freq_to_midi_note(float freq) {
    // 边界检查
    if (freq <= 0.0f || !isfinite(freq)) {
        return -1;
    }
    // MIDI = 69 + 12 * log2(freq / 440)
    const double ratio = (double) freq / (double) A4_FREQ;
    const double midi_double = (double) A4_MIDI + 12.0 * log(ratio) / M_LN2;
    if (!isfinite(midi_double)) {
        return -1;
    }
    // 四舍五入到最近整数
    return (int) lround(midi_double);
}

float freq_to_cents_off(float freq, int midi_note) {
    // 边界检查
    if (freq <= 0.0f || !isfinite(freq)) {
        return 0.0f;
    }
    // 该 MIDI 编号对应的标准频率
    // standard_freq = 440 * 2^((midi - 69) / 12)
    const double midi_diff = (double) (midi_note - A4_MIDI);
    const double standard_freq = (double) A4_FREQ * pow(2.0, midi_diff / 12.0);
    if (standard_freq <= 0.0) {
        return 0.0f;
    }
    // cents = 1200 * log2(freq / standard_freq)
    double cents = 1200.0 * log((double) freq / standard_freq) / M_LN2;
    if (!isfinite(cents)) {
        return 0.0f;
    }
    // 规约到 [-50, +50]
    cents = fmod(cents + 50.0, 100.0);
    if (cents < 0.0) {
        cents += 100.0;
    }
    cents -= 50.0;
    return (float) cents;
}

const char *freq_to_note_name(float freq) {
    // 静态线程局部缓冲区：返回静态字符串，符合接口约定
    // thread_local 保证多线程调用安全
    static thread_local char name_buf[8];

    if (freq <= 0.0f || !isfinite(freq)) {
        // 异常输入：返回 "N/A"
        std::snprintf(name_buf, sizeof(name_buf), "N/A");
        return name_buf;
    }

    const int midi = freq_to_midi_note(freq);
    if (midi < 0) {
        std::snprintf(name_buf, sizeof(name_buf), "N/A");
        return name_buf;
    }

    // octave = (midi / 12) - 1
    // 例如 MIDI 69 = A4, MIDI 60 = C4, MIDI 0 = C-1
    const int note_index = midi % 12;
    const int octave = (midi / 12) - 1;

    // 处理 note_index 为负的情况（理论上 midi >= 0 不会出现，但稳健起见）
    const int safe_note_index = (note_index < 0) ? (note_index + 12) : note_index;

    std::snprintf(name_buf, sizeof(name_buf), "%s%d",
                  kNoteNames[safe_note_index], octave);
    return name_buf;
}
