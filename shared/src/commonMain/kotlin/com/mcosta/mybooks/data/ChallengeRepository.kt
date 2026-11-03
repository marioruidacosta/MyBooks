package com.mcosta.mybooks.data

import com.mcosta.mybooks.data.db.ChallengeDao
import com.mcosta.mybooks.data.db.ChallengeEntity
import com.mcosta.mybooks.domain.Challenge
import com.mcosta.mybooks.domain.MAX_GOAL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Yearly reading challenges, newest year first. One goal per year: setting it again replaces it. */
class ChallengeRepository(private val dao: ChallengeDao) {
    fun observe(): Flow<List<Challenge>> = dao.observeAll().map { list -> list.map { Challenge(it.year, it.goal) } }

    suspend fun all(): List<Challenge> = dao.getAll().map { Challenge(it.year, it.goal) }

    suspend fun setGoal(year: Int, goal: Int) {
        require(goal in 1..MAX_GOAL) { "goal must be 1..$MAX_GOAL" }
        dao.upsert(ChallengeEntity(year, goal))
    }

    suspend fun remove(year: Int) = dao.delete(year)
}
