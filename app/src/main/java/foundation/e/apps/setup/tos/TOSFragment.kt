package foundation.e.apps.setup.tos

import android.os.Bundle
import android.text.Html
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.databinding.FragmentTosBinding

@AndroidEntryPoint
class TOSFragment : Fragment(R.layout.fragment_tos) {

    private var _binding: FragmentTosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TOSViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTosBinding.bind(view)
        var canNavigate = false

        viewModel.tocStatus.observe(viewLifecycleOwner) {
            if (canNavigate) {
                view.findNavController().navigate(R.id.action_TOSFragment_to_signInFragment)
            }

            if (it == true) {
                binding.TOSWarning.visibility = View.GONE
                binding.TOSButtons.visibility = View.GONE
                binding.toolbar.visibility = View.VISIBLE
                binding.acceptDateTV.visibility = View.VISIBLE
            }
        }

        val tosHtml = resources.openRawResource(R.raw.terms_of_use)
        val tosHtmlData = tosHtml.reader().readText()
        binding.TOSTextView.text = Html.fromHtml(
            tosHtmlData,
            Html.FROM_HTML_MODE_COMPACT and
                Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING and
                Html.FROM_HTML_OPTION_USE_CSS_COLORS
        )

        binding.toolbar.setNavigationOnClickListener {
            view.findNavController().navigateUp()
        }

        binding.disagreeBT.setOnClickListener {
            activity?.finish()
        }

        binding.agreeBT.setOnClickListener {
            viewModel.saveTOCStatus(true)
            canNavigate = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
