package com.kiite.player.domain

import com.kiite.player.core.BookmarkMath
import com.kiite.player.core.CalibrationMath
import com.kiite.player.core.ParsedScript
import com.kiite.player.core.ScriptAttachment
import com.kiite.player.core.ScriptCalibration
import com.kiite.player.core.ScriptFormat
import com.kiite.player.core.TimedLine
import com.kiite.player.core.TrackEntry
import com.kiite.player.data.LibraryRepository
import com.kiite.player.data.ScriptCalibrationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 台本：加载、匹配状态、时间轴校准。
 *
 * 从 `MainViewModel` 抽出来的原因很直接——这块有 270 行，而且状态自成一体：
 * 「解析出的原文」+「校准增量」→「给界面用的时间轴」。
 * 抽出来之后 `MainViewModel` 只留一句 `val script: StateFlow<ScriptUi> = scripts.script`。
 *
 * 依赖全部用函数传入而不是把 ViewModel 传进来，避免反向耦合：
 *  - [currentTrack] 取当前曲目（台本为空时要回落到音频内嵌歌词）；
 *  - [livePosition] 取播放器实时位置（校准点位不能用界面 400ms 轮询的旧值）；
 *  - [notify] 提示文案出口。
 *
 * 约定：**校准只存 App 自己的 JSON，绝不回写用户的 .lrc/.srt。**
 */
class ScriptController(
    private val repo: LibraryRepository,
    private val calibrationStore: ScriptCalibrationStore,
    private val scope: CoroutineScope,
    private val currentTrack: () -> TrackEntry?,
    private val livePosition: () -> Long?,
    private val notify: (String) -> Unit,
) {
    private val _script = MutableStateFlow(ScriptUi())
    val script: StateFlow<ScriptUi> = _script.asStateFlow()

    /** 解析出来的原始时间轴；校准永远以它为基准，绝不就地改。 */
    private var rawScript: ParsedScript? = null
    private var scriptKey: String? = null

    private val calibration = MutableStateFlow<Map<String, ScriptCalibration>>(emptyMap())

    // ---------- 给「按行书签」用的读取口 ----------
    // 书签只关心「第几句、什么时候开始、原文是什么」，不该自己去翻 ParsedScript 和校准逻辑。

    /** 第 [index] 句生效后的开始时间（已应用校准）；没有时间轴时为 null。 */
    fun lineStartMs(index: Int): Long? = _script.value.parsed?.timedLines?.getOrNull(index)?.startMs

    /** 第 [index] 句的原文。 */
    fun lineText(index: Int): String? = _script.value.parsed?.timedLines?.getOrNull(index)?.text

    /**
     * 当前台本的指纹。台本换了（行数/内容变了）时行号就不可信，
     * 书签靠它判断「这一行还是当初那一句吗」。
     */
    fun fingerprint(): String =
        BookmarkMath.fingerprint(_script.value.parsed?.timedLines?.map { it.text }.orEmpty())

    fun loadCalibrations() {
        runCatching { calibration.value = calibrationStore.load() }
    }

    /** 换曲目 / 停止播放时清空，避免上一集的台词残留。 */
    fun clear() = setScript(ScriptUi())

    // ---------- 加载 ----------

    fun loadFor(track: TrackEntry) {
        val att = track.primaryScript
        if (att != null) {
            loadAttachment(att)
        } else {
            setScript(ScriptUi(loading = true))
            scope.launch {
                val embedded = repo.readEmbeddedLyrics(track.path)
                if (embedded != null) {
                    setScript(
                        ScriptUi(fromEmbedded = true, loading = false),
                        raw = ParsedScript(ScriptFormat.TXT, embedded),
                        key = track.path,
                    )
                } else {
                    setScript(ScriptUi(loading = false))
                }
            }
        }
    }

    fun loadAttachment(att: ScriptAttachment) {
        setScript(ScriptUi(attachment = att, loading = true))
        scope.launch {
            val parsed = repo.parseScript(att.scriptPath)
            if (parsed.isEmpty) {
                val track = currentTrack()
                val embedded = track?.let { repo.readEmbeddedLyrics(it.path) }
                if (embedded != null) {
                    setScript(
                        ScriptUi(attachment = att, fromEmbedded = true),
                        raw = ParsedScript(ScriptFormat.TXT, embedded),
                        key = track?.path,
                    )
                } else {
                    setScript(ScriptUi(attachment = att, parsed = parsed, error = parsed.warning ?: "台本为空"))
                }
            } else {
                setScript(ScriptUi(attachment = att), raw = parsed, key = att.scriptPath)
            }
        }
    }

    fun loadEmbeddedLyrics() {
        val track = currentTrack() ?: return
        setScript(ScriptUi(loading = true))
        scope.launch {
            val embedded = repo.readEmbeddedLyrics(track.path)
            if (embedded != null) {
                setScript(
                    ScriptUi(fromEmbedded = true),
                    raw = ParsedScript(ScriptFormat.TXT, embedded),
                    key = track.path,
                )
            } else {
                setScript(ScriptUi(error = "这首音频里没有嵌入台本"))
            }
        }
    }

    // ---------- 校准 ----------

    /**
     * 所有台本赋值的唯一入口。
     *
     * 把「原文」和「校准后的显示版本」分开：`rawScript` 留着算增量，`_script.parsed` 给界面和桌面歌词用，
     * 这样两个消费方都不需要知道校准的存在。
     */
    private fun setScript(ui: ScriptUi, raw: ParsedScript? = null, key: String? = null) {
        rawScript = raw
        scriptKey = key
        _script.value = decorateScript(ui, raw, key)
    }

    private fun decorateScript(ui: ScriptUi, raw: ParsedScript?, key: String?): ScriptUi {
        val confidence = CalibrationMath.confidenceOf(
            manual = ui.attachment?.manual == true,
            fromEmbedded = ui.fromEmbedded,
            score = ui.attachment?.score ?: 0,
        )
        val hasTimed = raw?.timedLines?.isNotEmpty() == true
        val base = ui.copy(
            confidence = confidence,
            confidenceHint = CalibrationMath.confidenceHint(confidence, hasTimed),
        )
        if (raw == null || !hasTimed) return base

        val lineCount = raw.timedLines.size
        val stored = key?.let { calibration.value[it] } ?: ScriptCalibration()
        val stale = CalibrationMath.isStale(stored, lineCount)
        val cal = CalibrationMath.pruneStale(stored, lineCount)
        return base.copy(
            parsed = CalibrationMath.applyToScript(raw, cal),
            calibration = cal,
            calibrationKey = key,
            staleLines = stale,
            rawLineCount = lineCount,
        )
    }

    /** 用当前原始台本重新算一遍校准结果（偏移/微调之后调用）。 */
    private fun republishScript() {
        val raw = rawScript ?: return
        _script.value = decorateScript(_script.value, raw, scriptKey)
    }

    private fun editCalibration(
        transform: (ScriptCalibration, List<TimedLine>) -> ScriptCalibration,
    ) {
        val raw = rawScript ?: return
        if (raw.timedLines.isEmpty()) return
        val key = scriptKey ?: return
        val current = CalibrationMath.pruneStale(
            calibration.value[key] ?: ScriptCalibration(),
            raw.timedLines.size,
        )
        val next = transform(current, raw.timedLines)
        calibration.value = if (next.isEmpty) {
            calibration.value - key
        } else {
            calibration.value + (key to next)
        }
        calibrationStore.save(calibration.value)
        republishScript()
    }

    fun nudgeOffset(deltaMs: Long) {
        editCalibration { cal, lines -> CalibrationMath.shiftOffset(cal, deltaMs, lines.size) }
    }

    fun setOffset(offsetMs: Long) {
        editCalibration { cal, lines -> CalibrationMath.setOffset(cal, offsetMs, lines.size) }
    }

    fun resetOffset() {
        editCalibration { cal, lines -> CalibrationMath.setOffset(cal, 0L, lines.size) }
    }

    fun resetLines() {
        editCalibration { cal, _ -> CalibrationMath.resetLines(cal) }
        notify("已清除单句微调")
    }

    fun resetAll() {
        editCalibration { cal, _ -> CalibrationMath.reset(cal) }
        notify("台本已恢复原始时间轴")
    }

    /** 把第 [index] 句的起点对齐到当前位置。 */
    fun alignLineStart(index: Int) {
        val pos = livePosition() ?: run { notify("先把这一集放起来，再校准"); return }
        editCalibration { cal, lines -> CalibrationMath.withLineStart(lines, cal, index, pos) }
        notify("第 " + (index + 1) + " 句起点已对齐到 " + formatClock(pos))
    }

    fun alignLineEnd(index: Int) {
        val pos = livePosition() ?: run { notify("先把这一集放起来，再校准"); return }
        editCalibration { cal, lines -> CalibrationMath.withLineEnd(lines, cal, index, pos) }
        notify("第 " + (index + 1) + " 句终点已对齐到 " + formatClock(pos))
    }

    fun nudgeLineStart(index: Int, deltaMs: Long) {
        editCalibration { cal, lines -> CalibrationMath.nudgeLineStart(lines, cal, index, deltaMs) }
    }

    fun nudgeLineEnd(index: Int, deltaMs: Long) {
        editCalibration { cal, lines -> CalibrationMath.nudgeLineEnd(lines, cal, index, deltaMs) }
    }

    fun resetLine(index: Int) {
        editCalibration { cal, _ -> CalibrationMath.resetLine(cal, index) }
    }

    /**
     * 第 [from]–[to] 句**整段一起**平移 [deltaMs]。
     *
     * 适用于「中间漏标了一个停顿，之后所有句子都晚同样的量」这种情形：
     * 逐句微调会被区间内部的邻居挡住，永远追不上实际的错位量。
     */
    fun shiftLines(from: Int, to: Int, deltaMs: Long) {
        var applied = 0L
        var range: IntRange? = null
        editCalibration { cal, lines ->
            val r = CalibrationMath.normalizeRange(lines.size, from, to) ?: return@editCalibration cal
            range = r
            val out = CalibrationMath.shiftRange(lines, cal, r, deltaMs)
            applied = out.appliedMs
            out.calibration
        }
        val r = range ?: return
        val who = "第 " + (r.first + 1) + "–" + (r.last + 1) + " 句"
        when {
            applied == 0L -> notify(
                who + "已经贴到" + (if (deltaMs > 0) "下一句" else "上一句") + "，没法再" +
                    (if (deltaMs > 0) "往后" else "往前") + "移了",
            )
            applied != deltaMs -> notify(
                "已把" + who + "整体平移 " + CalibrationMath.formatSignedSeconds(applied) +
                    "（到相邻句边界为止）",
            )
            else -> notify("已把" + who + "整体平移 " + CalibrationMath.formatSignedSeconds(applied))
        }
    }

    /** 清掉第 [from]–[to] 句里的单句微调，区间外不动。 */
    fun resetLinesRange(from: Int, to: Int) {
        var cleared = 0
        editCalibration { cal, lines ->
            val r = CalibrationMath.normalizeRange(lines.size, from, to) ?: return@editCalibration cal
            val next = CalibrationMath.resetRange(cal, r)
            cleared = cal.lines.size - next.lines.size
            next
        }
        notify(
            if (cleared > 0) "已清除本区间 " + cleared + " 句的单句微调"
            else "本区间没有单独微调过的句子",
        )
    }

    private fun formatClock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }
}
