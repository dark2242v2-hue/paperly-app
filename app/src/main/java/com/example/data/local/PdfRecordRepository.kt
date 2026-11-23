package com.example.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class PdfRecordRepository(private val dao: PdfRecordDao) {

    val recentRecords: Flow<List<PdfRecordEntity>> = dao.observeAll()

    suspend fun insert(record: PdfRecordEntity): Long = dao.insert(record)

    suspend fun getById(id: Long): PdfRecordEntity? = dao.getById(id)

    suspend fun deleteRecord(record: PdfRecordEntity) {
        withContext(Dispatchers.IO) {
            runCatching { File(record.filePath).delete() }
        }
        dao.deleteById(record.id)
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
