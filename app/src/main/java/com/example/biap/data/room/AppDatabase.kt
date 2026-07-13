package com.example.biap.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DesignEntity::class,
        SizeInventoryEntity::class,
        GlobalCustomFieldEntity::class,
        DeletedDesignEntity::class,
        DeletedCustomFieldEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun designDao(): DesignDao
}
