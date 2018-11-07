package io.eelo.appinstaller.categories.activity

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import io.eelo.appinstaller.R
import io.eelo.appinstaller.application.model.Application
import io.eelo.appinstaller.application.model.InstallManagerGetter
import io.eelo.appinstaller.categories.activity.viewModel.CategoryViewModel
import io.eelo.appinstaller.categories.model.Category
import io.eelo.appinstaller.common.ApplicationListAdapter
import io.eelo.appinstaller.utils.Common
import io.eelo.appinstaller.utils.Constants.CATEGORY_KEY

class CategoryActivity : AppCompatActivity() {

    private lateinit var category: Category
    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private var applicationList = ArrayList<Application>()
    private val installManagerGetter = InstallManagerGetter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        category = intent.getSerializableExtra(CATEGORY_KEY) as Category
        supportActionBar?.title = category.title

        categoryViewModel = ViewModelProviders.of(this).get(CategoryViewModel::class.java)

        Common.EXECUTOR.submit {
            val installManager = installManagerGetter.connectAndGet(this)
            categoryViewModel.initialise(installManager, category.id)
            categoryViewModel.loadApplications(this)
        }

        recyclerView = findViewById(R.id.app_list)
        recyclerView.visibility = View.GONE
        progressBar = findViewById(R.id.progress_bar)
        progressBar.visibility = View.VISIBLE

        // Initialise recycler view
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ApplicationListAdapter(this, applicationList)

        // Bind to the list of applications in this activity's category
        categoryViewModel.getApplications().observe(this, Observer {
            applicationList.clear()
            applicationList.addAll(it!!)
            progressBar.visibility = View.GONE
            recyclerView.adapter.notifyDataSetChanged()
            recyclerView.visibility = View.VISIBLE
            recyclerView.scrollToPosition(0)
        })
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            android.R.id.home ->
                finish()
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        installManagerGetter.disconnect(this)
    }
}
