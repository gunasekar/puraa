package com.puraa.relay

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        OutboxEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(OutboxStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun outbox(): OutboxDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "puraa.db",
            )
                // Schema versions 1–4 predate the first release (dev
                // iterations); v4 is the shipping baseline. Preserve the
                // outbox across future updates by adding a Migration(N, N+1)
                // to addMigrations() and bumping `version` on each schema
                // change — NOT destructive migration, which would silently
                // drop queued-but-unsent SMS on an app update.
                .build()
                .also { instance = it }
        }
    }
}

class OutboxStatusConverter {
    @TypeConverter
    fun fromStatus(value: OutboxEntity.Status): String = value.name

    @TypeConverter
    fun toStatus(value: String): OutboxEntity.Status =
        OutboxEntity.Status.valueOf(value)
}
