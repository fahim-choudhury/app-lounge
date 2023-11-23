package foundation.e.apps.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.data.application.HomeApi
import foundation.e.apps.data.application.HomeApiImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Singleton
    @Binds
    fun getHomeApi(homeApiImpl: HomeApiImpl): HomeApi
}
