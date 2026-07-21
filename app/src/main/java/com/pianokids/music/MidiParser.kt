package com.pianokids.music

import java.io.InputStream

/**
 * MIDI 文件中的一个音符。
 *
 * @property midiNote MIDI 音符号（A4=69）
 * @property startTimeTicks 相对文件的起始 tick
 * @property durationTicks 持续 tick 数
 * @property track 所在轨道编号（从 0 开始）
 */
data class MidiNote(
    val midiNote: Int,
    val startTimeTicks: Long,
    val durationTicks: Long,
    val track: Int,
)

/**
 * 解析后的 MIDI 文件。
 *
 * @property notes 所有轨道的音符，按起始时间升序
 * @property ticksPerQuarter 每四分音符的 tick 数
 * @property tempo 当前主速度（BPM）
 * @property durationMs 整曲时长（毫秒）
 */
data class MidiFile(
    val notes: List<MidiNote>,
    val ticksPerQuarter: Int,
    val tempo: Int,
    val durationMs: Long,
)

/**
 * 轻量 MIDI 文件解析器。
 *
 * 支持：
 * - SMF format 0/1（header chunk + 多个 track chunk）
 * - 变长量（VLQ）delta-time
 * - note_on (0x90..0x9F) / note_off (0x80..0x8F) 事件
 * - tempo meta event (0xFF 0x51 0x03)
 * - running status（同一轨道内复用 status byte；meta / sysex 不参与 running status）
 *
 * 不支持 sysex 内容解析、歌词等非音符事件（会被正确跳过）。
 */
class MidiParser {

    /**
     * 解析一个 MIDI 文件字节流。
     * @throws IllegalArgumentException 文件头非法或读取失败
     */
    fun parse(input: InputStream): MidiFile {
        val data = input.readBytes()
        if (data.size < 14) {
            throw IllegalArgumentException("MIDI 文件过短: ${data.size} bytes")
        }
        // 1. header chunk
        val cur = Cursor(data, 0)
        val headerId = cur.readAscii(4)
        if (headerId != "MThd") {
            throw IllegalArgumentException("非法 MIDI header: $headerId")
        }
        val headerLen = cur.readInt32()
        if (headerLen < 6) {
            throw IllegalArgumentException("MIDI header len 太短: $headerLen")
        }
        // format = readInt16(); ntracks = readInt16(); division = readInt16()
        cur.readInt16() // format 暂不使用
        val ntracks = cur.readInt16()
        val division = cur.readInt16()
        // 跳过 header 末尾可能附加字节
        cur.skipTo(0 + 8 + headerLen)

        val ticksPerQuarter = if (division and 0x8000 == 0) {
            division and 0x7FFF
        } else {
            // SMPTE 格式极少见，按 fallback 480 处理
            480
        }

        // 2. 依次解析每个 track chunk
        val notes = mutableListOf<MidiNote>()
        var mainTempo = 500_000 // 默认 120 BPM -> 500000 us/quarter
        var lastTick = 0L

        var trackIndex = 0
        var tracksParsed = 0
        while (cur.remaining() >= 8 && tracksParsed < ntracks) {
            val chunkId = cur.readAscii(4)
            val chunkLen = cur.readInt32()
            val chunkEnd = cur.position + chunkLen
            if (chunkId == "MTrk") {
                val r = parseTrack(cur, chunkEnd, trackIndex)
                notes.addAll(r.notes)
                if (r.tempo > 0) mainTempo = r.tempo
                if (r.lastTick > lastTick) lastTick = r.lastTick
                tracksParsed++
            }
            trackIndex++
            cur.skipTo(chunkEnd)
        }

        // 3. 按 startTime 排序
        notes.sortBy { it.startTimeTicks }

        // 4. tick -> ms：ms = tick * tempo / ticksPerQuarter / 1000
        val durationMs = if (ticksPerQuarter > 0) {
            lastTick * mainTempo / ticksPerQuarter / 1000
        } else {
            0L
        }
        val bpm = if (mainTempo > 0) (60_000_000 / mainTempo).toInt() else 120

        return MidiFile(
            notes = notes,
            ticksPerQuarter = ticksPerQuarter,
            tempo = bpm,
            durationMs = durationMs,
        )
    }

    private data class TrackParseResult(
        val notes: List<MidiNote>,
        val tempo: Int,
        val lastTick: Long,
    )

    /**
     * 解析一个 track chunk 的字节内容。
     * @param chunkEnd 该 chunk 在 [data] 中的结束位置（不含）
     * @param trackIndex 轨道编号
     */
    private fun parseTrack(cur: Cursor, chunkEnd: Int, trackIndex: Int): TrackParseResult {
        val notes = mutableListOf<MidiNote>()
        // 当前未结束的音符：noteNumber -> startTick
        val pending = HashMap<Int, Long>()
        var tick = 0L
        var runningStatus = 0
        var tempo = 500_000
        var lastTick = 0L

        while (cur.position < chunkEnd) {
            // 1. delta-time (VLQ)
            val delta = cur.readVarLong()
            tick += delta
            if (tick > lastTick) lastTick = tick

            // 2. status byte（可能是 running status）
            val peek = cur.peekByte()
            val status: Int
            if (peek and 0x80 != 0) {
                // 新 status byte
                status = cur.readByte()
                if (status == 0xF0 || status == 0xF7 || status == 0xFF) {
                    // meta / sysex 不参与 running status
                    runningStatus = 0
                } else {
                    runningStatus = status
                }
            } else {
                // running status：复用之前的 status
                status = runningStatus
            }

            when {
                status == 0xFF -> {
                    // meta event: metaType + VLQ(len) + data
                    val metaType = cur.readByte()
                    val metaLen = cur.readVarLong().toInt()
                    when (metaType) {
                        0x51 -> {
                            // Set Tempo: 3 字节 big-endian us/quarter
                            if (metaLen >= 3 && cur.remaining() >= 3) {
                                val b1 = cur.readByte()
                                val b2 = cur.readByte()
                                val b3 = cur.readByte()
                                tempo = (b1 shl 16) or (b2 shl 8) or b3
                                // 跳过剩余（若有）
                                cur.skip(metaLen - 3)
                            } else {
                                cur.skip(metaLen)
                            }
                        }
                        0x2F -> {
                            // End of Track：跳过长度字节后退出
                            cur.skip(metaLen)
                            break
                        }
                        else -> {
                            cur.skip(metaLen)
                        }
                    }
                }

                status == 0xF0 || status == 0xF7 -> {
                    // sysex event：VLQ 长度 + 数据
                    val sxLen = cur.readVarLong().toInt()
                    cur.skip(sxLen)
                }

                status in 0x80..0xEF -> {
                    val high = status and 0xF0
                    val dataLen = if (high == 0xC0 || high == 0xD0) 1 else 2
                    val d1 = cur.readByte()
                    val d2 = if (dataLen == 2) cur.readByte() else 0

                    when (high) {
                        0x80 -> {
                            // note off
                            val startTick = pending.remove(d1)
                            if (startTick != null) {
                                val dur = tick - startTick
                                if (dur >= 0) {
                                    notes.add(
                                        MidiNote(
                                            midiNote = d1,
                                            startTimeTicks = startTick,
                                            durationTicks = dur,
                                            track = trackIndex,
                                        ),
                                    )
                                }
                            }
                        }
                        0x90 -> {
                            if (d2 == 0) {
                                // note_on velocity=0 视为 note_off
                                val startTick = pending.remove(d1)
                                if (startTick != null) {
                                    val dur = tick - startTick
                                    if (dur >= 0) {
                                        notes.add(
                                            MidiNote(
                                                midiNote = d1,
                                                startTimeTicks = startTick,
                                                durationTicks = dur,
                                                track = trackIndex,
                                            ),
                                        )
                                    }
                                }
                            } else {
                                pending[d1] = tick
                            }
                        }
                        // 其他通道事件（CC/PC/PB/AT）暂不处理，但已正确跳过
                    }
                }

                else -> {
                    // 未知状态字节：避免死循环，跳过一字节
                    // running status 为 0 时进入此处意味着文件异常
                    cur.readByte()
                    runningStatus = 0
                }
            }
        }

        // 收尾：把尚未结束的音符按到末尾
        pending.forEach { (note, start) ->
            val dur = lastTick - start
            if (dur > 0) {
                notes.add(
                    MidiNote(
                        midiNote = note,
                        startTimeTicks = start,
                        durationTicks = dur,
                        track = trackIndex,
                    ),
                )
            }
        }

        return TrackParseResult(
            notes = notes,
            tempo = tempo,
            lastTick = lastTick,
        )
    }

    /**
     * 字节读取游标：封装偏移与边界检查。
     */
    private class Cursor(private val data: ByteArray, initialPos: Int) {
        var position: Int = initialPos
            private set

        fun remaining(): Int = data.size - position

        fun peekByte(): Int =
            if (position < data.size) data[position].toInt() and 0xFF else 0

        fun readByte(): Int {
            if (position >= data.size) return 0
            val b = data[position].toInt() and 0xFF
            position++
            return b
        }

        fun readInt16(): Int {
            val hi = readByte()
            val lo = readByte()
            return (hi shl 8) or lo
        }

        fun readInt32(): Int {
            val b0 = readByte()
            val b1 = readByte()
            val b2 = readByte()
            val b3 = readByte()
            return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }

        fun readAscii(len: Int): String {
            val sb = StringBuilder(len)
            repeat(len) { sb.append(readByte().toChar()) }
            return sb.toString()
        }

        /**
         * 读取 MIDI 变长量（VLQ）。
         */
        fun readVarLong(): Long {
            var value = 0L
            var i = 0
            var b: Int
            do {
                b = readByte()
                value = (value shl 7) or (b and 0x7F).toLong()
                i++
                if (i > 4) break // 上限保护
            } while (b and 0x80 != 0)
            return value
        }

        fun skip(n: Int) {
            position = (position + n).coerceAtMost(data.size)
        }

        fun skipTo(target: Int) {
            if (target > position) position = target
        }
    }
}

/**
 * 将 [MidiFile] 转换为统一 [NoteSequence] 数据模型。
 *
 * tick -> ms：ms = tick * tempo(us/quarter) / ticksPerQuarter / 1000
 */
fun MidiFile.toNoteSequence(title: String = ""): NoteSequence {
    val tempoUs = if (tempo in 1..600) 60_000_000 / tempo else 500_000
    val notes = this.notes.map { n ->
        val startMs = n.startTimeTicks * tempoUs / ticksPerQuarter / 1000
        val durMs = n.durationTicks * tempoUs / ticksPerQuarter / 1000
        Note(
            midi = n.midiNote,
            startMs = startMs,
            durationMs = durMs.coerceAtLeast(80L),
        )
    }
    return NoteSequence(
        title = title,
        tempo = tempo,
        notes = notes,
        durationMs = durationMs,
        source = NoteSequence.Source.MIDI,
    )
}
