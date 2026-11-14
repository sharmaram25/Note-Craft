package com.notecraft.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.notecraft.app.data.db.NoteKind
import com.notecraft.app.ui.AppRoot
import com.notecraft.app.ui.NavRequest
import com.notecraft.app.ui.theme.NoteCraftTheme
import com.notecraft.app.ui.theme.isAppDark
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val navRequests = MutableSharedFlow<NavRequest>(replay = 1, extraBufferCapacity = 8)
    private val container get() = (application as NoteCraftApp).container
    private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !container.ready.value || !settingsLoaded }
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            settingsLoaded = true
            val dark = isAppDark(s)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(s.secureScreen) {
                if (s.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            NoteCraftTheme(s) {
                AppRoot(
                    container = container,
                    settings = s,
                    navRequests = navRequests,
                    authenticate = ::authenticate,
                    canAuthenticate = ::canAuthenticate,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val repo = container.repository
        when (intent.action) {
            AppIntents.ACTION_NEW_NOTE -> navRequests.tryEmit(NavRequest.NewNote)
            AppIntents.ACTION_NEW_LIST -> navRequests.tryEmit(NavRequest.NewList)
            AppIntents.ACTION_NEW_REMINDER -> navRequests.tryEmit(NavRequest.NewReminder)
            AppIntents.ACTION_SEARCH -> navRequests.tryEmit(NavRequest.Search)
            AppIntents.ACTION_OPEN -> {
                val id = intent.getLongExtra(AppIntents.EXTRA_NOTE_ID, -1)
                if (id > 0) navRequests.tryEmit(NavRequest.OpenNote(id))
                else if (intent.hasExtra(AppIntents.EXTRA_TAB)) navRequests.tryEmit(NavRequest.Tab(intent.getIntExtra(AppIntents.EXTRA_TAB, 0)))
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
                if (text.isNotBlank()) lifecycleScope.launch {
                    val id = repo.createNote(body = text.trim())
                    navRequests.tryEmit(NavRequest.OpenNote(id))
                }
            }
            Intent.ACTION_SEND -> {
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().trim()
                val image = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                lifecycleScope.launch {
                    val id = repo.createNote(title = subject, body = text, kind = NoteKind.NOTE)
                    if (image != null && intent.type?.startsWith("image/") == true) {
                        if (!repo.addAttachment(id, image)) Toast.makeText(this@MainActivity, "Couldn't import that image", Toast.LENGTH_SHORT).show()
                    }
                    navRequests.tryEmit(NavRequest.OpenNote(id))
                }
            }
        }
    }

    fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(title: String, subtitle: String?, onResult: (Boolean) -> Unit) {
        if (!canAuthenticate()) {
            Toast.makeText(this, "Set up a screen lock to use this feature", Toast.LENGTH_LONG).show()
            onResult(false)
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        val isAtLeast33 = Build.VERSION.SDK_INT >= 33
    }
}
