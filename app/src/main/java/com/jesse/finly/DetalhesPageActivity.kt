package com.jesse.finly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.DetalhesBinding
import com.jesse.finly.models.Transacao
import com.jesse.finly.utils.FinanceiroUtils
import com.jesse.finly.utils.BiometricUtils
import com.jesse.finly.notifications.NotificationHelper
import com.jesse.finly.utils.CurrencyFormatter
import com.jesse.finly.utils.IdiomaUtils
import com.jesse.finly.utils.Moeda
import com.jesse.finly.utils.ToastHelper
import com.jesse.finly.utils.UserPreferencesManager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetalhesPageActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "FinlyAppPrefs"
        private const val KEY_EMAIL = "EMAIL"
    }

    private lateinit var binding: DetalhesBinding

    private var isTransacaoMode = false

    private var userId: Int = -1
    private var userNameStr = ""
    private var emailStr = ""
    private var phoneStr = ""
    private var senhaStr = ""

    private var transId: Int = -1
    private var transItem = ""
    private var transValor = 0.0
    private var transData = ""
    private var transTipo = ""
    private var transCat = ""
    private var transMes = ""
    private var transAno = 2026
    private var transIsRecorrente = false
    private var transParcelasRestantes = 0
    private var transParcelasTotais = 0
    private var transStatus = false

    private val requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            ativarNotificacoes()
        } else {
            binding.switchNotifications.isChecked = false
            showToast(getString(R.string.toast_permissao_notif_negada))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ativa o modo borda a borda corretamente
        enableEdgeToEdge()
        
        binding = DetalhesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ajustar Insets para o cabeçalho não ficar colado no topo (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerView.updatePadding(top = systemBars.top)
            insets
        }
        
        // Garantir transparência e visibilidade dos ícones da barra de navegação (Modo Edge-to-Edge)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = 
            AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES

        // VERIFICAR SE PRECISA MOSTRAR TOSTE DE TEMA APÓS RECREAÇÃO
        verificarToastTemaPendente()
        atualizarAparenciaCabecalho()

        // CARREGAR ENDEREÇO ELETRÓNICO LOGO NO INÍCIO (Crucial para as funções de apagar)
        emailStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""

        isTransacaoMode = intent.getBooleanExtra("isTransactionDetail", false)

        if (isTransacaoMode) {
            configurarParaTransacao()
        } else {
            configurarParaPerfil()
        }

        configurarCliques()
    }

    private fun obterIconeParaCategoria(nome: String): Int {
        return when (nome.trim().lowercase()) {
            "geral" -> R.drawable.ic_list
            "habitação" -> R.drawable.ic_home
            "alimentação" -> R.drawable.ic_restaurant
            "transporte" -> R.drawable.ic_transport
            "saúde" -> R.drawable.ic_health
            "lazer" -> R.drawable.ic_sports
            "educação" -> R.drawable.ic_school
            "compras" -> R.drawable.ic_list
            "assinaturas" -> R.drawable.ic_pdf
            "investimentos" -> R.drawable.ic_euro
            "poupança" -> R.drawable.ic_poupanca
            "exterior" -> R.drawable.ic_flight
            else -> R.drawable.ic_tag
        }
    }

    private fun configurarParaTransacao() {
        binding.llTransacaoButtons.visibility = View.VISIBLE
        binding.llPerfilButtons.visibility = View.GONE
        binding.cardSettings.visibility = View.GONE

        transId = intent.getIntExtra("id", -1)
        transItem = intent.getStringExtra("item") ?: ""
        transValor = intent.getDoubleExtra("valor", 0.0)
        transData = intent.getStringExtra("vencimento") ?: ""
        transTipo = intent.getStringExtra("tipo") ?: ""
        transCat = intent.getStringExtra("categoria") ?: "Geral"
        transMes = intent.getStringExtra("mes") ?: ""
        transAno = intent.getIntExtra("ano", 2026)
        transIsRecorrente = intent.getBooleanExtra("isRecorrente", false)
        transParcelasRestantes = intent.getIntExtra("parcelasRestantes", 0)
        transParcelasTotais = intent.getIntExtra("parcelasTotais", 0)
        transStatus = intent.getBooleanExtra("status", false)

        atualizarUITransacao()
    }

    private fun atualizarUITransacao() {
        binding.userName.text = transItem
        
        // Ícone da Categoria dentro do círculo
        binding.tvUserInitial.visibility = View.GONE
        binding.ivUserProfile.visibility = View.VISIBLE
        binding.ivUserProfile.setImageResource(obterIconeParaCategoria(transCat))

        val corPositiva = ContextCompat.getColor(this, R.color.colorPositive)
        val corNegativa = ContextCompat.getColor(this, R.color.colorNegative)

        val moedaAtual = UserPreferencesManager(this).obterMoedaAtual()

        // Valor Prominente Grande
        binding.llValorHighlight.visibility = View.VISIBLE
        if (transTipo == "DESPESA") {
            binding.tvValorHighlight.text = CurrencyFormatter.formatarComSinal(-transValor, moedaAtual)
            binding.tvValorHighlight.setTextColor(corNegativa)
            binding.tvBadgeTipo.text = getString(R.string.label_despesa)
            binding.tvBadgeTipo.setTextColor(corNegativa)
        } else {
            binding.tvValorHighlight.text = CurrencyFormatter.formatarComSinal(transValor, moedaAtual, forcarSinalPositivo = true)
            binding.tvValorHighlight.setTextColor(corPositiva)
            binding.tvBadgeTipo.text = getString(R.string.label_renda)
            binding.tvBadgeTipo.setTextColor(corPositiva)
        }

        // Campo 1: Categoria
        binding.tvLabelField1.text = getString(R.string.label_categoria_simples)
        binding.tvValueField1.text = IdiomaUtils.formatarNomeCategoria(this, transCat)
        binding.ivIconField1.setImageResource(obterIconeParaCategoria(transCat))

        // Campo 2: Data de Vencimento
        binding.tvLabelField2.text = getString(R.string.label_data_vencimento)
        binding.tvValueField2.text = "$transData/$transAno"
        binding.ivIconField2.setImageResource(R.drawable.ic_calendar)

        // Campo 3: Recorrência
        binding.llField3.visibility = View.VISIBLE
        binding.divField2.visibility = View.VISIBLE
        binding.tvLabelField3.text = getString(R.string.label_recorrencia)
        binding.ivIconField3.setImageResource(R.drawable.ic_repeat)
        binding.tvValueField3.text = if (transIsRecorrente) {
            if (transParcelasTotais == -1) {
                getString(R.string.recorrencia_repetir_sempre)
            } else {
                getString(R.string.recorrencia_repetir_meses, transParcelasTotais)
            }
        } else {
            getString(R.string.recorrencia_unica)
        }

        // Campo 4: Estado do Pagamento com Switch
        binding.llField4.visibility = View.VISIBLE
        binding.divField3.visibility = View.VISIBLE
        binding.tvLabelField4.text = getString(R.string.label_estado_pagamento)
        binding.switchStatusTransacao.visibility = View.VISIBLE
        binding.switchStatusTransacao.setOnCheckedChangeListener(null)
        binding.switchStatusTransacao.isChecked = transStatus

        atualizarVisualStatusTransacao(transStatus, corPositiva, corNegativa)

        binding.switchStatusTransacao.setOnCheckedChangeListener { buttonView, isChecked ->
            FinanceiroUtils.dispararHapticFeedback(buttonView)
            transStatus = isChecked
            atualizarVisualStatusTransacao(isChecked, corPositiva, corNegativa)

            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(this@DetalhesPageActivity)
                val trans = db.utilizadorDao().obterTransacaoPorId(transId)
                trans?.let {
                    val atualizada = it.copy(status = isChecked)
                    db.utilizadorDao().atualizarTransacao(atualizada)
                    FirebaseManager.salvarTransacaoNoFirestore(atualizada)
                }
            }
        }
    }

    private fun atualizarVisualStatusTransacao(isPago: Boolean, corPositiva: Int, corNegativa: Int) {
        if (isPago) {
            binding.tvValueField4.text = getString(R.string.label_pago)
            binding.tvValueField4.setTextColor(corPositiva)
            binding.ivIconField4.setImageResource(android.R.drawable.checkbox_on_background)
            binding.ivIconField4.setColorFilter(corPositiva)
        } else {
            binding.tvValueField4.text = getString(R.string.label_pendente)
            binding.tvValueField4.setTextColor(corNegativa)
            binding.ivIconField4.setImageResource(android.R.drawable.checkbox_off_background)
            binding.ivIconField4.setColorFilter(corNegativa)
        }
    }

    private fun configurarParaPerfil() {
        binding.llTransacaoButtons.visibility = View.GONE
        binding.llPerfilButtons.visibility = View.VISIBLE
        binding.cardSettings.visibility = View.VISIBLE
        binding.llValorHighlight.visibility = View.GONE
        binding.llField3.visibility = View.GONE
        binding.divField2.visibility = View.GONE
        binding.llField4.visibility = View.GONE

        // Configurar Campo 1 para E-mail
        binding.tvLabelField1.text = getString(R.string.label_email)
        binding.ivIconField1.setImageResource(R.drawable.ic_email)

        // Configurar Campo 2 para Telemóvel
        binding.tvLabelField2.text = getString(R.string.label_telemovel)
        binding.ivIconField2.setImageResource(R.drawable.ic_phone)

        val currentEmail = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""
        emailStr = if (currentEmail == "CONVIDADO") currentEmail else currentEmail.trim().lowercase()

        userNameStr = intent.getStringExtra("name") ?: ""
        phoneStr = intent.getStringExtra("phone") ?: ""
        senhaStr = intent.getStringExtra("senha") ?: ""
        userId = intent.getIntExtra("id", -1)

        if (emailStr == "CONVIDADO") {
            binding.userName.text = getString(R.string.utilizador_convidado)
            binding.tvValueField1.text = getString(R.string.sem_email_sincronizado)
            binding.llField2.visibility = View.GONE
            binding.btnEditarPerfil.visibility = View.GONE
            binding.btnExcluirConta.visibility = View.GONE
            configurarVisualSemFoto(getString(R.string.utilizador_convidado))
        } else {
            if (userNameStr.isNotEmpty()) {
                atualizarUIPerfil()
            }
            recarregarDadosPerfil()
        }

        configurarConfiguracoes()
    }

    private fun atualizarAparenciaCabecalho() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val headerBg = if (isDark) ContextCompat.getColor(this, R.color.surfaceColor) else ContextCompat.getColor(this, R.color.headerColor)
        binding.headerView.setBackgroundColor(headerBg)

        val textColor = ContextCompat.getColor(this, R.color.textColorPrimary)
        binding.userName.setTextColor(textColor)
        binding.btnBackProfile.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    private fun formatarTelemovel(phone: String): String {
        val clean = phone.trim()
        val digits = clean.filter { it.isDigit() }
        if (digits.length == 9) {
            return "${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6)}"
        }
        return clean
    }

    private fun atualizarUIPerfil() {
        binding.userName.text = userNameStr
        binding.tvValueField1.text = emailStr
        binding.tvValueField2.text = formatarTelemovel(phoneStr)
        binding.llField2.visibility = View.VISIBLE

        configurarVisualSemFoto(userNameStr)
    }

    private fun configurarVisualSemFoto(nome: String) {
        binding.ivUserProfile.visibility = View.GONE
        binding.tvUserInitial.visibility = View.VISIBLE
        
        binding.tvUserInitial.setBackgroundResource(R.drawable.circle_background_teal)
        val color = ContextCompat.getColor(this, R.color.colorPrimary)
        binding.tvUserInitial.background.mutate().setTint(color)
        
        val inicial = if (nome.isNotEmpty()) nome.trim().take(1).uppercase() else "U"
        binding.tvUserInitial.text = inicial
    }

    private fun configurarCliques() {
        binding.btnBackProfile.setOnClickListener { finish() }

        binding.btnEditarTransacao.setOnClickListener {
            val intent = Intent(this, RegistoActivity::class.java)
            intent.putExtra("isEditTransaction", true)
            intent.putExtra("id", transId)
            intent.putExtra("item", transItem)
            intent.putExtra("valor", transValor)
            intent.putExtra("vencimento", transData)
            intent.putExtra("tipo", transTipo)
            intent.putExtra("categoria", transCat)
            intent.putExtra("mes", transMes)
            intent.putExtra("ano", transAno)
            intent.putExtra("isRecorrente", transIsRecorrente)
            intent.putExtra("parcelasTotais", transParcelasTotais)
            intent.putExtra("status", transStatus)
            startActivity(intent)
        }

        binding.btnEliminarTransacao.setOnClickListener {
            confirmarEliminacaoTransacao()
        }

        binding.btnEditarPerfil.setOnClickListener {
            irParaEdicaoPerfil()
        }

        binding.btnExcluirConta.setOnClickListener {
            confirmarExclusaoConta()
        }

        binding.btnVoltarLogin.setOnClickListener {
            mostrarDialogSair()
        }
    }

    private fun irParaEdicaoPerfil() {
        val intent = Intent(this, RegistoActivity::class.java)
        intent.putExtra("isUserEdit", true)
        intent.putExtra("id", userId)
        intent.putExtra("name", userNameStr)
        intent.putExtra("email", emailStr)
        intent.putExtra("phone", phoneStr)
        intent.putExtra("senha", senhaStr)
        startActivity(intent)
    }

    private fun configurarConfiguracoes() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        val isDarkMode = prefs.getBoolean("DARK_MODE", false)
        binding.switchDarkMode.setOnCheckedChangeListener(null)
        binding.switchDarkMode.isChecked = isDarkMode
        atualizarTextoModoEscuro(isDarkMode)

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            val currentMode = AppCompatDelegate.getDefaultNightMode()
            val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isCurrentlyDark = if (currentMode == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) isSystemDark else (currentMode == AppCompatDelegate.MODE_NIGHT_YES)

            prefs.edit {
                putBoolean("DARK_MODE", isChecked)
                putBoolean("SHOULD_SHOW_THEME_TOAST", true)
            }
            atualizarTextoModoEscuro(isChecked)
            
            // sincronizar PREFERÊNCIA NO FIREBASE
            atualizarPreferenciaUtilizador(isChecked, "DARK_MODE")
            
            if (isCurrentlyDark != isChecked) {
                val desiredMode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                AppCompatDelegate.setDefaultNightMode(desiredMode)
            }
        }

        val notifAtivas = prefs.getBoolean("NOTIFICATIONS", false)
        binding.switchNotifications.setOnCheckedChangeListener(null)
        binding.switchNotifications.isChecked = notifAtivas
        
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                verificarPermissaoNotificacao()
            } else {
                prefs.edit { putBoolean("NOTIFICATIONS", false) }
                cancelarNotificacoes()
            }
        }

        val biometriaAtiva = prefs.getBoolean("pref_biometric_ativa", false)
        binding.switchBiometria.setOnCheckedChangeListener(null)
        binding.switchBiometria.isChecked = biometriaAtiva

        binding.switchBiometria.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                if (BiometricUtils.isBiometricAvailable(this)) {
                    BiometricUtils.promptBiometria(
                        this,
                        title = getString(R.string.biometria_prompt_titulo),
                        subtitle = getString(R.string.biometria_prompt_subtitulo),
                        onSuccess = {
                            prefs.edit { putBoolean("pref_biometric_ativa", true) }
                            atualizarPreferenciaUtilizador(true, "BIOMETRIA")
                            showToast(getString(R.string.toast_biometria_ativada))
                        },
                        onError = {
                            buttonView.isChecked = false
                            prefs.edit { putBoolean("pref_biometric_ativa", false) }
                            atualizarPreferenciaUtilizador(false, "BIOMETRIA")
                            showToast(getString(R.string.toast_biometria_cancelada))
                        }
                    )
                } else {
                    buttonView.isChecked = false
                    prefs.edit { putBoolean("pref_biometric_ativa", false) }
                    atualizarPreferenciaUtilizador(false, "BIOMETRIA")
                    showToast(getString(R.string.toast_biometria_indisponivel))
                }
            } else {
                prefs.edit { putBoolean("pref_biometric_ativa", false) }
                atualizarPreferenciaUtilizador(false, "BIOMETRIA")
                showToast(getString(R.string.toast_biometria_desativada))
            }
        }

        val prefsManager = UserPreferencesManager(this)
        val moedaAtual = prefsManager.obterMoedaAtual()

        fun atualizarVisualBotoesMoeda(moeda: Moeda) {
            val isBrl = (moeda == Moeda.BRL)
            val colorPrimary = ContextCompat.getColor(this, R.color.colorPrimary)
            val colorSurface = ContextCompat.getColor(this, R.color.surfaceColor)
            val colorWhite = ContextCompat.getColor(this, R.color.white)
            val colorTextPrimary = ContextCompat.getColor(this, R.color.textColorPrimary)

            if (isBrl) {
                binding.btnMoedaPerfilBRL.setBackgroundColor(colorPrimary)
                binding.btnMoedaPerfilBRL.setTextColor(colorWhite)
                binding.btnMoedaPerfilEUR.setBackgroundColor(colorSurface)
                binding.btnMoedaPerfilEUR.setTextColor(colorTextPrimary)
            } else {
                binding.btnMoedaPerfilEUR.setBackgroundColor(colorPrimary)
                binding.btnMoedaPerfilEUR.setTextColor(colorWhite)
                binding.btnMoedaPerfilBRL.setBackgroundColor(colorSurface)
                binding.btnMoedaPerfilBRL.setTextColor(colorTextPrimary)
            }
        }

        val isBrl = (moedaAtual == Moeda.BRL)
        binding.toggleMoedaPerfil.check(if (isBrl) R.id.btnMoedaPerfilBRL else R.id.btnMoedaPerfilEUR)
        atualizarVisualBotoesMoeda(moedaAtual)

        binding.btnMoedaPerfilEUR.setOnClickListener { view ->
            FinanceiroUtils.dispararHapticFeedback(view)
            binding.toggleMoedaPerfil.check(R.id.btnMoedaPerfilEUR)
            atualizarVisualBotoesMoeda(Moeda.EUR)
            prefsManager.salvarMoeda(Moeda.EUR) { _ ->
                showToast(getString(R.string.toast_moeda_alterada_eur))
            }
        }

        binding.btnMoedaPerfilBRL.setOnClickListener { view ->
            FinanceiroUtils.dispararHapticFeedback(view)
            binding.toggleMoedaPerfil.check(R.id.btnMoedaPerfilBRL)
            atualizarVisualBotoesMoeda(Moeda.BRL)
            prefsManager.salvarMoeda(Moeda.BRL) { _ ->
                showToast(getString(R.string.toast_moeda_alterada_brl))
            }
        }
        // Exibir o nome do idioma atual na linha
        binding.tvIdiomaAtual.text = IdiomaUtils.obterNomeIdiomaAtual(this)

        // Abrir diálogo de seleção ao clicar
        binding.llConfigIdioma.setOnClickListener { view ->
            FinanceiroUtils.dispararHapticFeedback(view)
            mostrarDialogEscolhaIdioma()
        }
    }

    private fun mostrarDialogEscolhaIdioma() {
        val opcoes = arrayOf(
            "Português (Portugal)",
            "Português (Brasil)",
            "English (US)",
            "Español"
        )

        val tags = arrayOf(
            "pt-PT",
            "pt-BR",
            "en-US",
            "es"
        )

        val idiomaAtual = IdiomaUtils.obterTagIdiomaAtual()
        var indexSelecionado = tags.indexOfFirst {
            idiomaAtual.startsWith(it, ignoreCase = true)
        }
        if (indexSelecionado == -1) indexSelecionado = 0 // Pré-selecionado por padrão: Português (Portugal)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.titulo_dialog_idioma))
            .setSingleChoiceItems(opcoes, indexSelecionado) { dialog, which ->
                val tagEscolhida = tags[which]
                dialog.dismiss()
                IdiomaUtils.aplicarIdioma(tagEscolhida)
            }
            .setNegativeButton(getString(R.string.btn_cancelar), null)
            .show()
    }


    private fun atualizarTextoModoEscuro(isAtivo: Boolean) {
        binding.tvDarkModeLabel.text = if (isAtivo) {
            getString(R.string.dark_mode_ativado)
        } else {
            getString(R.string.dark_mode_desativado)
        }
    }

    private fun verificarToastTemaPendente() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean("SHOULD_SHOW_THEME_TOAST", false)) {
            val isDark = prefs.getBoolean("DARK_MODE", false)
            showToast(if (isDark) getString(R.string.toast_modo_escuro_ativado) else getString(R.string.toast_modo_claro_ativado))
            prefs.edit { remove("SHOULD_SHOW_THEME_TOAST") }
        }
    }

    private fun verificarPermissaoNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                ativarNotificacoes()
            } else {
                requestNotificationPermissionLauncher.launch(permission)
            }
        } else {
            ativarNotificacoes()
        }
    }

    private fun ativarNotificacoes() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean("NOTIFICATIONS", true) }
        atualizarPreferenciaUtilizador(valor = true, "NOTIFICATIONS")
        agendarWorkerNotificacoes()
        showToast(getString(R.string.toast_notificacoes_ativadas))
    }

    private fun cancelarNotificacoes() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean("NOTIFICATIONS", false) }
        atualizarPreferenciaUtilizador(false, "NOTIFICATIONS")
        cancelarWorkerNotificacoes()
        showToast(getString(R.string.toast_notificacoes_desativadas))
    }

    private fun agendarWorkerNotificacoes() {
        NotificationHelper.agendarWorkerNotificacoes(this)
    }

    private fun cancelarWorkerNotificacoes() {
        NotificationHelper.cancelarWorkerNotificacoes(this)
    }



    private fun atualizarPreferenciaUtilizador(valor: Boolean, tipo: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@DetalhesPageActivity)
            val user = db.utilizadorDao().buscarPorEmail(emailStr.trim().lowercase())
            user?.let {
                val updatedUser = when(tipo) {
                    "DARK_MODE" -> it.copy(darkMode = valor)
                    "NOTIFICATIONS" -> it.copy(notifications = valor)
                    "BIOMETRIA" -> it.copy(biometricAtiva = valor)
                    else -> it
                }
                db.utilizadorDao().atualizarUtilizador(updatedUser)
                FirebaseManager.salvarUtilizadorNoFirestore(updatedUser)
            }
        }
    }

    private fun confirmarEliminacaoTransacao() {
        if (transIsRecorrente) {
            val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_eliminar, binding.root as? android.view.ViewGroup, false)

            view.findViewById<TextView>(R.id.tvMensagemRecorrente).text =
                getString(R.string.dialog_eliminar_recorrencia_msg, transItem)
            
            view.findViewById<View>(R.id.btnEliminarApenasEste).setOnClickListener {
                executarEliminacaoSimples()
                dialog.dismiss()
            }
            
            view.findViewById<View>(R.id.btnEliminarTudo).setOnClickListener {
                executarEliminacaoFutura()
                dialog.dismiss()
            }
            
            view.findViewById<View>(R.id.btnCancelar).setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.setContentView(view)
            dialog.window?.setDimAmount(0.90f)
            dialog.show()
        } else {
            val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
            val view = layoutInflater.inflate(R.layout.bottom_sheet_eliminar_simples, binding.root as? android.view.ViewGroup, false)

            view.findViewById<TextView>(R.id.tvMensagemSimples).text = 
                getString(R.string.dialog_eliminar_item_msg, transItem)

            view.findViewById<View>(R.id.btnConfirmarEliminar).setOnClickListener {
                executarEliminacaoSimples()
                dialog.dismiss()
            }

            view.findViewById<View>(R.id.btnCancelarSimples).setOnClickListener {
                dialog.dismiss()
            }

            dialog.setContentView(view)
            dialog.window?.setDimAmount(0.90f)
            dialog.show()
        }
    }

    private fun mostrarLoadingOverlay() {
        binding.layoutLoadingOverlay.visibility = View.VISIBLE
    }

    private fun ocultarLoadingOverlay() {
        binding.layoutLoadingOverlay.visibility = View.GONE
    }

    private fun executarEliminacaoSimples() {
        mostrarLoadingOverlay()
        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@DetalhesPageActivity)
            val trans = Transacao(
                id = transId, item = transItem, valor = transValor, vencimento = transData,
                tipo = transTipo, donoEmail = emailStr, mes = transMes, ano = transAno,
                categoria = transCat, status = transStatus, recorrente = transIsRecorrente,
                parcelasRestantes = transParcelasRestantes, parcelasTotais = transParcelasTotais
            )
            db.utilizadorDao().apagarTransacao(trans)
            FirebaseManager.eliminarTransacaoDoFirestore(trans)
            withContext(Dispatchers.Main) {
                ocultarLoadingOverlay()
                showToast(getString(R.string.toast_item_removido))
                finish()
            }
        }
    }

    private fun executarEliminacaoFutura() {
        mostrarLoadingOverlay()
        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@DetalhesPageActivity)
            val dao = db.utilizadorDao()
            
            // 1. Obter todas as transações do utilizador ANTES de apagar qualquer uma
            val todas = dao.obterTransacoesPorDono(emailStr)
            val mesesArray = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
            
            val nomeAlvo = transItem.trim()
            val indexAtual = mesesArray.indexOfFirst { it.equals(transMes.trim(), ignoreCase = true) }

            todas.forEach { t ->
                if (t.item.trim().equals(nomeAlvo, ignoreCase = true)) {
                    val indexT = mesesArray.indexOfFirst { it.equals(t.mes.trim(), ignoreCase = true) }
                    val isFuturoOuAtual = (t.ano > transAno) || (t.ano == transAno && indexT >= indexAtual)
                    
                    if (isFuturoOuAtual) {
                        // Apaga a atual (a que estamos a ver) e todas as que já foram geradas no futuro
                        dao.apagarTransacao(t)
                        FirebaseManager.eliminarTransacaoDoFirestore(t)
                    } else {
                        // Desativa a recorrência nas transações Passadas.
                        // Isso é o que impede o sistema de gerar novos meses automaticamente.
                        if (t.recorrente) {
                            val atualizada = t.copy(recorrente = false)
                            dao.atualizarTransacao(atualizada)
                            FirebaseManager.salvarTransacaoNoFirestore(atualizada)
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                ocultarLoadingOverlay()
                showToast(getString(R.string.toast_recorrencia_removida))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Atualizar o endereço eletrónico da SharedPreferences (caso tenha mudado no RegistoActivity)
        val prefEmail = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""
        emailStr = if (prefEmail == "CONVIDADO") prefEmail else prefEmail.trim().lowercase()

        if (isTransacaoMode) {
            recarregarDadosTransacao()
        } else {
            if (emailStr != "CONVIDADO" && emailStr.isNotEmpty()) {
                recarregarDadosPerfil()
            }
        }
    }

    private fun recarregarDadosTransacao() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@DetalhesPageActivity)
            val t = db.utilizadorDao().obterTransacaoPorId(transId)
            t?.let {
                withContext(Dispatchers.Main) {
                    transItem = it.item
                    transValor = it.valor
                    transData = it.vencimento
                    transTipo = it.tipo
                    transCat = it.categoria
                    transMes = it.mes
                    transAno = it.ano
                    transStatus = it.status
                    transIsRecorrente = it.recorrente
                    transParcelasTotais = it.parcelasTotais
                    transParcelasRestantes = it.parcelasRestantes
                    atualizarUITransacao()
                }
            }
        }
    }

    private fun recarregarDadosPerfil() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@DetalhesPageActivity)
            val user = db.utilizadorDao().buscarPorEmail(emailStr.trim().lowercase())
            user?.let {
                userId = it.id
                userNameStr = it.nome
                phoneStr = it.telemovel
                senhaStr = it.senha
                withContext(Dispatchers.Main) {
                    atualizarUIPerfil()
                }
            }
        }
    }

    private fun mostrarDialogSair() {
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_logout)?.mutate()
        icon?.setTint(ContextCompat.getColor(this, R.color.colorPrimary))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_sair_titulo))
            .setIcon(icon)
            .setMessage(getString(R.string.dialog_sair_msg))
            .setPositiveButton(getString(R.string.dialog_sair_btn_sim)) { _, _ ->
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                    remove(KEY_EMAIL)
                }
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.dialog_sair_btn_nao), null)
            .show()
    }

    private fun confirmarExclusaoConta() {
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_delete)?.mutate()
        icon?.setTint(ContextCompat.getColor(this, R.color.colorNegative))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_eliminar_conta_titulo))
            .setIcon(icon)
            .setMessage(getString(R.string.dialog_eliminar_conta_msg))
            .setPositiveButton(getString(R.string.dialog_btn_sim_eliminar)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val sucessoNuvem = FirebaseManager.excluirContaTotal(emailStr)
                    if (sucessoNuvem) {
                        val db = MinhaBaseDados.getDatabase(this@DetalhesPageActivity)
                        val dao = db.utilizadorDao()
                        dao.apagarTodasTransacoesDoDono(emailStr)
                        val utilizador = dao.buscarPorEmail(emailStr)
                        utilizador?.let { dao.apagarUtilizador(it) }

                        withContext(Dispatchers.Main) {
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { clear() }
                            val intent = Intent(this@DetalhesPageActivity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            showToast(getString(R.string.toast_conta_eliminada))
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showToast(getString(R.string.toast_erro_eliminar_conta_nuvem))
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancelar), null)
            .show()
    }

    private fun showToast(msg: String, isLong: Boolean = false) {
        ToastHelper.showCustomToast(this, msg, isLong)
    }
}
