package com.kiite.player.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable

/**
 * 一条播放历史。
 *
 * 关键字是 [trackPath]：同一集反复播放只占一条，位置与时间不断被覆盖。
 * [finishedAtMs] > 0 表示「这一集已经听完」，用于「最近完成」；
 * 用户重新开播时会被清零，否则「听完了」会一直挂着。
 */
@Serializable
data class HistoryRecord(
    val trackPath: String,
    val trackName: String = "",
    val projectPath: String = "",
    val projectName: String = "",
    /** 上次停下的位置（毫秒）。 */
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** 最后一次播放的时间戳。 */
    val lastPlayedAtMs: Long = 0L,
    /** 播放次数，用来区分「偶尔点开」和「反复在听」。 */
    val playedCount: Int = 1,
    /** 听完的时间戳，0 表示没听完。 */
    val finishedAtMs: Long = 0L,
) {
    val finished: Boolean get() = finishedAtMs > 0L

    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * 书签：曲目里的一个时间点 + 可选备注。
 *
 * 同时冗余保存曲目名与作品名：曲库被清空、文件被移走之后，
 * 书签列表仍然能显示「这是哪一集里的哪一句」，而不是一条光秃秃的路径。
 *
 * 两种书签：
 * - **时间点书签**（[lineIndex] < 0）：记下「听到这儿了」，位置由用户当前进度决定；
 * - **按行书签**（[lineIndex] >= 0）：记在台本的某一句上，位置取那一句的时间轴。
 *
 * 按行书签额外存 [lineText] 与 [scriptFingerprint]：台本换了（行数或内容变了）之后，
 * 行号就不可信了——前者让列表还能显示原来的那一句，后者用来提示「台本已变更」。
 */
@Serializable
data class Bookmark(
    val id: String,
    val trackPath: String,
    val trackName: String = "",
    val projectPath: String = "",
    val projectName: String = "",
    val positionMs: Long = 0L,
    val note: String = "",
    val createdAtMs: Long = 0L,
    /** 台本行号（从 0 开始）；< 0 表示这是普通的时间点书签。 */
    val lineIndex: Int = -1,
    /** 那一句的原文，台本换了也能看出当初标的是哪句。 */
    val lineText: String = "",
    /** 加书签时台本的指纹，用来判断行号是否还指着同一句。 */
    val scriptFingerprint: String = "",
) {
    val isLine: Boolean get() = lineIndex >= 0
}

/** 加书签的结果：是否合并到了附近已有的书签上，供界面给出不同提示。 */
data class BookmarkAddOutcome(val bookmarks: List<Bookmark>, val merged: Boolean)

/** 按行书签的开关结果：[added] 为 false 表示这次是「取消」。 */
data class BookmarkToggle(val bookmarks: List<Bookmark>, val added: Boolean)

/**
 * 播放历史与书签的纯计算部分。
 *
 * 所有函数都是「进一份列表、出一份列表」，不碰文件、不碰 Android，
 * 方便把边界情况（结束时清空、位置回退、重复添加）都写成单元测试。
 */
object HistoryMath {

    /** 历史最多保留多少条，超出按最后播放时间淘汰最旧的。 */
    const val MAX_RECORDS = 200

    /** 距结尾多少毫秒内算「听完」。和进度记忆的清空阈值保持一致。 */
    const val END_TOLERANCE_MS = 3_000L

    /** 少于这个位置不认为「听到过」，续播时从头开始。 */
    const val RESUME_MIN_MS = 4_000L

    /** 单日收听时长保留多少天，避免这个 map 无限长。 */
    private const val DAYS_KEPT = 90

    fun isFinished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L && positionMs >= durationMs - END_TOLERANCE_MS

    /**
     * 开始播放：置顶、次数 +1、清掉「已听完」。
     *
     * 位置不在这里改——开播那一刻的位置由调用方决定（可能是续播点，也可能从 0 开始）。
     */
    fun onStart(
        records: List<HistoryRecord>,
        trackPath: String,
        trackName: String,
        projectPath: String,
        projectName: String,
        positionMs: Long,
        durationMs: Long,
        nowMs: Long,
    ): List<HistoryRecord> {
        val old = records.firstOrNull { it.trackPath == trackPath }
        val updated = HistoryRecord(
            trackPath = trackPath,
            trackName = trackName,
            projectPath = projectPath,
            projectName = projectName,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = if (durationMs > 0L) durationMs else (old?.durationMs ?: 0L),
            lastPlayedAtMs = nowMs,
            playedCount = (old?.playedCount ?: 0) + 1,
            finishedAtMs = 0L,
        )
        return prune(listOf(updated) + records.filter { it.trackPath != trackPath })
    }

    /**
     * 播放中的位置同步：**不改变顺序**。
     *
     * 否则每 5 秒一次的位置保存会让列表反复重排，用户翻列表时行会自己跳动。
     */
    fun onProgress(
        records: List<HistoryRecord>,
        trackPath: String,
        positionMs: Long,
        durationMs: Long,
        nowMs: Long,
    ): List<HistoryRecord> {
        val index = records.indexOfFirst { it.trackPath == trackPath }
        if (index < 0) return records
        val old = records[index]
        val next = old.copy(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = if (durationMs > 0L) durationMs else old.durationMs,
            lastPlayedAtMs = nowMs,
        )
        if (next == old) return records
        return records.toMutableList().also { it[index] = next }
    }

    /** 播完一集：位置归到结尾附近，打上「已完成」。 */
    fun onFinished(records: List<HistoryRecord>, trackPath: String, nowMs: Long): List<HistoryRecord> {
        val index = records.indexOfFirst { it.trackPath == trackPath }
        if (index < 0) return records
        val old = records[index]
        val end = if (old.durationMs > 0L) old.durationMs else old.positionMs
        val next = old.copy(positionMs = end, lastPlayedAtMs = nowMs, finishedAtMs = nowMs)
        if (next == old) return records
        return records.toMutableList().also { it[index] = next }
    }

    fun remove(records: List<HistoryRecord>, trackPath: String): List<HistoryRecord> =
        records.filterNot { it.trackPath == trackPath }

    /** 按最后播放时间倒序，并且最多留 [MAX_RECORDS] 条。 */
    fun prune(records: List<HistoryRecord>, max: Int = MAX_RECORDS): List<HistoryRecord> =
        records.sortedByDescending { it.lastPlayedAtMs }.take(max)

    /**
     * 点历史里的一条时，应该从哪儿开始放。
     *
     * 听完过的、或者只听到开头几秒的，都从头开始——否则会停在片尾或原地不动。
     */
    fun resumePositionOf(record: HistoryRecord?): Long {
        if (record == null) return 0L
        if (record.finished) return 0L
        if (record.positionMs < RESUME_MIN_MS) return 0L
        return record.positionMs
    }

    // ---------- 累计收听时长 ----------

    fun dayKey(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()

    /**
     * 把一段收听时长记到「今天」并累加到总计。
     * @return (按天统计, 总计)
     */
    fun addListened(
        daily: Map<String, Long>,
        total: Long,
        deltaMs: Long,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<Map<String, Long>, Long> {
        if (deltaMs <= 0L) return daily to total
        val key = dayKey(nowMs, zone)
        val merged = daily + (key to ((daily[key] ?: 0L) + deltaMs))
        // 只留最近 DAYS_KEPT 天，key 是 ISO 日期，字典序即时间序
        val trimmed = if (merged.size <= DAYS_KEPT) {
            merged
        } else {
            merged.entries.sortedByDescending { it.key }.take(DAYS_KEPT).associate { it.key to it.value }
        }
        return trimmed to (total + deltaMs)
    }

    fun todayMs(daily: Map<String, Long>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        daily[dayKey(nowMs, zone)] ?: 0L

    /**
     * 把一段收听时长记到「一天里的第几小时」（key = `0`..`23`）。
     *
     * 与 [addListened] 分开两个函数、分开两张表：按天那张会裁剪到最近 90 天，
     * 而时段分布本来就该看长期习惯，不能被裁掉。
     */
    fun addToHour(
        hourly: Map<String, Long>,
        deltaMs: Long,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<String, Long> {
        if (deltaMs <= 0L) return hourly
        val key = Instant.ofEpochMilli(nowMs).atZone(zone).hour.toString()
        return hourly + (key to ((hourly[key] ?: 0L) + deltaMs))
    }

    /** 某个小时（0-23）的累计时长。 */
    fun hourMs(hourly: Map<String, Long>, hour: Int): Long = hourly[hour.toString()] ?: 0L

    /** 区间收听时长（含两端）。`from` 晚于 `to` 时返回 0。 */
    fun sumRange(
        daily: Map<String, Long>,
        from: LocalDate,
        to: LocalDate,
    ): Long {
        if (from.isAfter(to)) return 0L
        var sum = 0L
        var d = from
        while (!d.isAfter(to)) {
            sum += daily[d.toString()] ?: 0L
            d = d.plusDays(1)
        }
        return sum
    }

    /** 有收听记录的日期，按日期升序（统计「收听天数」「连续天数」用）。 */
    fun listenedDays(daily: Map<String, Long>): List<LocalDate> =
        daily.filterValues { it > 0L }.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .sorted()

    /** 本周一（周一为一周起点，与国内习惯一致）。 */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    // ---------- 显示 ----------

    /** 「刚刚 / 12 分钟前 / 昨天 / 3 天前 / 2026-01-02」。 */
    fun relativeTime(nowMs: Long, thenMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (thenMs <= 0L) return "—"
        val diff = (nowMs - thenMs).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        return when {
            minutes < 1L -> "刚刚"
            minutes < 60L -> minutes.toString() + " 分钟前"
            diff < 24L * 3_600_000L -> (diff / 3_600_000L).toString() + " 小时前"
            else -> {
                val then = Instant.ofEpochMilli(thenMs).atZone(zone).toLocalDate()
                val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
                val days = java.time.temporal.ChronoUnit.DAYS.between(then, today)
                when {
                    days <= 1L -> "昨天"
                    days < 7L -> days.toString() + " 天前"
                    else -> then.toString()
                }
            }
        }
    }

    /** 「12 分钟」「3 小时 20 分」，用于累计时长。 */
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0 分钟"
        val totalMinutes = ms / 60_000L
        if (totalMinutes < 1L) return "不到 1 分钟"
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours <= 0L -> minutes.toString() + " 分钟"
            minutes == 0L -> hours.toString() + " 小时"
            else -> hours.toString() + " 小时 " + minutes + " 分"
        }
    }

    /** 历史上「今天」是不是新的一天，用于跨零点后把日统计分开（保留给界面判断）。 */
    fun isSameDay(aMs: Long, bMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val da: LocalDate = Instant.ofEpochMilli(aMs).atZone(zone).toLocalDate()
        val db: LocalDate = Instant.ofEpochMilli(bMs).atZone(zone).toLocalDate()
        return da == db
    }
}

/** 书签的纯计算部分。 */
object BookmarkMath {

    /** 同曲目内两次书签相距小于这个值，就认为是「同一个点」，合并而不是堆两条。 */
    const val NEAR_MS = 1_500L

    /** 书签总数上限，防止误触刷出一堆。 */
    const val MAX_TOTAL = 500

    /**
     * 加一条书签。
     *
     * 位置附近（[NEAR_MS] 内）已有书签时合并过去：位置更新，备注保留旧的（新备注非空则覆盖）。
     * 重复点「加书签」大多是手抖，不该出现两条一模一样的记录。
     */
    fun add(list: List<Bookmark>, bookmark: Bookmark, max: Int = MAX_TOTAL): BookmarkAddOutcome {
        // 两种书签各归各的池子：按行书签按「行号」判重，时间点书签按「位置接近」判重。
        // 混在一起会出现「在 B 句加了书签，结果把前面 A 句的时间点书签改掉了」。
        val near = if (bookmark.isLine) {
            list.firstOrNull {
                it.isLine && it.trackPath == bookmark.trackPath && it.lineIndex == bookmark.lineIndex
            }
        } else {
            nearestOfTrack(list.filterNot { it.isLine }, bookmark.trackPath, bookmark.positionMs)
        }
        if (near != null) {
            val merged = near.copy(
                positionMs = bookmark.positionMs,
                note = bookmark.note.ifBlank { near.note },
                createdAtMs = if (near.createdAtMs > 0L) near.createdAtMs else bookmark.createdAtMs,
                trackName = bookmark.trackName.ifBlank { near.trackName },
                projectPath = bookmark.projectPath.ifBlank { near.projectPath },
                projectName = bookmark.projectName.ifBlank { near.projectName },
                lineIndex = if (bookmark.isLine) bookmark.lineIndex else near.lineIndex,
                lineText = bookmark.lineText.ifBlank { near.lineText },
                scriptFingerprint = bookmark.scriptFingerprint.ifBlank { near.scriptFingerprint },
            )
            return BookmarkAddOutcome(list.map { if (it.id == near.id) merged else it }, merged = true)
        }
        val next = list + bookmark
        // 超上限时淘汰最早创建的那条
        val trimmed = if (next.size <= max) next else next.sortedByDescending { it.createdAtMs }.take(max)
        return BookmarkAddOutcome(trimmed, merged = false)
    }

    /** 某一集某一行的书签（没有则 null）。 */
    fun ofLine(list: List<Bookmark>, trackPath: String, lineIndex: Int): Bookmark? =
        list.firstOrNull { it.isLine && it.trackPath == trackPath && it.lineIndex == lineIndex }

    /** 某一集里被标了书签的行号集合，供台本行渲染小图标。 */
    fun lineIndexesOf(list: List<Bookmark>, trackPath: String): Set<Int> =
        list.asSequence()
            .filter { it.isLine && it.trackPath == trackPath }
            .map { it.lineIndex }
            .toSet()

    /**
     * 按行书签的开关：这一句已经有书签就删掉，没有就加上。
     *
     * 长按/点小图标都是同一个动作，用「开关」而不是「只加」，
     * 否则想去掉某一行的标记就只能跑到书签面板里找——那一步没人愿意走。
     */
    fun toggleLine(list: List<Bookmark>, bookmark: Bookmark, max: Int = MAX_TOTAL): BookmarkToggle {
        if (!bookmark.isLine) return BookmarkToggle(list, added = false)
        val existing = ofLine(list, bookmark.trackPath, bookmark.lineIndex)
        if (existing != null) {
            return BookmarkToggle(list.filterNot { it.id == existing.id }, added = false)
        }
        return BookmarkToggle(add(list, bookmark, max).bookmarks, added = true)
    }

    /**
     * 台本指纹：行数 + 内容，用来判断「行号还指着同一句吗」。
     *
     * 不用文件路径或修改时间：用户可能只是重新导出同一份台本，
     * 内容没变就不该判失效；反过来内容改了（哪怕文件没动），行号就真的不可信了。
     */
    fun fingerprint(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        var h = 17
        for (line in lines) h = h * 31 + line.hashCode()
        return lines.size.toString() + ":" + Integer.toHexString(h)
    }

    /** 按行书签记录的指纹与当前台本不一致（台本换过/改过），行号可能已经指错。 */
    fun lineStale(bookmark: Bookmark, currentFingerprint: String): Boolean =
        bookmark.isLine &&
            bookmark.scriptFingerprint.isNotBlank() &&
            currentFingerprint.isNotBlank() &&
            bookmark.scriptFingerprint != currentFingerprint

    fun updateNote(list: List<Bookmark>, id: String, note: String): List<Bookmark> =
        list.map { if (it.id == id) it.copy(note = note.trim()) else it }

    fun remove(list: List<Bookmark>, id: String): List<Bookmark> = list.filterNot { it.id == id }

    /** 某一集的书签，按时间点升序（就是听的时候从前到后的顺序）。 */
    fun ofTrack(list: List<Bookmark>, trackPath: String): List<Bookmark> =
        list.filter { it.trackPath == trackPath }.sortedBy { it.positionMs }

    /** 某一集里离 [positionMs] 最近、且在容差内的书签。 */
    fun nearestOfTrack(
        list: List<Bookmark>,
        trackPath: String,
        positionMs: Long,
        toleranceMs: Long = NEAR_MS,
    ): Bookmark? = list
        .asSequence()
        .filter { it.trackPath == trackPath }
        .map { it to kotlin.math.abs(it.positionMs - positionMs) }
        .filter { it.second <= toleranceMs }
        .minByOrNull { it.second }
        ?.first

    /**
     * 面板里的分组顺序：最近加了书签的曲目排在前面，
     * 同一曲目内部按时间点升序。
     */
    fun grouped(list: List<Bookmark>): List<Pair<String, List<Bookmark>>> {
        val byTrack = list.groupBy { it.trackPath }
        return byTrack.values
            .sortedByDescending { group -> group.maxOf { it.createdAtMs } }
            .map { group -> group.first().trackPath to group.sortedBy { it.positionMs } }
    }
}
