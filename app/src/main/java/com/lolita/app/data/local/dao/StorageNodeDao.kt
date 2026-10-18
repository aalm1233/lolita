package com.lolita.app.data.local.dao

import androidx.room.*
import com.lolita.app.data.local.entity.StorageNode
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageNodeDao {
    @Query("SELECT * FROM storage_nodes ORDER BY sort_order, name, id")
    fun observeAll(): Flow<List<StorageNode>>

    @Query("SELECT * FROM storage_nodes ORDER BY sort_order, name, id")
    suspend fun getAll(): List<StorageNode>

    @Query("SELECT * FROM storage_nodes WHERE id = :id")
    suspend fun getById(id: Long): StorageNode?

    @Query("SELECT * FROM storage_nodes WHERE location_id = :locationId AND parent_node_id IS :parentNodeId ORDER BY sort_order, name, id")
    fun observeChildren(locationId: Long, parentNodeId: Long?): Flow<List<StorageNode>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(node: StorageNode): Long

    @Update
    suspend fun update(node: StorageNode)

    @Delete
    suspend fun delete(node: StorageNode)

    @Query("DELETE FROM storage_nodes WHERE parent_node_id IS NOT NULL")
    suspend fun deleteAllCompartments()

    @Query("DELETE FROM storage_nodes")
    suspend fun deleteAllRoots()

    @Transaction
    suspend fun deleteAll() {
        deleteAllCompartments()
        deleteAllRoots()
    }
}
