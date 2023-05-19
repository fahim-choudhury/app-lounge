package foundation.e.apps.ui.application.subFrags

import android.os.Bundle
import android.text.Html
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.databinding.FragmentDescriptionBinding

@AndroidEntryPoint
class DescriptionFragment : Fragment(R.layout.fragment_description) {

    private var _binding: FragmentDescriptionBinding? = null
    private val binding get() = _binding!!

    private val args: DescriptionFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDescriptionBinding.bind(view)

        binding.toolbar.apply {
            setNavigationOnClickListener {
                view.findNavController().navigateUp()
            }
        }

        binding.descriptionTV.text = Html.fromHtml(args.description, Html.FROM_HTML_MODE_COMPACT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
