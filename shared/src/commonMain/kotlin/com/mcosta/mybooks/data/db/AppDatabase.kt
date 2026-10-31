package com.mcosta.mybooks.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(entities = [BookEntity::class], version = 2, exportSchema = true)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}

/** Implemented by Room's code generator for each platform. */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Finishes a platform's database builder: migrations, SQLite driver and IO dispatcher. */
fun createDatabase(builder: RoomDatabase.Builder<AppDatabase>, driver: SQLiteDriver): AppDatabase =
    builder
        .addMigrations(MIGRATION_1_2)
        .setDriver(driver)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** v2: reading start date and progress; old Open Library cover-by-ISBN links ask for a 404 instead of a blank GIF. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE books ADD COLUMN dateStarted INTEGER")
        connection.execSQL("ALTER TABLE books ADD COLUMN currentPage INTEGER")
        connection.execSQL("ALTER TABLE books ADD COLUMN progressPercent INTEGER")
        connection.execSQL(
            "UPDATE books SET coverUrl = coverUrl || '?default=false' " +
                "WHERE coverUrl LIKE 'https://covers.openlibrary.org/b/isbn/%' AND coverUrl NOT LIKE '%?%'"
        )
    }
}
