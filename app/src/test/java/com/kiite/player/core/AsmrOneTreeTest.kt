package com.kiite.player.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * asmr.one 文件树 → 待下载音频列表的挑选逻辑（AsmrOneTree）。
 * 样例仿真实接口 GET https://api.asmr.one/api/tree/{id} 的结构：
 * 根节点 name 是作品目录名，audio 节点的 source 是相对作品根的完整路径。
 */
class AsmrOneTreeTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val sample = """
    {
      "name": "復仇式綠奴play～愛的證明篇～",
      "type": "folder",
      "children": [
        {"name": "mp3", "type": "folder", "children": [
          {"type": "audio", "name": "01.mp3", "title": "01", "source": "復仇式綠奴play～愛的證明篇～/mp3/01.mp3"},
          {"type": "audio", "name": "02.mp3", "title": "02", "source": "復仇式綠奴play～愛的證明篇～/mp3/02.mp3"},
          {"type": "audio", "name": "01.wav", "title": "01", "source": "復仇式綠奴play～愛的證明篇～/mp3/01.wav"},
          {"type": "audio", "name": "01.mp3.vtt", "source": "復仇式綠奴play～愛的證明篇～/mp3/01.mp3.vtt"}
        ]},
        {"name": "SE", "type": "folder", "children": [
          {"type": "audio", "name": "se01.ogg", "source": "復仇式綠奴play～愛的證明篇～/SE/se01.ogg"}
        ]},
        {"name": "images", "type": "folder", "children": [
          {"type": "image", "name": "cover.jpg", "source": "復仇式綠奴play～愛的證明篇～/images/cover.jpg"}
        ]},
        {"type": "text", "name": "readme.txt", "source": "復仇式綠奴play～愛的證明篇～/readme.txt"}
      ]
    }
    """.trimIndent()

    @Test
    fun `tree parses with unknown fields ignored`() {
        val tree = json.decodeFromString(AsmrOneTreeNode.serializer(), sample)
        assertEquals("復仇式綠奴play～愛的證明篇～", tree.name)
        assertEquals(4, tree.children.size)
    }

    @Test
    fun `audio only picks mp3 over wav in same folder`() {
        val tree = json.decodeFromString(AsmrOneTreeNode.serializer(), sample)
        val files = AsmrOneTree.audioFiles(tree)
        // mp3 文件夹里 mp3/wav 并存 → 只保留 mp3；.vtt 字幕不是音频；SE 只有 ogg → 保留
        assertEquals(
            listOf(
                "復仇式綠奴play～愛的證明篇～/mp3/01.mp3",
                "復仇式綠奴play～愛的證明篇～/mp3/02.mp3",
                "復仇式綠奴play～愛的證明篇～/SE/se01.ogg",
            ),
            files.map { it.source },
        )
    }

    @Test
    fun `ext extracted from source lowercase`() {
        val node = AsmrOneTreeNode(name = "A.MP3", source = "x/A.MP3")
        assertEquals("mp3", AsmrOneTree.extOf(node))
    }

    @Test
    fun `flac preferred only when no mp3 or wav`() {
        val tree = AsmrOneTreeNode(
            name = "root",
            children = listOf(
                AsmrOneTreeNode(
                    name = "d",
                    children = listOf(
                        AsmrOneTreeNode(type = "audio", source = "root/d/a.flac"),
                        AsmrOneTreeNode(type = "audio", source = "root/d/a.wav"),
                        AsmrOneTreeNode(type = "audio", source = "root/d/a.mp3"),
                    ),
                ),
            ),
        )
        assertEquals(listOf("root/d/a.mp3"), AsmrOneTree.audioFiles(tree).map { it.source })
    }

    @Test
    fun `folder without preferred exts keeps all audio`() {
        val tree = AsmrOneTreeNode(
            name = "root",
            children = listOf(
                AsmrOneTreeNode(
                    name = "d",
                    children = listOf(
                        AsmrOneTreeNode(type = "audio", source = "root/d/a.ogg"),
                        AsmrOneTreeNode(type = "audio", source = "root/d/b.m4a"),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf("root/d/a.ogg", "root/d/b.m4a"),
            AsmrOneTree.audioFiles(tree).map { it.source },
        )
    }

    @Test
    fun `images and text are not audio`() {
        val tree = AsmrOneTreeNode(
            name = "root",
            children = listOf(
                AsmrOneTreeNode(type = "image", source = "root/cover.jpg"),
                AsmrOneTreeNode(type = "text", source = "root/readme.txt"),
            ),
        )
        assertEquals(0, AsmrOneTree.audioFiles(tree).size)
    }

    @Test
    fun `extension-valued type counts as audio`() {
        // 真实接口的文件节点 type 可能直接是 "wav"/"mp3" 这类扩展名（不是 "audio"），
        // 之前只认 type=="audio"/null，导致推荐音频预选为空（真机踩过）
        val tree = AsmrOneTreeNode(
            name = "root",
            children = listOf(
                AsmrOneTreeNode(
                    name = "x",
                    children = listOf(AsmrOneTreeNode(type = "wav", name = "a.wav", source = "root/x/a.wav")),
                ),
                AsmrOneTreeNode(
                    name = "y",
                    children = listOf(AsmrOneTreeNode(type = "mp3", name = "b.mp3", source = "root/y/b.mp3")),
                ),
                AsmrOneTreeNode(type = "text", name = "c.txt", source = "root/c.txt"),
            ),
        )
        assertEquals(
            listOf("root/x/a.wav", "root/y/b.mp3"),
            AsmrOneTree.audioFiles(tree).map { it.source },
        )
    }

    @Test
    fun `tracks response top-level array unwrapped to work root`() {
        // 真实接口 GET /api/tracks/{id} 顶层是数组，作品根目录是第一个元素（真机踩过：
        // 按对象解析报 Expected start of the object '{' but had '['）
        val arrayBody = """
        [
          {
            "type": "folder",
            "title": "复仇式绿奴play",
            "name": "复仇式绿奴play",
            "children": [
              {"type": "audio", "name": "01.mp3", "source": "复仇式绿奴play/mp3/01.mp3"}
            ]
          }
        ]
        """.trimIndent()
        val root = AsmrOneTree.parseRoot(arrayBody, json)
        assertEquals("复仇式绿奴play", root.name)
        assertEquals(
            listOf("复仇式绿奴play/mp3/01.mp3"),
            AsmrOneTree.audioFiles(root).map { it.source },
        )
    }

    @Test
    fun `tracks response multi-element array wrapped in synthetic root`() {
        val body = """
        [
          {"type": "audio", "name": "01.mp3", "source": "work/01.mp3"},
          {"type": "folder", "name": "sub", "children": []}
        ]
        """.trimIndent()
        val root = AsmrOneTree.parseRoot(body, json)
        assertEquals(2, root.children.size)
        assertEquals(listOf("work/01.mp3"), AsmrOneTree.audioFiles(root).map { it.source })
    }

    @Test
    fun `tracks response object body still parsed directly`() {
        val body = """{"type":"folder","name":"work","children":[]}"""
        assertEquals("work", AsmrOneTree.parseRoot(body, json).name)
    }

    @Test
    fun `nodes without source get path joined from folder names`() {
        // 真机 beta9 现场：文件树节点既没有 source 也没有媒体 URL，只有 name。
        // source 全为 null → 勾选集合永远空（「点文件没反应」「点文件夹共 0 个」）。
        // 归一化后用父子文件夹名拼出相对作品根的路径。
        val body = """
        [
          {
            "type": "folder",
            "name": "复仇式绿奴play",
            "children": [
              {"type": "folder", "name": "mp3", "children": [
                {"type": "audio", "name": "01.mp3", "title": "01"},
                {"type": "audio", "name": "02.mp3", "title": "02"}
              ]},
              {"type": "folder", "name": "插画", "children": [
                {"type": "image", "name": "封面.jpg"}
              ]}
            ]
          }
        ]
        """.trimIndent()
        val root = AsmrOneTree.parseRoot(body, json)
        // 单个元素的数组会被解包成作品根（play 文件夹本身）
        val play = root
        val mp3 = play.children[0]
        assertEquals(listOf("mp3/01.mp3", "mp3/02.mp3"), mp3.children.map { it.source })
        assertEquals("插画/封面.jpg", play.children[1].children[0].source)        // 推荐音频默认预选不再为空
        assertEquals(
            listOf("mp3/01.mp3", "mp3/02.mp3"),
            AsmrOneTree.audioFiles(root).map { it.source },
        )
    }

    @Test
    fun `media download url is reduced to relative path`() {
        val body = """
        [
          {
            "type": "folder",
            "name": "work",
            "children": [
              {"type": "audio", "name": "01.mp3",
               "mediaDownloadUrl": "https://api.asmr.one/api/media/1234/mp3/01.mp3?token=x"}
            ]
          }
        ]
        """.trimIndent()
        val root = AsmrOneTree.parseRoot(body, json)
        assertEquals("mp3/01.mp3", root.children[0].source)
    }

    @Test
    fun `explicit source wins over joined path`() {
        val node = AsmrOneTreeNode(
            name = "root",
            children = listOf(
                AsmrOneTreeNode(
                    name = "d",
                    children = listOf(AsmrOneTreeNode(type = "audio", name = "a.mp3", source = "root/d/a.mp3")),
                ),
            ),
        )
        val norm = AsmrOneTree.normalize(node)
        assertEquals("root/d/a.mp3", norm.children[0].children[0].source)
    }
}
