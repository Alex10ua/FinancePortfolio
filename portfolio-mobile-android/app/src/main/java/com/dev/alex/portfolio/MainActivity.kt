package com.dev.alex.portfolio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity

/** A FragmentActivity because BiometricPrompt needs one. */
class MainActivity : FragmentActivity() {
    private val container: AppContainer
        get() = (application as PortfolioApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container.biometrics.attach(this)
        setContent { AppRoot() }
    }

    override fun onDestroy() {
        container.biometrics.detach(this)
        super.onDestroy()
    }
}
