package com.mcosta.mybooks.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        fun create(context: Context, name: String = "mybooks.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** v2: reading start date and progress; old Open Library cover-by-ISBN links ask for a 404 instead of a blank GIF. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN dateStarted INTEGER")
        db.execSQL("ALTER TABLE books ADD COLUMN currentPage INTEGER")
        db.execSQL("ALTER TABLE books ADD COLUMN progressPercent INTEGER")
        db.execSQL(
            "UPDATE books SET coverUrl = coverUrl || '?default=false' " +
                "WHERE coverUrl LIKE 'https://covers.openlibrary.org/b/isbn/%' AND coverUrl NOT LIKE '%?%'"
        )
    }
}
