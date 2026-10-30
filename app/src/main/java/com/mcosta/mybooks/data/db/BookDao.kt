package com.mcosta.mybooks.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    /** [status] null = all books. [query] must already be escaped with [escapeLike]. */
    @Query(
        """
        SELECT * FROM books
        WHERE (:status IS NULL OR status = :status)
          AND (:query = '' OR title LIKE '%' || :query || '%' ESCAPE '\'
                           OR authors LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY title COLLATE NOCASE
        """
    )
    fun observe(status: ReadingStatus?, query: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE isbn = :isbn")
    suspend fun findByIsbn(isbn: String): BookEntity?

    @Query("SELECT * FROM books ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<BookEntity>

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Escapes LIKE wildcards so user search text matches literally (used with ESCAPE '\'). */
fun escapeLike(q: String): String =
    q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
