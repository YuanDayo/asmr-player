package com.kiite.player.ui

import com.kiite.player.core.ArchiveEntry
import com.kiite.player.core.ChapterEntry
import com.kiite.player.core.ManualLinks
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.ProjectGrouper
import com.kiite.player.core.ScanCancelledException
import com.kiite.player.core.ScanProgress
import com.kiite.player.core.ScanResult
import com.kiite.player.core.TrackEntry
import com.kiite.player.data.AppLog
import com.kiite.player.data.AppSettings
import com.kiite.player.data.LibraryRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 曲库：扫描、手动指定台本的覆盖层、各种按作品/章节取曲目的查询。
 *
 * 这三样是同一坨状态，必须一起搬：
 * - `rawScan` 是扫描的**原始结果**，永远不会被手动指定污染；
 * - `manualLinks` 是「这一集用这个台本文件」的覆盖层；
 * - 界面看到的 `scan` 永远是「原始结果 + 覆盖层」重算出来的那份。
 *
 * 所以清除手动指定时能正确回落到自动匹配的结果，而不是回落到「上一次重算后的结果」。
 *
 * 与其它块的边界：扫描完成后要不要恢复播放现场、要不要自动写标签，都由 [onScanReady] 回调交出去，
 * 曲库自己不碰播放器，也不碰标签。
 */
class LibraryController(
    private val repo: LibraryRepository,
    private val scope: CoroutineScope,
    private val settings: () -> AppSettings,
    private val notify: (String) -> Unit,
    private val setBusy: (String, String?) -> Unit,
    /** 扫描成功后回调（恢复播放现场、按需自动写入标签）。 */
    private val onScanReady: (ScanResult) -> Unit,
) {
    private val _scan = MutableStateFlow<ScanResult?>(null)
    val scan: StateFlow<ScanResult?> = _scan.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    private val _selectedProject = MutableStateFlow<String?>(null)
    val selectedProject: StateFlow<String?> = _selectedProject.asStateFlow()

    private val _manualLinks = MutableStateFlow<Map<String, String>>(emptyMap())
    val manualLinks: StateFlow<Map<String, String>> = _manualLinks.asStateFlow()

    /** 自动匹配的原始结果；手动指定始终在它之上重算，清除后能正确回落。 */
    private var rawScan: ScanResult? = null

    private var scanJob: Job? = null

    // ---------- 根目录 / 扫描 ----------

    /** 清空曲库（换根目录到空值时用）。 */
    fun clear() {
        _scan.value = null
        rawScan = null
        _selectedProject.value = null
    }

    fun rescan() {
        val root = settings().rootPath?.let(::File) ?: return
        if (!root.isDirectory) {
            notify("根目录不存在或不可读")
            return
        }
        scanJob?.cancel()
        scanJob = scope.launch {
            _scanning.value = true
            _scanProgress.value = null
            setBusy("scan", "正在扫描…")
            var lastEmit = 0L
            val outcome = runCatching {
                repo.scan(
                    root,
                    isCancelled = { scanJob?.isActive == false },
                    onProgress = { p ->
                        // 节流：最多 ~8 次/秒刷新进度文案
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 120) {
                            lastEmit = now
                            _scanProgress.value = p
                            setBusy("scan", "正在扫描… 已发现 ${p.tracks} 首音频")
                        }
                    },
                )
            }
            _scanning.value = false
            _scanProgress.value = null
            setBusy("scan", null)
            outcome
                .onSuccess { result ->
                    rawScan = result
                    val merged = withManualLinks(result, _manualLinks.value)
                    _scan.value = merged
                    AppLog.log(
                        "扫描完成：项目 " + merged.projectCount + "，音频 " + merged.trackCount +
                            "，已配台本 " + merged.withScriptCount + "，压缩包 " + merged.archiveCount,
                    )
                    notify("扫描完成：${merged.trackCount} 首音频，${merged.withScriptCount} 首匹配到台本")
                    onScanReady(merged)
                }
                .onFailure {
                    when (it) {
                        is ScanCancelledException, is CancellationException ->
                            notify("扫描已取消，已有曲库保持不变")
                        else -> {
                            AppLog.log("扫描失败：" + it.message)
                            notify("扫描失败：${it.message}")
                        }
                    }
                }
        }
    }

    /** 取消进行中的扫描：已扫描到的部分丢弃，原有曲库保持不变。 */
    fun cancelScan() {
        scanJob?.cancel()
    }

    fun selectProject(path: String?) {
        _selectedProject.value = path
    }

    // ---------- 手动指定台本 ----------

    fun loadManualLinks() {
        scope.launch { _manualLinks.value = repo.loadManualLinks() }
    }

    /**
     * 指定「这一集用这个台本文件」，并立刻重算曲库快照。
     *
     * [onApplied] 在快照重算**之后**回调：曲目对象是重算时新建的，
     * 调用方要拿新对象（它带着台本附件）而不是旧的。
     */
    fun setManualLink(trackPath: String, scriptPath: String, onApplied: () -> Unit = {}) {
        scope.launch {
            repo.saveManualLink(trackPath, scriptPath)
            _manualLinks.value = _manualLinks.value + (trackPath to scriptPath)
            reapply()
            onApplied()
        }
    }

    /** 取消手动指定，回落到自动匹配的结果。 */
    fun clearManualLink(trackPath: String, onApplied: () -> Unit = {}) {
        scope.launch {
            repo.clearManualLink(trackPath)
            _manualLinks.value = _manualLinks.value - trackPath
            reapply()
            onApplied()
        }
    }

    private fun withManualLinks(scan: ScanResult, links: Map<String, String>): ScanResult {
        val tracks = ManualLinks.apply(scan.tracks, links)
        return scan.copy(
            tracks = tracks,
            projects = ProjectGrouper.group(scan.rootPath, tracks, scan.archives),
        )
    }

    private fun reapply() {
        val raw = rawScan ?: return
        _scan.value = withManualLinks(raw, _manualLinks.value)
    }

    // ---------- 查询 ----------

    fun trackOf(path: String): TrackEntry? = _scan.value?.tracks?.firstOrNull { it.path == path }

    fun projectOf(path: String): ProjectEntry? = _scan.value?.projects?.firstOrNull { it.path == path }

    fun tracksOf(folderPath: String?): List<TrackEntry> {
        val tracks = _scan.value?.tracks ?: return emptyList()
        return if (folderPath == null) tracks else tracks.filter { it.folderPath == folderPath }
    }

    fun tracksOfProject(projectPath: String): List<TrackEntry> {
        val scan = _scan.value ?: return emptyList()
        return scan.tracks.filter {
            ProjectGrouper.projectRootOf(scan.rootPath, it.folderPath) == projectPath
        }
    }

    /** 某个总项目里未解压的压缩包。 */
    fun archivesOfProject(projectPath: String): List<ArchiveEntry> {
        val scan = _scan.value ?: return emptyList()
        return scan.archives.filter {
            ProjectGrouper.projectRootOf(scan.rootPath, it.folderPath) == projectPath
        }
    }

    /** 项目内的章节分组：章节 → 该章节的曲目。 */
    fun chaptersOf(project: ProjectEntry): List<Pair<ChapterEntry, List<TrackEntry>>> {
        val all = tracksOfProject(project.path)
        return project.chapters
            .map { ch -> ch to all.filter { it.folderPath == ch.path } }
            .filter { it.second.isNotEmpty() }
    }

    /** 解压 zip 并重新扫描。 */
    fun extractArchive(archivePath: String) {
        scope.launch {
            setBusy("extract", "正在解压…")
            val outcome = runCatching { repo.extractZip(archivePath) }
            setBusy("extract", null)
            outcome
                .onSuccess { n ->
                    notify("已解压 " + n + " 个文件，正在重新扫描…")
                    rescan()
                }
                .onFailure { notify("解压失败：" + it.message) }
        }
    }

    /** 曲库文件数（根目录下），用于「统计」类提示。 */
    fun countAudio(dir: File) = scope.launch {
        val n = repo.countAudioFiles(dir)
        notify("该文件夹及其子目录共有 $n 首音频")
    }

    /**
     * 曲库里是否还有这一集。
     * 扫描还没完成（曲目表为空）时一律当作「有」，否则会把整张历史列表误标成失效。
     */
    fun isTrackAvailable(path: String): Boolean {
        val scan = _scan.value ?: return true
        if (scan.tracks.isEmpty()) return true
        return scan.tracks.any { it.path == path }
    }

    /**
     * 批量判断哪些路径已经不在曲库里。
     *
     * 列表里逐行调 [isTrackAvailable] 是 O(记录数 × 曲目数)，两千首曲库下每行都要扫一遍；
     * 这里一次建集合，整张历史只扫一遍。
     */
    fun unavailablePaths(paths: Collection<String>): Set<String> {
        val scan = _scan.value ?: return emptySet()
        if (scan.tracks.isEmpty()) return emptySet()
        val known = scan.tracks.mapTo(HashSet(scan.tracks.size)) { it.path }
        return paths.filterNotTo(HashSet()) { it in known }
    }
}
