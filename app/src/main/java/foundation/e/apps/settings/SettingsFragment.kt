/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2022  E FOUNDATION
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

package foundation.e.apps.settings

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.work.ExistingPeriodicWorkPolicy
import coil.load
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.BuildConfig
import foundation.e.apps.MainActivity
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.R
import foundation.e.apps.databinding.CustomPreferenceBinding
import foundation.e.apps.login.LoginViewModel
import foundation.e.apps.updates.manager.UpdatesWorkManager
import foundation.e.apps.utils.enums.User
import foundation.e.apps.utils.modules.CommonUtilsFunctions
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

    companion object {
        private const val TAG = "SettingsFragment"
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

                val osVersion = CommonUtilsFunctions.getSystemProperty("ro.lineage.version")
                val appVersionLabel = getString(R.string.app_version_label)
                var contents = "$appVersionLabel: $summary"
                if (!osVersion.isNullOrBlank()) {
                    contents += "\n${context.getString(R.string.os_version)}: $osVersion"
                }

                CommonUtilsFunctions.copyTextToClipboard(
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

        mainActivityViewModel.gPlayAuthData.let { authData ->
            mainActivityViewModel.getUser().name.let { user ->
                when (user) {
                    User.ANONYMOUS.name -> {
                        binding.accountType.text = view.context.getString(R.string.user_anonymous)
                    }
                    User.GOOGLE.name -> {
                        if (!authData.isAnonymous) {
                            binding.accountType.text = authData.userProfile?.name
                            binding.email.text = mainActivityViewModel.getUserEmail()
                            binding.avatar.load(authData.userProfile?.artwork?.url)
                        }
                    }
                }
            }
        }

        binding.tos.setOnClickListener {
            it.findNavController().navigate(R.id.TOSFragment)
        }

        binding.logout.setOnClickListener {
            loginViewModel.logout()
        }
    }

    fun isAnyAppSourceSelected() =
        showAllApplications?.isChecked == true || showFOSSApplications?.isChecked == true || showPWAApplications?.isChecked == true

    private fun backToMainActivity() {
        Intent(context, MainActivity::class.java).also {
            activity?.finish()
            activity?.overridePendingTransition(0, 0)
            startActivity(it)
            activity?.overridePendingTransition(0, 0)
        }
    }

    override fun onDestroyView() {
        if (sourcesChangedFlag) {
            loginViewModel.startLoginFlow()
        }
        super.onDestroyView()
        _binding = null
    }
}
