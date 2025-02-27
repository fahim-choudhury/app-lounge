/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2022-2023  E FOUNDATION
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

package foundation.e.apps.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceFragmentCompat
import androidx.work.ExistingPeriodicWorkPolicy
import coil.load
import com.aurora.gplayapi.data.models.AuthData
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.BuildConfig
import foundation.e.apps.R
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.application.UpdatesDao
import foundation.e.apps.ui.LoginViewModel
import foundation.e.apps.databinding.CustomPreferenceBinding
import foundation.e.apps.install.updates.UpdatesWorkManager
import foundation.e.apps.ui.MainActivityViewModel
import foundation.e.apps.utils.SystemInfoProvider
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {

    private var _binding: CustomPreferenceBinding? = null
    private val binding get() = _binding!!
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()
    private var showAllApplications: CheckBoxPreference? = null
    private var showFOSSApplications: CheckBoxPreference? = null
    private var showPWAApplications: CheckBoxPreference? = null

    val loginViewModel: LoginViewModel by lazy {
        ViewModelProvider(requireActivity())[LoginViewModel::class.java]
    }

    private var sourcesChangedFlag = false

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var clipboardManager: ClipboardManager

    private val user by lazy {
        mainActivityViewModel.getUser()
    }

    private val allSourceCheckboxes by lazy {
        listOf(showAllApplications, showFOSSApplications, showPWAApplications)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        // Show applications preferences
        showAllApplications = findPreference<CheckBoxPreference>("showAllApplications")
        showFOSSApplications = findPreference<CheckBoxPreference>("showFOSSApplications")
        showPWAApplications = findPreference<CheckBoxPreference>("showPWAApplications")
        val updateCheckInterval =
            preferenceManager.findPreference<Preference>(getString(R.string.update_check_intervals))
        updateCheckInterval?.setOnPreferenceChangeListener { _, newValue ->
            Timber.d("onCreatePreferences: updated Value: $newValue")
            context?.let {
                UpdatesWorkManager.enqueueWork(
                    it,
                    newValue.toString().toLong(),
                    ExistingPeriodicWorkPolicy.REPLACE
                )
            }
            true
        }

        val versionInfo = findPreference<LongPressPreference>("versionInfo")
        versionInfo?.apply {
            summary = BuildConfig.VERSION_NAME
            setOnLongClickListener {

                val osVersion = SystemInfoProvider.getSystemProperty(SystemInfoProvider.KEY_LINEAGE_VERSION)
                val appVersionLabel = getString(R.string.app_version_label)
                var contents = "$appVersionLabel: $summary"
                if (!osVersion.isNullOrBlank()) {
                    contents += "\n${context.getString(R.string.os_version)}: $osVersion"
                }

                copyTextToClipboard(
                    clipboardManager,
                    appVersionLabel,
                    contents
                )
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()

                true
            }
        }

        allSourceCheckboxes.forEach {
            it?.onPreferenceChangeListener = sourceCheckboxListener
        }
    }

    /**
     * Checkbox listener to prevent all checkboxes from getting unchecked.
     */
    private val sourceCheckboxListener =
        Preference.OnPreferenceChangeListener { preference: Preference, newValue: Any? ->

            sourcesChangedFlag = true
            loginViewModel.authObjects.value = null

            val otherBoxesChecked =
                allSourceCheckboxes.filter { it != preference }.any { it?.isChecked == true }

            if (newValue == false && !otherBoxesChecked) {
                (preference as CheckBoxPreference).isChecked = true
                Toast.makeText(
                    requireActivity(),
                    R.string.select_one_source_of_applications,
                    Toast.LENGTH_SHORT
                ).show()
                false
            } else true
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = CustomPreferenceBinding.bind(view)

        super.onViewCreated(view, savedInstanceState)

        val autoInstallUpdate = fetchCheckboxPreference(R.string.auto_install_enabled)
        val onlyUnmeteredNetwork = fetchCheckboxPreference(R.string.only_unmetered_network)

        setCheckboxDependency(onlyUnmeteredNetwork, autoInstallUpdate)

        // This is useful if a user from older App Lounge updates to this version
        disableDependentCheckbox(onlyUnmeteredNetwork, autoInstallUpdate)

        mainActivityViewModel.gPlayAuthData.let { authData ->
            mainActivityViewModel.getUser().name.let { user ->
                handleUser(user, authData)
            }
        }

        binding.tos.setOnClickListener {
            it.findNavController().navigate(R.id.TOSFragment)
        }

        binding.logout.setOnClickListener {
            loginViewModel.logout()
        }

        if (user == User.NO_GOOGLE) {
            /*
             * For No-Google mode, do not allow the user to click
             * on the option to show GPlay apps.
             * Instead show a message and prompt them to login.
             */
            showAllApplications?.apply {
                setOnPreferenceChangeListener { _, _ ->
                    Snackbar.make(
                        binding.root,
                        R.string.login_to_see_gplay_apps,
                        Snackbar.LENGTH_SHORT
                    ).setAction(R.string.login) {
                        /*
                         * The login and logout logic is the same,
                         * it clears all login data (authdata, user selection)
                         * and restarts the login flow.
                         * Hence it's the same as logout.
                         */
                        binding.logout.performClick()
                    }.show()
                    this.isChecked = false
                    return@setOnPreferenceChangeListener false
                }
            }

            /*
             * For no-google mode, just show a "Login" instead of "Logout".
             * The background logic is the same.
             */
            binding.logout.apply {
                setText(R.string.login)
            }
        }
    }

    private fun handleUser(user: String, authData: AuthData) {
        when (user) {
            User.ANONYMOUS.name -> {
                binding.accountType.setText(R.string.user_anonymous)
                binding.email.isVisible = false
            }

            User.GOOGLE.name -> {
                if (!authData.isAnonymous) {
                    binding.accountType.text = authData.userProfile?.name
                    binding.email.text = mainActivityViewModel.getUserEmail()
                    binding.avatar.load(authData.userProfile?.artwork?.url)
                }
            }

            User.NO_GOOGLE.name -> {
                binding.accountType.setText(R.string.logged_out)
                binding.email.isVisible = false
            }
        }
    }

    private fun fetchCheckboxPreference(id: Int): CheckBoxPreference? {
        return findPreference(getString(id))
    }

    fun isAnyAppSourceSelected() =
        showAllApplications?.isChecked == true || showFOSSApplications?.isChecked == true || showPWAApplications?.isChecked == true

    private fun disableDependentCheckbox(
        checkBox: CheckBoxPreference?,
        parentCheckBox: CheckBoxPreference?
    ) {
        checkBox?.apply {
            if (parentCheckBox?.isChecked == false) {
                this.isChecked = false
                this.isEnabled = false
            }
        }
    }

    private fun setCheckboxDependency(
        checkBox: CheckBoxPreference?,
        parentCheckBox: CheckBoxPreference?,
        parentCheckBoxPreferenceChangeListener: OnPreferenceChangeListener? = null,
    ) {
        checkBox?.dependency = parentCheckBox?.key
        parentCheckBox?.onPreferenceChangeListener =
            OnPreferenceChangeListener { preference, newValue ->
                if (newValue == false) {
                    checkBox?.isChecked = false
                }
                parentCheckBoxPreferenceChangeListener?.onPreferenceChange(preference, newValue)
                    ?: true
            }
    }

    private fun copyTextToClipboard(
        clipboard: ClipboardManager,
        label: String,
        text: String,
    ) {
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onDestroyView() {
        if (sourcesChangedFlag) {
            UpdatesDao.addItemsForUpdate(emptyList())
            loginViewModel.startLoginFlow()
        }
        super.onDestroyView()
        _binding = null
    }
}
