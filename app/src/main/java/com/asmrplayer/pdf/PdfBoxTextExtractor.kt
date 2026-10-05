package com.asmrplayer.pdf

import android.content.Context
import com.asmrplayer.core.PdfTextExtractor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** 用 pdfbox-android 从台本 PDF 中抽取文本。 */
class PdfBoxTextExtractor(@Suppress("unused") private val context: Context) : PdfTextExtractor {

    override fun extract(file: File): String? = try {
        PDDocument.load(file).use { doc ->
            if (doc.isEncrypted) null
            else PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }
    } catch (e: Throwable) {
        null
    }
}
