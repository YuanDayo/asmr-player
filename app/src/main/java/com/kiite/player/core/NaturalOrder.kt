package com.kiite.player.core

/** 文件名自然排序：01 < 02 < 10。供曲目、章节、项目三处共用。 */
object NaturalOrder {

    fun compare(a: String, b: String): Int {
        val ka = key(a)
        val kb = key(b)
        val n = minOf(ka.size, kb.size)
        for (i in 0 until n) {
            val x = ka[i]
            val y = kb[i]
            val c = when {
                x is Long && y is Long -> x.compareTo(y)
                x is Long -> -1
                y is Long -> 1
                else -> x.toString().compareTo(y.toString())
            }
            if (c != 0) return c
        }
        return ka.size - kb.size
    }

    /**
     * 路径自然排序：逐段比较，前缀全部相同时短路径在前。
     *
     * 只比最后一级目录名是不够的——总集篇里每个子作品都可能有一模一样的
     * 「1.音声文件」，那样会全部并列、退化成按文件名混排。
     */
    fun comparePath(a: String, b: String): Int {
        val pa = segments(a)
        val pb = segments(b)
        val n = minOf(pa.size, pb.size)
        for (i in 0 until n) {
            val c = compare(pa[i], pb[i])
            if (c != 0) return c
        }
        return pa.size - pb.size
    }

    /** 按 `/` 与 `\` 切段，丢弃空段（开头的盘符/根分隔符不参与比较）。 */
    fun segments(path: String): List<String> =
        path.split('/', '\\').filter { it.isNotEmpty() }

    private fun key(name: String): List<Any> {
        val out = ArrayList<Any>()
        var i = 0
        val s = name.lowercase()
        while (i < s.length) {
            val c = s[i]
            if (c.isDigit()) {
                var j = i
                while (j < s.length && s[j].isDigit()) j++
                out.add(s.substring(i, j).toLongOrNull() ?: 0L)
                i = j
            } else {
                var j = i
                while (j < s.length && !s[j].isDigit()) j++
                out.add(s.substring(i, j))
                i = j
            }
        }
        return out
    }
}
