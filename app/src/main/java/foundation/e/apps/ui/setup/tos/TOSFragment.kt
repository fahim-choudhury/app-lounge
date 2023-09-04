package foundation.e.apps.ui.setup.tos

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.databinding.FragmentTosBinding
import org.jsoup.Jsoup

@AndroidEntryPoint
class TOSFragment : Fragment(R.layout.fragment_tos) {

    private var _binding: FragmentTosBinding? = null
    private val binding get() = _binding!!

    private var webView: WebView? = null

    private val viewModel: TOSViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTosBinding.bind(view)
        webView = binding.tosWebView

        viewModel.tocStatus.observe(viewLifecycleOwner) {
            if (it == true && webView != null) {
                binding.TOSWarning.visibility = View.GONE
                binding.TOSButtons.visibility = View.GONE
                binding.toolbar.visibility = View.VISIBLE
                binding.acceptDateTV.visibility = View.VISIBLE
                val constraintSet = ConstraintSet()
                constraintSet.clone(binding.root)
                constraintSet.connect(
                    webView!!.id,
                    ConstraintSet.TOP,
                    binding.acceptDateTV.id,
                    ConstraintSet.BOTTOM,
                    20
                )
                constraintSet.applyTo(binding.root)
            }
        }

        loadTos()

        binding.toolbar.setNavigationOnClickListener {
            view.findNavController().navigateUp()
        }

        binding.disagreeBT.setOnClickListener {
            activity?.finish()
        }

        binding.agreeBT.setOnClickListener {
            viewModel.saveTOCStatus(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        /*
         * Fix WebView memory leaks. https://stackoverflow.com/a/19391512
         * Issue: https://gitlab.e.foundation/e/os/backlog/-/issues/485
         */
        webView?.run {
            setOnScrollChangeListener(null)
            removeAllViews()
            destroy()
        }
        webView = null
        _binding = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loadTos()
    }

    private fun loadTos() {
        val isNightMode =
            requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        var css = "terms_of_use.css"

        if (isNightMode) {
            css = "terms_of_use_dark.css"
        }

        val tosHtml = resources.openRawResource(R.raw.terms_of_use).reader().readText()
        val body = Jsoup.parse(tosHtml).body()
        val sb = StringBuilder()
            .append(
                "<HTML><HEAD><LINK href=\"$css\" rel=\"stylesheet\"/></HEAD>"
            )
            .append(body.toString())
            .append("</HTML>")
        webView?.loadDataWithBaseURL(
            "file:///android_asset/",
            sb.toString(),
            "text/html",
            "utf-8",
            null
        )

        webView?.setOnScrollChangeListener { _, scrollX, scrollY, _, _ ->
            if (scrollX == 0 && scrollY == 0 && viewModel.tocStatus.value == true) {
                binding.acceptDateTV.visibility = View.VISIBLE
            } else {
                binding.acceptDateTV.visibility = View.GONE
            }
        }
    }
}
