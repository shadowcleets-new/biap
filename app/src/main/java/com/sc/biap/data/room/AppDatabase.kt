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
        DeletedCustomFieldEntity::class
    ],
    version = 4,
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

        /** Every migration the app knows about. Pass to `addMigrations(*ALL_MIGRATIONS)`. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4)
    }
}
