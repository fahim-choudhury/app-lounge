package foundation.e.apps.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import foundation.e.apps.data.database.install.AppInstallConverter
import foundation.e.apps.data.exodus.Tracker
import foundation.e.apps.data.exodus.TrackerDao
import foundation.e.apps.data.faultyApps.FaultyApp
import foundation.e.apps.data.faultyApps.FaultyAppDao
import foundation.e.apps.data.fdroid.FdroidDao
import foundation.e.apps.data.fdroid.models.FdroidEntity
import foundation.e.apps.data.parentalcontrol.ContentRatingEntity
import foundation.e.apps.data.parentalcontrol.ContentRatingDao
import foundation.e.apps.data.parentalcontrol.FDroidNsfwApp
import foundation.e.apps.data.parentalcontrol.googleplay.GPlayContentRatingGroup

@Database(
    entities = [
        Tracker::class,
        FdroidEntity::class,
        FaultyApp::class,
        ContentRatingEntity::class,
        FDroidNsfwApp::class,
        GPlayContentRatingGroup::class,
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(AppInstallConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
    abstract fun fdroidDao(): FdroidDao
    abstract fun faultyAppsDao(): FaultyAppDao
    abstract fun contentRatingDao(): ContentRatingDao

    companion object {
        private lateinit var INSTANCE: AppDatabase

        fun getInstance(context: Context): AppDatabase {
            if (!Companion::INSTANCE.isInitialized) {
                synchronized(AppDatabase::class) {
                    INSTANCE =
                        Room.databaseBuilder(context, AppDatabase::class.java, "App_Lounge")
                            .fallbackToDestructiveMigration()
                            .addMigrations(migration3To4)
                            .build()
                }
            }
            return INSTANCE
        }

        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM FaultyApp")
            }
        }
    }
}
