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
        NotificationHistoryEntity::class,
        NotificationBlockRuleEntity::class,
        SelectedBluetoothDeviceEntity::class
    ],
    version = 10,
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
    abstract fun notificationHistoryDao(): NotificationHistoryDao
    abstract fun notificationBlockRuleDao(): NotificationBlockRuleDao
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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    ALTER TABLE notification_read_config ADD COLUMN keywordFilter TEXT NOT NULL DEFAULT ''
                """.trimIndent())
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Đổi khóa chính sang "packageName#userId" để tách app Không gian 2
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_read_config_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        packageName TEXT NOT NULL,
                        userId INTEGER NOT NULL DEFAULT 0,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        readMode TEXT NOT NULL DEFAULT 'FULL_CONTENT',
                        keywordFilter TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO notification_read_config_new (id, packageName, userId, isEnabled, readMode, keywordFilter)
                    SELECT packageName || '#0', packageName, 0, isEnabled, readMode, keywordFilter
                    FROM notification_read_config
                """.trimIndent())
                db.execSQL("DROP TABLE notification_read_config")
                db.execSQL("ALTER TABLE notification_read_config_new RENAME TO notification_read_config")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        packageName TEXT NOT NULL,
                        userId INTEGER NOT NULL,
                        appLabel TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        postedAt INTEGER NOT NULL,
                        isSilent INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_history_postedAt ON notification_history(postedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_history_packageName_userId ON notification_history(packageName, userId)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_block_rules (
                        packageName TEXT NOT NULL,
                        userId INTEGER NOT NULL,
                        keyword TEXT NOT NULL,
                        appLabel TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(packageName, userId, keyword)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_block_rules_createdAt ON notification_block_rules(createdAt)")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nobg.db"
                )
                .addMigrations(
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
