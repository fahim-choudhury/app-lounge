package foundation.e.apps.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.api.StoreApiRepository
import foundation.e.apps.api.cleanapk.CleanAPKInterface
import foundation.e.apps.api.cleanapk.CleanApkAppDetailApi
import foundation.e.apps.api.cleanapk.CleanApkAppsRepository
import foundation.e.apps.api.gplay.GplayRepository
import foundation.e.apps.api.gplay.utils.GPlayHttpClient
import foundation.e.apps.login.LoginSourceRepository
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NamedRepositoryModule {
    @Singleton
    @Provides
    @Named("gplayRepository")
    fun getGplayRepository(
        @ApplicationContext context: Context,
        gPlayHttpClient: GPlayHttpClient,
        loginSourceRepository: LoginSourceRepository
    ): StoreApiRepository {
        return GplayRepository(context, gPlayHttpClient, loginSourceRepository)
    }

    @Singleton
    @Provides
    @Named("cleanApkAppsRepository")
    fun getCleanApkAppsRepository(
        cleanAPKInterface: CleanAPKInterface,
        cleanApkAppDetailApi: CleanApkAppDetailApi
    ): StoreApiRepository {
        return CleanApkAppsRepository(cleanAPKInterface, cleanApkAppDetailApi)
    }

    @Singleton
    @Provides
    @Named("cleanApkPWARepository")
    fun getCleanApkPWARepository(
        cleanAPKInterface: CleanAPKInterface,
        cleanApkAppDetailApi: CleanApkAppDetailApi
    ): StoreApiRepository {
        return CleanApkAppsRepository(cleanAPKInterface, cleanApkAppDetailApi)
    }

}