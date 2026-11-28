package com.example.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class PdfRecordRepository(private val dao: PdfRecordDao) {

    val recentRecords: Flow<List<PdfRecordEntity>> = dao.observeAll()

    suspend fun getAllSnapshot(): List<PdfRecordEntity> = dao.getAllSnapshot()

    suspend fun insert(record: PdfRecordEntity): Long = dao.insert(record)

    suspend fun getById(id: Long): PdfRecordEntity? = dao.getById(id)

    suspend fun deleteRecord(record: PdfRecordEntity) {
        withContext(Dispatchers.IO) {
            runCatching { File(record.filePath).delete() }
        }
        dao.deleteById(record.id)
    }

    suspend fun renameRecord(record: PdfRecordEntity, requestedName: String): PdfRecordEntity {
        val updated = withContext(Dispatchers.IO) {
            val srcFile = File(record.filePath)
            require(srcFile.exists()) { "PDF file no longer exists." }
            val cleanedStem = requestedName.trim()
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), "_")
                .trim('_')
                .ifBlank { throw IllegalArgumentException("Enter a valid file name.") }
            val parent = srcFile.parentFile ?: throw IllegalStateException("Invalid storage directory.")
            var destFile = File(parent, "$cleanedStem.pdf")
            if (destFile.absolutePath == srcFile.absolutePath) {
                return@withContext record
            }
            var counter = 2
            while (destFile.exists()) {
                destFile = File(parent, "${cleanedStem}_$counter.pdf")
                counter++
            }
            val moved = srcFile.renameTo(destFile) || run {
                srcFile.copyTo(destFile, overwrite = false)
                srcFile.delete()
                destFile.exists()
            }
            check(moved) { "Could not rename file on storage." }
            record.copy(displayName = destFile.name, filePath = destFile.absolutePath)
        }
        dao.updateFileMetadata(updated.id, updated.displayName, updated.filePath)
        return updated
    }

    suspend fun clearAll(pdfsDir: File, cacheDir: File) {
        withContext(Dispatchers.IO) {
            pdfsDir.listFiles()?.forEach { runCatching { it.delete() } }
            cacheDir.listFiles()?.forEach {
                if (it.name.startsWith("picked_") || it.name.startsWith("scan_")) {
                    runCatching { it.deleteRecursively() }
                }
            }
        }
        dao.clearAll()
    }
}
