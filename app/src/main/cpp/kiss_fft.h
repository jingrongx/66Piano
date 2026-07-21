//
// kiss_fft.h —— 轻量级 FFT（参考 KissFFT 接口风格，但实现大幅简化）
//
// 仅支持 nfft 为 2 的幂次，使用 radix-2 Cooley-Tukey 算法。
// 实现完全独立，不依赖任何第三方库。
//

#ifndef PIANOKIDS_KISS_FFT_H
#define PIANOKIDS_KISS_FFT_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// 复数类型：float 精度，r 为实部，i 为虚部
typedef struct {
    float r;
    float i;
} kiss_fft_cpx;

// 不透明的 FFT 配置句柄（指针），由 kiss_fft_alloc 创建，kiss_fft_free 释放
typedef struct kiss_fft_state *kiss_fft_cfg;

/**
 * 创建一个 FFT 配置（预先计算好旋转因子与位反转表）。
 *
 * @param nfft        FFT 长度，必须为 2 的幂（如 256/512/1024/2048...）。
 *                    当 nfft <= 0 或不是 2 的幂时返回 NULL。
 * @param inverse_fft 0 表示正向 FFT，非 0 表示逆变换 IFFT。
 * @param mem         预分配缓冲区（保留参数，本实现内部忽略，传 nullptr 即可）。
 * @param lenmem      预分配缓冲区长度（保留参数，传 nullptr 即可）。
 * @return            FFT 配置句柄；失败返回 NULL。调用方需用 kiss_fft_free 释放。
 */
kiss_fft_cfg kiss_fft_alloc(int nfft, int inverse_fft, void *mem, size_t *lenmem);

/**
 * 执行一次 FFT/IFFT 变换。
 *
 * @param cfg  由 kiss_fft_alloc 返回的配置句柄，不能为 NULL。
 * @param in   输入复数数组，长度为 nfft，不能为 NULL。
 * @param out  输出复数数组，长度为 nfft，不能为 NULL。
 *             out 与 in 可以指向同一块内存（原地变换）。
 */
void kiss_fft(kiss_fft_cfg cfg, const kiss_fft_cpx *in, kiss_fft_cpx *out);

/**
 * 释放 FFT 配置句柄。传入 NULL 安全（无操作）。
 */
void kiss_fft_free(kiss_fft_cfg cfg);

/**
 * 工具函数：判断 nfft 是否为 2 的幂。
 */
int kiss_fft_is_power_of_two(int nfft);

#ifdef __cplusplus
}
#endif

#endif // PIANOKIDS_KISS_FFT_H
