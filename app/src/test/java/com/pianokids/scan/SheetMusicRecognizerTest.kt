package com.pianokids.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SheetMusicRecognizer] 单元测试。
 *
 * 直接构造 ARGB [IntArray] 像素数组，调用 [SheetMusicRecognizer.recognizeFromPixels]，
 * 完全绕开 Robolectric 的 Bitmap 像素模拟（其默认 LEGACY 模式下 setPixels/getPixels
 * 行为不可靠）和 android.graphics.Color stub（纯 JVM 单元测试中 Color 常量都是 0）。
 *
 * 使用真实 ARGB 整数值：
 * - WHITE = 0xFFFFFFFF = -1
 * - BLACK = 0xFF000000 = -16777216
 */
class SheetMusicRecognizerTest {

    /** ARGB 不透明白色 */
    private val white = 0xFFFFFFFF.toInt()
    /** ARGB 不透明黑色 */
    private val black = 0xFF000000.toInt()

    private val recognizer = SheetMusicRecognizer()

    @Test
    fun `empty white image returns empty result`() {
        val w = 600
        val h = 200
        val pixels = IntArray(w * h) { white }
        val result = recognizer.recognizeFromPixels(pixels, w, h, title = "empty")
        assertEquals("空图像不应识别到音符", 0, result.noteCount)
        assertEquals("空图像不应有音符", 0, result.sequence.notes.size)
        assertTrue(
            "空图像不应检测到完整五线谱，实际 ${result.staffLineYs.size}",
            result.staffLineYs.size < 5,
        )
        assertEquals("空图像曲名应透传", "empty", result.sequence.title)
    }

    @Test
    fun `detects 5 staff lines when present`() {
        val w = 600
        val h = 200
        val pixels = IntArray(w * h) { white }
        // 5 条等间距水平黑线，gap=10 像素
        listOf(50, 60, 70, 80, 90).forEach { y -> drawHorizontalLine(pixels, w, h, y) }

        val result = recognizer.recognizeFromPixels(pixels, w, h, title = "staff")
        assertEquals(
            "应检测到 5 条五线谱，实际 ${result.staffLineYs.size}",
            5,
            result.staffLineYs.size,
        )
        // 验证间距均匀
        val ys = result.staffLineYs
        val gaps = ys.zipWithNext { a, b -> kotlin.math.abs(b - a) }
        gaps.forEach { g ->
            assertTrue("线间距应在 8~12 像素范围内，实际 $g", g in 8..12)
        }
    }

    @Test
    fun `detects at least one note when staff lines and a note are present`() {
        val w = 600
        val h = 200
        val pixels = IntArray(w * h) { white }
        // 5 条水平黑线
        listOf(50, 60, 70, 80, 90).forEach { y -> drawHorizontalLine(pixels, w, h, y) }
        // 在 x=300, y=70（中间线上）画一个黑色实心圆（音符）
        drawNote(pixels, w, h, cx = 300, cy = 70, radius = 5)

        val result = recognizer.recognizeFromPixels(pixels, w, h, title = "one_note")
        assertTrue(
            "应检测到五线谱（5 条），实际 ${result.staffLineYs.size}",
            result.staffLineYs.size == 5,
        )
        assertTrue(
            "应至少识别到 1 个音符，实际 ${result.noteCount}",
            result.noteCount >= 1,
        )
        assertTrue(
            "识别的 NoteSequence 应包含音符",
            result.sequence.notes.isNotEmpty(),
        )
        // 验证音符的 MIDI 在合理范围（C3 ~ C6 = 48 ~ 84）
        result.sequence.notes.forEach { note ->
            assertTrue(
                "音符 MIDI 应在合理范围，实际 ${note.midi}",
                note.midi in 48..84,
            )
        }
    }

    @Test
    fun `recognize handles small bitmap without scaling`() {
        // 小尺寸图像（由调用方负责降采样，recognizeFromPixels 本身不降采样）
        val w = 300
        val h = 100
        val pixels = IntArray(w * h) { white }
        listOf(20, 28, 36, 44, 52).forEach { y -> drawHorizontalLine(pixels, w, h, y) }

        val result = recognizer.recognizeFromPixels(pixels, w, h, title = "small")
        assertEquals(5, result.staffLineYs.size)
    }

    @Test
    fun `recognize handles larger bitmap with downsampling`() {
        // 大尺寸图像：间距 20 像素，recognizeFromPixels 本身不降采样，
        // 但 findStaffLines 对间距有合理范围的容差（5~80），所以 20 像素间距也能识别
        val w = 1200
        val h = 400
        val pixels = IntArray(w * h) { white }
        // 间距 20 像素
        listOf(100, 120, 140, 160, 180).forEach { y -> drawHorizontalLine(pixels, w, h, y) }

        val result = recognizer.recognizeFromPixels(pixels, w, h, title = "large")
        assertEquals(5, result.staffLineYs.size)
    }

    // ============== 像素辅助函数 ==============

    /**
     * 在 [pixels] 数组中画一条横贯整张图的水平黑线。
     */
    private fun drawHorizontalLine(pixels: IntArray, w: Int, h: Int, y: Int) {
        if (y < 0 || y >= h) return
        for (x in 0 until w) {
            pixels[y * w + x] = black
        }
    }

    /**
     * 在 [pixels] 数组中画一个黑色实心圆。
     */
    private fun drawNote(pixels: IntArray, w: Int, h: Int, cx: Int, cy: Int, radius: Int) {
        val r2 = radius * radius
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy > r2) continue
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until w && y in 0 until h) {
                    pixels[y * w + x] = black
                }
            }
        }
    }
}
