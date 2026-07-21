package com.pianokids.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SheetMusicRecognizer] 单元测试。
 *
 * 使用 Robolectric 在 JVM 上模拟 Android Bitmap 像素操作，
 * 构造合成五线谱图像验证识别流程：
 * 1. 空白图：应返回空结果（无五线谱、无音符）
 * 2. 仅 5 条横线：应检测到 5 条五线谱
 * 3. 5 条横线 + 音符圆点：应检测到至少 1 个音符
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
        val bitmap = createWhiteBitmap(600, 200)
        // 5 条等间距水平线，gap=10 像素
        drawHorizontalLines(bitmap, listOf(50, 60, 70, 80, 90))
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
        val bitmap = createWhiteBitmap(600, 200)
        // 5 条水平线
        drawHorizontalLines(bitmap, listOf(50, 60, 70, 80, 90))
        // 在 x=300, y=70（中间线上）画一个音符圆点（黑色实心圆）
        drawNote(bitmap, 300, 70, radius = 5)
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
        val bitmap = createWhiteBitmap(300, 100)
        drawHorizontalLines(bitmap, listOf(20, 28, 36, 44, 52))
        val result = recognizer.recognize(bitmap, title = "small")
        assertEquals(5, result.staffLineYs.size)
    }

    @Test
    fun `recognize handles larger bitmap with downsampling`() {
        // 大于 600 宽的图像应触发降采样（不应崩溃）
        val bitmap = createWhiteBitmap(1200, 400)
        drawHorizontalLines(bitmap, listOf(100, 120, 140, 160, 180))
        val result = recognizer.recognize(bitmap, title = "large")
        // 降采样后线间距约为 10，仍应能识别 5 条线
        assertEquals(5, result.staffLineYs.size)
    }

    // ============== 辅助 ==============

    private fun createWhiteBitmap(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        return bitmap
    }

    private fun drawHorizontalLines(bitmap: Bitmap, ys: List<Int>) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        for (y in ys) {
            canvas.drawLine(
                0f,
                y.toFloat(),
                bitmap.width.toFloat(),
                y.toFloat(),
                paint,
            )
        }
    }

    private fun drawNote(bitmap: Bitmap, cx: Int, cy: Int, radius: Int) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), paint)
    }
}
