package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "before_after_projects")
data class BAProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String,
    val beforePath: String, // Internal storage path for before image
    val afterPath: String,   // Internal storage path for after image
    val logoPath: String?,  // Internal storage path for optional logo image
    val businessName: String,
    val slogan: String,
    val beforeLabel: String = "BEFORE",
    val afterLabel: String = "AFTER",
    val bgColorInt: Int = 0xFF1B1D1F.toInt(),
    val accentColorIndex: Int = 0,
    val layoutVersion: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
