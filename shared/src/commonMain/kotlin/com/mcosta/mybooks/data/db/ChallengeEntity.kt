package com.mcosta.mybooks.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A year's reading challenge: read [goal] books in [year]. */
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val year: Int,
    val goal: Int,
)

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges ORDER BY year DESC")
    fun observeAll(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges ORDER BY year DESC")
    suspend fun getAll(): List<ChallengeEntity>

    @Upsert
    suspend fun upsert(challenge: ChallengeEntity)

    @Query("DELETE FROM challenges WHERE year = :year")
    suspend fun delete(year: Int)
}
