package com.sc.biap.data.room

import androidx.room.TypeConverter
import com.sc.biap.data.DesignPhoto
import com.sc.biap.data.OrderLine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Order lines ride in a JSON column rather than a child table.
     *
     * A line is never queried, sorted or joined on its own - it is only ever read as part of
     * the order it belongs to. A second table with a foreign key would buy nothing here and
     * cost a relation query, a cascade rule and a migration to maintain.
     */
    /**
     * The extra photographs, in one column for the same reason order lines are.
     *
     * A photo is only ever read as part of the design it belongs to, never queried or sorted
     * on its own. Note the failure mode differs from a corrupt order line: returning empty
     * here loses the POINTERS, not the photographs - the bytes stay in Drive under keys the
     * design would no longer name. Better than refusing to open the catalogue, and the cover
     * photo lives in its own columns and is untouched either way.
     */
    @TypeConverter
    fun fromDesignPhotos(value: List<DesignPhoto>): String = json.encodeToString(value)

    @TypeConverter
    fun toDesignPhotos(value: String): List<DesignPhoto> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            android.util.Log.e("Converters", "Corrupt design photos column, returning empty: $value", e)
            emptyList()
        }
    }

    @TypeConverter
    fun fromOrderLines(value: List<OrderLine>): String = json.encodeToString(value)

    @TypeConverter
    fun toOrderLines(value: String): List<OrderLine> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            android.util.Log.e("Converters", "Corrupt order lines column, returning empty: $value", e)
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            android.util.Log.e("Converters", "Corrupt List column, returning empty: $value", e)
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringMap(value: Map<String, String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            android.util.Log.e("Converters", "Corrupt Map column, returning empty: $value", e)
            emptyMap()
        }
    }
}
