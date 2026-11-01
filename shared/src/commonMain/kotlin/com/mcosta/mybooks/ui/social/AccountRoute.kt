package com.mcosta.mybooks.ui.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mcosta.mybooks.social.SocialServices
import com.mcosta.mybooks.ui.LocalPlatformServices

/** The community account: the sign-in flow until there's a profile, then the account screen. */
@Composable
fun AccountRoute(services: SocialServices, onBack: () -> Unit) {
    val signIn = viewModel { SignInViewModel(services.auth, services.profiles) }
    val state by signIn.state.collectAsStateWithLifecycle()
    if (state.step == SignInStep.DONE) {
        val shrinker = LocalPlatformServices.current.shrinker
        AccountScreen(viewModel { AccountViewModel(services, shrinker) }, onBack = onBack)
    } else {
        SignInScreen(signIn, onBack = onBack, onDone = {})
    }
}
