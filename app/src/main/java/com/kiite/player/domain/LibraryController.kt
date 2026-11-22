package com.kiite.player.domain

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
import com.kiite.player.data.LibraryDataSource
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
    private val repo: LibraryDataSource,
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

    /**
     * 已从曲库移除的作品目录。只从展示里过滤，磁盘文件仍在时也不会被重扫复活；
     * 设置里可以一键恢复。删除本地文件时不留这条记录（文件本身已经没了）。
     */
    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

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
        runScan(root)
    }

    private fun runScan(root: File) {
        scanJob?.cancel()
        scanJob = scope.launch {
            _scanning.value = true
            _scanProgress.value = null
            setBusy("scan", "正在扫描…")
            var lastEmit = 0L
            val onProgress: (ScanProgress) -> Unit = { p ->
                // 节流：最多 ~8 次/秒刷新进度文案
                val now = System.currentTimeMillis()
                if (now - lastEmit > 120) {
                    lastEmit = now
                    _scanProgress.value = p
                    setBusy("scan", "正在扫描… 已发现 ${p.tracks} 首音频")
                }
            }
            // 失败自动重试一次：SD 卡短暂挂载不稳 / IO 抖动很常见，
            // 用户手动点「重新扫描」和自动重试走同一条路；取消不重试
            var outcome = runCatching {
                repo.scan(root, isCancelled = { scanJob?.isActive == false }, onProgress = onProgress)
            }
            if (outcome.isFailure && scanJob?.isActive == true &&
                outcome.exceptionOrNull() !is ScanCancelledException &&
                outcome.exceptionOrNull() !is CancellationException
            ) {
                AppLog.log("扫描失败，自动重试一次：" + outcome.exceptionOrNull()?.message)
                outcome = runCatching {
                    repo.scan(root, isCancelled = { scanJob?.isActive == false }, onProgress = onProgress)
                }
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

    fun loadHiddenProjects() {
        scope.launch {
            _hidden.value = repo.loadHiddenProjects()
            reapply()
        }
    }

    /**
     * 从曲库移除一部作品。[deleteFiles] 为 true 时连本地目录一起删（不可恢复）；
     * 为 false 只加隐藏标记，文件还在，设置里能恢复。
     *
     * 只允许删曲库根目录下的一级/多级作品目录：根目录本身、根目录之外的路径一律拒绝，
     * 免得一次误点把整个音乐库抹掉。
     */
    fun deleteProject(path: String, deleteFiles: Boolean, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val root = settings().rootPath?.let { runCatching { File(it).canonicalFile }.getOrNull() }
            val target = runCatching { File(path).canonicalFile }.getOrNull()
            if (root == null || target == null || !target.isDirectory ||
                target == root || !target.path.startsWith(root.path + File.separator)
            ) {
                notify("只能移除曲库目录内的作品文件夹")
                onDone(false)
                return@launch
            }
            if (deleteFiles) {
                // 同上（见 deleteProjects）：仓库层已经切了 IO，这里不再重复切
                val deleted = repo.deleteProjectFiles(target.path)
                if (!deleted) {
                    notify("删除失败：文件可能被占用或没有写入权限")
                    onDone(false)
                    return@launch
                }
                if (target.path in _hidden.value) {
                    repo.saveHiddenProjects(_hidden.value - target.path)
                    _hidden.value = _hidden.value - target.path
                }
            } else {
                val next = _hidden.value + target.path
                repo.saveHiddenProjects(next)
                _hidden.value = next
            }
            reapply()
            if (_selectedProject.value == target.path) _selectedProject.value = null
            notify(
                if (deleteFiles) "已删除作品和本地文件，正在刷新曲库"
                else "已从曲库移除，本地文件保留（设置里可恢复）",
            )
            onDone(true)
        }
    }

    /**
     * 批量移除作品。逐条的判定与 [deleteProject] 完全一致（同一套根目录边界校验），
     * 区别只在收尾：**只重算一次曲库、只弹一条提示**。
     *
     * 逐个调用 [deleteProject] 会把全库重算 N 遍 —— 几十部作品时每删一个就重建一次
     * 索引与会话，批量删除会变成「点一下卡十秒」。所以这里自己攒 `hidden`，
     * 最后统一落盘 + reapply。
     */
    fun deleteProjects(paths: Collection<String>, deleteFiles: Boolean, onDone: (Set<String>) -> Unit = {}) {
        scope.launch {
            val root = settings().rootPath?.let { runCatching { File(it).canonicalFile }.getOrNull() }
            val removed = LinkedHashSet<String>()
            var hidden = _hidden.value
            var failed = 0
            for (raw in paths) {
                val target = runCatching { File(raw).canonicalFile }.getOrNull()
                if (root == null || target == null || !target.isDirectory || target == root ||
                    !target.path.startsWith(root.path + File.separator)
                ) {
                    failed++
                    continue
                }
                if (deleteFiles) {
                    // 不要再包一层 withContext(Dispatchers.IO)：仓库层的
                    // deleteProjectFiles 自己就切了 IO（LibraryRepository 里那层），
                    // 这里再切一次等于多一跳，而且这一跳会让单测的虚拟时间推不动它
                    // —— LibraryDeleteTest 因此在 CI（Linux release 变体）上偶发失败过。
                    if (!repo.deleteProjectFiles(target.path)) {
                        failed++
                        continue
                    }
                    // 文件都没了，隐藏标记留着没有意义（重扫也不会再出现）
                    hidden = hidden - target.path
                } else {
                    hidden = hidden + target.path
                }
                removed += target.path
            }
            if (removed.isEmpty()) {
                notify(if (failed > 0) "没有可移除的作品（目录不在曲库内）" else "没有选中可移除的作品")
                onDone(emptySet())
                return@launch
            }
            if (hidden != _hidden.value) {
                repo.saveHiddenProjects(hidden)
                _hidden.value = hidden
            }
            // 删了文件的这批必须重扫：`reapply()` 只按隐藏标记过滤，看不到「目录已经不在」，
            // 不重扫的话列表里会留着刚删掉的作品（提示说已删除、列表还在）。
            // 只移出索引的那批不用扫，过滤隐藏标记就够了。
            if (deleteFiles) rescan() else reapply()
            if (_selectedProject.value in removed) _selectedProject.value = null
            val tail = if (failed > 0) "（" + failed + " 部失败）" else ""
            notify(
                if (deleteFiles) {
                    "已删除 " + removed.size + " 部作品和本地文件" + tail
                } else {
                    "已从曲库移除 " + removed.size + " 部，本地文件保留（设置里可恢复）" + tail
                },
            )
            onDone(removed)
        }
    }

    /** 把「只移除索引」的作品放回曲库。 */
    fun restoreProject(path: String) {
        scope.launch {
            val next = _hidden.value - path
            repo.saveHiddenProjects(next)
            _hidden.value = next
            reapply()
            notify("已恢复到曲库")
        }
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
        val hidden = _hidden.value
        fun hiddenFolder(folderPath: String): Boolean {
            val root = ProjectGrouper.projectRootOf(scan.rootPath, folderPath)
            return hidden.any { root == it || root.startsWith(it + File.separator) }
        }
        // 先过滤曲目/压缩包，再按剩下的重新分组，项目数、集数才不会算进已移除的作品。
        val tracks = ManualLinks.apply(scan.tracks, links).filterNot { hiddenFolder(it.folderPath) }
        val archives = scan.archives.filterNot { hiddenFolder(it.folderPath) }
        return scan.copy(
            tracks = tracks,
            archives = archives,
            projects = ProjectGrouper.group(scan.rootPath, tracks, archives),
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
