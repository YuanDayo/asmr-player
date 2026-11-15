package com.kiite.player.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * asmr.one 文件树 → 待下载音频列表的挑选逻辑（AsmrOneTree）。
 *
 * 样例严格照 kikoeru-express 的 toTree()（filesystem/utils.js）真实结构：
 *   GET /api/tracks/{id} 顶层是**数组**，作品目录里的顶级条目直接排在里面，没有作品根节点；
 *   文件夹 {"type":"folder","title":"mp3","children":[...]}
 *   文件   {"type":"audio","hash":"12345/0","title":"01.mp3","workTitle":…,
 *           "mediaDownloadUrl":"/api/media/download/12345/0"}
 * 注意：**没有 name，也没有 source**——这正是「真机里文件点不动、文件夹共 0 个」的根因。
 */
class AsmrOneTreeTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val sample = """
    [
      {"type":"folder","title":"mp3","children":[
        {"type":"audio","hash":"12345/0","title":"01.mp3","workTitle":"作品",
         "mediaStreamUrl":"/api/media/stream/12345/0","mediaDownloadUrl":"/api/media/download/12345/0"},
        {"type":"audio","hash":"12345/1","title":"02.mp3","workTitle":"作品",
         "mediaStreamUrl":"/api/media/stream/12345/1","mediaDownloadUrl":"/api/media/download/12345/1"},
        {"type":"audio","hash":"12345/2","title":"01.wav","workTitle":"作品",
         "mediaStreamUrl":"/api/media/stream/12345/2","mediaDownloadUrl":"/api/media/download/12345/2"}
      ]},
      {"type":"folder","title":"SE","children":[
        {"type":"audio","hash":"12345/3","title":"se01.ogg","workTitle":"作品",
         "mediaDownloadUrl":"/api/media/download/12345/3"}
      ]},
      {"type":"folder","title":"插画","children":[
        {"type":"image","hash":"12345/4","title":"封面.jpg","workTitle":"作品",
         "mediaDownloadUrl":"/api/media/download/12345/4"}
      ]},
      {"type":"text","hash":"12345/5","title":"readme.txt","workTitle":"作品",
       "mediaDownloadUrl":"/api/media/download/12345/5"}
    ]
    """.trimIndent()

    private fun root() = AsmrOneTree.parseRoot(sample, json)

    @Test
    fun `real kikoeru tree keeps every leaf selectable`() {
        // 回归测试：叶子节点没有 name/source，只有 title + hash。
        // 修之前 source 全为 null → 点击文件没反应、文件夹「共 0 个」。
        val r = root()
        assertEquals(4, r.children.size)
        val leaves = AsmrOneTree.leaves(r)
        assertEquals(6, leaves.size)
        leaves.forEach { assertNotNull(it.source) }
        assertEquals("01.mp3", leaves[0].displayName)
        assertEquals("封面.jpg", leaves[4].displayName)
    }

    @Test
    fun `audio only picks mp3 over wav in same folder`() {
        // mp3 目录里 01.mp3/01.wav/02.mp3 并存 → 只留 mp3；SE 只有 ogg → 保留；图片/txt 排除
        val files = AsmrOneTree.audioFiles(root())
        assertEquals(
            listOf("01.mp3", "02.mp3", "se01.ogg"),
            files.map { it.displayName },
        )
        assertEquals(
            listOf("mp3/01.mp3", "mp3/02.mp3", "SE/se01.ogg"),
            files.map { it.relPath },
        )
    }

    @Test
    fun `download spec prefers mediaDownloadUrl path`() {
        val files = AsmrOneTree.audioFiles(root())
        assertEquals("/api/media/download/12345/0", files[0].source)
        assertEquals("/api/media/download/12345/1", files[1].source)
    }

    @Test
    fun `hash used when no media url`() {
        val body = """
        [
          {"type":"folder","title":"mp3","children":[
            {"type":"audio","hash":"777/0","title":"a.mp3"}
          ]}
        ]
        """.trimIndent()
        val node = AsmrOneTree.audioFiles(AsmrOneTree.parseRoot(body, json)).single()
        assertEquals("@hash:777/0", node.source)
    }

    @Test
    fun `nodes without any url fall back to joined path`() {
        val body = """
        [
          {"type":"folder","title":"mp3","children":[
            {"type":"audio","title":"a.mp3"},
            {"type":"audio","title":"b.mp3"}
          ]}
        ]
        """.trimIndent()
        val r = AsmrOneTree.parseRoot(body, json)
        assertEquals(listOf("mp3/a.mp3", "mp3/b.mp3"), AsmrOneTree.audioFiles(r).map { it.source })
    }

    @Test
    fun `absolute media url kept as is`() {
        val body = """
        [
          {"type":"audio","hash":"9/0","title":"a.mp3",
           "mediaDownloadUrl":"https://cdn.example.com/media/9/0.mp3?sig=x"}
        ]
        """.trimIndent()
        val node = AsmrOneTree.audioFiles(AsmrOneTree.parseRoot(body, json)).single()
        assertEquals("https://cdn.example.com/media/9/0.mp3?sig=x", node.source)
    }

    @Test
    fun `old style name and source still supported`() {
        val body = """
        [
          {"type":"folder","name":"mp3","children":[
            {"type":"audio","name":"01.mp3","source":"mp3/01.mp3"}
          ]}
        ]
        """.trimIndent()
        val node = AsmrOneTree.audioFiles(AsmrOneTree.parseRoot(body, json)).single()
        assertEquals("mp3/01.mp3", node.source)
        assertEquals("mp3/01.mp3", node.relPath)
    }

    @Test
    fun `ext extracted from title`() {
        val node = AsmrOneTreeNode(title = "A.MP3", source = "@hash:1/0")
        assertEquals("mp3", AsmrOneTree.extOf(node))
    }

    @Test
    fun `images and text are not audio`() {
        val r = root()
        val leaves = AsmrOneTree.leaves(r)
        val jpg = leaves.first { it.displayName.endsWith(".jpg") }
        val txt = leaves.first { it.displayName.endsWith(".txt") }
        assertFalse(AsmrOneTree.isAudio(jpg))
        assertFalse(AsmrOneTree.isAudio(txt))
        assertTrue(AsmrOneTree.isAudio(leaves.first { it.displayName.endsWith(".mp3") }))
    }

    @Test
    fun `flac preferred only when no mp3 or wav`() {
        val body = """
        [
          {"type":"folder","title":"d","children":[
            {"type":"audio","hash":"1/0","title":"a.flac"},
            {"type":"audio","hash":"1/1","title":"a.wav"},
            {"type":"audio","hash":"1/2","title":"a.mp3"}
          ]}
        ]
        """.trimIndent()
        assertEquals(
            listOf("a.mp3"),
            AsmrOneTree.audioFiles(AsmrOneTree.parseRoot(body, json)).map { it.displayName },
        )
    }

    @Test
    fun `folder without preferred exts keeps all audio`() {
        val body = """
        [
          {"type":"folder","title":"d","children":[
            {"type":"audio","hash":"1/0","title":"a.ogg"},
            {"type":"audio","hash":"1/1","title":"b.m4a"}
          ]}
        ]
        """.trimIndent()
        assertEquals(
            listOf("a.ogg", "b.m4a"),
            AsmrOneTree.audioFiles(AsmrOneTree.parseRoot(body, json)).map { it.displayName },
        )
    }

    @Test
    fun `tracks response object body still parsed directly`() {
        val body = """{"type":"folder","title":"work","children":[]}"""
        assertEquals("work", AsmrOneTree.parseRoot(body, json).displayName)
    }
}
