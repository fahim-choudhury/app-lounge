/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
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

package foundation.e.apps.categories

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.Category
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.R
import foundation.e.apps.categories.model.CategoriesRVAdapter
import foundation.e.apps.databinding.FragmentAppsBinding
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.parentFragment.TimeoutFragment

@AndroidEntryPoint
class AppsFragment : TimeoutFragment(R.layout.fragment_apps) {
    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private val categoriesViewModel: CategoriesViewModel by viewModels()
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppsBinding.bind(view)

        /*
         * Explanation of double observers in HomeFragment.kt
         */

        mainActivityViewModel.internetConnection.observe(viewLifecycleOwner) {
            refreshDataOrRefreshToken(mainActivityViewModel)
        }
        mainActivityViewModel.authData.observe(viewLifecycleOwner) {
            refreshDataOrRefreshToken(mainActivityViewModel)
        }

        /*
         * Code regarding is just moved outside the observers.
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
         */

        val categoriesRVAdapter = CategoriesRVAdapter()
        val recyclerView = binding.recyclerView

        recyclerView.apply {
            adapter = categoriesRVAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            visibility = View.GONE
        }

        categoriesViewModel.categoriesList.observe(viewLifecycleOwner) {
            stopLoadingUI()
            categoriesRVAdapter.setData(it.first)
            if (it.third != ResultStatus.OK) {
                onTimeout()
            }
        }
    }

    override fun onTimeout() {
        if (!isTimeoutDialogDisplayed()) {
            stopLoadingUI()
            displayTimeoutAlertDialog(
                timeoutFragment = this,
                activity = requireActivity(),
                message = getString(R.string.timeout_desc_cleanapk),
                positiveButtonText = getString(android.R.string.ok),
                positiveButtonBlock = {},
                negativeButtonText = getString(R.string.retry),
                negativeButtonBlock = {
                    showLoadingUI()
                    resetTimeoutDialogLock()
                    mainActivityViewModel.retryFetchingTokenAfterTimeout()
                },
                allowCancel = true,
            )
        }
    }

    override fun refreshData(authData: AuthData) {
        showLoadingUI()
        categoriesViewModel.getCategoriesList(
            Category.Type.APPLICATION,
            authData
        )
    }

    private fun showLoadingUI() {
        binding.shimmerLayout.startShimmer()
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun stopLoadingUI() {
        binding.shimmerLayout.stopShimmer()
        binding.shimmerLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        resetTimeoutDialogLock()
        binding.shimmerLayout.startShimmer()
    }

    override fun onPause() {
        binding.shimmerLayout.stopShimmer()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
