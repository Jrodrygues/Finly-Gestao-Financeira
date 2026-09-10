package com.jesse.finly

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.utils.BiometricUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "FinlyAppPrefs"
        private const val KEY_EMAIL = "EMAIL"
        private const val KEY_LAST_LOGIN = "LAST_LOGIN_TIMESTAMP"
        private const val SESSION_TIMEOUT = 15L * 24 * 60 * 60 * 1000 // 15 Dias
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Força o modo Light para combinar com a tela de ‘login’
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        
        // Instala a Splash Screen da API 31+ ANTES do super.onCreate
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        // Limpeza de segurança: Garante que documentos do modo convidado não fiquem guardados na nuvem
        lifecycleScope.launch(Dispatchers.IO) {
            FirebaseManager.limparConvidadoDoFirestore()
        }

        // Aguarda 2 segundos e decide o ecrã seguinte
        Handler(Looper.getMainLooper()).postDelayed(
            {
                checkSessionAndNavigate()
            },
            2000,
        )
    }

    private fun checkSessionAndNavigate() {
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("DARK_MODE", false)
        AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        val lastLogin = sharedPref.getLong(KEY_LAST_LOGIN, 0L)
        val currentTime = System.currentTimeMillis()
        val email = sharedPref.getString(KEY_EMAIL, "") ?: ""
        val biometriaAtiva = sharedPref.getBoolean("pref_biometric_ativa", false)

        val navigateToMain = {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1)

            val targetIntent = when (intent.getStringExtra("TARGET_ACTIVITY")) {
                "DETALHES" -> Intent(this, DetalhesPageActivity::class.java).apply {
                    putExtras(this@SplashActivity.intent)
                }
                "MAIN" -> Intent(this, MainActivity::class.java)
                else -> Intent(this, ResumoActivity::class.java)
            }

            if (biometriaAtiva && BiometricUtils.isBiometricAvailable(this)) {
                BiometricUtils.promptBiometria(
                    this,
                    title = getString(R.string.dialog_biometria_titulo),
                    subtitle = getString(R.string.dialog_biometria_msg),
                    onSuccess = {
                        startActivity(targetIntent)
                        finish()
                    },
                    onError = {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.dialog_biometria_titulo))
                            .setMessage(getString(R.string.dialog_biometria_msg))
                            .setPositiveButton(getString(R.string.btn_tentar_novamente)) { _, _ ->
                                checkSessionAndNavigate()
                            }
                            .setNegativeButton(getString(R.string.btn_sair)) { _, _ ->
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    }
                )
            } else {
                startActivity(targetIntent)
                finish()
            }
        }

        if ((lastLogin != 0L) && (currentTime - lastLogin < SESSION_TIMEOUT) && (email.isNotEmpty())) {
            if (email == "CONVIDADO") {
                navigateToMain()
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = MinhaBaseDados.getDatabase(applicationContext)
                    val user = db.utilizadorDao().buscarPorEmail(email)
                    
                    withContext(Dispatchers.Main) {
                        if (user != null) {
                            sharedPref.edit {
                                putBoolean("DARK_MODE", user.darkMode)
                                putBoolean("NOTIFICATIONS", user.notifications)
                                putBoolean("pref_biometric_ativa", user.biometricAtiva)
                            }
                            navigateToMain()
                        } else {
                            sharedPref.edit {
                                remove(KEY_EMAIL)
                                remove(KEY_LAST_LOGIN)
                            }
                            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                            finish()
                        }
                    }
                }
            }
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
