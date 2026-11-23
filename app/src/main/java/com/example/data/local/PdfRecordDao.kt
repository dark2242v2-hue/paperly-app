package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfRecordDao {
    @Query("SELECT * FROM pdf_records ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<PdfRecordEntity>>

    @Query("SELECT * FROM pdf_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PdfRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PdfRecordEntity): Long

    @Query("DELETE FROM pdf_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pdf_records")
    suspend fun clearAll()
}
