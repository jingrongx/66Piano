//
// kiss_fft.cpp —— 轻量级 radix-2 Cooley-Tukey FFT 实现
//
// 设计目标：
//   1. 接口与 KissFFT 风格一致，便于替换；
//   2. 仅支持 2 的幂长度，去掉通用素因子分解带来的复杂度；
//   3. 无任何第三方依赖，便于嵌入 NDK 项目；
//   4. 使用预计算的旋转因子表与位反转表，运行时仅做蝶形运算。
//

#include "kiss_fft.h"

#include <cmath>
#include <cstdlib>
#include <cstring>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// 内部 FFT 状态：保存变换长度、方向标志、旋转因子表与位反转表
struct kiss_fft_state {
    int nfft;            // 变换长度（必须为 2 的幂）
    int inverse_fft;     // 是否为逆变换
    int *bit_reverse;    // 位反转索引表，长度为 nfft
    kiss_fft_cpx *twiddles; // 旋转因子表，长度为 nfft/2
};

// 判断整数是否为 2 的幂
int kiss_fft_is_power_of_two(int nfft) {
    return nfft > 0 && (nfft & (nfft - 1)) == 0;
}

// 计算 log2(x)，要求 x 为 2 的幂
static int kiss_fft_log2(int x) {
    int k = 0;
    while ((1 << k) < x) {
        ++k;
    }
    return k;
}

kiss_fft_cfg kiss_fft_alloc(int nfft, int inverse_fft, void * /*mem*/, size_t * /*lenmem*/) {
    // 边界检查：长度必须为 2 的幂
    if (!kiss_fft_is_power_of_two(nfft)) {
        return nullptr;
    }

    // 分配状态结构
    kiss_fft_state *st = (kiss_fft_state *) malloc(sizeof(kiss_fft_state));
    if (st == nullptr) {
        return nullptr;
    }

    st->nfft = nfft;
    st->inverse_fft = inverse_fft != 0;
    st->bit_reverse = nullptr;
    st->twiddles = nullptr;

    // 分配位反转表
    st->bit_reverse = (int *) malloc(sizeof(int) * (size_t) nfft);
    if (st->bit_reverse == nullptr) {
        free(st);
        return nullptr;
    }

    // 计算位反转索引
    const int bits = kiss_fft_log2(nfft);
    for (int i = 0; i < nfft; ++i) {
        int x = i;
        int rev = 0;
        for (int b = 0; b < bits; ++b) {
            rev = (rev << 1) | (x & 1);
            x >>= 1;
        }
        st->bit_reverse[i] = rev;
    }

    // 分配并计算旋转因子表 W_N^k = exp(-2πi k / N)（逆变换取正号）
    const int half = nfft / 2;
    st->twiddles = (kiss_fft_cpx *) malloc(sizeof(kiss_fft_cpx) * (size_t)(half > 0 ? half : 1));
    if (st->twiddles == nullptr) {
        free(st->bit_reverse);
        free(st);
        return nullptr;
    }

    const double sign = st->inverse_fft ? 1.0 : -1.0;
    for (int k = 0; k < half; ++k) {
        const double angle = sign * 2.0 * M_PI * (double) k / (double) nfft;
        st->twiddles[k].r = (float) cos(angle);
        st->twiddles[k].i = (float) sin(angle);
    }

    return st;
}

void kiss_fft_free(kiss_fft_cfg cfg) {
    if (cfg == nullptr) {
        return;
    }
    kiss_fft_state *st = (kiss_fft_state *) cfg;
    if (st->bit_reverse != nullptr) {
        free(st->bit_reverse);
        st->bit_reverse = nullptr;
    }
    if (st->twiddles != nullptr) {
        free(st->twiddles);
        st->twiddles = nullptr;
    }
    free(st);
}

void kiss_fft(kiss_fft_cfg cfg, const kiss_fft_cpx *in, kiss_fft_cpx *out) {
    // 边界检查
    if (cfg == nullptr || in == nullptr || out == nullptr) {
        return;
    }

    const kiss_fft_state *st = (const kiss_fft_state *) cfg;
    const int n = st->nfft;
    const int *rev = st->bit_reverse;

    // 步骤 1：按位反转顺序写入 out
    // 允许 in == out 的原地变换：先拷贝一份到 out 中
    if (in != out) {
        for (int i = 0; i < n; ++i) {
            out[i] = in[rev[i]];
        }
    } else {
        // 原地变换：使用 in 的位反转重排，借助临时拷贝
        kiss_fft_cpx *tmp = (kiss_fft_cpx *) malloc(sizeof(kiss_fft_cpx) * (size_t) n);
        if (tmp == nullptr) {
            return; // 内存不足，安全返回
        }
        for (int i = 0; i < n; ++i) {
            tmp[i] = in[rev[i]];
        }
        memcpy(out, tmp, sizeof(kiss_fft_cpx) * (size_t) n);
        free(tmp);
    }

    // 步骤 2：蝶形运算，逐层合并
    // 每层 step 个点：相邻 2 个蝶形大小为 m
    for (int m = 2; m <= n; m <<= 1) {
        const int half_m = m >> 1;
        // 对应这一层的旋转因子索引步长
        // W_N^k 中 N = n，k = k_step * j
        const int k_step = n / m;

        // 处理每一组蝶形
        for (int group_start = 0; group_start < n; group_start += m) {
            for (int j = 0; j < half_m; ++j) {
                const kiss_fft_cpx w = st->twiddles[j * k_step];
                kiss_fft_cpx a = out[group_start + j];
                kiss_fft_cpx b = out[group_start + j + half_m];

                // 蝶形：t = w * b；out[j] = a + t；out[j+half_m] = a - t
                kiss_fft_cpx t;
                t.r = w.r * b.r - w.i * b.i;
                t.i = w.r * b.i + w.i * b.r;

                out[group_start + j].r = a.r + t.r;
                out[group_start + j].i = a.i + t.i;
                out[group_start + j + half_m].r = a.r - t.r;
                out[group_start + j + half_m].i = a.i - t.i;
            }
        }
    }

    // 逆变换需要除以 N
    if (st->inverse_fft) {
        const float inv = 1.0f / (float) n;
        for (int i = 0; i < n; ++i) {
            out[i].r *= inv;
            out[i].i *= inv;
        }
    }
}
