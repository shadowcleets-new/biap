package com.sc.biap.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DesignEntity::class,
        SizeInventoryEntity::class,
        GlobalCustomFieldEntity::class,
        DeletedDesignEntity::class,
        DeletedCustomFieldEntity::class,
        UserProfileEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun designDao(): DesignDao

    companion object {
        /**
         * v3 -> v4: image pointer columns.
         *
         * `imageRef` is the Storage key of the current image version. Replacing a photo
         * writes a NEW key and repoints this column, so a blob is never overwritten in
         * place - a failed upload cannot destroy the existing image, and the URL changing
         * is what keeps on-device image caches from serving a stale photo forever.
         *
         * `imageUpdatedAt` exists so the image pointer merges INDEPENDENTLY of the rest of
         * the design. Without it, someone editing a price would carry their older photo
         * reference along with the record and clobber a newer photo.
         *
         * Purely additive: three nullable/defaulted columns, no data rewritten, no rows
         * touched. Existing designs keep their local `imagePath` and simply have no remote
         * version yet.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE designs ADD COLUMN imageRef TEXT")
                db.execSQL("ALTER TABLE designs ADD COLUMN imageUpdatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE designs ADD COLUMN imageUpdatedBy TEXT")
            }
        }

        /**
         * v4 -> v5: the signed-in person's own profile.
         *
         * A new table only - nothing existing is read, rewritten or dropped, so a failure
         * here cannot cost a design. One row, keyed by a constant, because the database is
         * already per-identity and therefore already describes exactly one person.
         *
         * No access-level column, deliberately: a role stored in the user's own database is
         * a role the user can edit. That lives in Firestore where only an admin may write it.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profile (
                        id INTEGER NOT NULL PRIMARY KEY,
                        firstName TEXT NOT NULL DEFAULT '',
                        lastName TEXT NOT NULL DEFAULT '',
                        age TEXT NOT NULL DEFAULT '',
                        designation TEXT NOT NULL DEFAULT '',
                        photoRef TEXT,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v5 -> v6: archiving, which replaced deletion as the way to retire a design.
         *
         * Two columns rather than one. `archivedAt` is the state (0 = active);
         * `archiveUpdatedAt` records when that state last changed, so sync can resolve the
         * flag on its own clock. Without the second column, a device editing a price while
         * holding a stale archive state would drag it along and silently un-archive the
         * design - the same trap the image pointer already avoids.
         *
         * Purely additive, defaulted to 0, so every existing design reads as active.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE designs ADD COLUMN archivedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE designs ADD COLUMN archiveUpdatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Every migration the app knows about. Pass to `addMigrations(*ALL_MIGRATIONS)`. */
        val ALL_MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}
