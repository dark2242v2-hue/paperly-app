package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PdfRecordEntity::class], version = 1, exportSchema = false)
abstract class PaperlyDatabase : RoomDatabase() {
    abstract fun pdfRecordDao(): PdfRecordDao

    companion object {
        @Volatile
        private var instance: PaperlyDatabase? = null

        fun getInstance(context: Context): PaperlyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PaperlyDatabase::class.java,
                    "paperly_local.db"
                ).build().also { instance = it }
            }
        }
    }
}
