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
import android.util.Log
import android.view.View
import com.aurora.gplayapi.data.models.AuthData
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.categories.model.CategoriesVPAdapter
import foundation.e.apps.databinding.FragmentCategoriesBinding
import foundation.e.apps.utils.parentFragment.TimeoutFragment

@AndroidEntryPoint
class CategoriesFragment : TimeoutFragment(R.layout.fragment_categories) {
    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!

    private val TAG = CategoriesFragment::class.java.simpleName

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCategoriesBinding.bind(view)

        val tabLayout = binding.tabLayout
        val viewPager = binding.viewPager
        viewPager.adapter = CategoriesVPAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = getString(R.string.apps)
                1 -> tab.text = getString(R.string.games)
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onTimeout() {
        val position = binding.viewPager.currentItem

        val fragment = childFragmentManager.fragments.find {
            when (position) {
                0 -> it is AppsFragment
                1 -> it is GamesFragment
                else -> false
            }
        }

        fragment?.let {
            if (it is TimeoutFragment) {
                Log.d(TAG, "Showing timeout on Categories fragment: " + it::class.java.name)
                it.onTimeout()
            }
        }
    }

    override fun refreshData(authData: AuthData) {}
}
