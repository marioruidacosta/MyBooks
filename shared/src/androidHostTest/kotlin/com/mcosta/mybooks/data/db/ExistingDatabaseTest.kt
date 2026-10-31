package com.mcosta.mybooks.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The database already on the phone (v2, created by the Android-only Room) must open unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExistingDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "existing-v2.db"

    @Before
    fun createVersion2DatabaseLikeThePhone() {
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        // Exactly what the released app's Room wrote (shared/schemas/.../2.json).
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `isbn` TEXT, " +
                "`title` TEXT NOT NULL, `authors` TEXT NOT NULL, `publisher` TEXT, `publishedYear` INTEGER, " +
                "`pageCount` INTEGER, `coverUrl` TEXT, `description` TEXT, `status` TEXT NOT NULL, `rating` INTEGER, " +
                "`notes` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL, `dateRead` INTEGER, `searchText` TEXT NOT NULL, " +
                "`dateStarted` INTEGER, `currentPage` INTEGER, `progressPercent` INTEGER)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_isbn` ON `books` (`isbn`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '5511e75e61eb8696627386ad45164148')")
        db.execSQL(
            "INSERT INTO books (isbn, title, authors, status, rating, notes, dateAdded, searchText, dateStarted, currentPage, progressPercent) " +
                "VALUES ('9789726084884', 'A violência e o escárnio', 'Albert Cossery', 'READING', 4, 'n', 10, " +
                "'a violencia e o escarnio\nalbert cossery', 100, 40, 22)"
        )
        db.version = 2
        db.close()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun existingV2DatabaseOpensWithBooks() = runTest {
        val db = createAndroidDatabase(context, name)
        val book = db.bookDao().getAll().single().toBook()
        db.close()
        assertEquals("A violência e o escárnio", book.title)
        assertEquals(ReadingStatus.READING, book.status)
        assertEquals(40, book.currentPage)
        assertEquals(22, book.progressPercent)
    }
}
