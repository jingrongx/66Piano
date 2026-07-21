package com.pianokids.music

import android.content.Context
import android.net.Uri
import com.pianokids.data.repo.PiecesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * MIDI 文件导入用例。
 *
 * 从 SAF Uri 读取 MIDI 文件，解析为 [NoteSequence]，通过 [PiecesRepository] 落库。
 */
class MidiImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val midiParser: MidiParser,
    private val piecesRepository: PiecesRepository,
) {

    /**
     * 导入 MIDI 文件。
     *
     * @param uri SAF 提供的文件 Uri
     * @param defaultTitle 默认标题（当文件名无法解析时使用）
     * @return 保存后的乐谱 id
     */
    suspend fun import(uri: Uri, defaultTitle: String = "导入的乐谱"): Long = withContext(Dispatchers.IO) {
        val title = queryTitle(uri) ?: defaultTitle
        val sequence = context.contentResolver.openInputStream(uri)?.use { input ->
            val midiFile = midiParser.parse(input)
            // toNoteSequence 内部已设置 source = MIDI
            midiFile.toNoteSequence(title = title)
        } ?: throw IllegalStateException("无法打开文件")
        piecesRepository.save(
            id = null,
            title = title,
            sequence = sequence,
            coverColor = 0xFF42A5F5.toInt(),
        )
    }

    private fun queryTitle(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) {
                it.getString(idx)?.substringBeforeLast('.')
            } else null
        }
    }
}
