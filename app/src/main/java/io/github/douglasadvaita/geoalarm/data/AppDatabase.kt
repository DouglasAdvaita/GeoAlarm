package io.github.douglasadvaita.geoalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Alarm::class, TrackDay::class, TrackStay::class, TrackPointRecord::class, TrackArrival::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun trackDao(): TrackDao

    companion object {
        // v3: agrega recorridos sin tocar las alarmas existentes
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_days` (" +
                        "`dayKey` INTEGER NOT NULL, `startLat` REAL NOT NULL, " +
                        "`startLng` REAL NOT NULL, `startMs` INTEGER NOT NULL, " +
                        "`endMs` INTEGER NOT NULL, `distanceM` REAL NOT NULL, " +
                        "`encoded` TEXT NOT NULL, PRIMARY KEY(`dayKey`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_stays` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dayKey` INTEGER NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, " +
                        "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, " +
                        "`radiusM` REAL NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_track_stays_dayKey` ON `track_stays` (`dayKey`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayKey` INTEGER NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `atMs` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_points_dayKey_atMs` ON `track_points` (`dayKey`, `atMs`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `track_arrivals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayKey` INTEGER NOT NULL, `alarmId` INTEGER NOT NULL, `destination` TEXT NOT NULL, `message` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `arrivedAtMs` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_arrivals_dayKey` ON `track_arrivals` (`dayKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_arrivals_arrivedAtMs` ON `track_arrivals` (`arrivedAtMs`)")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geoalarm.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
