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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.categories.model.CategoriesRVAdapter
import foundation.e.apps.databinding.FragmentAppsBinding

@AndroidEntryPoint
class AppsFragment : Fragment(R.layout.fragment_apps) {
    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!

    private val categoriesViewModel: CategoriesViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAppsBinding.bind(view)

        val categoriesRVAdapter = CategoriesRVAdapter()
        val recyclerView = binding.recyclerView

        recyclerView.apply {
            adapter = categoriesRVAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            visibility = View.GONE
        }

        categoriesViewModel.getCategoriesList("apps")
        categoriesViewModel.categoriesList.observe(viewLifecycleOwner, {
            categoriesRVAdapter.setData(it)
            binding.progressBar.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
