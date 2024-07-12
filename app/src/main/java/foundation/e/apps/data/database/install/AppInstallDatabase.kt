package foundation.e.apps.data.database.install

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import foundation.e.apps.data.database.AppDatabase
import foundation.e.apps.data.install.AppInstallDAO
import foundation.e.apps.data.install.models.AppInstall

@Database(entities = [AppInstall::class], version = 5, exportSchema = false)
@TypeConverters(AppInstallConverter::class)
abstract class AppInstallDatabase : RoomDatabase() {
    abstract fun fusedDownloadDao(): AppInstallDAO

    companion object {
        private lateinit var INSTANCE: AppInstallDatabase
        private const val DATABASE_NAME = "fused_database"

        fun getInstance(context: Context): AppInstallDatabase {
            if (!Companion::INSTANCE.isInitialized) {
                synchronized(AppDatabase::class) {
                    INSTANCE =
                        Room.databaseBuilder(context, AppInstallDatabase::class.java, DATABASE_NAME)
                            .fallbackToDestructiveMigration()
                            .build()
                }
            }
            return INSTANCE
        }
    }
}
