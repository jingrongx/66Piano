//
// pitch_yin.cpp —— YIN 基频检测算法实现
//
// 算法步骤：
//   1. 计算差分函数 d(tau) = sum_{j=0}^{W-1} (x[j] - x[j+tau])^2
//   2. 计算累积均值归一化差分函数 d'(tau)：
//        d'(0) = 1
//        d'(tau) = d(tau) / [(1/tau) * sum_{j=1}^{tau} d(j)]
//   3. 从 tau_min 开始扫描，寻找第一个低于 threshold 的局部极小；
//      若没有低于 threshold 的极小，则取全局极小；
//   4. 在该 tau 周围做抛物线插值，精化得到非整数 tau；
//   5. 频率 = sample_rate / tau；检测不到返回 -1.0f。
//

#include "pitch_yin.h"

#include <cmath>
#include <cstdlib>
#include <cstring>

// 任务要求的频率检测范围
static constexpr float YIN_MIN_FREQ_HZ = 50.0f;
static constexpr float YIN_MAX_FREQ_HZ = 2000.0f;

float yin_detect_pitch(const float *buffer, int length, int sample_rate, float threshold) {
    // ============ 边界检查 ============
    if (buffer == nullptr || length <= 0 || sample_rate <= 0) {
        return -1.0f;
    }

    // 计算 tau 的扫描范围
    // tau = sample_rate / freq，频率越高 tau 越小，频率越低 tau 越大
    const int tau_max = (int) ((float) sample_rate / YIN_MIN_FREQ_HZ);
    const int tau_min = (int) ((float) sample_rate / YIN_MAX_FREQ_HZ);
    if (tau_min < 1 || tau_max <= tau_min) {
        return -1.0f;
    }

    // YIN 要求 buffer 长度至少为 tau_max + W（W 为窗口长度，这里取 W = length - tau_max）
    // 需要保证 length - tau_max > 0
    if (length <= tau_max + 1) {
        // 缓冲区不足以容纳最大 tau，仍然尝试以 length/2 作为最大 tau
        const int effective_tau_max = (length / 2) - 1;
        if (effective_tau_max <= tau_min) {
            return -1.0f;
        }
        // 注意：effective_tau_max 对应的最低可检测频率更高，但仍尝试检测
        const int window_size = length - effective_tau_max;
        if (window_size <= 0) {
            return -1.0f;
        }

        // 分配差分函数与归一化差分函数缓冲区
        float *diff = (float *) malloc(sizeof(float) * (size_t)(effective_tau_max + 1));
        if (diff == nullptr) {
            return -1.0f;
        }
        float *cmndf = (float *) malloc(sizeof(float) * (size_t)(effective_tau_max + 1));
        if (cmndf == nullptr) {
            free(diff);
            return -1.0f;
        }

        // 步骤 1：计算差分函数 d(tau)，tau = 0..effective_tau_max
        diff[0] = 0.0f;
        for (int tau = 1; tau <= effective_tau_max; ++tau) {
            double sum = 0.0;
            for (int j = 0; j < window_size; ++j) {
                const float delta = buffer[j] - buffer[j + tau];
                sum += (double) delta * (double) delta;
            }
            diff[tau] = (float) sum;
        }

        // 步骤 2：累积均值归一化差分函数 d'(tau)
        // d'(0) = 1
        cmndf[0] = 1.0f;
        double running_sum = 0.0;
        for (int tau = 1; tau <= effective_tau_max; ++tau) {
            running_sum += diff[tau];
            if (running_sum > 0.0) {
                cmndf[tau] = (float) ((double) diff[tau] * (double) tau / running_sum);
            } else {
                cmndf[tau] = 1.0f;
            }
        }

        // 步骤 3：寻找第一个低于 threshold 的局部极小
        // 局部极小定义：cmndf[tau] < cmndf[tau-1] && cmndf[tau] < cmndf[tau+1]
        // 同时这里采用 YIN 论文的简化策略：从 tau_min 开始，找到第一个 cmndf[tau] <= threshold 的点；
        // 然后向前搜索局部极小。
        int best_tau = -1;
        float best_value = threshold + 1.0f;

        const int search_start = tau_min > 1 ? tau_min : 1;
        const int search_end = effective_tau_max - 1;

        // 先找低于 threshold 的局部极小
        for (int tau = search_start; tau <= search_end; ++tau) {
            if (cmndf[tau] <= threshold) {
                // 向前找到局部极小（cmndf 不再下降时停止）
                int local_min_tau = tau;
                while (local_min_tau + 1 <= effective_tau_max &&
                       cmndf[local_min_tau + 1] < cmndf[local_min_tau]) {
                    ++local_min_tau;
                }
                best_tau = local_min_tau;
                best_value = cmndf[local_min_tau];
                break;
            }
        }

        // 若未找到低于 threshold 的极小，则取全局极小
        if (best_tau == -1) {
            best_value = cmndf[search_start];
            best_tau = search_start;
            for (int tau = search_start + 1; tau <= effective_tau_max; ++tau) {
                if (cmndf[tau] < best_value) {
                    best_value = cmndf[tau];
                    best_tau = tau;
                }
            }
            // 若全局极小仍非常高（说明可能是无声或非周期信号），返回 -1
            if (best_value > 1.0f) {
                free(diff);
                free(cmndf);
                return -1.0f;
            }
        }

        // 步骤 4：抛物线插值精化
        // 用 cmndf 在 best_tau-1, best_tau, best_tau+1 三点拟合抛物线，找到极小点对应的非整数 tau
        float refined_tau = (float) best_tau;
        if (best_tau > 0 && best_tau < effective_tau_max) {
            const float s0 = cmndf[best_tau - 1];
            const float s1 = cmndf[best_tau];
            const float s2 = cmndf[best_tau + 1];
            const float denom = (2.0f * (2.0f * s1 - s2 - s0));
            if (denom != 0.0f) {
                const float shift = (s2 - s0) / denom;
                // 限制偏移在 [-1, 1] 内，避免数值异常
                const float clamped_shift = shift > 1.0f ? 1.0f : (shift < -1.0f ? -1.0f : shift);
                refined_tau = (float) best_tau + clamped_shift;
            }
        }

        free(diff);
        free(cmndf);

        // 步骤 5：计算频率
        if (refined_tau <= 0.0f) {
            return -1.0f;
        }
        const float freq = (float) sample_rate / refined_tau;
        if (freq < YIN_MIN_FREQ_HZ || freq > YIN_MAX_FREQ_HZ) {
            // 超出检测范围，认为不可靠
            return -1.0f;
        }
        return freq;
    }

    // ============ 正常分支：长度足够 ============
    const int window_size = length - tau_max;
    if (window_size <= 0) {
        return -1.0f;
    }

    // 分配差分函数与归一化差分函数缓冲区
    float *diff = (float *) malloc(sizeof(float) * (size_t)(tau_max + 1));
    if (diff == nullptr) {
        return -1.0f;
    }
    float *cmndf = (float *) malloc(sizeof(float) * (size_t)(tau_max + 1));
    if (cmndf == nullptr) {
        free(diff);
        return -1.0f;
    }

    // 步骤 1：计算差分函数 d(tau)
    diff[0] = 0.0f;
    for (int tau = 1; tau <= tau_max; ++tau) {
        double sum = 0.0;
        for (int j = 0; j < window_size; ++j) {
            const float delta = buffer[j] - buffer[j + tau];
            sum += (double) delta * (double) delta;
        }
        diff[tau] = (float) sum;
    }

    // 步骤 2：累积均值归一化差分函数 d'(tau)
    cmndf[0] = 1.0f;
    double running_sum = 0.0;
    for (int tau = 1; tau <= tau_max; ++tau) {
        running_sum += diff[tau];
        if (running_sum > 0.0) {
            cmndf[tau] = (float) ((double) diff[tau] * (double) tau / running_sum);
        } else {
            cmndf[tau] = 1.0f;
        }
    }

    // 步骤 3：寻找第一个低于 threshold 的局部极小
    int best_tau = -1;
    float best_value = threshold + 1.0f;

    const int search_start = tau_min > 1 ? tau_min : 1;
    const int search_end = tau_max - 1;

    for (int tau = search_start; tau <= search_end; ++tau) {
        if (cmndf[tau] <= threshold) {
            // 向前找到局部极小
            int local_min_tau = tau;
            while (local_min_tau + 1 <= tau_max &&
                   cmndf[local_min_tau + 1] < cmndf[local_min_tau]) {
                ++local_min_tau;
            }
            best_tau = local_min_tau;
            best_value = cmndf[local_min_tau];
            break;
        }
    }

    // 若未找到低于 threshold 的极小，则取全局极小
    if (best_tau == -1) {
        best_value = cmndf[search_start];
        best_tau = search_start;
        for (int tau = search_start + 1; tau <= tau_max; ++tau) {
            if (cmndf[tau] < best_value) {
                best_value = cmndf[tau];
                best_tau = tau;
            }
        }
        if (best_value > 1.0f) {
            free(diff);
            free(cmndf);
            return -1.0f;
        }
    }

    // 步骤 4：抛物线插值精化
    float refined_tau = (float) best_tau;
    if (best_tau > 0 && best_tau < tau_max) {
        const float s0 = cmndf[best_tau - 1];
        const float s1 = cmndf[best_tau];
        const float s2 = cmndf[best_tau + 1];
        const float denom = (2.0f * (2.0f * s1 - s2 - s0));
        if (denom != 0.0f) {
            const float shift = (s2 - s0) / denom;
            const float clamped_shift = shift > 1.0f ? 1.0f : (shift < -1.0f ? -1.0f : shift);
            refined_tau = (float) best_tau + clamped_shift;
        }
    }

    free(diff);
    free(cmndf);

    // 步骤 5：计算频率
    if (refined_tau <= 0.0f) {
        return -1.0f;
    }
    const float freq = (float) sample_rate / refined_tau;
    if (freq < YIN_MIN_FREQ_HZ || freq > YIN_MAX_FREQ_HZ) {
        return -1.0f;
    }
    return freq;
}
