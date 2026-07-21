package com.pianokids.ui.scan

import com.pianokids.music.NoteSequence

/**
 * 拍照识谱跨页面数据传递 holder。
 *
 * 由于 [NoteSequence] 不是 Parcelable 且体积较大，
 * 用进程级单例做一次性传递：[ScanScreen] 写入 → [PieceEditorScreen] 读取后清空。
 */
object ScanResultHolder {

    @Volatile
    private var pending: NoteSequence? = null

    /**
     * 写入待消费的识别结果。
     */
    fun put(sequence: NoteSequence) {
        pending = sequence
    }

    /**
     * 取出并清空待消费的结果。若无返回 null。
     */
    fun consume(): NoteSequence? {
        val v = pending
        pending = null
        return v
    }
}
