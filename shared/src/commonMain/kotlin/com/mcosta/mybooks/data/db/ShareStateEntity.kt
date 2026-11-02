package com.mcosta.mybooks.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.mcosta.mybooks.domain.ReadingStatus
import kotlinx.coroutines.flow.Flow

/**
 * Community sharing state of one book. No foreign key to books: a deleted book keeps its row until the
 * server has been told to remove it.
 */
@Entity(tableName = "share_state")
data class ShareStateEntity(
    @PrimaryKey val bookId: Long,
    /** The book's permanent id on the server. */
    val shareId: String,
    /** The user id the row belongs to; "" for rows restored from a backup while signed out. */
    val owner: String,
    /** Whether the "before" fields are set: the state before the first shared change, until it's uploaded. */
    val hasBefore: Boolean,
    val beforeStatus: ReadingStatus?,
    val beforeRating: Int?,
    val beforeProgress: Int?,
    val beforeReview: String?,
    /** Waiting to be uploaded (or removed from the server). */
    val pending: Boolean,
    /** "Don't share this book". */
    val excluded: Boolean,
    /** The book was deleted; the row goes once the server has removed it. */
    val deleted: Boolean,
)

@Dao
interface ShareStateDao {
    @Query("SELECT * FROM share_state WHERE bookId = :bookId")
    suspend fun get(bookId: Long): ShareStateEntity?

    @Query("SELECT * FROM share_state WHERE bookId = :bookId")
    fun observe(bookId: Long): Flow<ShareStateEntity?>

    @Upsert
    suspend fun upsert(row: ShareStateEntity)

    @Query("DELETE FROM share_state WHERE bookId = :bookId")
    suspend fun delete(bookId: Long)

    @Query("SELECT * FROM share_state WHERE owner = :owner AND pending = 1 ORDER BY bookId")
    suspend fun pendingFor(owner: String): List<ShareStateEntity>

    @Query("SELECT * FROM share_state ORDER BY bookId")
    suspend fun all(): List<ShareStateEntity>

    @Query("UPDATE share_state SET owner = :owner WHERE owner = ''")
    suspend fun adoptOrphans(owner: String)

    @Query("DELETE FROM share_state WHERE owner = :owner")
    suspend fun deleteForOwner(owner: String)
}
