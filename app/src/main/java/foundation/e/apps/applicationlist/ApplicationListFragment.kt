package foundation.e.apps.applicationlist

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.R
import foundation.e.apps.api.fused.FusedAPIInterface
import foundation.e.apps.api.fused.data.Origin
import foundation.e.apps.applicationlist.model.ApplicationListRVAdapter
import foundation.e.apps.databinding.FragmentApplicationListBinding
import javax.inject.Inject

@AndroidEntryPoint
class ApplicationListFragment : Fragment(R.layout.fragment_application_list), FusedAPIInterface {

    private val args: ApplicationListFragmentArgs by navArgs()
    private val TAG = ApplicationListFragment::class.java.simpleName

    @Inject
    lateinit var gson: Gson

    private val applicationListViewModel: ApplicationListViewModel by viewModels()
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    private var _binding: FragmentApplicationListBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentApplicationListBinding.bind(view)

        // Set title
        binding.toolbar.apply {
            title = args.translation
            setNavigationOnClickListener {
                view.findNavController().navigate(R.id.categoriesFragment)
            }
        }

        val recyclerView = binding.recyclerView
        val listAdapter = ApplicationListRVAdapter(this)
        recyclerView.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(view.context)
        }

        applicationListViewModel.getList(args.category)
        applicationListViewModel.list.observe(viewLifecycleOwner, {
            listAdapter.setData(it)
            binding.progressBar.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getApplication(
        id: String,
        name: String,
        packageName: String,
        versionCode: Int,
        offerType: Int?,
        origin: Origin?
    ) {
        val data = mainActivityViewModel.authData.value?.let {
            gson.fromJson(
                it,
                AuthData::class.java
            )
        }
        val offer = offerType ?: 0
        val org = origin ?: Origin.CLEANAPK
        data?.let {
            applicationListViewModel.getApplication(
                id,
                name,
                packageName,
                versionCode,
                offer,
                it,
                org
            )
        }
    }
}
