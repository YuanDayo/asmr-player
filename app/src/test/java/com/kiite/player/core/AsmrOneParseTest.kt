package com.kiite.player.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * asmr.one 搜索结果解析。样例取自真实接口
 * GET https://api.asmr.one/api/search/耳舐め?order=dl_count&sort=desc&page=1&subtitle=0
 * （2026-11 实测字段，多出的字段靠 ignoreUnknownKeys 兜住）。
 */
class AsmrOneParseTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val sample = """
    {
      "works": [
        {
          "id": 285384,
          "title": "オナサポ科のJKママ♪(無料作品)",
          "subtitle": null,
          "circle_id": 50557,
          "name": "犬走り",
          "circle": {"id": 50557, "name": "犬走り", "source_id": "RG50557", "source_type": "DLSITE"},
          "nsfw": true,
          "release": "2020-04-30",
          "create_date": "2020-04-30T00:00+09:00",
          "dl_count": 325154,
          "price": 0,
          "source_id": "RJ285384",
          "source_type": "DLSITE",
          "rate_count": 30813,
          "rate_average_2dp": 4.52,
          "duration": 1609,
          "has_subtitle": false,
          "age_category_string": "adult",
          "vas": [{"id": "89c286a1-b475-5d92-8bdd-c1902ffffff", "name": "一之瀬りと"}, {"id": 2, "name": null}],
          "tags": [{"id": 9, "name": "耳舐め"}],
          "mainCoverUrl": "https://api.asmr.one/api/cover/285384.jpg?type=main",
          "thumbnailCoverUrl": "https://api.asmr.one/api/cover/285384.jpg?type=240x240",
          "userRating": null
        },
        {
          "id": 999999,
          "title": "缺编号的测试作品",
          "name": "のみ残るサークル",
          "vas": [],
          "source_id": "BJ999999"
        }
      ],
      "pagination": {"currentPage": 1, "pageSize": 20, "totalCount": 12666}
    }
    """.trimIndent()

    @Test
    fun `parse works and pagination`() {
        val resp = json.decodeFromString(AsmrOneSearchResponse.serializer(), sample)
        assertEquals(2, resp.works.size)
        assertEquals(12666, resp.pagination?.totalCount)
        assertEquals(1, resp.pagination?.currentPage)
    }

    @Test
    fun `code extracted only from RJ source_id`() {
        val resp = json.decodeFromString(AsmrOneSearchResponse.serializer(), sample)
        val first = resp.works[0]
        assertEquals("RJ285384", first.code)
        // BJ 编号不符合 RJ 格式 → code 为 null（不能进 DLsite 编号目录）
        assertNull(resp.works[1].code)
    }

    @Test
    fun `circle name falls back to top-level name`() {
        val resp = json.decodeFromString(AsmrOneSearchResponse.serializer(), sample)
        assertEquals("犬走り", resp.works[0].circleName)
        assertEquals("のみ残るサークル", resp.works[1].circleName)
    }

    @Test
    fun `va names filter blank`() {
        val resp = json.decodeFromString(AsmrOneSearchResponse.serializer(), sample)
        assertEquals(listOf("一之瀬りと"), resp.works[0].vaNames)
        assertTrue(resp.works[1].vaNames.isEmpty())
    }

    @Test
    fun `uuid ids in vas and circle are ignored`() {
        // 真实接口的 vas[].id / circle[].id 可能是 UUID 字符串（真机踩过：
        // Long 解析报 Unexpected symbol 'c' in numeric literal），模型不带这些字段即可
        val resp = json.decodeFromString(AsmrOneSearchResponse.serializer(), sample)
        assertEquals(285384L, resp.works[0].id)
        assertEquals("犬走り", resp.works[0].circleName)
    }

    @Test
    fun `cover urls kept as-is`() {
        val resp = json.decodeFromString(AsmrOneSearchResponse.serializer(), sample)
        assertEquals(
            "https://api.asmr.one/api/cover/285384.jpg?type=240x240",
            resp.works[0].thumbnailCoverUrl,
        )
    }
}
