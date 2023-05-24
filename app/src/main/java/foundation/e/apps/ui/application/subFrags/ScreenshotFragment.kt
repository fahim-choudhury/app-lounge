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

package foundation.e.apps.ui.application.subFrags

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.databinding.FragmentScreenshotBinding
import foundation.e.apps.ui.application.model.DepthPageTransformer
import foundation.e.apps.ui.application.model.ScreenshotRVAdapter

@AndroidEntryPoint
class ScreenshotFragment : Fragment(R.layout.fragment_screenshot) {

    private var _binding: FragmentScreenshotBinding? = null
    private val binding get() = _binding!!

    private val args: ScreenshotFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScreenshotBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            view.findNavController().navigateUp()
        }

        val screenshotRVAdapter = ScreenshotRVAdapter(args.list.toList(), args.origin)
        binding.viewPager.apply {
            adapter = screenshotRVAdapter
            currentItem = args.position
            setPageTransformer(DepthPageTransformer())
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
