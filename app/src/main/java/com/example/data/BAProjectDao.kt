package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BAProjectDao {
    @Query("SELECT * FROM before_after_projects ORDER BY timestamp DESC")
    fun getAllProjects(): Flow<List<BAProject>>

    @Query("SELECT * FROM before_after_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Int): BAProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: BAProject): Long

    @Delete
    suspend fun deleteProject(project: BAProject)

    @Query("DELETE FROM before_after_projects WHERE id = :id")
    suspend fun deleteProjectById(id: Int)
}
