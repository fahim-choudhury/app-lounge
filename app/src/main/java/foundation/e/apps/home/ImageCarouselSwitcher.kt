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

package foundation.e.apps.home

import android.os.Handler
import android.os.Looper
import androidx.viewpager.widget.ViewPager

class ImageCarouselSwitcher(private var imagesCount: Int, private val imageCarousel: ViewPager) {
    private val handler = Handler(Looper.getMainLooper())
    private val switchImageInterval = 2000L

    fun start() {
        handler.postDelayed(this::switchImage, switchImageInterval)
    }

    private fun switchImage() {
        if (isAllImagesSubmitted()) {
            imageCarousel.setCurrentItem(0, true)
        } else {
            imageCarousel.setCurrentItem(imageCarousel.currentItem + 1, true)
            handler.postDelayed(this::switchImage, switchImageInterval)
        }
    }

    private fun isAllImagesSubmitted(): Boolean {
        return imageCarousel.currentItem == imagesCount - 1
    }
}
