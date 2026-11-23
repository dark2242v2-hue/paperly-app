package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_records")
data class PdfRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayName: String,
    val filePath: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val originalSizeBytes: Long? = null,
    val operationTag: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
