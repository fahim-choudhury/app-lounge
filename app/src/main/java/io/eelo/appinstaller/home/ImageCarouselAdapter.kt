package io.eelo.appinstaller.home

import android.content.Context
import android.graphics.Bitmap
import android.support.v4.view.PagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.eelo.appinstaller.R
import io.eelo.appinstaller.application.model.Application
import io.eelo.appinstaller.application.viewmodel.ApplicationViewModel
import kotlinx.android.synthetic.main.image_carousel_item.view.*

class ImageCarouselAdapter(context: Context, private val bannerApps: List<Pair<Application, Bitmap>>) : PagerAdapter() {

    private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val applicationViewModel = ApplicationViewModel()

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view == obj as LinearLayout
    }

    override fun getCount(): Int {
        return bannerApps.size
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = layoutInflater.inflate(R.layout.image_carousel_item, container, false)
        val wideImage = view.image

        wideImage.setImageBitmap(bannerApps[position].second)
        wideImage.setOnClickListener {
            applicationViewModel.onApplicationClick(view.context, bannerApps[position].first)
        }

        container.addView(view)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as LinearLayout)
    }
}
