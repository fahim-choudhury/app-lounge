/*
 *  Copyright MURENA SAS 2023
 *  Apps  Quickly and easily install Android apps onto your device!
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.R
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationManagerModule {

    const val DOWNLOADS = "DOWNLOADS"
    const val UPDATES = "UPDATES"

    @Singleton
    @Provides
    fun provideNotificationManagerInstance(@ApplicationContext context: Context): NotificationManager {
        return context.getSystemService(NotificationManager::class.java)
    }

    @Singleton
    @Provides
    @Named("download")
    @RequiresApi(Build.VERSION_CODES.O)
    fun provideDownloadNotificationChannel(
        @ApplicationContext context: Context
    ): NotificationChannel {
        return NotificationChannel(
            DOWNLOADS,
            context.getString(R.string.downloads),
            NotificationManager.IMPORTANCE_DEFAULT
        )
    }

    @Singleton
    @Provides
    @Named("update")
    @RequiresApi(Build.VERSION_CODES.O)
    fun provideUpdateNotificationChannel(
        @ApplicationContext context: Context
    ): NotificationChannel {
        return NotificationChannel(
            UPDATES,
            context.getString(R.string.updates),
            NotificationManager.IMPORTANCE_DEFAULT
        )
    }
}
