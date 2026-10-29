package io.github.douglasadvaita.geoalarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM track_days WHERE dayKey BETWEEN :from AND :to ORDER BY dayKey")
    fun observeDays(from: Int, to: Int): Flow<List<TrackDay>>

    @Query("SELECT * FROM track_days WHERE dayKey BETWEEN :from AND :to ORDER BY dayKey")
    suspend fun getDays(from: Int, to: Int): List<TrackDay>

    @Query("SELECT * FROM track_days WHERE dayKey = :dayKey")
    suspend fun getDay(dayKey: Int): TrackDay?

    @Query("SELECT * FROM track_stays WHERE dayKey BETWEEN :from AND :to ORDER BY startMs")
    fun observeStays(from: Int, to: Int): Flow<List<TrackStay>>

    @Query("SELECT * FROM track_stays WHERE dayKey BETWEEN :from AND :to ORDER BY startMs")
    suspend fun getStays(from: Int, to: Int): List<TrackStay>

    @Query("SELECT * FROM track_arrivals WHERE dayKey BETWEEN :from AND :to ORDER BY arrivedAtMs")
    fun observeArrivals(from: Int, to: Int): Flow<List<TrackArrival>>

    @Query("SELECT * FROM track_points WHERE dayKey = :dayKey ORDER BY atMs")
    suspend fun getPoints(dayKey: Int): List<TrackPointRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: TrackDay)

    @Insert
    suspend fun insertStay(stay: TrackStay)

    @Insert
    suspend fun insertPoint(point: TrackPointRecord)

    @Insert
    suspend fun insertArrival(arrival: TrackArrival)

    @Query("DELETE FROM track_points WHERE dayKey = :dayKey")
    suspend fun deletePoints(dayKey: Int)
}
