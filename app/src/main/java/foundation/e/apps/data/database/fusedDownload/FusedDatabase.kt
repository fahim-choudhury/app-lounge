package foundation.e.apps.data.database.fusedDownload

import android.content.Context
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import foundation.e.apps.data.database.AppDatabase
import foundation.e.apps.data.fusedDownload.FusedDownloadDAO
import foundation.e.apps.data.fusedDownload.models.FusedDownload

@Database(entities = [FusedDownload::class], version = 4, exportSchema = false)
@TypeConverters(FusedConverter::class)
abstract class FusedDatabase : RoomDatabase() {
    abstract fun fusedDownloadDao(): FusedDownloadDAO

    companion object {
        private lateinit var INSTANCE: FusedDatabase
        private const val DATABASE_NAME = "fused_database"

        fun getInstance(context: Context): FusedDatabase {
            if (!Companion::INSTANCE.isInitialized) {
                synchronized(AppDatabase::class) {
                    INSTANCE =
                        Room.databaseBuilder(context, FusedDatabase::class.java, DATABASE_NAME)
                            .fallbackToDestructiveMigration()
                            .build()
                }
            }
            return INSTANCE
        }
    }
}
