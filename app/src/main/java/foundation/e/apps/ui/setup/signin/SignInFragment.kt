package foundation.e.apps.ui.setup.signin

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.databinding.FragmentSignInBinding
import foundation.e.apps.data.login.LoginViewModel
import foundation.e.apps.di.CommonUtilsModule.safeNavigate

@AndroidEntryPoint
class SignInFragment : Fragment(R.layout.fragment_sign_in) {
    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by lazy {
        ViewModelProvider(requireActivity())[LoginViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSignInBinding.bind(view)

        binding.googleBT.setOnClickListener {
            view.findNavController()
                .safeNavigate(R.id.signInFragment, R.id.action_signInFragment_to_googleSignInFragment)
        }

        binding.anonymousBT.setOnClickListener {
            viewModel.initialAnonymousLogin {
                view.findNavController()
                    .safeNavigate(R.id.signInFragment, R.id.action_signInFragment_to_homeFragment)
            }
        }

        binding.noGoogleBT.setOnClickListener {
            viewModel.initialNoGoogleLogin {
                view.findNavController()
                    .safeNavigate(R.id.signInFragment, R.id.action_signInFragment_to_homeFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
