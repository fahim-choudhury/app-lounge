/*
 * Copyright (C) 2019-2021  E FOUNDATION
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps



import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.MenuItem
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomnavigation.LabelVisibilityMode
import com.google.android.material.snackbar.Snackbar
import foundation.e.apps.applicationmanager.ApplicationManager
import foundation.e.apps.applicationmanager.ApplicationManagerServiceConnection
import foundation.e.apps.applicationmanager.ApplicationManagerServiceConnectionCallback
import foundation.e.apps.categories.CategoriesFragment
import foundation.e.apps.databinding.ActivityMainBinding
import foundation.e.apps.home.HomeFragment
import foundation.e.apps.search.SearchFragment
import foundation.e.apps.settings.SettingsFragment
import foundation.e.apps.updates.UpdatesFragment
import foundation.e.apps.utils.Common
import foundation.e.apps.utils.Constants
import foundation.e.apps.utils.Constants.CURRENTLY_SELECTED_FRAGMENT_KEY
import java.io.BufferedReader
import java.io.File
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity(), BottomNavigationView.OnNavigationItemSelectedListener,
        ApplicationManagerServiceConnectionCallback {
    private lateinit var binding: ActivityMainBinding

    private var currentFragmentId = 0
    private val homeFragment = HomeFragment()
    private val searchFragment = SearchFragment()
    private val updatesFragment = UpdatesFragment()
    private val applicationManagerServiceConnection =
            ApplicationManagerServiceConnection(this)
    private val codeRequestPermissions = 9527
    var doubleBackToExitPressedOnce = false;
    private var isReceiverRegistered = false
    private var accentColorOS by Delegates.notNull<Int>()

    init {
        instance = this
    }

    companion object {
        private var instance: MainActivity? = null

        lateinit var mActivity: MainActivity
        var sharedPreferences : SharedPreferences?=null
        val sharedPrefFile = "kotlinsharedpreference"

        /*
         * Provides the application context via MainActivity
         * @return applicationContext
         */
        fun applicationContext(): Context {
            return instance!!.applicationContext
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        accentColorOS = Common.getAccentColor(this)

        mActivity = this
        disableCategoryIfOpenSource()


        binding.bottomNavigationView.setOnNavigationItemSelectedListener{
            if (selectFragment(it.itemId, it)) {
                disableCategoryIfOpenSource()
                currentFragmentId = it.itemId
                return@setOnNavigationItemSelectedListener true
            }
            return@setOnNavigationItemSelectedListener false
        }


        disableShiftingOfNabBarItems()

        Common.updateMicroGStatus(this)

        // Show the home fragment by default
        currentFragmentId = if (savedInstanceState != null &&
                savedInstanceState.containsKey(CURRENTLY_SELECTED_FRAGMENT_KEY)) {
            savedInstanceState.getInt(CURRENTLY_SELECTED_FRAGMENT_KEY)
        } else if (intent.hasExtra(Constants.UPDATES_NOTIFICATION_CLICK_EXTRA)) {
            R.id.menu_updates
        } else {
            R.id.menu_home
        }
        setupLangReceiver()
        applicationManagerServiceConnection.bindService(this)

        bottom_navigation_view_color()
        openSearchFragment()


    }

    override fun onResume() {
        super.onResume()
        Common.updateMicroGStatus(this)
    }

    private fun openSearchFragment() {
        if (intent.getBooleanExtra(Constants.OPEN_SEARCH, false)) {
            currentFragmentId = R.id.menu_search
            val bundle = Bundle()
            bundle.putString(Constants.MICROG_QUERY, "microg")
            searchFragment.arguments= bundle
        }
    }


    private fun bottom_navigation_view_color() {
        val iconsColorStates =
                ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_checked),
                        intArrayOf(android.R.attr.state_checked)), intArrayOf(
                        Color.parseColor("#C4CFD9"),
                        accentColorOS
                ))

        val textColorStates = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)), intArrayOf(
                Color.parseColor("#C4CFD9"),
                accentColorOS

        ))

        binding.bottomNavigationView.setItemIconTintList(iconsColorStates)
        binding.bottomNavigationView.setItemTextColor(textColorStates)

    }

    override fun onServiceBind(applicationManager: ApplicationManager) {
        initialiseFragments(applicationManager)
        selectFragment(currentFragmentId, null)
    }

    private fun initialiseFragments(applicationManager: ApplicationManager) {
        homeFragment.initialise(applicationManager, accentColorOS)
        searchFragment.initialise(applicationManager, accentColorOS)
        updatesFragment.initialise(applicationManager, accentColorOS)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (selectFragment(item.itemId, item)) {
            currentFragmentId = item.itemId
            return true
        }
        return false
    }

    fun showApplicationTypePreference(): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(mActivity)
        var showAllApps = preferences.getBoolean(mActivity.getString(R.string.Show_all_apps), true)
        var showAllOpenSourceApps = preferences.getBoolean(mActivity.getString(R.string.show_only_open_source_apps_key), false)
        var showAllPwaApps = preferences.getBoolean(mActivity.getString(R.string.show_only_pwa_apps_key), false)
        if (showAllOpenSourceApps) {
            return "open"
        } else if (showAllApps) {
            return "any"
        } else if (showAllPwaApps) {
            return "pwa"
        }
        return "any"
    }

    fun tintMenuIcon(context: Context, item: MenuItem, @ColorRes color: Int) {
        val normalDrawable = item.icon
        val wrapDrawable = DrawableCompat.wrap(normalDrawable)

        DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(context, color))//context.resources.getColor(color))
        item.icon = wrapDrawable
    }

    private fun selectFragment(fragmentId: Int, item: MenuItem?): Boolean {

        when (fragmentId) {

            R.id.menu_home -> {
                item?.setIcon(R.drawable.ic_menu_home)
                showFragment(homeFragment)

                return true
            }
            R.id.menu_categories -> {
                item?.setIcon(R.drawable.ic_menu_categories)
                showFragment(CategoriesFragment())

                return true
            }
            R.id.menu_search -> {
                item?.setIcon(lineageos.platform.R.drawable.ic_search)
                showFragment(searchFragment)
                return true
            }
            R.id.menu_updates -> {
                item?.setIcon(R.drawable.ic_menu_updates)
                showFragment(updatesFragment)
                return true
            }
            R.id.menu_settings -> {
                item?.setIcon(lineageos.platform.R.drawable.ic_settings)
                showFragment(SettingsFragment())
                return true
            }
        }
        return false
    }
    private var mLangReceiver: BroadcastReceiver? = null
    protected fun setupLangReceiver(): BroadcastReceiver? {
        if (mLangReceiver == null) {
            mLangReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    finish()
                }
            }
            val filter = IntentFilter(Intent.ACTION_LOCALE_CHANGED)
            registerReceiver(mLangReceiver, filter)
            isReceiverRegistered = true;
        }
        return mLangReceiver
    }

    private fun showFragment(fragment: Fragment) {
        binding.bottomNavigationView.menu.findItem(currentFragmentId).isChecked = true
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.frame_layout, fragment)
                .commitAllowingStateLoss();
    }

    @SuppressLint("RestrictedApi")
    private fun disableShiftingOfNabBarItems() {
        val menuView = binding.bottomNavigationView.getChildAt(0) as BottomNavigationMenuView
        try {
            val mShiftingMode = menuView.javaClass.getDeclaredField("mShiftingMode")
            mShiftingMode.isAccessible = true
            mShiftingMode.setBoolean(menuView, false)
            mShiftingMode.isAccessible = false
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }

        for (i in 0 until menuView.childCount) {
            val itemView = menuView.getChildAt(i) as BottomNavigationItemView
            itemView.setLabelVisibilityMode(LabelVisibilityMode.LABEL_VISIBILITY_LABELED);            itemView.setChecked(itemView.itemData.isChecked)
        }
    }

    private fun disableCategoryIfOpenSource(){
        if(showApplicationTypePreference()=="open") {
            binding.bottomNavigationView.menu.removeItem(R.id.menu_categories)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.STORAGE_PERMISSION_REQUEST_CODE &&
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Snackbar.make(binding.container, R.string.error_storage_permission_denied,
                    Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(CURRENTLY_SELECTED_FRAGMENT_KEY, currentFragmentId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            unregisterReceiver(mLangReceiver)
            isReceiverRegistered = false // set it back to false.
        }
        homeFragment.decrementApplicationUses()
        searchFragment.decrementApplicationUses()
        updatesFragment.decrementApplicationUses()
        applicationManagerServiceConnection.unbindService(this)
    }

    override fun onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed()
            return
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.exit, Toast.LENGTH_SHORT).show();

        Handler().postDelayed(Runnable() {
            run {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000)
    }
}
