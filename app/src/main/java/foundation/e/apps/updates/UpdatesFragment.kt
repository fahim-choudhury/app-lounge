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

package foundation.e.apps.updates

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import foundation.e.apps.application.model.Application
import foundation.e.apps.application.model.State
import foundation.e.apps.applicationmanager.ApplicationManager
import foundation.e.apps.common.ApplicationListAdapter
import foundation.e.apps.databinding.FragmentUpdatesBinding
import foundation.e.apps.updates.viewmodel.UpdatesViewModel


class UpdatesFragment() : Fragment() {
    private var _binding: FragmentUpdatesBinding? = null
    private val binding get() = _binding!!

    private lateinit var updatesViewModel: UpdatesViewModel
    private var applicationManager: ApplicationManager? = null
    private lateinit var recyclerView: RecyclerView
    private var applicationList = ArrayList<Application>()
    var accentColorOS=0;
    lateinit var reloadProgressBar: ProgressBar

    fun initialise(applicationManager: ApplicationManager, accentColorOS: Int) {
        this.applicationManager = applicationManager
        this.accentColorOS=accentColorOS;
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentUpdatesBinding.inflate(inflater, container, false)

        if (applicationManager == null) {
            return null
        }

        updatesViewModel = ViewModelProvider(this).get(UpdatesViewModel::class.java)

        // Fragment variables
        recyclerView = binding.appList
        reloadProgressBar = binding.progressBar2
        val updateAll = binding.updateAll
        val splashContainer = binding.updatesSplashLayout.splashContainer
        val progressBar = binding.progressBar
        val errorContainer = binding.errorLayout.errorContainer
        val errorDescription = binding.errorLayout.errorDescription
        val errorResolve = binding.errorLayout.errorResolve

        updateAll.setTextColor(accentColorOS)
        progressBar.indeterminateDrawable.colorFilter = PorterDuffColorFilter(accentColorOS, PorterDuff.Mode.SRC_IN)
        reloadProgressBar.indeterminateDrawable.colorFilter = PorterDuffColorFilter(accentColorOS, PorterDuff.Mode.SRC_IN)

        //set accent color to Error button (Retry )
        errorResolve.setTextColor(Color.parseColor("#ffffff"))
        errorResolve.setBackgroundColor(accentColorOS)


        // Initialise UI elements
        updatesViewModel.initialise(applicationManager!!)
        recyclerView.visibility = View.GONE
        updateAll.isEnabled = false
        updateAll.setOnClickListener {
            applicationList.forEach { application ->
                if (application.state == State.NOT_UPDATED) {
                    application.buttonClicked(requireContext(), requireActivity())
                }
            }
        }
        progressBar.visibility = View.VISIBLE
        reloadProgressBar.visibility = View.GONE
        errorContainer.visibility = View.GONE
        splashContainer.visibility = View.GONE
        errorResolve.setOnClickListener {
            updateAll.isEnabled = false
            progressBar.visibility = View.VISIBLE
            updatesViewModel.loadApplicationList(requireContext())
        }

        // Initialise recycler view
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = ApplicationListAdapter(requireActivity(), applicationList, accentColorOS)

        // Bind recycler view adapter to outdated applications list in view model
        updatesViewModel.getApplications().observe(viewLifecycleOwner, Observer {
            if (it != null) {


                applicationList.clear()
                applicationList.addAll(it)
                progressBar.visibility = View.GONE
                reloadProgressBar.visibility=View.GONE
                recyclerView.adapter?.notifyDataSetChanged()
                recyclerView.scrollToPosition(0)
                if (applicationList.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    splashContainer.visibility = View.VISIBLE
                    updateAll.isEnabled = false
                } else {
                    splashContainer.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    updateAll.isEnabled = true
                }
            }
        })

        // Bind to the screen error
        updatesViewModel.getScreenError().observe(viewLifecycleOwner, Observer {
            if (it != null) {
                errorDescription.text = requireActivity().getString(it.description)
                errorContainer.visibility = View.VISIBLE
                updateAll.isEnabled = false
                progressBar.visibility = View.GONE
                reloadProgressBar.visibility = View.GONE

                splashContainer.visibility = View.GONE
                recyclerView.visibility = View.GONE
            } else {
                errorContainer.visibility = View.GONE
            }
        })
        updatesViewModel.loadApplicationList(requireContext())


        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (::updatesViewModel.isInitialized) {
            updatesViewModel.getApplications().value?.let {
                it.forEach { application ->
                    reloadProgressBar.visibility=View.VISIBLE
                    application.checkForStateUpdate(requireContext())
                }
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    reloadProgressBar.visibility=View.GONE
                }, 10000)

            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun decrementApplicationUses() {
        if (::updatesViewModel.isInitialized) {
            updatesViewModel.getApplications().value?.let {
                it.forEach { application ->
                    application.decrementUses()
                }
            }
        }
    }
}