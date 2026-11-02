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

@Database(entities = [BookEntity::class, ShareStateEntity::class], version = 3, exportSchema = true)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun shareStateDao(): ShareStateDao
}

/** Implemented by Room's code generator for each platform. */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Finishes a platform's database builder: migrations, SQLite driver and IO dispatcher. */
fun createDatabase(builder: RoomDatabase.Builder<AppDatabase>, driver: SQLiteDriver): AppDatabase =
    builder
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

/** v3: public review on books, and the community sharing state of each book. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE books ADD COLUMN review TEXT NOT NULL DEFAULT ''")
        // Exactly the table Room generates (schemas/…/3.json).
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `share_state` (`bookId` INTEGER NOT NULL, `shareId` TEXT NOT NULL, " +
                "`owner` TEXT NOT NULL, `hasBefore` INTEGER NOT NULL, `beforeStatus` TEXT, `beforeRating` INTEGER, " +
                "`beforeProgress` INTEGER, `beforeReview` TEXT, `pending` INTEGER NOT NULL, `excluded` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL, PRIMARY KEY(`bookId`))"
        )
    }
}
