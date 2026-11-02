package com.mcosta.mybooks.data

import com.mcosta.mybooks.data.db.ShareStateDao
import com.mcosta.mybooks.data.db.ShareStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ShareStateDao]. */
class FakeShareStateDao : ShareStateDao {
    val rows = MutableStateFlow<Map<Long, ShareStateEntity>>(emptyMap())

    override suspend fun get(bookId: Long) = rows.value[bookId]
    override fun observe(bookId: Long): Flow<ShareStateEntity?> = rows.map { it[bookId] }
    override suspend fun upsert(row: ShareStateEntity) {
        rows.value = rows.value + (row.bookId to row)
    }
    override suspend fun delete(bookId: Long) {
        rows.value = rows.value - bookId
    }
    override suspend fun pendingFor(owner: String) = rows.value.values.filter { it.owner == owner && it.pending }.sortedBy { it.bookId }
    override suspend fun all() = rows.value.values.sortedBy { it.bookId }
    override suspend fun adoptOrphans(owner: String) {
        rows.value = rows.value.mapValues { (_, r) -> if (r.owner == "") r.copy(owner = owner) else r }
    }
    override suspend fun deleteForOwner(owner: String) {
        rows.value = rows.value.filterValues { it.owner != owner }
    }
}
