package com.kiite.player.core

/**
 * 严格 SemVer 解析与比较，规则与 `scripts/semver.py` 保持一致。
 *
 * 为什么不用「逐段取数字」的老写法：`X.Y.Z-betaN` 与 `X.Y.Z` 会被当成同一个版本，
 * 于是正式版发出来时预发布用户收不到提示，反过来正式版用户也可能被推预发布包。
 *
 * 兼容性：历史 Tag 里出现过 `2.0beta1` 这种不合规写法，解析失败时退回宽松的数字比较
 * （`compareLenient`），保证旧版本号不会因为解析异常而误判。
 */
object SemVer {
    data class Version(val core: List<Int>, val prerelease: List<String>?) {
        val isPrerelease: Boolean get() = prerelease != null
    }

    private val STRICT = Regex(
        "^(\\d+)\\.(\\d+)\\.(\\d+)" +
            "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" +
            "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
    )

    private val LEGACY_PRERELEASE = Regex("beta|alpha|rc", RegexOption.IGNORE_CASE)

    /** 去掉首尾空白与开头的 v，`vX.Y.Z-betaN` → `X.Y.Z-betaN`。 */
    fun normalize(raw: String): String = raw.trim().removePrefix("v").trim()

    fun parse(raw: String): Version? {
        val m = STRICT.matchEntire(normalize(raw)) ?: return null
        val pre = m.groupValues[4]
        return Version(
            listOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()),
            if (pre.isBlank()) null else pre.split('.'),
        )
    }

    fun isPrerelease(raw: String): Boolean {
        val parsed = parse(raw)
        return parsed?.isPrerelease ?: LEGACY_PRERELEASE.containsMatchIn(normalize(raw))
    }

    /** a 比 b 旧返回 -1，相同返回 0，更新返回 1。 */
    fun compare(a: String, b: String): Int {
        val va = parse(a)
        val vb = parse(b)
        return if (va != null && vb != null) compareVersions(va, vb) else compareLenient(a, b)
    }

    internal fun compareVersions(a: Version, b: Version): Int {
        for (i in 0 until 3) {
            val x = a.core[i]
            val y = b.core[i]
            if (x != y) return if (x > y) 1 else -1
        }
        val pa = a.prerelease
        val pb = b.prerelease
        if (pa == null && pb == null) return 0
        if (pa == null) return 1 // 正式版大于同核心版本的预发布
        if (pb == null) return -1
        for (i in 0 until minOf(pa.size, pb.size)) {
            val x = pa[i]
            val y = pb[i]
            if (x == y) continue
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            if (nx != null && ny != null) return if (nx > ny) 1 else -1
            if (nx != null) return -1 // 数字标识符小于字母标识符
            if (ny != null) return 1
            return if (x > y) 1 else -1
        }
        return if (pa.size > pb.size) 1 else if (pa.size < pb.size) -1 else 0
    }

    /** 宽松比较：只取每段里的数字，专治 `1.6`、`2.0beta1` 这类历史写法。 */
    internal fun compareLenient(a: String, b: String): Int {
        val pa = segments(a)
        val pb = segments(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    private fun segments(raw: String): List<Int> =
        normalize(raw).split('.').map { seg -> seg.filter { it.isDigit() }.toIntOrNull() ?: 0 }
}
