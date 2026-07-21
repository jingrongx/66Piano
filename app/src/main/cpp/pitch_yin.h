//
// pitch_yin.h —— YIN 基频检测算法
//
// 算法参考文献：
//   de Cheveigné, A. & Kawahara, H. (2002).
//   "YIN, a fundamental frequency estimator for speech and music."
//   JASA 111(4), 1917-1930.
//
// 适用于钢琴音域 A0 (~27.5 Hz) ~ B6 (~1975 Hz)，
// 但任务要求检测范围限定为 50 Hz ~ 2000 Hz。
//

#ifndef PIANOKIDS_PITCH_YIN_H
#define PIANOKIDS_PITCH_YIN_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * YIN 基频检测。
 *
 * @param buffer      输入音频样本（float，通常为 -1.0 ~ 1.0 归一化值，但任意线性比例均可）。
 * @param length      样本数量。建议长度至少为 2 * sample_rate / min_freq（约 2 * sr / 50 = 0.88 ms * sr）。
 * @param sample_rate 采样率（Hz）。
 * @param threshold   YIN 阈值，默认 0.15，典型范围 0.10 ~ 0.20。
 *
 * @return 检测到的基频（Hz）。检测不到返回 -1.0f。
 */
float yin_detect_pitch(const float *buffer, int length, int sample_rate, float threshold);

#ifdef __cplusplus
}
#endif

#endif // PIANOKIDS_PITCH_YIN_H
