package io.eelo.appinstaller

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.design.internal.BottomNavigationItemView
import android.support.design.internal.BottomNavigationMenuView
import android.support.design.widget.BottomNavigationView
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import io.eelo.appinstaller.applicationmanager.ApplicationManager
import io.eelo.appinstaller.applicationmanager.ApplicationManagerServiceConnection
import io.eelo.appinstaller.applicationmanager.ApplicationManagerServiceConnectionCallback
import io.eelo.appinstaller.categories.CategoriesFragment
import io.eelo.appinstaller.home.HomeFragment
import io.eelo.appinstaller.search.SearchFragment
import io.eelo.appinstaller.settings.SettingsFragment
import io.eelo.appinstaller.updates.UpdatesFragment
import io.eelo.appinstaller.utils.Constants
import io.eelo.appinstaller.utils.Constants.CURRENTLY_SELECTED_FRAGMENT_KEY
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), BottomNavigationView.OnNavigationItemSelectedListener,
        ApplicationManagerServiceConnectionCallback {
    private var currentFragmentId = 0
    private val homeFragment = HomeFragment()
    private val searchFragment = SearchFragment()
    private val updatesFragment = UpdatesFragment()
    private val applicationManagerServiceConnection =
            ApplicationManagerServiceConnection(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottom_navigation_view.setOnNavigationItemSelectedListener(this)
        disableShiftingOfNabBarItems()

        // Show the home fragment by default
        currentFragmentId = if (savedInstanceState != null &&
                savedInstanceState.containsKey(CURRENTLY_SELECTED_FRAGMENT_KEY)) {
            savedInstanceState.getInt(CURRENTLY_SELECTED_FRAGMENT_KEY)
        } else {
            R.id.menu_home
        }

        applicationManagerServiceConnection.bindService(this)
    }

    override fun onServiceBind(applicationManager: ApplicationManager) {
        initialiseFragments(applicationManager)
        selectFragment(currentFragmentId)
    }

    private fun initialiseFragments(applicationManager: ApplicationManager) {
        homeFragment.initialise(applicationManager)
        searchFragment.initialise(applicationManager)
        updatesFragment.initialise(applicationManager)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (selectFragment(item.itemId)) {
            currentFragmentId = item.itemId
            return true
        }
        return false
    }

    private fun selectFragment(fragmentId: Int): Boolean {
        when (fragmentId) {
            R.id.menu_home -> {
                showFragment(homeFragment)
                return true
            }
            R.id.menu_categories -> {
                showFragment(CategoriesFragment())
                return true
            }
            R.id.menu_search -> {
                showFragment(searchFragment)
                return true
            }
            // TODO Enable once updates screen is implemented
            /*R.id.menu_updates -> {
                showFragment(updatesFragment)
                return true
            }*/
            R.id.menu_settings -> {
                showFragment(SettingsFragment())
                return true
            }
        }
        return false
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.frame_layout, fragment)
                .commit()
    }

    @SuppressLint("RestrictedApi")
    private fun disableShiftingOfNabBarItems() {
        val menuView = bottom_navigation_view.getChildAt(0) as BottomNavigationMenuView
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
            itemView.setShiftingMode(false)
            itemView.setChecked(itemView.itemData.isChecked)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        if (requestCode == Constants.STORAGE_PERMISSION_REQUEST_CODE &&
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Snackbar.make(container, R.string.error_storage_permission_denied,
                    Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putInt(CURRENTLY_SELECTED_FRAGMENT_KEY, currentFragmentId)
    }

    override fun onDestroy() {
        super.onDestroy()
        homeFragment.decrementApplicationUses()
        searchFragment.decrementApplicationUses()
        updatesFragment.decrementApplicationUses()
        applicationManagerServiceConnection.unbindService(this)
    }
}
