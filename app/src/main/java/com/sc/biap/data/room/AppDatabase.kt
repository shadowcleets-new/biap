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
        UserProfileEntity::class,
        OrderEntity::class,
        DeletedOrderEntity::class,
        BatchEntity::class,
        KarigarEntity::class,
        DeletedKarigarEntity::class,
        ActivityEntity::class,
        DeletedActivityEntity::class
    ],
    version = 9,
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

        /**
         * v6 -> v7: orders, and the tombstones for cancelled ones.
         *
         * Two new tables only. Nothing existing is read, rewritten or dropped, so a failure
         * here cannot cost a design - the same shape as the profile migration.
         *
         * `lines` is a JSON text column rather than a child table: a line is only ever read
         * as part of its order, never queried on its own, so a foreign key and a cascade
         * rule would be structure with no query behind it.
         *
         * `deleted_orders` exists for the same reason `deleted_designs` does. Cancelling an
         * order without recording that it was cancelled means the next sync finds it still
         * present in the backup and puts it straight back - and, because cancelling returns
         * its pieces to stock, that would double-count the stock too.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS orders (
                        id TEXT NOT NULL PRIMARY KEY,
                        createdAt INTEGER NOT NULL,
                        lines TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS deleted_orders (
                        orderId TEXT NOT NULL PRIMARY KEY,
                        deletedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v7 -> v8: photographs beyond the cover.
         *
         * Two columns, both defaulted, nothing existing read or rewritten. Every design keeps
         * its cover exactly where it was in `imagePath`/`imageRef` and simply gains an empty
         * list of extras - which is also why a build predating this still shows the main
         * photo of every design rather than nothing at all.
         *
         * `extraImagesUpdatedAt` is the second column for the same reason the image and
         * archive pointers each have one: adding a photo and editing a price are separate
         * facts, and whole-record last-write-wins would let one silently undo the other.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE designs ADD COLUMN extraImages TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE designs ADD COLUMN extraImagesUpdatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v8 -> v9: batches, and the workshop's people.
         *
         * Five new tables and not one existing column touched, so a failure here cannot cost
         * a design, a price or a piece of stock - the same shape as the orders and profile
         * migrations before it.
         *
         * WHAT IS NOT HERE, DELIBERATELY: no backfill. Designs that already hold stock get no
         * batch rows, so their sizes read as "stock, but no batch history" rather than being
         * handed an invented batch 1 dated today that nobody ever made. The first real refill
         * starts the history honestly. DataRepository treats an empty ledger for a size as
         * "unbatched" and leaves the stock column alone, which is why the catalogue and the
         * order path keep working untouched for every design that existed before today.
         *
         * `batches` cascades from designs and has no tombstone table: nothing deletes a batch
         * on its own, because emptying a size sets `remaining` to 0 and keeps the row.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS batches (
                        id TEXT NOT NULL PRIMARY KEY,
                        designId TEXT NOT NULL,
                        size TEXT NOT NULL,
                        number INTEGER NOT NULL,
                        quantity INTEGER NOT NULL,
                        remaining INTEGER NOT NULL,
                        price INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(designId) REFERENCES designs(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_batches_designId_size ON batches (designId, size)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_batches_designId_size_number " +
                        "ON batches (designId, size, number)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS karigars (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        activities TEXT NOT NULL DEFAULT '[]',
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS deleted_karigars (
                        karigarId TEXT NOT NULL PRIMARY KEY,
                        deletedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activities (
                        name TEXT NOT NULL PRIMARY KEY,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS deleted_activities (
                        name TEXT NOT NULL PRIMARY KEY,
                        deletedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Every migration the app knows about. Pass to `addMigrations(*ALL_MIGRATIONS)`. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
            MIGRATION_8_9
        )
    }
}
