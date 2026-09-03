package com.alam.scratchpad

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal object EditablePdf {
    private const val PAYLOAD_NAME = "scratchpad.scratchpad"
    private const val MAX_PAYLOAD_BYTES = 64 * 1024 * 1024

    fun embed(pdf: ByteArray, project: ByteArray, output: OutputStream) {
        if (project.size !in 1..MAX_PAYLOAD_BYTES) throw IOException("Editable payload is too large")
        PDDocument.load(ByteArrayInputStream(pdf)).use { document ->
            val specification = PDComplexFileSpecification().apply {
                file = PAYLOAD_NAME
                fileUnicode = PAYLOAD_NAME
                embeddedFile = PDEmbeddedFile(document, ByteArrayInputStream(project)).apply {
                    subtype = DrawingProjectCodec.MIME_TYPE
                    size = project.size
                }
                cosObject.setName("AFRelationship", "Data")
            }
            val embeddedFiles = PDEmbeddedFilesNameTreeNode().apply {
                names = mapOf(PAYLOAD_NAME to specification)
            }
            val catalog = document.documentCatalog
            catalog.names = PDDocumentNameDictionary(catalog).apply {
                this.embeddedFiles = embeddedFiles
            }
            catalog.cosObject.setItem(COSName.getPDFName("AF"), COSArray().apply {
                add(specification.cosObject)
            })
            document.save(output)
        }
    }

    fun readProject(input: InputStream): DrawingProject? = PDDocument.load(input).use { document ->
        val files = PDDocumentNameDictionary(document.documentCatalog).embeddedFiles ?: return null
        val specification = files.names?.get(PAYLOAD_NAME) ?: return null
        val embedded = specification.embeddedFile ?: return null
        if (embedded.size !in 1..MAX_PAYLOAD_BYTES) throw IOException("Invalid editable payload size")
        embedded.createInputStream().use(DrawingProjectCodec::read)
    }
}
