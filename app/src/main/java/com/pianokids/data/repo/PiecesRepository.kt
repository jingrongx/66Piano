package com.pianokids.data.repo

import com.pianokids.data.db.PieceDao
import com.pianokids.data.db.PieceEntity
import com.pianokids.music.Note
import com.pianokids.music.NoteSequence
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自定义/导入乐谱仓库：在 [NoteSequence] 与 [PieceEntity] 之间转换，
 * 并对外暴露 CRUD 接口供 UI 使用。
 *
 * 序列化采用 [org.json.JSONObject]（Android 内置，无需额外依赖）。
 * notesJson 格式：
 * ```
 * { "notes": [ { "m": 60, "s": 0, "d": 500, "v": 90 }, ... ] }
 * ```
 */
@Singleton
class PiecesRepository @Inject constructor(
    private val pieceDao: PieceDao,
) {

    /**
     * 观察所有乐谱（按更新时间倒序）。
     */
    fun observeAll(): Flow<List<PieceEntity>> = pieceDao.observeAll()

    /**
     * 获取单个乐谱。
     */
    suspend fun get(id: Long): PieceEntity? = pieceDao.get(id)

    /**
     * 保存一条乐谱（新增或更新）。
     *
     * @return 新增时返回生成的 id，更新时返回原 id
     */
    suspend fun save(
        id: Long?,
        title: String,
        sequence: NoteSequence,
        coverColor: Int,
    ): Long {
        val now = System.currentTimeMillis()
        val entity = PieceEntity(
            id = id ?: 0L,
            title = title.ifBlank { defaultTitle(sequence.source) },
            source = sequence.source.name,
            tempo = sequence.tempo,
            timeSigNumerator = sequence.timeSignatureNumerator,
            timeSigDenominator = sequence.timeSignatureDenominator,
            notesJson = serializeNotes(sequence.notes),
            durationMs = sequence.durationMs,
            coverColor = coverColor,
            createdAt = now,
            updatedAt = now,
        )
        return if (id != null) {
            pieceDao.update(entity)
            id
        } else {
            pieceDao.insert(entity)
        }
    }

    /**
     * 删除一条乐谱。
     */
    suspend fun delete(id: Long) = pieceDao.delete(id)

    /**
     * 总条目数。
     */
    suspend fun count(): Int = pieceDao.count()

    /**
     * 把数据库实体转为 [NoteSequence]。
     */
    fun toSequence(entity: PieceEntity): NoteSequence {
        val notes = deserializeNotes(entity.notesJson)
        return NoteSequence(
            title = entity.title,
            tempo = entity.tempo,
            timeSignatureNumerator = entity.timeSigNumerator,
            timeSignatureDenominator = entity.timeSigDenominator,
            notes = notes,
            durationMs = entity.durationMs,
            source = parseSource(entity.source),
        )
    }

    // ============== 内部工具 ==============

    private fun serializeNotes(notes: List<Note>): String {
        val arr = JSONArray()
        for (n in notes) {
            arr.put(JSONObject().apply {
                put("m", n.midi)
                put("s", n.startMs)
                put("d", n.durationMs)
                put("v", n.velocity)
            })
        }
        return JSONObject().put("notes", arr).toString()
    }

    private fun deserializeNotes(json: String): List<Note> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONObject(json).getJSONArray("notes")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Note(
                            midi = o.optInt("m", Note.REST_MIDI),
                            startMs = o.optLong("s", 0L),
                            durationMs = o.optLong("d", 250L),
                            velocity = o.optInt("v", 90),
                        ),
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseSource(raw: String): NoteSequence.Source = try {
        NoteSequence.Source.valueOf(raw)
    } catch (_: Throwable) {
        NoteSequence.Source.CUSTOM
    }

    private fun defaultTitle(source: NoteSequence.Source): String = when (source) {
        NoteSequence.Source.MIDI -> "导入的乐谱"
        NoteSequence.Source.SCAN -> "拍照识谱"
        NoteSequence.Source.CUSTOM -> "我的乐谱"
    }
}
