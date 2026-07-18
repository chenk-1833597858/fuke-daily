package com.fuke.daily.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fuke.daily.data.model.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ═══════════════════════════════════════════════════
//  Room 数据库
// ═══════════════════════════════════════════════════

@Database(
    entities = [
        MainList::class,
        SubList::class,
        ContentConfig::class,
        OptionButton::class,
        RichText::class,
        MainlineBranch::class,
        MainlineItem::class,
        QuizGroup::class,
        QuizCard::class,
        LinkRecord::class,
        TimerItem::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = false,
)
@TypeConverters(ListTypeConverters::class, TimerTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mainListDao(): MainListDao
    abstract fun timerDao(): TimerDao
    abstract fun linkHistoryDao(): LinkHistoryDao

    /**
     * 关闭数据库连接，触发WAL自动checkpoint
     * 导出数据库前调用，确保导出的文件包含最新数据
     * 调用后需要重新获取数据库实例（Room会自动重新打开）
     */
    fun closeForCheckpoint() {
        openHelper.close()
    }
}

// ═══════════════════════════════════════════════════
//  数据库迁移
// ═══════════════════════════════════════════════════

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 添加新字段到 timers 表
        db.execSQL("ALTER TABLE timers ADD COLUMN type TEXT NOT NULL DEFAULT 'ALARM'")
        db.execSQL("ALTER TABLE timers ADD COLUMN selectedDays TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE timers ADD COLUMN startHour INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timers ADD COLUMN startMinute INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timers ADD COLUMN endHour INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timers ADD COLUMN endMinute INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timers ADD COLUMN reminderSubType TEXT NOT NULL DEFAULT 'LOOP'")
        db.execSQL("ALTER TABLE timers ADD COLUMN intervalMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timers ADD COLUMN count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timers ADD COLUMN alarmEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE timers ADD COLUMN vibrationEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE timers ADD COLUMN floatingWindowEnabled INTEGER NOT NULL DEFAULT 1")
        // 迁移旧字段：linkedListId → linkedProjectId
        db.execSQL("ALTER TABLE timers ADD COLUMN linkedProjectId INTEGER NOT NULL DEFAULT 0")
        // 复制旧数据
        db.execSQL("UPDATE timers SET linkedProjectId = linkedListId")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE main_lists ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE mainline_items ADD COLUMN isCurrent INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE timers ADD COLUMN nextTriggerTime INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timers ADD COLUMN lastTriggerTime INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE timers ADD COLUMN reminderCurrentCount INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 添加颜色字段到 content_configs 表
        db.execSQL("ALTER TABLE content_configs ADD COLUMN input1TextColor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE content_configs ADD COLUMN input1RefColor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE content_configs ADD COLUMN input2TextColor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE content_configs ADD COLUMN input2RefColor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE content_configs ADD COLUMN input3TextColor TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE content_configs ADD COLUMN input3RefColor TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 添加轮播速度字段到 sub_lists 表（如果不存在）
        MigrationUtils.addColumnIfNotExists(
            db, "sub_lists", "carouselInterval",
            "INTEGER NOT NULL", "0"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 添加响铃时长字段（如果之前版本没有）
        MigrationUtils.addColumnIfNotExists(
            db, "timers", "alarmDuration",
            "INTEGER NOT NULL", "20"
        )
        // 添加随机间隔模式字段到 timers 表
        MigrationUtils.addColumnIfNotExists(
            db, "timers", "randomBaseInterval",
            "INTEGER NOT NULL", "1"
        )
        MigrationUtils.addColumnIfNotExists(
            db, "timers", "randomMinMultiplier",
            "INTEGER NOT NULL", "1"
        )
        MigrationUtils.addColumnIfNotExists(
            db, "timers", "randomMaxMultiplier",
            "INTEGER NOT NULL", "10"
        )
        MigrationUtils.addColumnIfNotExists(
            db, "timers", "isAllDay",
            "INTEGER NOT NULL", "1"
        )
        // 添加暂停功能字段到 timers 表
        MigrationUtils.addColumnIfNotExists(
            db, "timers", "isPaused",
            "INTEGER NOT NULL", "0"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 添加随机间隔子模式字段
        MigrationUtils.addColumnIfNotExists(
            db, "timers", "randomSubType",
            "TEXT NOT NULL", "'LOOP'"
        )
        // 添加随机间隔次数字段
        MigrationUtils.addColumnIfNotExists(
            db, "timers", "randomCount",
            "INTEGER NOT NULL", "0"
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 添加结束时间是否次日字段
        MigrationUtils.addColumnIfNotExists(
            db, "timers", "endIsNextDay",
            "INTEGER NOT NULL", "0"
        )
    }
}

// ═══════════════════════════════════════════════════
//  Hilt 模块 — 提供 Database 和 DAO 实例
// ═══════════════════════════════════════════════════

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fuke-daily-db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMainListDao(db: AppDatabase): MainListDao = db.mainListDao()

    @Provides
    fun provideTimerDao(db: AppDatabase): TimerDao = db.timerDao()

    @Provides
    fun provideLinkHistoryDao(db: AppDatabase): LinkHistoryDao = db.linkHistoryDao()
}
