/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
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

package foundation.e.apps.ui.setup.signin.google

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import app.lounge.storage.cache.configurations
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.gplay.utils.AC2DMUtil
import foundation.e.apps.presentation.login.LoginViewModel
import foundation.e.apps.databinding.FragmentGoogleSigninBinding
import foundation.e.apps.di.CommonUtilsModule.safeNavigate

@AndroidEntryPoint
class GoogleSignInFragment : Fragment(R.layout.fragment_google_signin) {
    private var _binding: FragmentGoogleSigninBinding? = null
    private val binding get() = _binding!!

    // protected to avoid SyntheticAccessor
    protected val viewModel: LoginViewModel by lazy {
        ViewModelProvider(requireActivity())[LoginViewModel::class.java]
    }

    companion object {
        private const val EMBEDDED_SETUP_URL =
            "https://accounts.google.com/EmbeddedSetup/identifier?flowName=EmbeddedSetupAndroid"
        private const val AUTH_TOKEN = "oauth_token"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGoogleSigninBinding.bind(view)
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.acceptThirdPartyCookies(binding.webview)
        cookieManager.setAcceptThirdPartyCookies(binding.webview, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.webview.settings.safeBrowsingEnabled = false
        }

        binding.webview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val cookies = CookieManager.getInstance().getCookie(url) ?: return
                val cookieMap = AC2DMUtil.parseCookieString(cookies)
                if (cookieMap.isNotEmpty() && cookieMap[AUTH_TOKEN] != null) {
                    val oauthToken = cookieMap[AUTH_TOKEN] ?: ""
                    view.evaluateJavascript(
                        "document.querySelector(\"div[data-profile-identifier]\").textContent;"
                    ) {
                        val email = it.replace("\"".toRegex(), "")
                        viewModel.initialGoogleLogin(email, oauthToken) {

                            view.findNavController()
                                .safeNavigate(
                                    R.id.googleSignInFragment,
                                    R.id.action_googleSignInFragment_to_homeFragment
                                )
                        }
                    }
                }
            }
        }

        binding.webview.apply {
            settings.apply {
                allowContentAccess = true
                databaseEnabled = true
                domStorageEnabled = true
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            loadUrl(EMBEDDED_SETUP_URL)
        }

        binding.toolbar.setNavigationOnClickListener {
            it.findNavController().navigate(R.id.action_googleSignInFragment_to_signInFragment)
        }
    }

    override fun onDestroyView() {
        binding.root.removeAllViews()
        binding.webview.destroy()
        super.onDestroyView()
        _binding = null
    }
}
