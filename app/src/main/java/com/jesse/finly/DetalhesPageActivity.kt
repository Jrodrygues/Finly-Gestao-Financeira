package com.jesse.finly

import android.content.Intent
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.DetalhesBinding
import com.jesse.finly.models.Transacao
import com.jesse.finly.notifications.NotificationHelper
import com.jesse.finly.notifications.NotificationWorker
import com.jesse.finly.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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
            showToast("Permissão de notificações negada")
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

    private fun configurarParaTransacao() {
        binding.llTransacaoButtons.visibility = View.VISIBLE
        binding.llPerfilButtons.visibility = View.GONE
        binding.cardSettings.visibility = View.GONE
        
        // Garante que o círculo de iniciais da transação use o Teal correto
        binding.tvUserInitial.setBackgroundResource(R.drawable.circle_background_teal)
        val tealColor = ContextCompat.getColor(this, R.color.colorPrimary)
        binding.tvUserInitial.background.mutate().setTint(tealColor)

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
        binding.tvEmailDetail.text = getString(R.string.valor_label, transValor, transTipo)
        binding.tvPhoneDetail.text = getString(R.string.data_label, transData)
        binding.tvExtraDetail.text = getString(R.string.categoria_mes_label, transCat, transMes)
        binding.tvExtraDetail.visibility = View.VISIBLE
        
        // Mostrar RECORRÊNCIA EM LINHA separada
        if (transIsRecorrente) {
            val parcelasInfo = if (transParcelasTotais == -1) "Repetir Sempre" else "Repetir por $transParcelasTotais meses"
            binding.tvRecurrenceDetail.text = getString(R.string.recorrencia_label, parcelasInfo)
            binding.tvRecurrenceDetail.visibility = View.VISIBLE
        } else {
            binding.tvRecurrenceDetail.visibility = View.GONE
        }
        
        binding.tvUserInitial.text = transItem.take(1).uppercase()
        binding.tvUserInitial.visibility = View.VISIBLE
        binding.tvUserInitial.setBackgroundResource(R.drawable.circle_background_teal)
    }


    private fun configurarParaPerfil() {
        binding.llTransacaoButtons.visibility = View.GONE
        binding.llPerfilButtons.visibility = View.VISIBLE
        binding.cardSettings.visibility = View.VISIBLE

        val currentEmail = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""
        emailStr = if (currentEmail == "CONVIDADO") currentEmail else currentEmail.trim().lowercase()

        // CARREGAMENTO IMEDIATO VIA INTENT (Evita flicker e letra "U")
        userNameStr = intent.getStringExtra("name") ?: ""
        phoneStr = intent.getStringExtra("phone") ?: ""
        senhaStr = intent.getStringExtra("senha") ?: ""
        userId = intent.getIntExtra("id", -1)

        if (emailStr == "CONVIDADO") {
            binding.userName.text = getString(R.string.utilizador_convidado)
            binding.tvEmailDetail.text = getString(R.string.sem_email_sincronizado)
            binding.tvPhoneDetail.visibility = View.GONE
            binding.btnEditarPerfil.visibility = View.GONE
            binding.btnExcluirConta.visibility = View.GONE
            configurarVisualSemFoto("Convidado")
        } else {
            // Mostrar o que já temos antes de consultar o banco
            if (userNameStr.isNotEmpty()) {
                atualizarUIPerfil()
            }
            recarregarDadosPerfil()
        }
        
        configurarConfiguracoes()
    }

    private fun atualizarUIPerfil() {
        binding.userName.text = userNameStr
        binding.tvEmailDetail.text = emailStr
        binding.tvPhoneDetail.text = phoneStr
        binding.tvPhoneDetail.visibility = View.VISIBLE

        configurarVisualSemFoto(userNameStr)
    }

    private fun configurarVisualSemFoto(nome: String) {
        binding.ivUserProfile.visibility = View.GONE
        binding.tvUserInitial.visibility = View.VISIBLE
        
        // CORREÇÃO DEFINITIVA: Força verde oficial e remove qualquer interferência
        binding.tvUserInitial.setBackgroundResource(R.drawable.circle_background_teal)
        val color = ContextCompat.getColor(this, R.color.colorPrimary)
        binding.tvUserInitial.background.mutate().setTint(color)
        
        val inicial = if (nome.isNotEmpty()) nome.trim().take(1).uppercase() else "U"
        binding.tvUserInitial.text = inicial
    }




    private fun configurarCliques() {
        binding.btnVoltarPlanilha.setOnClickListener { finish() }
        binding.btnVerContactos.setOnClickListener { finish() }

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
            val desiredMode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            val currentMode = AppCompatDelegate.getDefaultNightMode()

            prefs.edit {
                putBoolean("DARK_MODE", isChecked)
                putBoolean("SHOULD_SHOW_THEME_TOAST", true)
            }
            atualizarTextoModoEscuro(isChecked)
            
            // sincronizar PREFERÊNCIA NO FIREBASE
            atualizarPreferenciaUtilizador(isChecked, "DARK_MODE")
            
            if (currentMode != desiredMode) {
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
    }

    private fun atualizarTextoModoEscuro(isAtivo: Boolean) {
        binding.tvDarkModeLabel.text = if (isAtivo) "Modo Escuro Ativado" else "Ativar Modo Escuro"
    }

    private fun verificarToastTemaPendente() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean("SHOULD_SHOW_THEME_TOAST", false)) {
            val isDark = prefs.getBoolean("DARK_MODE", false)
            showToast(if (isDark) "Modo escuro ativado" else "Modo claro ativado")
            prefs.edit { remove("SHOULD_SHOW_THEME_TOAST") }
        }
    }

    private fun verificarPermissaoNotificacao() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
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
        showToast("Notificações ativadas!")
    }

    private fun cancelarNotificacoes() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean("NOTIFICATIONS", false) }
        atualizarPreferenciaUtilizador(false, "NOTIFICATIONS")
        cancelarWorkerNotificacoes()
        showToast("Notificações desativadas")
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

    private fun executarEliminacaoSimples() {
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
                showToast("Item removido com sucesso!")
                finish()
            }
        }
    }

    private fun executarEliminacaoFutura() {
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
            .setTitle("Sair")
            .setIcon(icon)
            .setMessage("Deseja terminar a sessão?")
            .setPositiveButton("Sim") { _, _ ->
                // ENCERRAR NO FIREBASE
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { 
                    remove(KEY_EMAIL)
                }
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun confirmarExclusaoConta() {
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_delete)?.mutate()
        icon?.setTint(ContextCompat.getColor(this, R.color.colorPrimary))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_eliminar_titulo))
            .setIcon(icon)
            .setMessage(getString(R.string.dialog_eliminar_msg))
            .setPositiveButton("Sim, Eliminar") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    // 1. ELIMINAR DA NUVEM (FIRESTORE, STORAGE, AUTH)
                    val sucessoNuvem = FirebaseManager.excluirContaTotal(emailStr)
                    
                    if (sucessoNuvem) {
                        val db = MinhaBaseDados.getDatabase(this@DetalhesPageActivity)
                        val dao = db.utilizadorDao()
                        
                        // 2. ELIMINAR LOCALMENTE
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
                            MaterialAlertDialogBuilder(this@DetalhesPageActivity)
                                .setTitle("Ação Necessária")
                                .setMessage("Para sua segurança, a exclusão de conta requer um login recente. Por favor, saia e entre novamente na aplicação antes de tentar excluir a conta.")
                                .setPositiveButton("Sair agora") { _, _ ->
                                    mostrarDialogSair()
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
