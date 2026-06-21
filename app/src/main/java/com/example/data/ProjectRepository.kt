package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: BAProjectDao) {
    val allProjects: Flow<List<BAProject>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Int): BAProject? {
        return projectDao.getProjectById(id)
    }

    suspend fun insert(project: BAProject): Long {
        return projectDao.insertProject(project)
    }

    suspend fun delete(project: BAProject) {
        projectDao.deleteProject(project)
    }

    suspend fun deleteById(id: Int) {
        projectDao.deleteProjectById(id)
    }
}
