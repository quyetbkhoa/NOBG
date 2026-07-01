package com.nobg.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromMode(mode: NobgMode): String = mode.name

    @TypeConverter
    fun toMode(value: String): NobgMode = try {
        NobgMode.valueOf(value)
    } catch (e: Exception) {
        NobgMode.STANDARD
    }
}

@Database(entities = [AppEntity::class, BackupEntity::class, BatteryLogEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun backupDao(): BackupDao
    abstract fun batteryLogDao(): BatteryLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nobg.db"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
