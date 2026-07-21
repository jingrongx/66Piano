//
// note_detector.h —— 音符 onset 检测与音名转换
//
// 提供：
//   - RMS 能量 onset 检测
//   - 频率 -> 音名（带升号 #）
//   - 频率 -> MIDI 编号（A4 = 69）
//   - 频率相对标准音高的音分偏差（-50 ~ +50）
//

#ifndef PIANOKIDS_NOTE_DETECTOR_H
#define PIANOKIDS_NOTE_DETECTOR_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 计算音频帧的 RMS 能量，用于 onset / 静音判断。
 *
 * @param buffer 输入音频样本（任意线性比例均可）。
 * @param length 样本数量。
 * @return RMS 能量（>= 0）。输入异常返回 0.0f。
 */
float detect_onset_energy(const float *buffer, int length);

/**
 * 频率 -> 音名（带升号），如 "A4"、"C#5"、"G#3"。
 * 标准音 A4 = 440 Hz，MIDI 69。
 * 返回静态字符串（无需调用方释放）。
 *
 * @param freq 频率（Hz）。<= 0 或异常返回 "N/A"。
 */
const char *freq_to_note_name(float freq);

/**
 * 频率 -> MIDI 编号。
 * A4(440Hz) = 69。返回最接近的整数 MIDI 编号。
 * freq <= 0 返回 -1。
 */
int freq_to_midi_note(float freq);

/**
 * 频率相对给定 MIDI 标准音高的音分偏差。
 * 返回值范围 [-50, +50]，正值表示偏高，负值表示偏低。
 * 输入异常返回 0.0f。
 */
float freq_to_cents_off(float freq, int midi_note);

#ifdef __cplusplus
}
#endif

#endif // PIANOKIDS_NOTE_DETECTOR_H
