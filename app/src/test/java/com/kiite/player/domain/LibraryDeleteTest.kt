package com.kiite.player.domain

import androidx.compose.runtime.mutableStateOf
import com.kiite.player.core.ArchiveEntry
import com.kiite.player.core.ParsedScript
import com.kiite.player.core.ProjectEntry
import com.kiite.player.core.ScanResult
import com.kiite.player.core.ScriptFormat
import com.kiite.player.core.TrackEntry
import com.kiite.player.data.AppSettings
import com.kiite.player.data.LibraryDataSource
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 曲库移除作品：隐藏标记要挡住重扫，删除本地文件时只允许动曲库内部目录。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryDeleteTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeRepo(
        private val scanResult: () -> ScanResult,
        private var hiddenPaths: Set<String> = emptySet(),
    ) : LibraryDataSource {
        var deleted: MutableList<String> = mutableListOf()
        var savedHidden: Set<String> = emptySet()

        override suspend fun scan(
            root: File,
            isCancelled: () -> Boolean,
            onProgress: ((com.kiite.player.core.ScanProgress) -> Unit)?,
            previous: ScanResult?,
        ): ScanResult = scanResult()

        override suspend fun loadCache(): ScanResult? = null
        override suspend fun parseScript(path: String): ParsedScript = ParsedScript(ScriptFormat.UNKNOWN, "")
        override suspend fun readEmbeddedLyrics(trackPath: String): String? = null
        override suspend fun countAudioFiles(dir: File): Int = 0
        override fun extractZip(archivePath: String, intoDir: File?): Int = 0
        override suspend fun loadManualLinks(): Map<String, String> = emptyMap()
        override suspend fun saveManualLink(trackPath: String, scriptPath: String) = Unit
        override suspend fun clearManualLink(trackPath: String) = Unit
        override suspend fun loadHiddenProjects(): Set<String> = hiddenPaths
        override suspend fun saveHiddenProjects(paths: Set<String>) { savedHidden = paths }
        override suspend fun deleteProjectFiles(path: String): Boolean {
            deleted.add(path)
            return File(path).deleteRecursively()
        }
    }

    private fun setup(root: File, project: File): Pair<TestScope, Pair<LibraryController, FakeRepo>> {
        val track = TrackEntry(
            path = File(project, "01 测试.mp3").path,
            name = "01 测试.mp3",
            folderPath = project.path,
            folderName = project.name,
            sizeBytes = 1,
            modifiedMs = 0,
        )
        val zip = ArchiveEntry(
            path = File(project, "RJ123 作品.zip").path,
            name = "RJ123 作品.zip",
            folderPath = project.path,
        )
        val result = ScanResult(
            rootPath = root.path,
            scannedAtMs = 0,
            tracks = listOf(track),
            archives = listOf(zip),
        )
        val repo = FakeRepo({ result })
        val scope = TestScope()
        val settings = mutableStateOf(AppSettings(rootPath = root.path))
        val notes = mutableListOf<String>()
        val controller = LibraryController(
            repo = repo,
            scope = scope,
            settings = { settings.value },
            notify = { notes.add(it) },
            setBusy = { _, _ -> },
            onScanReady = {},
        )
        controller.loadHiddenProjects()
        controller.rescan()
        scope.runCurrent()
        return scope to (controller to repo)
    }

    @Test
    fun `隐藏的作品重扫后仍然不在曲库里`() = runTest {
        val root = tmp.newFolder("library")
        val project = tmp.newFolder("library", "RJ001 作品")
        val (scope, pair) = setup(root, project)
        val (controller, repo) = pair
        requireNotNull(controller.scan.value)
        assertEquals(1, controller.scan.value!!.projects.size)

        controller.deleteProject(project.path, deleteFiles = false)
        scope.runCurrent()

        assertTrue(repo.deleted.isEmpty())
        assertTrue(repo.savedHidden.contains(project.canonicalPath))
        assertEquals(0, controller.scan.value!!.projects.size)
        assertEquals(0, controller.scan.value!!.tracks.size)

        // 隐藏记录装载后，再次重扫也不能把它带回来
        controller.rescan()
        scope.runCurrent()
        assertEquals(0, controller.scan.value!!.projects.size)
    }

    @Test
    fun `删本地文件时不允许动根目录本身`() = runTest {
        val root = tmp.newFolder("library2")
        val project = tmp.newFolder("library2", "RJ002 作品")
        val (scope, pair) = setup(root, project)
        val (controller, repo) = pair

        controller.deleteProject(root.path, deleteFiles = true)
        scope.runCurrent()

        assertTrue(repo.deleted.isEmpty())
        assertTrue(root.exists())
        // 根目录之外的路径同样拒绝
        val outside = tmp.newFolder("outside")
        controller.deleteProject(outside.path, deleteFiles = true)
        scope.runCurrent()
        assertTrue(outside.exists())
        assertFalse(repo.savedHidden.isNotEmpty())
    }

    @Test
    fun `恢复后作品回到曲库`() = runTest {
        val root = tmp.newFolder("library3")
        val project = tmp.newFolder("library3", "RJ003 作品")
        val (scope, pair) = setup(root, project)
        val (controller, repo) = pair

        controller.deleteProject(project.path, deleteFiles = false)
        scope.runCurrent()
        assertEquals(0, controller.scan.value!!.projects.size)

        controller.restoreProject(project.canonicalPath)
        scope.runCurrent()
        assertTrue(repo.savedHidden.isEmpty())
        assertEquals(1, controller.scan.value!!.projects.size)
        assertTrue(project.exists())
    }
}
