package one.only.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import one.only.player.core.database.dao.DirectoryDao
import one.only.player.core.database.dao.FavoriteItemDao
import one.only.player.core.database.dao.MediumDao
import one.only.player.core.database.dao.MediumStateDao
import one.only.player.core.database.dao.PlaybackMarkDao
import one.only.player.core.database.dao.RemoteServerDao
import one.only.player.core.database.entities.AudioStreamInfoEntity
import one.only.player.core.database.entities.DirectoryEntity
import one.only.player.core.database.entities.FavoriteItemEntity
import one.only.player.core.database.entities.MediumEntity
import one.only.player.core.database.entities.MediumStateEntity
import one.only.player.core.database.entities.PlaybackMarkEntity
import one.only.player.core.database.entities.RemoteServerEntity
import one.only.player.core.database.entities.SubtitleStreamInfoEntity
import one.only.player.core.database.entities.VideoStreamInfoEntity

@Database(
    entities = [
        DirectoryEntity::class,
        MediumEntity::class,
        MediumStateEntity::class,
        VideoStreamInfoEntity::class,
        AudioStreamInfoEntity::class,
        SubtitleStreamInfoEntity::class,
        RemoteServerEntity::class,
        FavoriteItemEntity::class,
        PlaybackMarkEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun mediumDao(): MediumDao

    abstract fun mediumStateDao(): MediumStateDao

    abstract fun directoryDao(): DirectoryDao

    abstract fun remoteServerDao(): RemoteServerDao

    abstract fun favoriteItemDao(): FavoriteItemDao

    abstract fun playbackMarkDao(): PlaybackMarkDao

    companion object {
        const val DATABASE_NAME = "media_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建 media_state 新表
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_state` (
                        `uri` TEXT NOT NULL, 
                        `playback_position` INTEGER NOT NULL DEFAULT 0, 
                        `audio_track_index` INTEGER, 
                        `subtitle_track_index` INTEGER, 
                        `playback_speed` REAL, 
                        `last_played_time` INTEGER, 
                        `external_subs` TEXT NOT NULL DEFAULT '', 
                        `video_scale` REAL NOT NULL DEFAULT 1, 
                        PRIMARY KEY(`uri`)
                    )
                    """,
                )

                // 为 uri 列创建索引
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_state_uri` ON `media_state` (`uri`)
                    """,
                )

                // 将 media 表数据迁移到 media_state
                db.execSQL(
                    """
                    INSERT INTO `media_state` (
                        `uri`, 
                        `playback_position`, 
                        `audio_track_index`, 
                        `subtitle_track_index`, 
                        `playback_speed`, 
                        `last_played_time`, 
                        `external_subs`, 
                        `video_scale`
                    ) 
                    SELECT 
                        `uri`, 
                        `playback_position`, 
                        `audio_track_index`, 
                        `subtitle_track_index`, 
                        `playback_speed`, 
                        `last_played_time`, 
                        `external_subs`, 
                        `video_scale` 
                    FROM `media`
                    """,
                )

                // 为新 media 结构创建临时表
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_new` (
                        `uri` TEXT NOT NULL, 
                        `path` TEXT NOT NULL, 
                        `filename` TEXT NOT NULL, 
                        `parent_path` TEXT NOT NULL, 
                        `last_modified` INTEGER NOT NULL, 
                        `size` INTEGER NOT NULL, 
                        `width` INTEGER NOT NULL, 
                        `height` INTEGER NOT NULL, 
                        `duration` INTEGER NOT NULL, 
                        `media_store_id` INTEGER NOT NULL, 
                        `format` TEXT, 
                        `thumbnail_path` TEXT, 
                        PRIMARY KEY(`uri`)
                    )
                    """,
                )

                // 将旧 media 表数据迁移到新表
                db.execSQL(
                    """
                    INSERT INTO `media_new` (
                        `uri`, 
                        `path`, 
                        `filename`, 
                        `parent_path`, 
                        `last_modified`, 
                        `size`, 
                        `width`, 
                        `height`, 
                        `duration`, 
                        `media_store_id`, 
                        `format`, 
                        `thumbnail_path`
                    ) 
                    SELECT 
                        `uri`, 
                        `path`, 
                        `filename`, 
                        `parent_path`, 
                        `last_modified`, 
                        `size`, 
                        `width`, 
                        `height`, 
                        `duration`, 
                        `media_store_id`, 
                        `format`, 
                        `thumbnail_path` 
                    FROM `media`
                    """,
                )

                // 删除旧 media 表
                db.execSQL("DROP TABLE `media`")

                // 将 media_new 重命名为 media
                db.execSQL("ALTER TABLE `media_new` RENAME TO `media`")

                // 重建 media 表索引
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_uri` ON `media` (`uri`)
                    """,
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_path` ON `media` (`path`)
                    """,
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 删除 path 唯一索引
                db.execSQL("DROP INDEX IF EXISTS `index_media_path`")

                // 以非唯一约束重建 path 索引
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_media_path` ON `media` (`path`)
                    """,
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_state` ADD COLUMN `subtitle_delay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `media_state` ADD COLUMN `subtitle_speed` REAL NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_state` ADD COLUMN `is_in_recycle_bin` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_state` ADD COLUMN `original_path` TEXT")
                db.execSQL("ALTER TABLE `media_state` ADD COLUMN `original_parent_path` TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_state` ADD COLUMN `original_file_name` TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `remote_server` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `protocol` TEXT NOT NULL,
                        `host` TEXT NOT NULL,
                        `port` INTEGER,
                        `path` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `password` TEXT NOT NULL,
                        `is_proxy_enabled` INTEGER NOT NULL DEFAULT 0,
                        `proxy_host` TEXT NOT NULL DEFAULT '',
                        `proxy_port` INTEGER
                    )
                    """,
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_item` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `parent_id` INTEGER,
                        `target_type` TEXT NOT NULL,
                        `target_key` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `subtitle` TEXT NOT NULL,
                        `local_uri` TEXT,
                        `local_path` TEXT,
                        `remote_server_id` INTEGER,
                        `remote_protocol` TEXT,
                        `remote_path` TEXT,
                        `remote_server_name` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `sort_order` INTEGER NOT NULL
                    )
                    """,
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorite_item_target_key` ON `favorite_item` (`target_key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_item_parent_id` ON `favorite_item` (`parent_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_item_target_type` ON `favorite_item` (`target_type`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playback_mark` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `media_uri` TEXT NOT NULL,
                        `position_ms` INTEGER NOT NULL,
                        `duration_ms` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """,
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_mark_media_uri_position_ms` ON `playback_mark` (`media_uri`, `position_ms`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_mark_created_at` ON `playback_mark` (`created_at`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_parent_path` ON `media` (`parent_path`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_directories_parent_path` ON `directories` (`parent_path`)")
            }
        }
    }
}
