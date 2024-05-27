/*
 *  Copyright MURENA SAS 2024
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

package foundation.e.apps.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.MutableLiveData
import timber.log.Timber

object NetworkStatusManager {

    private lateinit var connectivityManager: ConnectivityManager
    private var internetConnectionLiveData: MutableLiveData<Boolean> = MutableLiveData()

    /**
     * Registers for network callback with [ConnectivityManager]
     * @param context should be applicationContext
     * @return [MutableLiveData], holds the [Boolean] value as status of internet availability
     */
    fun init(context: Context): MutableLiveData<Boolean> {
        connectivityManager =
            context.getSystemService(ConnectivityManager::class.java) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, createNetworkCallback())

        return internetConnectionLiveData
    }

    private fun createNetworkCallback(
    ): ConnectivityManager.NetworkCallback {
        return object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Timber.d("Network: onAvailable: ${network.networkHandle}")
                sendInternetStatus(true)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasInternet =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                Timber.d("Network: onCapabilitiesChanged: ${network.networkHandle}, hasInternet: $hasInternet")
                sendInternetStatus(hasInternet)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Timber.d("Network: onLost: ${network.networkHandle}")
                sendInternetStatus(false)
            }

            private fun sendInternetStatus(hasInternet: Boolean?) {
                hasInternet?.let {
                    internetConnectionLiveData.postValue(it)
                }
            }
        }
    }
}
