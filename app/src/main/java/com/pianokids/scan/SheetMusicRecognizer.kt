package com.pianokids.scan

import android.graphics.Bitmap
import com.pianokids.music.Note
import com.pianokids.music.NoteSequence

/**
 * 简化版光学乐谱识别（OMR）。
 *
 * 设计取舍：不依赖 OpenCV / Tesseract / Audiveris，
 * 仅用 Android 自带的 [Bitmap] 像素操作做单声部五线谱识别。
 *
 * 算法步骤：
 * 1. 灰度化 + Otsu 自适应二值化
 * 2. 行方向黑像素直方图，聚类得到五线谱 5 条线的 Y 坐标
 * 3. 估计线间距 gap，并按"线段移除"擦除五线谱横线
 * 4. 列方向黑像素直方图，聚类得到每个音符的水平位置
 * 5. 对每个音符位置扫描局部窗口，取质心 Y，反推 MIDI 音符号
 *
 * 限制：
 * - 仅支持高音谱号、C 大调、单声部
 * - 不识别附点、连音线、休止符
 * - 节拍默认 4/4，速度默认 120 BPM
 *
 * 用途：作为拍照识谱/拍照导入的"够用"识别器，识别结果交给用户在编辑器中微调。
 */
class SheetMusicRecognizer {

    /**
     * 识别结果。
     *
     * @property sequence 转换后的 [NoteSequence]
     * @property staffLineYs 检测到的五线谱 5 条线的 Y 坐标（从下到上，像素）
     * @property noteCount 识别到的音符数
     * @property debug 可选调试信息（识别耗时、阈值等）
     */
    data class Result(
        val sequence: NoteSequence,
        val staffLineYs: List<Int>,
        val noteCount: Int,
        val debug: String,
    )

    /**
     * 识别一张图像。
     *
     * @param bitmap 输入图像（任意尺寸，内部会降采样到合理大小）
     * @param title 输出曲名
     */
    fun recognize(bitmap: Bitmap, title: String = "拍照识别"): Result {
        val start = System.currentTimeMillis()

        // 1. 降采样到合理宽度（保留宽高比）
        val target = 600
        val scaled = if (bitmap.width > target) {
            val ratio = target.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(bitmap, target, (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val w = scaled.width
        val h = scaled.height

        // 2. 提取像素
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        return recognizeFromPixels(pixels, w, h, title, start)
    }

    /**
     * 从 ARGB 像素数组直接识别。核心算法，不依赖 Bitmap。
     *
     * 可测试入口：测试时可直接构造 [pixels] 数组调用此方法，
     * 无需依赖 Robolectric 的 Bitmap 像素模拟。
     */
    internal fun recognizeFromPixels(
        pixels: IntArray,
        w: Int,
        h: Int,
        title: String = "拍照识别",
        startMs: Long = System.currentTimeMillis(),
    ): Result {
        val start = startMs

        // 灰度 + Otsu 二值化
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        val threshold = otsuThreshold(gray)
        val isBlack = BooleanArray(w * h) { gray[it] < threshold }

        // 找五线谱
        val staffYs = findStaffLines(isBlack, w, h)
        if (staffYs.size < 5) {
            // 没找到完整五线谱，返回空结果
            return Result(
                sequence = NoteSequence(
                    title = title,
                    notes = emptyList(),
                    durationMs = 0L,
                    source = NoteSequence.Source.SCAN,
                ),
                staffLineYs = staffYs,
                noteCount = 0,
                debug = "未检测到完整五线谱（仅 ${staffYs.size} 条线）",
            )
        }

        // 五条线（从下到上）
        val line1 = staffYs[0]  // 最下 = E4
        val line5 = staffYs[4]  // 最上 = F5
        val gap = (line5 - line1) / 4f  // 线间距

        // 移除五线谱线（避免干扰音符检测）
        eraseStaffLines(isBlack, w, h, staffYs, gap)

        // 列方向扫描找音符
        val noteXs = findNoteColumns(isBlack, w, h, line1, line5, gap)

        // 每个音符位置反推 MIDI
        val tempo = 120
        val stepMs = (60_000.0 / tempo).toLong()  // 一个四分音符 500ms，按等间距
        val notes = mutableListOf<Note>()
        for ((idx, x) in noteXs.withIndex()) {
            val y = findNoteCenterY(isBlack, w, h, x, line1, line5, gap)
            if (y < 0) continue
            val midi = yToMidi(y, line1, line5, gap)
            if (midi < 0) continue
            notes.add(
                Note(
                    midi = midi,
                    startMs = idx * stepMs,
                    durationMs = stepMs,
                    velocity = 90,
                ),
            )
        }

        val durationMs = (notes.size * stepMs).coerceAtLeast(0L)
        val elapsed = System.currentTimeMillis() - start

        return Result(
            sequence = NoteSequence(
                title = title,
                tempo = tempo,
                notes = notes,
                durationMs = durationMs,
                source = NoteSequence.Source.SCAN,
            ),
            staffLineYs = staffYs,
            noteCount = notes.size,
            debug = "识别完成：${notes.size} 个音符，耗时 ${elapsed}ms，Otsu 阈值 $threshold，gap=${gap.toInt()}px",
        )
    }

    // ============== 内部算法 ==============

    /**
     * Otsu 自适应阈值。
     * 找到使类间方差最大的灰度阈值。
     */
    private fun otsuThreshold(gray: IntArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v]++
        val total = gray.size.toFloat()
        var sumAll = 0.0
        for (i in 0..255) sumAll += i * hist[i]
        var sumB = 0.0
        var wB = 0
        var maxVar = 0.0
        var threshold = 128
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0f) break
            sumB += i * hist[i].toFloat()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toFloat() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                threshold = i
            }
        }
        return threshold
    }

    /**
     * 检测五线谱的 5 条线。
     *
     * 策略：统计每一行的黑像素数，找出黑像素占比高的行；
     * 然后聚类得到一组（应该正好 5 条）等间距的线。
     */
    private fun findStaffLines(isBlack: BooleanArray, w: Int, h: Int): List<Int> {
        val rowBlack = IntArray(h)
        for (y in 0 until h) {
            var cnt = 0
            for (x in 0 until w) {
                if (isBlack[y * w + x]) cnt++
            }
            rowBlack[y] = cnt
        }
        // 一行被视为候选线：黑像素占比 > 50%
        val rowThreshold = (w * 0.5f).toInt()

        // 聚类相邻的候选行
        val lines = mutableListOf<Int>()
        var runStart = -1
        for (y in 0 until h) {
            if (rowBlack[y] >= rowThreshold) {
                if (runStart < 0) runStart = y
            } else {
                if (runStart >= 0) {
                    val center = (runStart + y - 1) / 2
                    lines.add(center)
                    runStart = -1
                }
            }
        }
        if (runStart >= 0) {
            lines.add((runStart + h - 1) / 2)
        }

        // 在所有候选线中，挑出 5 条等间距的
        return pickFiveEquallySpaced(lines)
    }

    /**
     * 从一组候选线中挑出 5 条等间距的（从下到上）。
     */
    private fun pickFiveEquallySpaced(candidates: List<Int>): List<Int> {
        if (candidates.size < 5) return candidates.sorted()
        val sorted = candidates.sorted()
        // 暴力枚举：任选两条作为线1和线5，看中间是否能找到3条匹配的线
        var best: List<Int>? = null
        var bestError = Long.MAX_VALUE
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val y1 = sorted[i]
                val y5 = sorted[j]
                if (y5 <= y1) continue
                val gap = (y5 - y1) / 4f
                if (gap < 5 || gap > 80) continue  // 线间距合理范围
                // 期望的 5 条线
                val expected = List(5) { k -> y1 + (gap * k).toInt() }
                // 对每条期望线，找最近的候选
                val matched = expected.map { e ->
                    sorted.minByOrNull { kotlin.math.abs(it - e) } ?: e
                }
                val error = matched.mapIndexed { idx, v -> kotlin.math.abs(v - expected[idx]).toLong() }.sum()
                if (error < bestError) {
                    bestError = error
                    best = matched
                }
            }
        }
        return best ?: sorted.takeLast(5)
    }

    /**
     * 擦除五线谱线（在每条线的 Y±1 像素范围内，移除黑色像素）。
     */
    private fun eraseStaffLines(
        isBlack: BooleanArray,
        w: Int,
        h: Int,
        staffYs: List<Int>,
        gap: Float,
    ) {
        val radius = (gap * 0.25f).toInt().coerceIn(1, 4)
        for (y in staffYs) {
            for (dy in -radius..radius) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (x in 0 until w) {
                    isBlack[yy * w + x] = false
                }
            }
        }
    }

    /**
     * 列方向扫描：统计每列在五线谱范围内的黑像素数，
     * 聚类得到音符的水平位置。
     */
    private fun findNoteColumns(
        isBlack: BooleanArray,
        w: Int,
        h: Int,
        line1: Int,
        line5: Int,
        gap: Float,
    ): List<Int> {
        val top = (line5 - gap).toInt().coerceAtLeast(0)
        val bottom = (line1 + gap).toInt().coerceAtMost(h - 1)
        val colBlack = IntArray(w) { x ->
            var cnt = 0
            for (y in top..bottom) {
                if (isBlack[y * w + x]) cnt++
            }
            cnt
        }
        // 阈值：五线谱范围内黑像素数 > gap 的 0.8 倍，视为有音符
        val colThreshold = (gap * 0.8f).toInt().coerceAtLeast(3)

        // 聚类相邻的候选列
        val clusters = mutableListOf<Int>()
        var runStart = -1
        var runSum = 0
        var runCount = 0
        for (x in 0 until w) {
            if (colBlack[x] >= colThreshold) {
                if (runStart < 0) runStart = x
                runSum += x
                runCount++
            } else {
                if (runStart >= 0 && runCount >= 2) {
                    clusters.add(runSum / runCount)
                }
                runStart = -1
                runSum = 0
                runCount = 0
            }
        }
        if (runStart >= 0 && runCount >= 2) {
            clusters.add(runSum / runCount)
        }

        // 合并过近的簇（小于 gap 的一半）
        val merged = mutableListOf<Int>()
        for (c in clusters) {
            if (merged.isEmpty() || c - merged.last() >= gap * 0.5f) {
                merged.add(c)
            }
        }
        return merged
    }

    /**
     * 在音符位置 X 附近，找该列在五线谱范围内的黑色像素质心 Y。
     */
    private fun findNoteCenterY(
        isBlack: BooleanArray,
        w: Int,
        h: Int,
        x: Int,
        line1: Int,
        line5: Int,
        gap: Float,
    ): Int {
        val top = (line5 - gap).toInt().coerceAtLeast(0)
        val bottom = (line1 + gap).toInt().coerceAtMost(h - 1)
        val window = (gap * 0.8f).toInt().coerceAtLeast(2)
        var sumY = 0
        var count = 0
        for (dx in -window..window) {
            val xx = x + dx
            if (xx < 0 || xx >= w) continue
            for (y in top..bottom) {
                if (isBlack[y * w + xx]) {
                    sumY += y
                    count++
                }
            }
        }
        return if (count > 0) sumY / count else -1
    }

    /**
     * 由 Y 坐标反推 MIDI 音符号（高音谱号）。
     *
     * 五线谱从下到上 5 条线对应 E4-G4-B4-D5-F5（midi 64, 67, 71, 74, 77）。
     * 每条线之间是 1 个 diatonic step（2 个半音的位置，但 BC 和 EF 之间只差 1 个半音）。
     *
     * 用 diatonic step 表示：以 E4 为 0，则
     * E4=0, F4=1, G4=2, A4=3, B4=4, C5=5, D5=6, E5=7, F5=8
     * 每 1 diatonic step 对应像素 = gap / 2
     */
    private fun yToMidi(y: Int, line1: Int, line5: Int, gap: Float): Int {
        if (gap <= 0f) return -1
        // y 离 line1（最下线，E4）的像素差
        val dy = line1 - y  // y 越小（越靠上）= 音越高
        val stepF = dy / (gap / 2f)  // 1 diatonic step = gap/2 像素
        val step = stepF.roundToInt()
        return diatonicStepToMidi(step)
    }

    /**
     * Diatonic step（E4=0）→ MIDI 音符号。
     */
    private fun diatonicStepToMidi(step: Int): Int {
        // E F G A B C D 的半音偏移
        val scaleOffsets = intArrayOf(0, 1, 3, 5, 7, 8, 10)
        val octaveOffset = if (step >= 0) step / 7 else -((-step - 1) / 7 + 1)
        val within = ((step % 7) + 7) % 7
        // base = E4 = midi 64
        return 64 + octaveOffset * 12 + scaleOffsets[within]
    }

    private fun Float.roundToInt(): Int = Math.round(this)
}
