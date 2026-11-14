package com.kiite.player.domain

import android.content.Context
import com.kiite.player.core.DlsiteWork
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.TrackEntry
import com.kiite.player.core.WorkRating
import com.kiite.player.data.AppLog
import com.kiite.player.data.RatingStore
import com.kiite.player.data.TagStore
import com.kiite.player.data.WorkMeta
import com.kiite.player.data.WorkMetaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 作品级的手工数据：分级、自定义标签、可自由编辑的元数据（标题/简介/收藏/集名/隐藏/排序）。
 *
 * 共同点很明确：**都以「作品」为键、都存在 App 自己的 JSON 里、都与音频文件本身无关**。
 * 因此它们从 `MainViewModel` 抽出来最自然——既不需要播放状态，也不需要曲库扫描结果。
 *
 * 键的选择：有 RJ/VJ 编号就用编号（跨设备、改名都稳），没有才退回项目路径，
 * 否则没有编号的文件夹根本没法单独标记。
 */
class WorkMetaController(
    app: Context,
    private val dlsiteCodeOfProject: (ProjectEntry) -> String?,
    /** 按编号取已抓到的 DLsite 作品（判 R18 用）。 */
    private val dlsiteWorkOf: (String) -> DlsiteWork?,
    private val notify: (String) -> Unit,
    private val emitUndo: (UndoRequest) -> Unit,
) {
    private val ratingStore = RatingStore(app)
    private val tagStore = TagStore(app)
    private val workMetaStore = WorkMetaStore(app)

    private val _manualRatings = MutableStateFlow<Map<String, String>>(emptyMap())
    val manualRatings: StateFlow<Map<String, String>> = _manualRatings.asStateFlow()

    private val _manualTags = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val manualTags: StateFlow<Map<String, List<String>>> = _manualTags.asStateFlow()

    private val _workMeta = MutableStateFlow<Map<String, WorkMeta>>(emptyMap())
    val workMeta: StateFlow<Map<String, WorkMeta>> = _workMeta.asStateFlow()

    private fun projectKey(project: ProjectEntry): String =
        dlsiteCodeOfProject(project)?.uppercase() ?: project.path

    // ---------- 分级 ----------

    fun loadRatings() {
        runCatching { _manualRatings.value = ratingStore.load() }
    }

    fun ratingOf(project: ProjectEntry): WorkRating {
        _manualRatings.value[projectKey(project)]?.let { return WorkRating.of(it) }
        val code = dlsiteCodeOfProject(project)?.uppercase()
        if (code != null) {
            dlsiteWorkOf(code)?.let {
                return if (it.r18) WorkRating.R18 else WorkRating.ALL
            }
        }
        return WorkRating.UNKNOWN
    }

    fun setRating(project: ProjectEntry, rating: WorkRating) {
        val key = projectKey(project)
        val merged = _manualRatings.value + (key to rating.id)
        _manualRatings.value = merged
        ratingStore.save(merged)
        AppLog.log("分级：" + project.name + " → " + rating.label)
        notify("已标记为「" + rating.label + "」")
    }

    /**
     * 批量设置分级，并发出一条可撤销提示。
     * 撤销会把每个作品恢复成批量前的分级（包括「原本是 UNKNOWN、没有手动记录」的情况）。
     */
    fun setRatingsBatch(targets: List<ProjectEntry>, rating: WorkRating) {
        if (targets.isEmpty()) return
        val previous = targets.associate { projectKey(it) to _manualRatings.value[projectKey(it)] }
        val merged = targets.fold(_manualRatings.value) { acc, p -> acc + (projectKey(p) to rating.id) }
        _manualRatings.value = merged
        ratingStore.save(merged)
        AppLog.log("批量分级 " + targets.size + " 个作品 → " + rating.label)
        emitUndo(
            UndoRequest("已把 " + targets.size + " 个作品标为「" + rating.label + "」") {
                val restored = previous.entries.fold(_manualRatings.value) { acc, (k, v) ->
                    if (v == null) acc - k else acc + (k to v)
                }
                _manualRatings.value = restored
                ratingStore.save(restored)
            },
        )
    }

    // ---------- 自定义标签 ----------

    fun loadTags() {
        runCatching { _manualTags.value = tagStore.load() }
    }

    fun tagsOf(project: ProjectEntry): List<String> = _manualTags.value[projectKey(project)].orEmpty()

    fun setTags(project: ProjectEntry, tags: List<String>) {
        val key = projectKey(project)
        val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val merged = if (clean.isEmpty()) _manualTags.value - key else _manualTags.value + (key to clean)
        _manualTags.value = merged
        tagStore.save(merged)
        AppLog.log("标签：" + project.name + " → " + if (clean.isEmpty()) "（清空）" else clean.joinToString("/"))
    }

    /** 全库用过的自定义标签，供筛选与推荐。 */
    fun knownCustomTags(): List<String> = _manualTags.value.values.flatten().distinct().sorted()

    // ---------- 作品元数据 ----------

    fun loadWorkMeta() {
        runCatching { _workMeta.value = workMetaStore.load() }
    }

    fun workMetaOf(project: ProjectEntry): WorkMeta =
        _workMeta.value[projectKey(project)] ?: WorkMeta()

    private fun putWorkMeta(project: ProjectEntry, meta: WorkMeta) {
        val key = projectKey(project)
        val empty = meta.title.isBlank() && !meta.favorite && meta.synopsis.isBlank() &&
            meta.epNames.isEmpty() && meta.hidden.isEmpty() && meta.order.isEmpty() &&
            meta.series.isBlank()
        val merged = if (empty) _workMeta.value - key else _workMeta.value + (key to meta)
        _workMeta.value = merged
        workMetaStore.save(merged)
    }

    /** 改作品名（自定义名称优先于「[编号] 文件夹名」；空白恢复原规则）。 */
    fun setWorkTitle(project: ProjectEntry, name: String) {
        putWorkMeta(project, workMetaOf(project).copy(title = name.trim()))
        AppLog.log("改名：" + project.name + " → " + name.trim().take(30))
    }

    /** 收藏（列表置顶）。 */
    fun isFavorite(project: ProjectEntry): Boolean = workMetaOf(project).favorite

    fun setFavorite(project: ProjectEntry, favorite: Boolean) {
        if (isFavorite(project) == favorite) return
        putWorkMeta(project, workMetaOf(project).copy(favorite = favorite))
    }

    /** 改简介（手写会覆盖 DLsite 抓到的内容）。 */
    fun setSynopsis(project: ProjectEntry, text: String) {
        putWorkMeta(project, workMetaOf(project).copy(synopsis = text.trim()))
        AppLog.log("简介：" + project.name + " → " + text.trim().take(30))
    }

    // ---------- 系列（组曲） ----------

    /** 作品所属系列；空白 = 未归类。 */
    fun seriesOf(project: ProjectEntry): String = workMetaOf(project).series

    /** 设置作品所属系列（空白清除）。 */
    fun setSeries(project: ProjectEntry, series: String) {
        val clean = series.trim()
        if (seriesOf(project) == clean) return
        putWorkMeta(project, workMetaOf(project).copy(series = clean))
        AppLog.log("系列：" + project.name + " → " + clean.ifBlank { "（未归类）" })
    }

    /** 全库用过的系列名（按名字排序），供筛选与推荐。 */
    fun knownSeries(): List<String> =
        _workMeta.value.values.map { it.series }.filter { it.isNotBlank() }.distinct().sorted()

    /** 改某一集的显示名（空白则恢复原名）。 */
    fun setEpisodeName(project: ProjectEntry, trackPath: String, name: String) {
        val meta = workMetaOf(project)
        val clean = name.trim()
        val names = if (clean.isEmpty()) meta.epNames - trackPath else meta.epNames + (trackPath to clean)
        putWorkMeta(project, meta.copy(epNames = names))
    }

    /** 隐藏 / 取消隐藏某一集。 */
    fun toggleEpisodeHidden(project: ProjectEntry, trackPath: String) {
        val meta = workMetaOf(project)
        val hidden = if (trackPath in meta.hidden) meta.hidden - trackPath else meta.hidden + trackPath
        putWorkMeta(project, meta.copy(hidden = hidden))
        notify(if (trackPath in hidden) "已隐藏这一集" else "已取消隐藏")
    }

    /**
     * 详情页里真正要展示的曲目：先按自定义顺序排，再滤掉隐藏的。
     * [tracks] 传当前项目的全部曲目。
     */
    fun visibleEpisodes(project: ProjectEntry, tracks: List<TrackEntry>): List<TrackEntry> {
        val meta = workMetaOf(project)
        val visible = tracks.filter { it.path !in meta.hidden }
        if (meta.order.isEmpty()) return visible
        val rank = meta.order.withIndex().associate { (i, p) -> p to i }
        return visible.sortedBy { rank[it.path] ?: Int.MAX_VALUE }
    }

    /** 某一集的显示名。 */
    fun episodeTitle(project: ProjectEntry, track: TrackEntry): String {
        val custom = workMetaOf(project).epNames[track.path]
        return if (!custom.isNullOrBlank()) custom else track.baseName
    }
}
