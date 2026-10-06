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
