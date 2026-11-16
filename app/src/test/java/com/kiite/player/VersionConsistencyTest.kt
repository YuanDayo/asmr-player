package com.kiite.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本一致性护栏（见 docs/RELEASE-SPEC.md）。
 *
 * 历史上 `AppInfo.VERSION_NAME` 被硬编码成 "2.5.0-beta2"，安装包已经是 2.5.1-beta3，
 * 结果设置页、更新日志标题、检查更新提示全都显示旧版本号。这组测试把它钉死。
 */
class VersionConsistencyTest {

    @Test
    fun `应用内版本号来自 buildConfig`() {
        assertEquals(BuildConfig.VERSION_NAME, AppInfo.VERSION_NAME)
        assertEquals(BuildConfig.BUILD_TYPE, AppInfo.BUILD_TYPE)
    }

    @Test
    fun `版本号符合 semver 与预发布后缀写法`() {
        val pattern = Regex("""^\d+\.\d+(\.\d+)?(-(beta|alpha|rc)\d+)?$""")
        assertTrue("版本号格式不合规：" + AppInfo.VERSION_NAME, pattern.matches(AppInfo.VERSION_NAME))
    }

    @Test
    fun `CHANGELOG 有当前版本段落`() {
        val changelog = findUpwards("CHANGELOG.md")
        assertTrue("找不到 CHANGELOG.md", changelog != null)
        val text = changelog!!.readText(Charsets.UTF_8)
        assertTrue(
            "CHANGELOG.md 缺少 ## [" + AppInfo.VERSION_NAME + "] 段落（标题必须与版本号一致）",
            text.contains("## [" + AppInfo.VERSION_NAME + "]"),
        )
    }

    /** 单元测试的工作目录是模块目录（app/），CHANGELOG 在仓库根目录。 */
    private fun findUpwards(name: String): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(4) {
            val candidate = dir?.let { File(it, name) }
            if (candidate?.isFile == true) return candidate
            dir = dir?.parentFile
        }
        return null
    }
}
