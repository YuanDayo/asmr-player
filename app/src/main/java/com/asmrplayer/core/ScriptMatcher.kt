package com.asmrplayer.core

import java.io.File

/**
 * 把台本文件自动关联到音频。
 *
 * 关系分三档（越近分越高）：
 *  - SAME_DIR      同一目录
 *  - ADJACENT      父子目录（台本/、script/ 等）
 *  - SAME_PROJECT  同一「总项目」下的不同子文件夹（音频/ 与 台本/ 并列时靠这一档）
 *
 * 匹配方式：主名完全相同 > 开头编号相同 > 名称包含 > 相似度；
 * 以上都不中时，若整个项目里只有若干「看起来是整目录共用」的台本，就摊给没有强匹配的曲目。
 */
class ScriptMatcher(
    private val minAttachScore: Int = 300,
    private val strongMatchScore: Int = 600,
    private val maxAttachmentsPerTrack: Int = 4,
) {

    private enum class Relation { SAME_DIR, ADJACENT, SAME_PROJECT, UNRELATED }

    private val scriptDirNames = setOf(
        "台本", "台本集", "脚本", "文本", "字幕", "文档", "原文",
        "script", "scripts", "transcript", "transcripts", "subtitle", "subtitles", "subs", "text", "docs", "doc",
    )

    /** 这些名字更像说明文档，不作为「整项目共用台本」摊给所有曲目。 */
    private val docLikeNames = listOf(
        "readme", "license", "licence", "notice", "changelog",
        "说明", "説明", "概要", "注意", "注意事項", "免責", "収録", "トラックリスト", "曲目", "目录",
    )

    /** 三档关系对应的基准分：同目录 > 父子目录 > 同项目。 */
    private fun tier(rel: Relation): Int = when (rel) {
        Relation.SAME_DIR -> 0
        Relation.ADJACENT -> 1
        else -> 2
    }

    fun associate(
        tracks: List<DiscoveredTrack>,
        scripts: List<ScriptRef>,
        rootPath: String = "",
    ): Pair<List<TrackEntry>, List<ScriptRef>> {
        if (tracks.isEmpty()) return emptyList<TrackEntry>() to scripts

        val candidates = HashMap<String, MutableList<ScriptAttachment>>()
        val scriptStrongMatch = HashMap<String, Int>()

        for (track in tracks) {
            for (script in scripts) {
                val score = score(track, script, rootPath)
                if (score < minAttachScore) continue
                candidates.getOrPut(track.path) { ArrayList() }.add(
                    ScriptAttachment(
                        scriptPath = script.path,
                        scriptName = script.name,
                        format = script.format,
                        score = score,
                        reason = reasonFor(score),
                    )
                )
                if (score >= strongMatchScore) {
                    val prev = scriptStrongMatch[script.path] ?: 0
                    if (score > prev) scriptStrongMatch[script.path] = score
                }
            }
        }

        // 兜底：整项目共用的台本，摊给「自身没有强匹配」的曲目
        val scriptsPerDir = scripts.groupingBy { it.folderPath }.eachCount()
        for (track in tracks) {
            val own = candidates[track.path] ?: emptyList()
            if (own.any { it.score >= strongMatchScore }) continue

            val nearby = scripts.filter { s ->
                val rel = relation(track, s, rootPath)
                rel == Relation.SAME_DIR || rel == Relation.ADJACENT || rel == Relation.SAME_PROJECT
            }
            if (nearby.isEmpty()) continue

            val shared = nearby.filter { s ->
                (scriptStrongMatch[s.path] ?: 0) < strongMatchScore &&
                    !isDocLike(s.name) &&
                    (
                        // 与音频同目录的台本一律算本曲目的台本
                        relation(track, s, rootPath) == Relation.SAME_DIR ||
                            inScriptDir(s) ||
                            (scriptsPerDir[s.folderPath] ?: 0) == 1
                        )
            }
            if (shared.isEmpty()) continue

            val bucket = candidates.getOrPut(track.path) { ArrayList() }
            for (s in shared) {
                if (bucket.any { it.scriptPath == s.path }) continue
                bucket.add(
                    ScriptAttachment(
                        scriptPath = s.path,
                        scriptName = s.name,
                        format = s.format,
                        score = 420,
                        reason = "整项目共用台本",
                    )
                )
            }
        }

        val result = tracks.map { track ->
            val atts = (candidates[track.path] ?: emptyList())
                .sortedWith(compareByDescending<ScriptAttachment> { it.score }.thenBy { it.scriptName.lowercase() })
                .take(maxAttachmentsPerTrack)
            TrackEntry(
                path = track.path,
                name = track.name,
                folderPath = track.folderPath,
                folderName = track.folderName,
                sizeBytes = track.sizeBytes,
                modifiedMs = track.modifiedMs,
                scripts = atts,
            )
        }

        val usedScripts = result.flatMap { it.scripts }.map { it.scriptPath }.toHashSet()
        val orphans = scripts.filter { it.path !in usedScripts }
        return result to orphans
    }

    private fun isUnder(root: String, folder: String): Boolean {
        val sep = File.separator
        return folder == root || folder.startsWith(root + sep)
    }

    /** 同一「总项目」= 曲库根目录下同一个第一级子目录。 */
    private fun sameProject(rootPath: String, a: String, b: String): Boolean {
        val root = rootPath.trimEnd(File.separatorChar)
        if (root.isEmpty()) return false
        if (!isUnder(root, a) || !isUnder(root, b)) return false
        return ProjectGrouper.projectRootOf(root, a) == ProjectGrouper.projectRootOf(root, b)
    }

    private fun relation(track: DiscoveredTrack, script: ScriptRef, rootPath: String): Relation {
        val sep = File.separator
        val tf = track.folderPath
        val sf = script.folderPath
        if (tf == sf) return Relation.SAME_DIR
        if (sf.startsWith(tf + sep) && !sf.removePrefix(tf + sep).contains(sep)) return Relation.ADJACENT
        if (tf.startsWith(sf + sep) && !tf.removePrefix(sf + sep).contains(sep)) return Relation.ADJACENT
        if (sameProject(rootPath, tf, sf)) return Relation.SAME_PROJECT
        return Relation.UNRELATED
    }

    private fun inScriptDir(script: ScriptRef): Boolean {
        val parts = script.folderPath.split(File.separatorChar)
        return parts.any { it.lowercase() in scriptDirNames }
    }

    private fun isDocLike(name: String): Boolean {
        val n = name.lowercase()
        return docLikeNames.any { n.contains(it) }
    }

    private fun score(track: DiscoveredTrack, script: ScriptRef, rootPath: String): Int {
        val rel = relation(track, script, rootPath)
        if (rel == Relation.UNRELATED) return 0
        val t = tier(rel)

        val trackNorm = TextUtils.normalizeForMatch(track.name)
        val scriptNorm = TextUtils.normalizeForMatch(script.name)

        var score = 0
        var exact = false

        if (trackNorm.isNotEmpty() && trackNorm == scriptNorm) {
            score = intArrayOf(1000, 940, 880)[t]
            exact = true
        }

        val trackNumber = TextUtils.leadingNumber(track.name)
        val scriptNumber = TextUtils.leadingNumber(script.baseName)
        // 双方都有编号且不同（01 对 02）→ 不同曲目，相似度兜底必须失效
        val numbersConflict = trackNumber != null && scriptNumber != null && trackNumber != scriptNumber

        if (!exact && !numbersConflict && trackNumber != null && trackNumber == scriptNumber) {
            score = intArrayOf(880, 840, 790)[t]
        }

        if (!exact && score == 0 && !numbersConflict) {
            if (trackNorm.isNotEmpty() && scriptNorm.isNotEmpty()) {
                val shorter = if (trackNorm.length <= scriptNorm.length) trackNorm else scriptNorm
                val longer = if (shorter == trackNorm) scriptNorm else trackNorm
                if (shorter.length >= 4 && longer.contains(shorter)) {
                    score = intArrayOf(820, 780, 730)[t]
                } else {
                    val sim = TextUtils.similarity(trackNorm, scriptNorm)
                    if (sim >= 0.70) score = intArrayOf(500, 450, 400)[t] + (sim * 100).toInt()
                }
            }
        }

        if (score == 0) return 0

        if (script.format.isTimed) score += 40
        if (inScriptDir(script)) score += 30
        if (isMainScriptName(script.name)) score += 25
        return score
    }

    private fun isMainScriptName(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("台本") || n.contains("script") || n.contains("原文") || n.contains("全篇") || n.contains("全文")
    }

    private fun reasonFor(score: Int): String = when {
        score >= 1000 -> "同名文件"
        score >= 880 -> "同编号"
        score >= 780 -> "编号匹配"
        score >= 700 -> "名称包含"
        score >= 400 -> "名称相似"
        else -> "整项目共用台本"
    }
}
