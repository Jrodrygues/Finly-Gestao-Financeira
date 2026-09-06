package com.jesse.finly

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.jesse.finly.database.FirebaseManager
import kotlinx.coroutines.tasks.await
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.LoginBinding
import com.jesse.finly.notifications.NotificationHelper
import com.jesse.finly.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: LoginBinding

    companion object {
        private const val PREFS_NAME = "FinlyAppPrefs"
        private const val KEY_NAME = "NAME"
        private const val KEY_EMAIL = "EMAIL"
        private const val KEY_LAST_LOGIN = "LAST_LOGIN_TIMESTAMP"
        private const val SESSION_TIMEOUT = 15L * 24 * 60 * 60 * 1000 // 15 Dias
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Forçar modo Light para o design "Soft"
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        
        super.onCreate(savedInstanceState)
        
        // 2. Ativar visual uniforme (Edge-to-Edge)
        enableEdgeToEdge()
        
        // 3. Garantir que os ícones do sistema (hora, botões) sejam visíveis (escuros) no fundo, claro.
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        binding = LoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ajustar Insets para que o conteúdo não fique sob as barras do sistema
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }


        verificarSessaoAtiva()

        binding.loginbtn.setOnClickListener {
            fazerLogin()
        }

        binding.btnRegistar.setOnClickListener {
            val intent = Intent(this, RegistoActivity::class.java)
            intent.putExtra("isNewUser", true)
            startActivity(intent)
        }

        binding.btnEsqueciSenha.setOnClickListener {
            mostrarDialogRecuperarSenha()
        }

        binding.btnConvidado.setOnClickListener {
            entrarComoConvidado()
        }

        formatarTextoCriarConta()

        // Carregar endereço eletrónico lembrado se existir
        carregarEmailLembrado()
    }

    private fun formatarTextoCriarConta() {
        val textoCompleto = getString(R.string.criar_conta_link)
        val spannable = SpannableString(textoCompleto)

        val indexCrieAqui = textoCompleto.indexOf("Crie aqui")
        if (indexCrieAqui != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.textColorSecondary)),
                0,
                indexCrieAqui,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorPrimary)),
                indexCrieAqui,
                textoCompleto.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                indexCrieAqui,
                textoCompleto.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.btnRegistar.text = spannable
    }

    private fun carregarEmailLembrado() {
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val emailLembrado = sharedPref.getString("EMAIL_LEMBRADO", "")
        if (!emailLembrado.isNullOrEmpty()) {
            binding.emailText.setText(emailLembrado)
            binding.cbLembrarEmail.isChecked = true
        }
    }

    override fun onResume() {
        super.onResume()
        // Verificar se há um endereço eletrónico acabado de registar para preencher
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val tempEmail = sharedPref.getString("TEMP_EMAIL", "")
        if (!tempEmail.isNullOrEmpty()) {
            binding.emailText.setText(tempEmail)
            // Limpar para não preencher sempre
            sharedPref.edit { remove("TEMP_EMAIL") }
        }
    }

    private fun verificarSessaoAtiva() {
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastLogin = sharedPref.getLong(KEY_LAST_LOGIN, 0L)
        val currentTime = System.currentTimeMillis()
        val email = sharedPref.getString(KEY_EMAIL, "") ?: ""

        if (lastLogin != 0L && (currentTime - lastLogin < SESSION_TIMEOUT) && email.isNotEmpty() && email != "CONVIDADO") {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(applicationContext)
                val user = db.utilizadorDao().buscarPorEmail(email)
                withContext(Dispatchers.Main) {
                    if (user != null) {
                        prosseguirParaApp()
                    } else {
                        sharedPref.edit { clear() }
                    }
                }
            }
        }
    }

    private fun fazerLogin() {
        val emailInput = binding.emailText.text.toString().trim()
        val senha = binding.senhaText.text.toString().trim()

        if (emailInput.isEmpty() || senha.isEmpty()) {
            showToast(getString(R.string.toast_introduza_email_senha))
            return
        }

        val email = emailInput.lowercase()
        setLoading(isLoading = true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Tentar ‘Login’ no Firebase primeiro
                val auth = FirebaseAuth.getInstance()
                val result = auth.signInWithEmailAndPassword(email, senha).await()
                
                if (result.user != null) {
                    // ‘Login’ Firebase sucesso
                    val db = MinhaBaseDados.getDatabase(this@LoginActivity)
                    var utilizador = db.utilizadorDao().buscarPorEmail(email)
                    
                    // Se não existe localmente (ex: mudou de telemóvel), descarregar do Firestore
                    if (utilizador == null) {
                        utilizador = FirebaseManager.obterPerfilDoFirestore(email)
                        utilizador?.let { db.utilizadorDao().insertUtilizador(it) }
                    }

                    withContext(Dispatchers.Main) {
                        setLoading(false)
                        // APLICAR PREFERÊNCIAS Baixadas (TEMA, NOTIFICAÇÕES E BIOMETRIA)
                        utilizador?.let { u ->
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                                putBoolean("DARK_MODE", u.darkMode)
                                putBoolean("NOTIFICATIONS", u.notifications)
                                putBoolean("pref_biometric_ativa", u.biometricAtiva)
                                putString("MOEDA", u.moeda)
                            }
                            val currentMode = AppCompatDelegate.getDefaultNightMode()
                            val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                            val isCurrentlyDark = if (currentMode == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) isSystemDark else (currentMode == AppCompatDelegate.MODE_NIGHT_YES)

                            if (isCurrentlyDark != u.darkMode) {
                                val desiredMode = if (u.darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                                AppCompatDelegate.setDefaultNightMode(desiredMode)
                            }
                            if (u.notifications) {
                                NotificationHelper.agendarWorkerNotificacoes(this@LoginActivity)
                            }
                        }

                        finalizarSessaoLogin(email, utilizador?.nome ?: "Utilizador")
                    }
                }
            } catch (_: Exception) {
                val db = MinhaBaseDados.getDatabase(this@LoginActivity)
                val utilizadorLocal = db.utilizadorDao().validarLogin(email, senha)
                
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    if (utilizadorLocal != null) {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                            putBoolean("DARK_MODE", utilizadorLocal.darkMode)
                            putBoolean("NOTIFICATIONS", utilizadorLocal.notifications)
                            putBoolean("pref_biometric_ativa", utilizadorLocal.biometricAtiva)
                            putString("MOEDA", utilizadorLocal.moeda)
                        }
                        finalizarSessaoLogin(email, utilizadorLocal.nome)
                    } else {
                        // Se não encontrou nem localmente, nem na nuvem, dar erro original
                        showToast(getString(R.string.toast_credenciais_incorretas))
                    }
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.loginbtn.isEnabled = !isLoading
        binding.btnConvidado.isEnabled = !isLoading
        binding.btnRegistar.isEnabled = !isLoading
    }

    private fun mostrarDialogRecuperarSenha() {
        val emailAtual = binding.emailText.text.toString().trim()
        val view = layoutInflater.inflate(R.layout.dialog_recuperar_senha, binding.root as? android.view.ViewGroup, false)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.recoverEmailText)
        input.setText(emailAtual)

        // Criar o ícone com verde manualmente para garantir visibilidade no tema claro
        val icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_help)?.mutate()
        icon?.setTint(androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_recuperar_senha_titulo))
            .setIcon(icon)
            .setMessage(getString(R.string.introduza_email_recuperacao))
            .setView(view)
            .setPositiveButton("Enviar") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) {
                    enviarEmailRecuperacao(email)
                } else {
                    showToast("Introduza um e-mail válido")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarEmailRecuperacao(email: String) {
        val auth = FirebaseAuth.getInstance()
        auth.useAppLanguage() // Configura o idioma do endereço eletrónico para o idioma do telemóvel do utilizador
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                auth.sendPasswordResetEmail(email).await()
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.email_recuperacao_enviado))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.erro_recuperacao))
                }
            }
        }
    }

    private fun finalizarSessaoLogin(email: String, nome: String) {
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPref.edit {
            putString(KEY_NAME, nome)
            putString(KEY_EMAIL, email)
            putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            
            if (binding.cbLembrarEmail.isChecked) {
                putString("EMAIL_LEMBRADO", email)
            } else {
                remove("EMAIL_LEMBRADO")
            }
        }
        prosseguirParaApp()
    }

    private fun entrarComoConvidado() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_modo_convidado_titulo))
            .setMessage(getString(R.string.msg_modo_convidado_aviso))
            .setPositiveButton(getString(R.string.btn_continuar_convidado)) { _, _ ->
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                    putString(KEY_EMAIL, "CONVIDADO")
                    putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
                }
                prosseguirParaApp()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val btnNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        btnPositive?.apply {
            setBackgroundColor(ContextCompat.getColor(this@LoginActivity, R.color.colorPrimary))
            setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.white))
            val dpHorizontal = (16 * resources.displayMetrics.density).toInt()
            val dpVertical = (8 * resources.displayMetrics.density).toInt()
            setPadding(dpHorizontal, dpVertical, dpHorizontal, dpVertical)
        }

        btnNegative?.apply {
            setTextColor(ContextCompat.getColor(this@LoginActivity, R.color.textColorSecondary))
        }
    }

    private fun prosseguirParaApp() {
        // Agora vamos diretamente para o Resumo Mensal (ResumoActivity)
        startActivity(Intent(this, ResumoActivity::class.java))
        finish()
    }
}
