package com.pianokids.scan

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SheetMusicRecognizer] 单元测试。
 *
 * 使用 Robolectric 在 JVM 上模拟 Android Bitmap 像素操作。
 *
 * 注意：Robolectric 默认 LEGACY 图形模式下 Canvas.drawLine/drawCircle 是像素级 NO-OP，
 * 不会真正写入像素到 Bitmap。因此本测试**直接使用 [Bitmap.setPixels]** 构造合成图像，
 * 完全绕开 Canvas，确保像素操作可被 [Bitmap.getPixels] 正确读取。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SheetMusicRecognizerTest {

    private val recognizer = SheetMusicRecognizer()

    @Test
    fun `empty white image returns empty result`() {
        val bitmap = createWhiteBitmap(600, 200)
        val result = recognizer.recognize(bitmap, title = "empty")
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
        val pixels = IntArray(w * h) { Color.WHITE }
        // 5 条等间距水平黑线，gap=10 像素
        listOf(50, 60, 70, 80, 90).forEach { y -> drawHorizontalLine(pixels, w, h, y) }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        val result = recognizer.recognize(bitmap, title = "staff")
        assertEquals(
            "应检测到 5 条五线谱，实际 ${result.staffLineYs.size}",
            5,
            result.staffLineYs.size,
        )
        // 验证间距均匀（从下到上或从上到下都行）
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
        val pixels = IntArray(w * h) { Color.WHITE }
        // 5 条水平黑线
        listOf(50, 60, 70, 80, 90).forEach { y -> drawHorizontalLine(pixels, w, h, y) }
        // 在 x=300, y=70（中间线上）画一个黑色实心圆（音符）
        drawNote(pixels, w, h, cx = 300, cy = 70, radius = 5)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        val result = recognizer.recognize(bitmap, title = "one_note")
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
        // 小于 600 宽的图像不应触发降采样
        val w = 300
        val h = 100
        val pixels = IntArray(w * h) { Color.WHITE }
        listOf(20, 28, 36, 44, 52).forEach { y -> drawHorizontalLine(pixels, w, h, y) }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        val result = recognizer.recognize(bitmap, title = "small")
        assertEquals(5, result.staffLineYs.size)
    }

    @Test
    fun `recognize handles larger bitmap with downsampling`() {
        // 大于 600 宽的图像应触发降采样（不应崩溃）
        val w = 1200
        val h = 400
        val pixels = IntArray(w * h) { Color.WHITE }
        // 间距 20，降采样到 600 宽后约间距 10
        listOf(100, 120, 140, 160, 180).forEach { y -> drawHorizontalLine(pixels, w, h, y) }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        val result = recognizer.recognize(bitmap, title = "large")
        // 降采样后线间距约为 10，仍应能识别 5 条线
        assertEquals(5, result.staffLineYs.size)
    }

    // ============== 像素辅助函数（避开 Canvas，直接操作像素） ==============

    /**
     * 在 [pixels] 数组中画一条横贯整张图的水平黑线。
     */
    private fun drawHorizontalLine(pixels: IntArray, w: Int, h: Int, y: Int) {
        if (y < 0 || y >= h) return
        for (x in 0 until w) {
            pixels[y * w + x] = Color.BLACK
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
                    pixels[y * w + x] = Color.BLACK
                }
            }
        }
    }

    private fun createWhiteBitmap(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        return bitmap
    }
}
