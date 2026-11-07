package com.clipvault.android.ui


import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.clipvault.android.security.BiometricAuthManager
import com.clipvault.android.ui.compose.ClipboardScreen
import com.clipvault.android.ui.viewmodel.ClipboardIntent
import com.clipvault.android.ui.viewmodel.ClipboardViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {


    private val viewModel: ClipboardViewModel by viewModels()
    private lateinit var biometricAuthManager: BiometricAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricAuthManager = BiometricAuthManager(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsState()
                    ClipboardScreen(
                        uiState = state,
                        onIntent = viewModel::onIntent,
                        onOpenVault = {
                            if (state.isVaultUnlocked) {
                                viewModel.onIntent(ClipboardIntent.LockVault)
                            } else {
                                biometricAuthManager.promptBiometric(
                                    activity = this,
                                    onSuccess = { viewModel.onIntent(ClipboardIntent.UnlockVault) },
                                    onError = { /* fallback */ }
                                )
                            }
                        },
                        onOpenSettings = { },
                        onOpenAboutDeveloper = { },
                        onOpenPrivacyPolicy = { }
                    )
                }
            }
        }
    }
}
