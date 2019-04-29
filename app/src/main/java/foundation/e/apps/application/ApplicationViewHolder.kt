package foundation.e.apps.application

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.support.design.widget.Snackbar
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import foundation.e.apps.R
import foundation.e.apps.application.model.Application
import foundation.e.apps.application.model.ApplicationStateListener
import foundation.e.apps.application.model.Downloader
import foundation.e.apps.application.model.State
import foundation.e.apps.application.model.data.BasicData
import foundation.e.apps.application.viewmodel.ApplicationViewModel
import foundation.e.apps.utils.Common
import foundation.e.apps.utils.Common.toMiB
import foundation.e.apps.utils.Error
import foundation.e.apps.utils.Execute
import kotlinx.android.synthetic.main.application_list_item.view.*
import kotlinx.android.synthetic.main.install_button_layout.view.*

class ApplicationViewHolder(private val activity: Activity, private val view: View) :
        RecyclerView.ViewHolder(view),
        ApplicationStateListener,
        Downloader.DownloadProgressCallback,
        BasicData.IconLoaderCallback {

    private val icon: ImageView = view.app_icon
    private val title: TextView = view.app_title
    private val author: TextView = view.app_author
    private val ratingBar: RatingBar = view.app_rating_bar
    private val rating: TextView = view.app_rating
    private val privacyScore: TextView = view.app_privacy_score
    private val installButton: Button = view.app_install
    private var application: Application? = null
    private val applicationViewModel = ApplicationViewModel()
    private var downloader: Downloader? = null

    init {
        view.setOnClickListener {
            if (application != null) {
                applicationViewModel.onApplicationClick(view.context, application!!)
            }
        }
        installButton.setOnClickListener {
            if (application?.fullData != null &&
                    application!!.fullData!!.getLastVersion() == null) {
                Snackbar.make(view, activity.getString(
                        Error.APK_UNAVAILABLE.description),
                        Snackbar.LENGTH_LONG).show()
            } else {
                application?.buttonClicked(activity, activity)
            }
        }
    }

    fun createApplicationView(app: Application) {
        this.application?.removeListener(this)
        this.application = app
        icon.setImageDrawable(view.context.resources.getDrawable(R.drawable.ic_app_default))
        application!!.loadIcon(this)
        application!!.addListener(this)
        title.text = application!!.basicData!!.name
        author.text = application!!.basicData!!.author
        ratingBar.rating = application!!.basicData!!.ratings.rating
        if (application!!.basicData!!.ratings.rating != -1f) {
            rating.text = application!!.basicData!!.ratings.rating.toString()
        } else {
            rating.text = activity.getString(R.string.not_available)
        }
        if (application!!.basicData!!.privacyRating != null && application!!.basicData!!.privacyRating != -1f) {
            privacyScore.text = application!!.basicData!!.privacyRating.toString()
        } else {
            privacyScore.text = activity.getString(R.string.not_available)
        }
        stateChanged(application!!.state)
    }

    override fun onIconLoaded(application: Application, bitmap: Bitmap) {
        if (this.application != null && application == this.application) {
            icon.setImageBitmap(bitmap)
        }
    }

    override fun stateChanged(state: State) {
        Execute({}, {
            installButton.text = activity.getString(state.installButtonTextId)
            when (state) {
                State.INSTALLED -> {
                    installButton.isEnabled =
                            Common.appHasLaunchActivity(activity, application!!.packageName)
                }
                State.INSTALLING -> {
                    installButton.isEnabled = false
                }
                else -> {
                    installButton.isEnabled = true
                }
            }
        })
    }

    override fun downloading(downloader: Downloader) {
        this.downloader = downloader
        this.downloader!!.addListener(this)
    }

    @SuppressLint("SetTextI18n")
    override fun notifyDownloadProgress(count: Int, total: Int) {
        installButton.text = ((toMiB(count) / toMiB(total)) * 100).toInt().toString() + "%"
    }

    override fun anErrorHasOccurred(error: Error) {
        Snackbar.make(activity.findViewById(R.id.container),
                activity.getString(error.description),
                Snackbar.LENGTH_LONG).show()
    }

    fun onViewRecycled() {
        downloader?.removeListener(this)
        downloader = null
    }
}
