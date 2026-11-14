package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 曲库体检：空作品、可疑小文件、跨作品重复的纯逻辑。 */
class LibraryAuditTest {

    private fun track(name: String, size: Long, path: String = "/lib/A/" + name) = TrackEntry(
        path = path,
        name = name,
        folderPath = "/lib/A",
        folderName = "A",
        sizeBytes = size,
        modifiedMs = 0L,
    )

    private fun project(path: String, name: String, tracks: Int) = ProjectEntry(
        path = path,
        name = name,
        trackCount = tracks,
        scriptCount = 0,
    )

    private fun auditOf(
        projects: List<ProjectEntry>,
        tracks: Map<String, List<TrackEntry>>,
    ): LibraryAudit.Report = LibraryAudit.audit(projects) { tracks[it.path].orEmpty() }

    @Test
    fun `empty project is reported`() {
        val report = auditOf(
            listOf(project("/lib/A", "空作品", 0)),
            mapOf("/lib/A" to emptyList()),
        )
        assertEquals(1, report.issues.count { it.type == LibraryAudit.IssueType.EMPTY_PROJECT })
        assertEquals("空作品", report.issues.first().projectName)
    }

    @Test
    fun `suspicious small file is reported but normal size is not`() {
        val report = auditOf(
            listOf(project("/lib/A", "A", 2)),
            mapOf("/lib/A" to listOf(track("ok.mp3", 5_000_000), track("tiny.mp3", 100))),
        )
        val issues = report.issues.filter { it.type == LibraryAudit.IssueType.SUSPICIOUS_FILE }
        assertEquals(1, issues.size)
        assertTrue(issues.first().detail.contains("tiny.mp3"))
    }

    @Test
    fun `zero byte file is not flagged as suspicious`() {
        // 0 字节通常意味着扫描时读不到，交由其他机制处理，不归「截断」
        val report = auditOf(
            listOf(project("/lib/A", "A", 1)),
            mapOf("/lib/A" to listOf(track("zero.mp3", 0))),
        )
        assertEquals(0, report.issues.filter { it.type == LibraryAudit.IssueType.SUSPICIOUS_FILE }.size)
    }

    @Test
    fun `same file in two projects is a duplicate group`() {
        val report = auditOf(
            listOf(project("/lib/A", "A", 1), project("/lib/B", "B", 1)),
            mapOf(
                "/lib/A" to listOf(track("01 深夜.mp3", 3_000_000)),
                "/lib/B" to listOf(track("01 深夜.mp3", 3_000_000, "/lib/B/01 深夜.mp3")),
            ),
        )
        assertEquals(1, report.duplicates.size)
        val group = report.duplicates.first()
        assertEquals(2, group.files.size)
        assertTrue("同名同大小应判强重复", group.strong)
        assertEquals(
            1,
            report.issues.count { it.type == LibraryAudit.IssueType.LIKELY_DUPLICATE_WORK },
        )
    }

    @Test
    fun `same name different size is weak duplicate`() {
        val report = auditOf(
            listOf(project("/lib/A", "A", 1), project("/lib/B", "B", 1)),
            mapOf(
                "/lib/A" to listOf(track("01.mp3", 3_000_000)),
                "/lib/B" to listOf(track("01.mp3", 2_000_000, "/lib/B/01.mp3")),
            ),
        )
        assertEquals(1, report.duplicates.size)
        assertFalse(report.duplicates.first().strong)
        assertEquals(
            1,
            report.issues.count { it.type == LibraryAudit.IssueType.CROSS_PROJECT_DUPLICATE },
        )
    }

    @Test
    fun `same name inside one project is not a duplicate`() {
        val report = auditOf(
            listOf(project("/lib/A", "A", 2)),
            mapOf("/lib/A" to listOf(track("01.mp3", 3_000_000), track("01.wav", 3_000_000))),
        )
        assertEquals(0, report.duplicates.size)
    }

    @Test
    fun `name matching is whitespace and case insensitive`() {
        val report = auditOf(
            listOf(project("/lib/A", "A", 1), project("/lib/B", "B", 1)),
            mapOf(
                "/lib/A" to listOf(track("Whisper Night.mp3", 3_000_000)),
                "/lib/B" to listOf(track("whisper  night.mp3", 3_000_000, "/lib/B/whisper  night.mp3")),
            ),
        )
        assertEquals(1, report.duplicates.size)
    }

    @Test
    fun `clean library has empty report`() {
        val report = auditOf(
            listOf(project("/lib/A", "A", 1)),
            mapOf("/lib/A" to listOf(track("ok.mp3", 5_000_000))),
        )
        assertTrue(report.empty)
        assertEquals(0, report.totalIssues)
    }

    @Test
    fun `normalizeName strips extension and lowercases`() {
        assertEquals("01 deep sea", LibraryAudit.normalizeName("01 Deep Sea.mp3"))
        assertEquals("01 deep sea", LibraryAudit.normalizeName("01   deep  sea .flac"))
    }
}
