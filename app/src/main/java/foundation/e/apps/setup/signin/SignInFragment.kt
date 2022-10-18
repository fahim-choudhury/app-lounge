package foundation.e.apps.setup.signin

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.databinding.FragmentSignInBinding
import foundation.e.apps.utils.enums.User

@AndroidEntryPoint
class SignInFragment : Fragment(R.layout.fragment_sign_in) {
    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SignInViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSignInBinding.bind(view)

        binding.googleBT.setOnClickListener {
            view.findNavController().navigate(R.id.googleSignInFragment)
        }

        binding.anonymousBT.setOnClickListener {
            viewModel.saveUserType(User.ANONYMOUS)
        }

        viewModel.userType.observe(viewLifecycleOwner) {
            if (it.isNotBlank()) {
                when (User.valueOf(it)) {
                    User.ANONYMOUS -> {
                        view.findNavController()
                            .navigate(R.id.action_signInFragment_to_homeFragment)
                    }
                    User.GOOGLE -> {
                        view.findNavController()
                            .navigate(R.id.action_signInFragment_to_homeFragment)
                    }
                    else -> {
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
