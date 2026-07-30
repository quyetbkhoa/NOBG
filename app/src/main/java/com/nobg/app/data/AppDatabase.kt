package com.nobg.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromMode(mode: NobgMode): String = mode.name

    @TypeConverter
    fun toMode(value: String): NobgMode = try {
        NobgMode.valueOf(value)
    } catch (e: Exception) {
        NobgMode.STANDARD
    }

    @TypeConverter
    fun fromNotifReadMode(mode: NotificationReadMode): String = mode.name

    @TypeConverter
    fun toNotifReadMode(value: String): NotificationReadMode = try {
        NotificationReadMode.valueOf(value)
    } catch (e: Exception) {
        NotificationReadMode.FULL_CONTENT
    }
}

@Database(
    entities = [
        AppEntity::class,
        BackupEntity::class,
        BatteryLogEntity::class,
        ChargingSessionEntity::class,
        CpuLogEntity::class,
        NotificationReadConfigEntity::class,
        SelectedBluetoothDeviceEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun backupDao(): BackupDao
    abstract fun batteryLogDao(): BatteryLogDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun cpuLogDao(): CpuLogDao
    abstract fun notificationReadDao(): NotificationReadDao
    abstract fun bluetoothDeviceDao(): BluetoothDeviceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_read_config (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        readMode TEXT NOT NULL DEFAULT 'FULL_CONTENT'
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS selected_bluetooth_devices (
                        address TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT '',
                        isSelected INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nobg.db"
                )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
