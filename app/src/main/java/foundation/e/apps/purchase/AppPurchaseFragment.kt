package foundation.e.apps.purchase

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.databinding.FragmentAppPurchaseBinding

/**
 * A simple [Fragment] subclass.
 * Use the [AppPurchaseFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AppPurchaseFragment : Fragment() {
    private lateinit var binding: FragmentAppPurchaseBinding

    companion object {
        private const val TAG = "AppPurchaseFragment"
    }

    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    private var isAppPurchased = false
    private var packageName = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentAppPurchaseBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        packageName = arguments?.getString("package_name") ?: ""
        val url = "https://play.google.com/store/apps/details?id=$packageName"
        setupWebView(url)
    }

    private fun setupWebView(url: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.acceptThirdPartyCookies(binding.playStoreWebView)
        cookieManager.setAcceptThirdPartyCookies(binding.playStoreWebView, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.playStoreWebView.settings.safeBrowsingEnabled = false
        }

        binding.playStoreWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("https://play.google.com/store/apps/details") && url.contains("raii") &&
                    url.contains("raboi") && url.contains("rasi") && url.contains("rapt")
                ) {
                    isAppPurchased = true
                }
            }
        }

        binding.playStoreWebView.apply {
            settings.apply {
                allowContentAccess = true
                databaseEnabled = true
                domStorageEnabled = true
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            loadUrl(url)
        }
    }

    override fun onDestroyView() {
        if (isAppPurchased) {
            mainActivityViewModel.isAppPurchased.value = packageName
        } else {
            mainActivityViewModel.purchaseDeclined.value = packageName
        }
        super.onDestroyView()
    }
}
