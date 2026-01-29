package com.ablematch.able.resume

import kr.dogfoot.hwplib.`object`.bodytext.paragraph.text.HWPCharNormal
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import kr.dogfoot.hwplib.reader.HWPReader
import java.io.InputStream

@Component
class ResumeTextExtractor {

    fun extract(file: MultipartFile): String {
        val name = file.originalFilename?.lowercase() ?: ""

        return when {
            name.endsWith(".pdf") -> extractPdf(file.inputStream)
            name.endsWith(".docx") -> extractDocx(file.inputStream)
            name.endsWith(".hwp") -> extractHwp(file.inputStream)
            else -> throw IllegalArgumentException("지원하지 않는 파일 형식")
        }
    }

    private fun extractPdf(input: InputStream): String {
        PDDocument.load(input).use { doc ->
            return PDFTextStripper().getText(doc)
        }
    }

    private fun extractDocx(input: InputStream): String {
        XWPFDocument(input).use { doc ->
            return doc.paragraphs.joinToString("\n") {
                it.text
            }
        }
    }

    private fun extractHwp(input: InputStream): String {
        val hwp = HWPReader.fromInputStream(input)
        val sb = StringBuilder()

        hwp.bodyText.sectionList.forEach { section ->
            section.paragraphs.forEach { paragraph ->
                val text = paragraph.text ?: return@forEach

                text.charList.forEach { char ->
                    if (char is HWPCharNormal) {
                        sb.append(char.ch)
                    }
                }
                sb.append('\n')
            }
        }
        return sb.toString()
    }

}
