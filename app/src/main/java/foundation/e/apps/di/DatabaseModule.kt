package foundation.e.apps.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.database.install.AppInstallDatabase
import foundation.e.apps.data.install.AppInstallDAO
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun provideDatabaseInstance(@ApplicationContext context: Context): AppInstallDatabase {
        return AppInstallDatabase.getInstance(context)
    }

    @Singleton
    @Provides
    fun provideFusedDaoInstance(appInstallDatabase: AppInstallDatabase): AppInstallDAO {
        return appInstallDatabase.fusedDownloadDao()
    }
}
