package foundation.e.apps.ui.purchase

import android.annotation.SuppressLint
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
import foundation.e.apps.databinding.FragmentAppPurchaseBinding
import foundation.e.apps.ui.MainActivityViewModel

/**
 * A simple [Fragment] subclass.
 * Use the [AppPurchaseFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AppPurchaseFragment : Fragment() {
    private lateinit var binding: FragmentAppPurchaseBinding

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

    @SuppressLint("SetJavaScriptEnabled")
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
                isAppPurchased = isAppPurchased(url)
            }

            private fun isAppPurchased(url: String): Boolean {
                val urlElementsOfPurchasedApp = listOf(
                    "https://play.google.com/store/apps/details",
                    "raii",
                    "raboi",
                    "rasi",
                    "rapt"
                )

                urlElementsOfPurchasedApp.forEach {
                    if (!url.contains(it)) {
                        return false
                    }
                }

                return true
            }
        }

        binding.playStoreWebView.apply {
            settings.apply {
                allowContentAccess = true
                databaseEnabled = true
                domStorageEnabled = true
                javaScriptEnabled = true // Google Play page is tested to not work otherwise
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
