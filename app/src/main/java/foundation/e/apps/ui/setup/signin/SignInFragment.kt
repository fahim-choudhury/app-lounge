package foundation.e.apps.ui.setup.signin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.data.login.LoginViewModel
import foundation.e.apps.databinding.FragmentSignInBinding
import foundation.e.apps.di.CommonUtilsModule.safeNavigate
import foundation.e.apps.utils.showGoogleSignInAlertDialog
import timber.log.Timber

@AndroidEntryPoint
class SignInFragment : Fragment(R.layout.fragment_sign_in) {
    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by lazy {
        ViewModelProvider(requireActivity())[LoginViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSignInBinding.inflate(inflater, container, false)
        val view = binding.root
        _binding!!.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    Login(Modifier.fillMaxSize())
                }
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.googleBT.setOnClickListener {
            context?.showGoogleSignInAlertDialog(
                { navigateToGoogleSignInFragment() },
                { }
            )
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

    @Composable
    private fun Login(modifier: Modifier) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.Companion.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Image(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_launcher),
                        contentDescription = "",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.width(108.dp).height(108.dp)
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        text = "App Lounge",
                        fontSize = 16.sp,
                        style = TextStyle(color = Color.DarkGray, fontWeight = FontWeight.Bold)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WelcomeText()
                SigninButtons()
                Text(stringResource(R.string.or), modifier = Modifier.padding(top = 10.dp))
                OutlinedButton(
                    modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 20.dp)
                        .fillMaxWidth(),
                    border = BorderStroke(1.dp, colorResource(R.color.colorAccent)),
                    onClick = {
                        navigateToGoogleSignInFragment()
                    },
                    shape = RoundedCornerShape(5.dp)
                ) {
                    SiginInButtonText(stringResource(R.string.pwa_and_open_source_apps))
                }
            }
        }
    }

    @OptIn(ExperimentalTextApi::class, ExperimentalUnitApi::class)
    @Composable
    private fun SigninButtons() {
        OutlinedButton(
            modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 20.dp).fillMaxWidth(),
            border = BorderStroke(1.dp, colorResource(R.color.colorAccent)),
            onClick = {
                navigateToGoogleSignInFragment()
            },
            shape = RoundedCornerShape(5.dp)
        ) {
            SiginInButtonText("Sign in with Google")
        }
        OutlinedButton(
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 5.dp).fillMaxWidth(),
            border = BorderStroke(1.dp, colorResource(R.color.colorAccent)),
            onClick = {
                viewModel.initialAnonymousLogin {
                    Timber.d("On user saved...")
                    view?.findNavController()
                        ?.safeNavigate(
                            R.id.signInFragment,
                            R.id.action_signInFragment_to_homeFragment
                        )
                }
            },
            shape = RoundedCornerShape(5.dp)
        ) {
            Row {
                Image(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_incognito),
                    contentDescription = "",
                    modifier = Modifier.padding(end = 10.dp)
                )
                SiginInButtonText("Anonymous login")
            }
        }
    }

    @OptIn(ExperimentalTextApi::class, ExperimentalUnitApi::class)
    @Composable
    private fun SiginInButtonText(text: String) {
        Text(
            text.uppercase(),
            color = colorResource(R.color.colorAccent),
            fontFamily = FontFamily(
                Font(familyName = DeviceFontFamilyName("sans-serif"))
            ),
            style = TextStyle(letterSpacing = TextUnit(1.7f, TextUnitType.Sp)),
            fontWeight = FontWeight.SemiBold
        )
    }

    @Composable
    private fun WelcomeText() {
        Text(
            "Welcome",
            style = TextStyle(
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            stringResource(R.string.sign_in_desc),
            modifier = Modifier.padding(horizontal = 70.dp),
            style = TextStyle(
                color = Color.Black,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun navigateToGoogleSignInFragment() {
        view?.findNavController()
            ?.safeNavigate(R.id.signInFragment, R.id.action_signInFragment_to_googleSignInFragment)
    }
}
