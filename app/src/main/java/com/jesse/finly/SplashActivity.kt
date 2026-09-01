package com.jesse.finly

import android.annotation.SuppressLint
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
import com.jesse.finly.database.MinhaBaseDados
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
        val lastLogin = sharedPref.getLong(KEY_LAST_LOGIN, 0L)
        val currentTime = System.currentTimeMillis()
        val email = sharedPref.getString(KEY_EMAIL, "") ?: ""

        if ((lastLogin != 0L) && (currentTime - lastLogin < SESSION_TIMEOUT) && (email.isNotEmpty())) {
            if (email == "CONVIDADO") {
                // Convidado entra diretamente
                startActivity(Intent(this, ResumoActivity::class.java))
                finish()
            } else {
                // Verificar se o utilizador real ainda existe na base de dados
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = MinhaBaseDados.getDatabase(applicationContext)
                    val user = db.utilizadorDao().buscarPorEmail(email)
                    
                    withContext(Dispatchers.Main) {
                        if (user != null) {
                            startActivity(Intent(this@SplashActivity, ResumoActivity::class.java))
                            finish()
                        } else {
                            // Utilizador não existe ou sessão inválida, limpa dados de "sessão"
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
            // Primeira vez ou sessão expirada, vai para o "Ecrã de ‘Login’"
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
