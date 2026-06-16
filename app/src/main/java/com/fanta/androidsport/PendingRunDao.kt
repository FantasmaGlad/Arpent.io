package com.fanta.androidsport

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingRunDao {
    @Insert
    fun insertRun(run: PendingRunEntity): Long

    @Query("SELECT * FROM pending_runs ORDER BY id ASC")
    fun getAllRuns(): List<PendingRunEntity>

    @Query("DELETE FROM pending_runs WHERE id = :id")
    fun deleteRunById(id: Long): Int
}
