package com.sc.biap.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies MD5-to-SHA256 database file adoption including WAL/SHM/.bak sidecars and sync stamp.
 */
class DatabaseAdoptionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sha256DatabaseSuffix differs from legacyMd5DatabaseSuffix and preserves blank handling`() {
        assertEquals("", sha256DatabaseSuffix(null))
        assertEquals("", sha256DatabaseSuffix("  "))
        assertEquals("", legacyMd5DatabaseSuffix(null))

        val uid = "firebase-uid-123"
        val shaSuffix = sha256DatabaseSuffix(uid)
        val md5Suffix = legacyMd5DatabaseSuffix(uid)
        assertEquals(33, shaSuffix.length)
        assertEquals(33, md5Suffix.length)
        assertNotEquals(md5Suffix, shaSuffix)
    }

    @Test
    fun `adoptDatabaseFilesWithSuffix moves db, wal, shm, bak, and sync stamp`() {
        val dbDir = tempFolder.newFolder("databases")
        val oldSuffix = legacyMd5DatabaseSuffix("uid-1")
        val newSuffix = sha256DatabaseSuffix("uid-1")

        val fromDb = File(dbDir, "bi_database${oldSuffix}.db").apply { writeText("db-bytes") }
        val fromWal = File(dbDir, "bi_database${oldSuffix}.db-wal").apply { writeText("wal-bytes") }
        val fromShm = File(dbDir, "bi_database${oldSuffix}.db-shm").apply { writeText("shm-bytes") }
        val fromBak = File(dbDir, "bi_database${oldSuffix}.db.bak").apply { writeText("bak-bytes") }
        val toDb = File(dbDir, "bi_database${newSuffix}.db")

        val prefs = FakeSharedPreferences().apply {
            edit().putLong("last_sync_at$oldSuffix", 1_700_000_000_000L).commit()
        }

        val changed = adoptDatabaseFilesWithSuffix(
            fromDb = fromDb,
            toDb = toDb,
            prefs = prefs,
            oldPrefKey = "last_sync_at$oldSuffix",
            newPrefKey = "last_sync_at$newSuffix",
        )

        assertTrue(changed)
        assertFalse(fromDb.exists())
        assertFalse(fromWal.exists())
        assertFalse(fromShm.exists())
        assertFalse(fromBak.exists())

        assertEquals("db-bytes", toDb.readText())
        assertEquals("wal-bytes", File(dbDir, "${toDb.name}-wal").readText())
        assertEquals("shm-bytes", File(dbDir, "${toDb.name}-shm").readText())
        assertEquals("bak-bytes", File(dbDir, "${toDb.name}.bak").readText())
        assertEquals(1_700_000_000_000L, prefs.getLong("last_sync_at$newSuffix", 0L))
        assertFalse(prefs.contains("last_sync_at$oldSuffix"))
    }

    @Test
    fun `adoptDatabaseFilesWithSuffix does not overwrite existing destination bak`() {
        val dbDir = tempFolder.newFolder("databases-keep-bak")
        val fromDb = File(dbDir, "bi_database_old.db").apply { writeText("old-db") }
        val fromBak = File(dbDir, "bi_database_old.db.bak").apply { writeText("old-bak") }
        val toDb = File(dbDir, "bi_database_new.db")
        val toBak = File(dbDir, "bi_database_new.db.bak").apply { writeText("existing-new-bak") }

        val prefs = FakeSharedPreferences()

        adoptDatabaseFilesWithSuffix(
            fromDb = fromDb,
            toDb = toDb,
            prefs = prefs,
            oldPrefKey = "last_sync_at_old",
            newPrefKey = "last_sync_at_new",
        )

        assertTrue(toDb.exists())
        assertEquals("existing-new-bak", toBak.readText())
        assertTrue("source .bak should remain untouched if dest .bak existed", fromBak.exists())
    }

    @Test
    fun `adoptDatabaseFilesWithSuffix carries orphaned bak when main db was already migrated`() {
        val dbDir = tempFolder.newFolder("databases-orphan-bak")
        val fromDb = File(dbDir, "bi_database_old.db")
        val fromBak = File(dbDir, "bi_database_old.db.bak").apply { writeText("old-bak") }
        val toDb = File(dbDir, "bi_database_new.db").apply { writeText("new-db") }
        val toBak = File(dbDir, "bi_database_new.db.bak")

        val changed = adoptDatabaseFilesWithSuffix(
            fromDb = fromDb,
            toDb = toDb,
            prefs = FakeSharedPreferences(),
            oldPrefKey = "last_sync_at_old",
            newPrefKey = "last_sync_at_new",
        )

        assertTrue(changed)
        assertFalse(fromBak.exists())
        assertEquals("old-bak", toBak.readText())
    }

    @Test
    fun `adoptDatabaseFilesWithSuffix does not overwrite existing destination db`() {
        val dbDir = tempFolder.newFolder("databases-no-overwrite")
        val fromDb = File(dbDir, "bi_database_old.db").apply { writeText("old-db") }
        val toDb = File(dbDir, "bi_database_new.db").apply { writeText("new-db") }
        val toBak = File(dbDir, "bi_database_new.db.bak").apply { writeText("new-bak") }

        val changed = adoptDatabaseFilesWithSuffix(
            fromDb = fromDb,
            toDb = toDb,
            prefs = FakeSharedPreferences(),
            oldPrefKey = "last_sync_at_old",
            newPrefKey = "last_sync_at_new",
        )

        assertFalse(changed)
        assertEquals("new-db", toDb.readText())
        assertEquals("new-bak", toBak.readText())
        assertEquals("old-db", fromDb.readText())
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int) = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long) = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float) = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean) = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?) = apply {
                if (key != null) removals.add(key)
            }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
                return true
            }
            override fun apply() {
                commit()
            }
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}
    }
}
