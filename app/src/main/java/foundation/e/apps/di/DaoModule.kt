package foundation.e.apps.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.database.AppDatabase
import foundation.e.apps.data.exodus.TrackerDao
import foundation.e.apps.data.faultyApps.FaultyAppDao
import foundation.e.apps.data.fdroid.FdroidDao
import foundation.e.apps.data.parentalcontrol.ContentRatingDao

@InstallIn(SingletonComponent::class)
@Module
object DaoModule {
    @Provides
    fun getTrackerDao(@ApplicationContext context: Context): TrackerDao {
        return AppDatabase.getInstance(context).trackerDao()
    }

    @Provides
    fun getFdroidDao(@ApplicationContext context: Context): FdroidDao {
        return AppDatabase.getInstance(context).fdroidDao()
    }

    @Provides
    fun getFaultyAppsDao(@ApplicationContext context: Context): FaultyAppDao {
        return AppDatabase.getInstance(context).faultyAppsDao()
    }

    @Provides
    fun getContentRatingDao(@ApplicationContext context: Context): ContentRatingDao {
        return AppDatabase.getInstance(context).contentRatingDao()
    }
}
