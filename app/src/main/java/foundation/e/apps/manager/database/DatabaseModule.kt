package foundation.e.apps.manager.database

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.manager.database.fusedDownload.FusedDownloadDAO
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun provideDatabaseInstance(@ApplicationContext context: Context): FusedDatabase {
        return FusedDatabase.getInstance(context)
    }

    @Singleton
    @Provides
    fun provideFusedDaoInstance(fusedDatabase: FusedDatabase): FusedDownloadDAO {
        return fusedDatabase.fusedDownloadDao()
    }
}
