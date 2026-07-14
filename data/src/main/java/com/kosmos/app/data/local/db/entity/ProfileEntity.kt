package com.kosmos.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey
    val id: String, // Single row, e.g. "LOCAL_USER"
    val name: String,
    val style: String,
    val updatedAt: Long
)
