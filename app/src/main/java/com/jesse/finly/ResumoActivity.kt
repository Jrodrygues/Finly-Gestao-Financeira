package com.jesse.finly

import android.animation.ObjectAnimator
import android.view.animation.DecelerateInterpolator
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.content.res.Configuration
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RadioButton
import kotlin.math.abs
import kotlin.math.roundToInt
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat


import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.Button
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.ActivityResumoBinding
import com.jesse.finly.dialogs.EditarCategoriaBottomSheet
import com.jesse.finly.models.Categoria
import com.jesse.finly.models.MetaPoupanca
import com.jesse.finly.models.Utilizador
import com.jesse.finly.models.Transacao
import com.jesse.finly.notifications.NotificationHelper
import com.jesse.finly.utils.FinanceiroUtils
import com.jesse.finly.utils.UserPreferencesManager
import com.jesse.finly.utils.CsvExporter
import com.jesse.finly.utils.CurrencyFormatter
import com.jesse.finly.utils.IdiomaUtils
import com.jesse.finly.utils.Moeda
import com.jesse.finly.utils.MoneyTextWatcher
import com.jesse.finly.utils.ToastHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import kotlin.math.round

import androidx.activity.viewModels
import com.jesse.finly.utils.BackupManager
import com.jesse.finly.viewmodels.ResumoViewModel

class ResumoActivity : AppCompatActivity() {
    private val viewModel: ResumoViewModel by viewModels()
    private lateinit var binding: ActivityResumoBinding
    private val meses = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

    private val anos by lazy {
        val anoAtual = java.time.Year.now().value
        val anoInicio = anoAtual - 1 // Histórico recente
        val anoFim = anoAtual + 4    // Projeções futuras
        (anoInicio..anoFim).toList().toTypedArray()
    }
    
    private val recurrenceMutex = Mutex()
    private var lastLoadedMonth: String? = null
    private var resumoFlowJob: Job? = null
    private var lastLoadedYear: Int? = null
    private var metaAtual: Double = 0.0
    private var isCategoriesExpanded = false
    private var transacoesAtuaisGrafico: List<Transacao> = emptyList()
    private var moedaAtual: Moeda = Moeda.EUR
    private var isShowingOnboardingMoeda = false

    private val exportarBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = BackupManager.exportarBackupParaUri(this@ResumoActivity, uri)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        showToast(getString(R.string.toast_backup_exportado))
                    } else {
                        showToast(getString(R.string.toast_erro_backup))
                    }
                }
            }
        }
    }

    private val importarBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val qtd = BackupManager.importarBackupDeUri(this@ResumoActivity, uri)
                withContext(Dispatchers.Main) {
                    if (qtd > 0) {
                        showToast(getString(R.string.toast_backup_importado, qtd))
                        val mesSel = lastLoadedMonth ?: obterMesSelecionadoCanonical()
                        val anoSel = lastLoadedYear ?: (binding.spinnerAno.selectedItem as? Int ?: anos[0])
                        carregarDados(mesSel, anoSel)
                    } else {
                        showToast(getString(R.string.toast_erro_backup))
                    }
                }
            }
        }
    }


    companion object {
        private const val PREFS_NAME = "FinlyAppPrefs"
        private const val KEY_LAST_MONTH = "ULTIMO_MES_SELECIONADO"
        private const val KEY_LAST_YEAR = "ULTIMO_ANO_SELECIONADO"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ativa o modo borda a borda corretamente
        enableEdgeToEdge()
        
        binding = ActivityResumoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarSpinners()
        configurarDrawer()
        setupBackNavigation()
        sincronizarDadosIniciais()

        // Garantir que os ícones do sistema acompanhem o tema (Modo Edge-to-Edge)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = 
            AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES

        // Ajustar Insets para o cabeçalho e rodapé respeitarem as barras do sistema
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Adiciona padding no topo do cabeçalho da atividade
            binding.viewHeader.updatePadding(top = systemBars.top)
            
            // Ajustar o cabeçalho do Drawer (Menu Hambúrguer)
            if (binding.navigationView.headerCount > 0) {
                val header = binding.navigationView.getHeaderView(0)
                header.updatePadding(top = systemBars.top)
            }
            
            // Garante que o conteúdo no fundo, suba para não ficar atrás dos botões nativos
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        binding.btnVerDetalhes.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("MES_SELECIONADO", obterMesSelecionadoCanonical())
            intent.putExtra("ANO_SELECIONADO", binding.spinnerAno.selectedItem as Int)
            intent.putExtra("IS_DETALHES_MODE", true)
            startActivity(intent)
        }

        binding.btnMenu.setOnClickListener {
            binding.navigationView.setCheckedItem(R.id.nav_resumo)
            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        binding.btnDefinirMeta.setOnClickListener {
            mostrarDialogDefinirMeta()
        }

        binding.btnVerMaisCategorias.setOnClickListener {
            isCategoriesExpanded = !isCategoriesExpanded
            configurarGrafico(transacoesAtuaisGrafico)
        }
    }


    private fun mostrarDialogDefinirMeta() {
        val view = layoutInflater.inflate(R.layout.dialog_definir_meta, binding.root as? android.view.ViewGroup, false)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.metaEditText)
        val metaInputLayout = view.findViewById<TextInputLayout>(R.id.metaInputLayout)

        val moedaAtual = UserPreferencesManager(this).obterMoedaAtual()

        if (moedaAtual == Moeda.BRL) {
            metaInputLayout.prefixText = "R$ "
            metaInputLayout.suffixText = null
        } else {
            metaInputLayout.prefixText = null
            metaInputLayout.suffixText = " €"
        }

        val watcher = MoneyTextWatcher(input, moedaAtual)
        input.addTextChangedListener(watcher)

        val centavos = round(metaAtual * 100).toLong()
        if (centavos > 0) {
            input.setText(centavos.toString())
        } else {
            input.setText("")
        }

        // Criar o ícone com verde manualmente para garantir visibilidade
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_dashboard)?.mutate()
        icon?.setTint(ContextCompat.getColor(this, R.color.colorPrimary))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_meta_poupanca_titulo))
            .setIcon(icon)
            .setMessage(getString(R.string.dialog_meta_poupanca_msg, binding.spinnerMes.selectedItem.toString()))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_guardar)) { _, _ ->
                val novaMeta = watcher.obterValorDouble()
                salvarMeta(novaMeta)
            }
            .setNegativeButton(getString(R.string.btn_cancelar), null)
            .show()
    }

    private fun salvarMeta(valor: Double) {
        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        val mesSel = binding.spinnerMes.selectedItem.toString()
        val anoSel = binding.spinnerAno.selectedItem as Int
        val emailClean = email.trim().lowercase()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
            val metaExistente = db.utilizadorDao().obterMetaPorMes(emailClean, mesSel, anoSel)
            val metaObj = metaExistente?.copy(valor = valor)
                ?: MetaPoupanca(mes = mesSel, ano = anoSel, valor = valor, donoEmail = emailClean)

            viewModel.salvarMeta(metaObj)

            withContext(Dispatchers.Main) {
                carregarDados(mesSel, anoSel)
                showToast(getString(R.string.toast_meta_guardada, mesSel))
            }
        }
    }

    private fun sincronizarDadosIniciais() {
        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        if (email.isEmpty() || email == "CONVIDADO") return
        val emailClean = email.trim().lowercase()

        // Escuta em tempo real para o perfil (incluindo categorias customizations)
        FirebaseManager.monitorarPerfil(emailClean) { perfilNuvem ->
            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
                val perfilLocal = db.utilizadorDao().buscarPorEmail(emailClean)

                val listLocal = (perfilLocal?.customCategories?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList())
                val setPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet()) ?: emptySet()
                val listNuvem = perfilNuvem.customCategories.split(",").map { it.trim() }.filter { it.isNotBlank() }

                val setUnido = (listLocal + setPrefs + listNuvem).filter { it.isNotBlank() }.toSet()
                val customStrFinal = setUnido.joinToString(",")

                val userAtualizado = perfilNuvem.copy(
                    id = perfilLocal?.id ?: perfilNuvem.id,
                    customCategories = customStrFinal
                )

                db.utilizadorDao().atualizarUtilizador(userAtualizado)

                withContext(Dispatchers.Main) {
                    val novaMoeda = Moeda.porCodigo(perfilNuvem.moeda)
                    moedaAtual = novaMoeda

                    // Se no documento Firestore 'moedaConfigurada' for true OU se já existir moeda gravada
                    val isConfigurado = perfilNuvem.moedaConfigurada || perfilNuvem.moeda.isNotBlank()

                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                        putBoolean("NOTIFICATIONS", perfilNuvem.notifications)
                        putBoolean("DARK_MODE", perfilNuvem.darkMode)
                        putBoolean("pref_biometric_ativa", perfilNuvem.biometricAtiva)
                        putString("MOEDA", perfilNuvem.moeda)
                        putBoolean("MOEDA_CONFIGURADA", isConfigurado)
                        putStringSet("CUSTOM_CATEGORIES_$emailClean", HashSet(setUnido))
                    }
                    atualizarDrawerHeader()

                    // Se o modal estiver aberto indevidamente por atraso da rede, fecha-o
                    if (isConfigurado && isShowingOnboardingMoeda) {
                        isShowingOnboardingMoeda = false
                    }

                    prosseguirComCarregamento()
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. sincronizar PERFIL inicial
            val perfilNuvem = FirebaseManager.obterPerfilDoFirestore(emailClean)
            if (perfilNuvem != null) {
                val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
                val perfilLocal = db.utilizadorDao().buscarPorEmail(emailClean)

                val listLocal = (perfilLocal?.customCategories?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList())
                val setPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet()) ?: emptySet()
                val listNuvem = perfilNuvem.customCategories.split(",").map { it.trim() }.filter { it.isNotBlank() }

                val setUnido = (listLocal + setPrefs + listNuvem).filter { it.isNotBlank() }.toSet()
                val customStrFinal = setUnido.joinToString(",")

                val userAtualizado = perfilNuvem.copy(
                    id = perfilLocal?.id ?: perfilNuvem.id,
                    customCategories = customStrFinal
                )

                db.utilizadorDao().atualizarUtilizador(userAtualizado)

                withContext(Dispatchers.Main) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                        putBoolean("NOTIFICATIONS", perfilNuvem.notifications)
                        putBoolean("DARK_MODE", perfilNuvem.darkMode)
                        putBoolean("pref_biometric_ativa", perfilNuvem.biometricAtiva)
                        putString("MOEDA", perfilNuvem.moeda)
                        putBoolean("MOEDA_CONFIGURADA", perfilNuvem.moedaConfigurada)
                        putStringSet("CUSTOM_CATEGORIES_$emailClean", HashSet(setUnido))
                    }
                    atualizarDrawerHeader()
                }
            }

            // 2. sincronizar TRANSAÇÕES
            val transacoesNuvem = FirebaseManager.descarregarTransacoesDoFirestore(emailClean)
            if (transacoesNuvem.isNotEmpty()) {
                val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
                val dao = db.utilizadorDao()
                
                transacoesNuvem.forEach { trans ->
                    val existe = dao.obterTransacaoPorId(trans.id)
                    if (existe == null) {
                        dao.inserirTransacao(trans)
                    } else {
                        dao.atualizarTransacao(trans)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    prosseguirComCarregamento()
                }
            }

            // 3. sincronizar METAS DE POUPANÇA
            val metasNuvem = FirebaseManager.descarregarMetasDoFirestore(emailClean)
            if (metasNuvem.isNotEmpty()) {
                val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
                val dao = db.utilizadorDao()
                metasNuvem.forEach { meta ->
                    val existe = dao.obterMetaPorMes(emailClean, meta.mes, meta.ano)
                    val metaSalvar = meta.copy(id = existe?.id ?: meta.id, donoEmail = emailClean)
                    dao.salvarMeta(metaSalvar)
                }
                withContext(Dispatchers.Main) {
                    prosseguirComCarregamento()
                }
            }
        }
    }

    private fun estilarMenuItemsDrawer() {
        val colorPrimary = ContextCompat.getColor(this, R.color.colorPrimary)
        val colorTextPrimary = ContextCompat.getColor(this, R.color.textColorPrimary)
        val colorNegative = ContextCompat.getColor(this, R.color.colorNegative)

        val menu = binding.navigationView.menu
        binding.navigationView.itemIconTintList = null
        binding.navigationView.itemTextColor = null

        // Se o utilizador for Premium, oculta a opção "Finly Premium 🌟" do menu lateral
        val isPremium = UserPreferencesManager(this).isPremium()
        menu.findItem(R.id.nav_premium)?.isVisible = !isPremium

        val checkedId = R.id.nav_resumo
        binding.navigationView.setCheckedItem(checkedId)

        fun aplicarEstiloItem(item: MenuItem) {
            val isSair = item.itemId == R.id.nav_sair
            val isChecked = item.itemId == checkedId

            val corAtual = when {
                isSair -> colorNegative
                isChecked -> colorPrimary
                else -> colorTextPrimary
            }

            val titleStr = item.title.toString()
            val spannable = SpannableString(titleStr)
            spannable.setSpan(ForegroundColorSpan(corAtual), 0, spannable.length, 0)
            item.title = spannable

            item.icon?.mutate()?.let { iconDrawable ->
                iconDrawable.setTint(corAtual)
                item.icon = iconDrawable
            }
        }

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.hasSubMenu()) {
                val subMenu = item.subMenu
                if (subMenu != null) {
                    for (j in 0 until subMenu.size()) {
                        aplicarEstiloItem(subMenu.getItem(j))
                    }
                }
            } else {
                aplicarEstiloItem(item)
            }
        }
    }

    private fun configurarDrawer() {
        // 1. Configurar listeners de navegação apenas uma vez
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_resumo -> { }
                R.id.nav_planilha -> {
                    binding.btnVerDetalhes.performClick()
                }
                R.id.nav_perfil -> {
                    irParaMeuPerfil()
                }
                R.id.nav_guia -> {
                    startActivity(Intent(this@ResumoActivity, GuiaActivity::class.java))
                }
                R.id.nav_evolucao -> {
                    startActivity(Intent(this@ResumoActivity, EvolucaoAnualActivity::class.java))
                }
                R.id.nav_premium -> {
                    PaywallActivity.abrir(this@ResumoActivity)
                }
                R.id.nav_gerir_categorias -> {
                    mostrarBottomSheetGerirCategorias()
                }
                R.id.nav_exportar -> {
                    val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
                    val emailClean = if (email.equals("CONVIDADO", ignoreCase = true) || email.isBlank()) "convidado" else email.trim().lowercase()
                    if (emailClean == "convidado") {
                        showToast(getString(R.string.toast_convidado_acao_bloqueada))
                    } else {
                        mostrarOpcoesExportacao()
                    }
                }
                R.id.nav_backup -> {
                    mostrarOpcoesBackup()
                }
                R.id.nav_sair -> {
                    mostrarDialogSair()
                }
            }
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }

        estilarMenuItemsDrawer()
        atualizarDrawerHeader()
    }

    private fun obterCategoriasCustomizadas(): MutableList<String> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        var customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", null)

        if (customSet == null) {
            val oldPrefs = getSharedPreferences("PreferenciasDaMinhaApp", MODE_PRIVATE)
            val oldSet = oldPrefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", null)
            if (!oldSet.isNullOrEmpty()) {
                customSet = oldSet
                prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", oldSet) }
            }
        }

        return (customSet ?: emptySet()).filter { it.isNotBlank() }.toMutableList()
    }

    private fun salvarNovaCategoria(nome: String) {
        val catClean = nome.trim()
        if (catClean.isEmpty()) return

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet())?.toMutableSet() ?: mutableSetOf()

        val userPrefs = UserPreferencesManager(this)
        if (!userPrefs.isPremium() && customSet.size >= 3 && !customSet.contains(catClean)) {
            showToast("Limite de 3 categorias personalizadas no plano Grátis. Seja Finly Premium!")
            PaywallActivity.abrir(this)
            return
        }

        customSet.add(catClean)
        prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", customSet) }

        sincronizarCategoriasNuvem(customSet)
        showToast(getString(R.string.toast_categoria_adicionada, catClean))
        val mesSel = obterMesSelecionadoCanonical()
        val anoSel = binding.spinnerAno.selectedItem?.toString()?.toIntOrNull() ?: 2026
        carregarDados(mesSel, anoSel)
    }

    private fun removerCategoriaCustomizada(categoria: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet())?.toMutableSet() ?: mutableSetOf()
        customSet.remove(categoria)
        prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", customSet) }

        sincronizarCategoriasNuvem(customSet)
        showToast(getString(R.string.toast_categoria_removida, categoria))
        val mesSel = obterMesSelecionadoCanonical()
        val anoSel = binding.spinnerAno.selectedItem?.toString()?.toIntOrNull() ?: 2026
        carregarDados(mesSel, anoSel)
    }

    private fun confirmarEliminacaoCategoria(categoria: String, onConfirm: () -> Unit) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
            val transacoesAfetadas = db.utilizadorDao().obterTransacoesPorDono(emailClean)
                .filter { it.categoria.equals(categoria, ignoreCase = true) }

            withContext(Dispatchers.Main) {
                if (transacoesAfetadas.isEmpty()) {
                    MaterialAlertDialogBuilder(this@ResumoActivity)
                        .setTitle(getString(R.string.dialog_eliminar_categoria_titulo))
                        .setMessage(getString(R.string.dialog_eliminar_categoria_msg, categoria))
                        .setPositiveButton(getString(R.string.btn_eliminar)) { _, _ ->
                            onConfirm()
                        }
                        .setNegativeButton(getString(R.string.btn_cancelar)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                } else {
                    val quantidade = transacoesAfetadas.size
                    MaterialAlertDialogBuilder(this@ResumoActivity)
                        .setTitle(getString(R.string.dialog_categoria_em_uso_titulo))
                        .setMessage(getString(R.string.dialog_categoria_em_uso_msg, categoria, quantidade))
                        .setPositiveButton(getString(R.string.btn_sim_eliminar)) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                transacoesAfetadas.forEach { trans ->
                                    val transAtualizada = trans.copy(categoria = "Geral")
                                    db.utilizadorDao().atualizarTransacao(transAtualizada)
                                    FirebaseManager.salvarTransacaoNoFirestore(transAtualizada)
                                }
                                withContext(Dispatchers.Main) {
                                    onConfirm()
                                }
                            }
                        }
                        .setNegativeButton(getString(R.string.btn_cancelar)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
    }

    private fun sincronizarCategoriasNuvem(customSet: Set<String>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        if (emailClean.isNotEmpty() && emailClean != "convidado") {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
                val user = db.utilizadorDao().buscarPorEmail(emailClean)
                val catStr = customSet.joinToString(",")
                val updatedUser = user?.copy(customCategories = catStr) ?: Utilizador(
                    email = emailClean,
                    donoEmail = emailClean,
                    customCategories = catStr
                )
                db.utilizadorDao().atualizarUtilizador(updatedUser)
                FirebaseManager.salvarUtilizadorNoFirestore(updatedUser)
            }
        }
    }

    private fun obterIconeParaCategoria(nome: String): Int {
        return FinanceiroUtils.obterIconeParaCategoria(nome)
    }

    private fun mostrarBottomSheetGerirCategorias() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val bsView = layoutInflater.inflate(R.layout.bottom_sheet_gerir_categorias, null)
        bottomSheetDialog.setContentView(bsView)
        bottomSheetDialog.window?.setDimAmount(0.85f)
        bottomSheetDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bsView.parent as? View
            bottomSheet?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        val etNova = bsView.findViewById<TextInputEditText>(R.id.etNovaCategoriaBS)
        val btnAdd = bsView.findViewById<MaterialButton>(R.id.btnAdicionarCategoriaBS)
        val btnFechar = bsView.findViewById<View>(R.id.btnFecharBS)
        val containerMinhas = bsView.findViewById<LinearLayout>(R.id.containerMinhasCategorias)
        val containerPadrao = bsView.findViewById<LinearLayout>(R.id.containerCategoriasPadrao)
        val tvSemCategorias = bsView.findViewById<TextView>(R.id.tvSemCategoriasCustom)

        val categoriasPadrao = listOf(
            "Geral", "Habitação", "Alimentação", "Transporte", "Saúde", "Lazer",
            "Educação", "Compras", "Assinaturas", "Investimentos", "Poupança", "Exterior"
        )

        var isPadraoExpanded = false
        val btnVerMaisPadrao = bsView.findViewById<Button>(R.id.btnVerMaisPadrao)

        fun atualizarListaCustom() {
            containerMinhas?.removeAllViews()
            containerPadrao?.removeAllViews()
            val customList = obterCategoriasCustomizadas()

            // 1. As Minhas Categorias Personalizadas (NO TOPO)
            if (customList.isEmpty()) {
                tvSemCategorias?.visibility = View.VISIBLE
            } else {
                tvSemCategorias?.visibility = View.GONE
                for (cat in customList) {
                    val itemView = layoutInflater.inflate(R.layout.item_categoria_custom, containerMinhas, false)
                    val ivIcon = itemView.findViewById<ImageView>(R.id.ivIconCategoriaCustom)
                    val tvNome = itemView.findViewById<TextView>(R.id.tvNomeCategoriaCustom)
                    val tvTag = itemView.findViewById<TextView>(R.id.tvTagPadraoCustom)
                    val btnRemover = itemView.findViewById<View>(R.id.btnRemoverCategoriaCustom)

                    ivIcon?.setImageResource(R.drawable.ic_tag)
                    tvNome?.text = cat
                    tvTag?.visibility = View.GONE
                    btnRemover?.visibility = View.VISIBLE
                    btnRemover?.setOnClickListener {
                        confirmarEliminacaoCategoria(cat) {
                            removerCategoriaCustomizada(cat)
                            atualizarListaCustom()
                        }
                    }
                    containerMinhas?.addView(itemView)
                }
            }

            // 2. Categorias do Sistema (Mostrar 4 por omissão ou todas se expandido)
            val padraoParaMostrar = if (isPadraoExpanded) categoriasPadrao else categoriasPadrao.take(4)
            btnVerMaisPadrao?.visibility = View.VISIBLE
            btnVerMaisPadrao?.text = if (isPadraoExpanded) getString(R.string.ver_menos_categorias) else getString(R.string.ver_mais_categorias_sistema)

            for (catPadrao in padraoParaMostrar) {
                val itemView = layoutInflater.inflate(R.layout.item_categoria_custom, containerPadrao, false)
                val ivIcon = itemView.findViewById<ImageView>(R.id.ivIconCategoriaCustom)
                val tvNome = itemView.findViewById<TextView>(R.id.tvNomeCategoriaCustom)
                val tvTag = itemView.findViewById<TextView>(R.id.tvTagPadraoCustom)
                val btnRemover = itemView.findViewById<View>(R.id.btnRemoverCategoriaCustom)

                ivIcon?.setImageResource(obterIconeParaCategoria(catPadrao))
                tvNome?.text = IdiomaUtils.formatarNomeCategoria(this, catPadrao)
                tvTag?.visibility = View.VISIBLE
                btnRemover?.visibility = View.GONE
                containerPadrao?.addView(itemView)
            }
        }

        btnVerMaisPadrao?.setOnClickListener {
            isPadraoExpanded = !isPadraoExpanded
            atualizarListaCustom()
        }

        atualizarListaCustom()

        btnAdd?.setOnClickListener {
            val novaCat = etNova?.text.toString().trim()
            if (novaCat.isNotEmpty()) {
                salvarNovaCategoria(novaCat)
                etNova?.setText("")
                etNova?.clearFocus()

                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etNova?.windowToken, 0)

                atualizarListaCustom()
            } else {
                showToast(getString(R.string.toast_digite_nome_categoria))
            }
        }

        btnFechar?.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun atualizarDrawerHeader() {
        val headerView = binding.navigationView.getHeaderView(0) ?: return
        val tvNome = headerView.findViewById<TextView>(R.id.nav_header_name)
        val tvEmail = headerView.findViewById<TextView>(R.id.nav_header_email)
        val tvInitial = headerView.findViewById<TextView>(R.id.nav_header_initial)
        val ivCloud = headerView.findViewById<ImageView>(R.id.ivCloudStatus)
        val tvPremiumBadge = headerView.findViewById<TextView>(R.id.tvNavHeaderPremiumBadge)

        val isPremium = UserPreferencesManager(this).isPremium()
        tvPremiumBadge?.visibility = if (isPremium) View.VISIBLE else View.GONE

        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        val emailClean = email.trim().lowercase()

        if (emailClean.isEmpty() || emailClean == "convidado") {
            tvNome?.text = getString(R.string.utilizador_convidado)
            tvEmail?.text = getString(R.string.dados_apenas_locais)
            mostrarInicial(tvInitial, getString(R.string.utilizador_convidado))
            
            ivCloud?.setImageResource(R.drawable.ic_cloud_off)
            ivCloud?.setOnClickListener { showToast(getString(R.string.toast_nuvem_convidado)) }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
            val utilizador = db.utilizadorDao().buscarPorEmail(emailClean)

            withContext(Dispatchers.Main) {
                if (utilizador != null) {
                    tvNome?.text = utilizador.nome
                    tvEmail?.text = utilizador.email
                    mostrarInicial(tvInitial, utilizador.nome)
                    
                    ivCloud?.setImageResource(R.drawable.ic_cloud_done)
                    ivCloud?.setOnClickListener { showToast(getString(R.string.toast_nuvem_sincronizado)) }
                }
            }
        }
    }

    private fun mostrarInicial(tvInitial: TextView?, nome: String) {
        tvInitial?.visibility = View.VISIBLE
        tvInitial?.setBackgroundResource(R.drawable.circle_background_teal)
        
        // CORREÇÃO DEFINITIVA: Força o Teal oficial no Menu Hambúrguer para evitar o azul
        val tealColor = ContextCompat.getColor(this, R.color.colorPrimary)
        tvInitial?.background?.mutate()?.setTint(tealColor)
        
        tvInitial?.text = if (nome.isNotBlank()) nome.trim().take(1).uppercase() else "U"
    }



    private fun mostrarDialogSair() {
        // Criar o ícone com verde manualmente para garantir visibilidade
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_logout)?.mutate()
        icon?.setTint(ContextCompat.getColor(this, R.color.colorPrimary))

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_sair_titulo))
            .setIcon(icon)
            .setMessage(getString(R.string.dialog_sair_msg))
            .setPositiveButton(getString(R.string.dialog_sair_btn_sim)) { _, _ ->
                // ENCERRAR NO FIREBASE
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                    remove("EMAIL")
                    remove("NAME")
                    remove("LAST_LOGIN_TIMESTAMP")
                    remove("MOEDA")
                    remove("MOEDA_CONFIGURADA") // Reset normal ao sair
                }
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.dialog_sair_btn_nao), null)
            .show()
    }

    private fun exportarParaPDF() {
        val mes = binding.spinnerMes.selectedItem.toString()
        val ano = binding.spinnerAno.selectedItem.toString()
        val transacoes = transacoesAtuaisGrafico
        val moedaAtual = UserPreferencesManager(this).obterMoedaAtual()

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        // Paints para estilo
        val primaryColorHex = "#009688"
        val headerPaint = Paint().apply { color = primaryColorHex.toColorInt() }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint().apply {
            color = "#B2FFFFFF".toColorInt()
            textSize = 12f
        }
        val cardBgPaint = Paint().apply { color = "#F4F8F7".toColorInt() }
        val cardBorderPaint = Paint().apply {
            color = "#D1E0DE".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val labelPaint = Paint().apply {
            color = "#666666".toColorInt()
            textSize = 10f
        }
        val valueRendaPaint = Paint().apply {
            color = "#2E7D32".toColorInt()
            textSize = 12f
            isFakeBoldText = true
        }
        val valueDespesaPaint = Paint().apply {
            color = "#C62828".toColorInt()
            textSize = 12f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = "#333333".toColorInt()
            textSize = 10f
        }
        val textBoldPaint = Paint().apply {
            color = primaryColorHex.toColorInt()
            textSize = 11f
            isFakeBoldText = true
        }
        val tableHeaderPaint = Paint().apply { color = primaryColorHex.toColorInt() }
        val tableHeaderCellPaint = Paint().apply {
            color = Color.WHITE
            textSize = 10f
            isFakeBoldText = true
        }
        val tableHeaderCellRightPaint = Paint().apply {
            color = Color.WHITE
            textSize = 10f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = "#E0E0E0".toColorInt()
            strokeWidth = 0.8f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        fun drawHeaderAndFooter(c: Canvas, pNum: Int) {
            c.drawRect(0f, 0f, pageWidth.toFloat(), 85f, headerPaint)
            c.drawText("Finly", 30f, 40f, titlePaint)
            c.drawText(getString(R.string.pdf_relatorio_subtitulo, mes, ano), 30f, 62f, subtitlePaint)

            // Logo ícone vetorial de altíssima definição (Renderizado a 6x para nitidez cristalina ao fazer ‘zoom’ no PDF)
            val logoVector = ContextCompat.getDrawable(this, R.drawable.ic_logo_finly_transp)
                ?: ContextCompat.getDrawable(this, R.drawable.ic_logo_finly)
            logoVector?.let { drawable ->
                val scale = 6
                val width = (565 - 505) * scale // 360px
                val height = (72 - 12) * scale  // 360px
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val bitmapCanvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(bitmapCanvas)

                val logoPaint = Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                    isDither = true
                }
                c.drawBitmap(bitmap, null, RectF(505f, 12f, 565f, 72f), logoPaint)
            }

            c.drawLine(30f, 810f, 565f, 810f, linePaint)
            val dataHoje = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date())
            c.drawText(getString(R.string.pdf_rodape_gerado, dataHoje), 30f, 825f, labelPaint)
            c.drawText(getString(R.string.pdf_rodape_pagina, pNum), 525f, 825f, labelPaint)
        }

        var currentY = 0f

        fun checkPageBreak(requiredSpace: Float = 30f) {
            if (currentY + requiredSpace > 780f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                drawHeaderAndFooter(canvas, pageNumber)
                currentY = 110f
            }
        }

        fun drawTableHeader(c: Canvas, y: Float) {
            c.drawRoundRect(30f, y, 565f, y + 22f, 4f, 4f, tableHeaderPaint)
            val headerY = y + 15f
            c.drawText(getString(R.string.pdf_col_item_desc), 40f, headerY, tableHeaderCellPaint)
            c.drawText(getString(R.string.pdf_col_categoria), 250f, headerY, tableHeaderCellPaint)
            c.drawText(getString(R.string.pdf_col_data), 370f, headerY, tableHeaderCellPaint)
            c.drawText(getString(R.string.pdf_col_valor), 555f, headerY, tableHeaderCellRightPaint)
        }

        drawHeaderAndFooter(canvas, pageNumber)

        // Card de Resumo no topo
        canvas.drawRoundRect(30f, 100f, 565f, 190f, 10f, 10f, cardBgPaint)
        canvas.drawRoundRect(30f, 100f, 565f, 190f, 10f, 10f, cardBorderPaint)

        val totalRendaVal = transacoes.filter { it.tipo == "RENDA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) && it.categoria != "Exterior" }.sumOf { it.valor }
        val despesas = transacoes.filter { it.tipo == "DESPESA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) }
        val totalDespesasVal = despesas.sumOf { it.valor }
        val saldoVal = FinanceiroUtils.calcularSaldoPago(transacoes)

        val codigoMoeda = moedaAtual.codigo

        val isRendaZero = abs(totalRendaVal) < 0.005
        val rendaText = FinanceiroUtils.formatarMoeda(totalRendaVal, isPositivo = if (isRendaZero) null else true, codigoMoeda = codigoMoeda)

        val isDespesaZero = abs(totalDespesasVal) < 0.005
        val despesaText = FinanceiroUtils.formatarMoeda(totalDespesasVal, isPositivo = if (isDespesaZero) null else false, codigoMoeda = codigoMoeda)

        val isSaldoZero = abs(saldoVal) < 0.005
        val saldoText = FinanceiroUtils.formatarMoeda(saldoVal, isPositivo = if (isSaldoZero) null else (saldoVal >= 0), codigoMoeda = codigoMoeda)

        val despesasPendentes = despesas.filter { !it.status }
        val totalDespesasPendentes = despesasPendentes.sumOf { it.valor }
        val statusPrevisao = if (saldoVal >= 0) getString(R.string.pdf_previsao_status_verde) else getString(R.string.pdf_previsao_status_vermelho)
        val detalhePrevisao = if (despesasPendentes.isNotEmpty()) {
            getString(R.string.pdf_previsao_pendentes, despesasPendentes.size, FinanceiroUtils.formatarMoeda(totalDespesasPendentes, codigoMoeda = codigoMoeda))
        } else {
            getString(R.string.pdf_previsao_contas_em_dia)
        }

        canvas.drawText(getString(R.string.pdf_card_total_rendas), 50f, 122f, labelPaint)
        canvas.drawText(rendaText, 50f, 142f, valueRendaPaint)

        canvas.drawText(getString(R.string.pdf_card_total_despesas), 210f, 122f, labelPaint)
        canvas.drawText(despesaText, 210f, 142f, valueDespesaPaint)

        canvas.drawText(getString(R.string.pdf_card_saldo_final), 380f, 122f, labelPaint)
        val isSaldoNegativo = binding.tvSaldoFinal.currentTextColor == ContextCompat.getColor(this, R.color.colorNegative)
        val saldoPaint = if (isSaldoZero) labelPaint else if (isSaldoNegativo) valueDespesaPaint else valueRendaPaint
        canvas.drawText(saldoText, 380f, 142f, saldoPaint)

        canvas.drawLine(50f, 157f, 545f, 157f, linePaint)
        canvas.drawText(getString(R.string.pdf_card_previsao_fim_mes, statusPrevisao, detalhePrevisao), 50f, 175f, textPaint)

        currentY = 210f

        // 1. Resumo de Despesas por Categoria
        val gastosPorCategoria = despesas.groupBy { it.categoria }
            .mapValues { entry -> entry.value.sumOf { it.valor } }
            .toList()
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }

        if (gastosPorCategoria.isNotEmpty()) {
            canvas.drawText(getString(R.string.pdf_secao_despesas_cat), 30f, currentY, textBoldPaint)
            currentY += 12f

            canvas.drawRoundRect(30f, currentY, 565f, currentY + 22f, 4f, 4f, tableHeaderPaint)
            val miniHeaderY = currentY + 15f
            canvas.drawText(getString(R.string.pdf_col_categoria), 40f, miniHeaderY, tableHeaderCellPaint)
            canvas.drawText(getString(R.string.pdf_col_total_gasto), 320f, miniHeaderY, tableHeaderCellPaint)
            canvas.drawText(getString(R.string.pdf_col_percent_total), 460f, miniHeaderY, tableHeaderCellPaint)
            currentY += 34f

            for ((cat, totalCat) in gastosPorCategoria) {
                checkPageBreak(20f)

                val catNome = if (cat.length > 28) cat.take(26) + ".." else cat
                val valorFormatado = CurrencyFormatter.formatar(totalCat, moedaAtual)
                val percent = if (totalDespesasVal > 0) ((totalCat / totalDespesasVal) * 100).toInt() else 0

                canvas.drawText(catNome, 40f, currentY, textPaint)
                canvas.drawText(valorFormatado, 320f, currentY, labelPaint)
                canvas.drawText("$percent%", 460f, currentY, labelPaint)

                canvas.drawLine(30f, currentY + 4f, 565f, currentY + 4f, linePaint)
                currentY += 18f
            }
            currentY += 15f
        }

        val paintRendaRight = Paint(valueRendaPaint).apply {
            textSize = 10f
            textAlign = Paint.Align.RIGHT
        }
        val paintDespesaRight = Paint(valueDespesaPaint).apply {
            textSize = 10f
            textAlign = Paint.Align.RIGHT
        }
        val rowHeight = 22f

        // 2. Tabela de RENDAS (ENTRADAS) ordenadas por data de vencimento
        val rendas = transacoes.filter { it.tipo == "RENDA" }.sortedBy { extrairOrdemData(it.vencimento) }
        checkPageBreak(60f)

        canvas.drawText(getString(R.string.pdf_secao_rendas), 30f, currentY, textBoldPaint)
        currentY += 12f
        drawTableHeader(canvas, currentY)
        currentY += 34f

        if (rendas.isEmpty()) {
            canvas.drawText(getString(R.string.pdf_sem_rendas), 40f, currentY, labelPaint)
            currentY += rowHeight
        } else {
            for (t in rendas) {
                checkPageBreak(rowHeight)

                val itemNomeStr = if (t.recorrente && t.parcelasTotais > 0) {
                    val parcelaAtual = t.parcelasTotais - t.parcelasRestantes
                    "${t.item} ($parcelaAtual/${t.parcelasTotais})"
                } else {
                    t.item
                }
                val itemNome = if (itemNomeStr.length > 28) itemNomeStr.take(26) + ".." else itemNomeStr
                val catNome = if (t.categoria.length > 18) t.categoria.take(16) + ".." else t.categoria
                val valorFormatado = CurrencyFormatter.formatar(t.valor, moedaAtual)

                canvas.drawText(itemNome, 40f, currentY, textPaint)
                canvas.drawText(catNome, 250f, currentY, labelPaint)
                canvas.drawText(t.vencimento, 370f, currentY, labelPaint)
                canvas.drawText(valorFormatado, 555f, currentY, paintRendaRight)

                canvas.drawLine(30f, currentY + 6f, 565f, currentY + 6f, linePaint)
                currentY += rowHeight
            }
        }

        currentY += 15f

        // 3. Tabela de DESPESAS (SAÍDAS) ordenadas por data de vencimento
        val despesasOrdenadas = despesas.sortedBy { extrairOrdemData(it.vencimento) }
        checkPageBreak(60f)

        canvas.drawText(getString(R.string.pdf_secao_despesas), 30f, currentY, textBoldPaint)
        currentY += 12f
        drawTableHeader(canvas, currentY)
        currentY += 34f

        if (despesasOrdenadas.isEmpty()) {
            canvas.drawText(getString(R.string.pdf_sem_despesas), 40f, currentY, labelPaint)
            currentY += rowHeight
        } else {
            for (t in despesasOrdenadas) {
                checkPageBreak(rowHeight)

                val itemNomeStr = if (t.recorrente && t.parcelasTotais > 0) {
                    val parcelaAtual = t.parcelasTotais - t.parcelasRestantes
                    "${t.item} ($parcelaAtual/${t.parcelasTotais})"
                } else {
                    t.item
                }
                val itemNome = if (itemNomeStr.length > 28) itemNomeStr.take(26) + ".." else itemNomeStr
                val catNome = if (t.categoria.length > 18) t.categoria.take(16) + ".." else t.categoria
                val valorFormatado = CurrencyFormatter.formatar(t.valor, moedaAtual)

                canvas.drawText(itemNome, 40f, currentY, textPaint)
                canvas.drawText(catNome, 250f, currentY, labelPaint)
                canvas.drawText(t.vencimento, 370f, currentY, labelPaint)
                canvas.drawText(valorFormatado, 555f, currentY, paintDespesaRight)

                canvas.drawLine(30f, currentY + 6f, 565f, currentY + 6f, linePaint)
                currentY += rowHeight
            }
        }

        pdfDocument.finishPage(page)

        val nomeFicheiro = "Relatorio_${mes}_$ano.pdf"
        try {
            val baos = ByteArrayOutputStream()
            pdfDocument.writeTo(baos)
            val bytes = baos.toByteArray()
            val ok = guardarFicheiroEmDownloads(nomeFicheiro, "application/pdf", bytes)
            if (ok) {
                showToast(getString(R.string.toast_relatorio_pdf_salvo), isLong = true)
            } else {
                showToast(getString(R.string.toast_relatorio_pdf_erro_guardar))
            }
        } catch (e: Exception) {
            showToast(getString(R.string.toast_relatorio_pdf_erro_gerar, e.message ?: ""))
        } finally {
            pdfDocument.close()
        }
    }

    private fun guardarFicheiroEmDownloads(nomeFicheiro: String, mimeType: String, bytes: ByteArray): Boolean {
        val resolver = contentResolver
        var sucesso = false

        // 1. Tentar MediaStore (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFicheiro)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                        os.flush()
                    }
                    sucesso = true
                }
            } catch (e: Exception) {
                Log.e("ResumoActivity", "Erro MediaStore Q+: ${e.message}")
            }
        }

        // 2. Gravação direta na pasta Downloads pública
        if (!sucesso) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, nomeFicheiro)
                FileOutputStream(file).use { os ->
                    os.write(bytes)
                    os.flush()
                }
                sucesso = true
            } catch (e: Exception) {
                Log.e("ResumoActivity", "Erro Fallback Downloads: ${e.message}")
            }
        }

        // 3. Fallback no diretório de ficheiros externos da ‘app’
        if (!sucesso) {
            try {
                val appDownloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (appDownloadsDir != null) {
                    if (!appDownloadsDir.exists()) appDownloadsDir.mkdirs()
                    val file = File(appDownloadsDir, nomeFicheiro)
                    FileOutputStream(file).use { os ->
                        os.write(bytes)
                        os.flush()
                    }
                    sucesso = true
                }
            } catch (e: Exception) {
                Log.e("ResumoActivity", "Erro App External Files: ${e.message}")
            }
        }

        return sucesso
    }

    private fun extrairOrdemData(vencimento: String): Int {
        val partes = vencimento.split("/")
        val dia = partes.getOrNull(0)?.toIntOrNull() ?: 1
        val mesNum = partes.getOrNull(1)?.toIntOrNull() ?: 1
        val anoNum = partes.getOrNull(2)?.toIntOrNull() ?: 2026
        return anoNum * 10000 + mesNum * 100 + dia
    }

    private fun mostrarOpcoesExportacao() {
        val mes = binding.spinnerMes.selectedItem?.toString() ?: ""
        val ano = binding.spinnerAno.selectedItem as? Int ?: 2026

        val dialogView = layoutInflater.inflate(R.layout.dialog_exportar_relatorio, binding.root as? ViewGroup, false)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvTituloExportar)
        val cardPDF = dialogView.findViewById<View>(R.id.cardExportarPDF)
        val cardCSV = dialogView.findViewById<View>(R.id.cardExportarCSV)
        val btnCancelar = dialogView.findViewById<View>(R.id.btnCancelarExportar)

        tvTitulo.text = getString(R.string.dialog_exportar_titulo, mes, ano)

        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        dialog.setContentView(dialogView)

        cardPDF.setOnClickListener {
            dialog.dismiss()
            exportarParaPDF()
        }

        cardCSV.setOnClickListener {
            dialog.dismiss()
            exportarParaCSV()
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun mostrarOpcoesBackup() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_backup, binding.root as? ViewGroup, false)
        val cardExp = dialogView.findViewById<View>(R.id.cardExportarBackup)
        val cardImp = dialogView.findViewById<View>(R.id.cardImportarBackup)
        val btnCancelar = dialogView.findViewById<View>(R.id.btnCancelarBackup)

        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        dialog.setContentView(dialogView)

        cardExp?.setOnClickListener {
            dialog.dismiss()
            val nomeFicheiro = "backup_finly_${System.currentTimeMillis()}.finly"
            exportarBackupLauncher.launch(nomeFicheiro)
        }

        cardImp?.setOnClickListener {
            dialog.dismiss()
            importarBackupLauncher.launch(arrayOf("*/*"))
        }

        btnCancelar?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun exportarParaCSV() {
        val mes = binding.spinnerMes.selectedItem?.toString() ?: ""
        val ano = binding.spinnerAno.selectedItem?.toString() ?: ""
        val transacoes = transacoesAtuaisGrafico

        if (transacoes.isEmpty()) {
            showToast(getString(R.string.toast_csv_sem_transacoes, mes, ano))
            return
        }

        try {
            val exporter = CsvExporter(this)
            val file = exporter.exportarTransacoesMes("$mes de $ano", transacoes)
            val bytes = file.readBytes()
            val nomeFicheiro = file.name
            val ok = guardarFicheiroEmDownloads(nomeFicheiro, "text/csv", bytes)
            if (ok) {
                showToast(getString(R.string.toast_csv_salvo), isLong = true)
            } else {
                showToast(getString(R.string.toast_csv_erro_guardar))
            }
        } catch (e: Exception) {
            showToast(getString(R.string.toast_csv_erro_exportar, e.message ?: ""))
        }
    }

    override fun onResume() {
        super.onResume()
        estilarMenuItemsDrawer()
        sincronizarSelecaoDataComPrefs()
        ativarSincronizacaoTempoReal()
        val notifEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("NOTIFICATIONS", false)
        if (notifEnabled) {
            NotificationHelper.agendarWorkerNotificacoes(this)
        }
        prosseguirComCarregamento()
        atualizarDrawerHeader()
    }

    private fun sincronizarSelecaoDataComPrefs() {
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val ultimoMesSalvo = sharedPref.getString(KEY_LAST_MONTH, null)
        val ultimoAnoSalvo = sharedPref.getInt(KEY_LAST_YEAR, 0)

        if (ultimoMesSalvo != null) {
            val indexMes = meses.indexOf(ultimoMesSalvo)
            if (indexMes != -1 && indexMes != binding.spinnerMes.selectedItemPosition) {
                binding.spinnerMes.setSelection(indexMes, false)
            }
        }

        if (ultimoAnoSalvo != 0) {
            val indexAno = anos.indexOf(ultimoAnoSalvo)
            if (indexAno != -1 && indexAno != binding.spinnerAno.selectedItemPosition) {
                binding.spinnerAno.setSelection(indexAno, false)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        FirebaseManager.pararMonitoramento()
    }

    private fun ativarSincronizacaoTempoReal() {
        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        if (email.isEmpty() || email == "CONVIDADO") return
        val emailClean = email.trim().lowercase()

        lifecycleScope.launch(Dispatchers.IO) {
            FirebaseManager.removerCampoSenhaDoFirestore(emailClean)
        }

        FirebaseManager.monitorarTransacoes(emailClean) { listaNuvem ->
            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
                val dao = db.utilizadorDao()
                
                // Inserir ou Atualizar o que veio da nuvem
                listaNuvem.forEach { trans ->
                    val existeLocal = dao.obterTransacaoPorId(trans.id)
                    if (existeLocal == null) {
                        dao.inserirTransacao(trans)
                    } else if (existeLocal != trans) {
                        dao.atualizarTransacao(trans)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    prosseguirComCarregamento()
                }
            }
        }

        FirebaseManager.monitorarMetas(emailClean) { metasNuvem ->
            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
                val dao = db.utilizadorDao()
                metasNuvem.forEach { meta ->
                    val existe = dao.obterMetaPorMes(emailClean, meta.mes, meta.ano)
                    val metaSalvar = meta.copy(id = existe?.id ?: meta.id, donoEmail = emailClean)
                    dao.salvarMeta(metaSalvar)
                }
                withContext(Dispatchers.Main) {
                    prosseguirComCarregamento()
                }
            }
        }
    }

    private fun prosseguirComCarregamento() {
        val prefsManager = UserPreferencesManager(this)

        // Se a moeda ainda não foi confirmada localmente
        if (!prefsManager.isMoedaConfigurada()) {
            val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
            val emailClean = email.trim().lowercase()

            // Utilizador autenticado: aguarda sincronização do Firebase
            if (emailClean.isNotEmpty() && !FirebaseManager.isGuestEmail(emailClean)) {
                prefsManager.sincronizarMoedaDoFirebase(emailClean) { moedaNuvem ->
                    moedaAtual = moedaNuvem
                    if (!prefsManager.isMoedaConfigurada()) {
                        verificarSeExibeOnboarding()
                    } else {
                        executarCarregamentoInterface()
                    }
                }
                return
            } else {
                verificarSeExibeOnboarding()
                return
            }
        }

        executarCarregamentoInterface()
    }

    private fun verificarSeExibeOnboarding() {
        if (!isShowingOnboardingMoeda) {
            isShowingOnboardingMoeda = true
            mostrarBottomSheetMoedaInicial()
        }
    }

    private fun executarCarregamentoInterface() {
        val mesAtual = obterMesSelecionadoCanonical()
        val anoAtual = binding.spinnerAno.selectedItem as? Int ?: anos[0]
        iniciarObservacaoFlow(mesAtual, anoAtual)
        atualizarDrawerHeader()
    }

    private fun iniciarObservacaoFlow(mesSel: String, anoSel: Int) {
        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        val emailClean = email.trim().lowercase()
        if (emailClean.isEmpty()) return

        resumoFlowJob?.cancel()
        resumoFlowJob = lifecycleScope.launch {
            val db = MinhaBaseDados.getDatabase(this@ResumoActivity)

            val transacoesFlow = db.utilizadorDao().obterTransacoesPorMesFlow(emailClean, mesSel, anoSel)
            val metaFlow = db.utilizadorDao().obterMetaPorMesFlow(emailClean, mesSel, anoSel)

            transacoesFlow.combine(metaFlow) { _, _ ->
                Pair(mesSel, anoSel)
            }.collect {
                carregarDados(mesSel, anoSel)
            }
        }
    }

    private fun mostrarBottomSheetMoedaInicial() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val bsView = layoutInflater.inflate(R.layout.bottom_sheet_escolher_moeda_inicial, null)
        bottomSheetDialog.setContentView(bsView)
        bottomSheetDialog.setCancelable(false)
        bottomSheetDialog.window?.setDimAmount(0.85f)

        val tvTitulo = bsView.findViewById<TextView>(R.id.tvTituloBoasVindas)
        val nomeSalvo = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("NAME", "") ?: ""
        val primeiroNome = nomeSalvo.trim().split(" ").firstOrNull() ?: ""

        if (primeiroNome.isNotEmpty()) {
            tvTitulo?.text = getString(R.string.onboarding_moeda_titulo_nome, primeiroNome)
        } else {
            tvTitulo?.text = getString(R.string.onboarding_moeda_titulo)
        }

        val cardEUR = bsView.findViewById<MaterialCardView>(R.id.cardMoedaEUR)
        val cardBRL = bsView.findViewById<MaterialCardView>(R.id.cardMoedaBRL)
        val rbEUR = bsView.findViewById<RadioButton>(R.id.rbMoedaEUR)
        val rbBRL = bsView.findViewById<RadioButton>(R.id.rbMoedaBRL)
        val btnConfirmar = bsView.findViewById<View>(R.id.btnConfirmarMoedaInicial)

        var moedaSelecionada = Moeda.EUR

        fun atualizarSelecaoVisual(moeda: Moeda) {
            moedaSelecionada = moeda
            val isBrl = (moeda == Moeda.BRL)
            rbEUR?.isChecked = !isBrl
            rbBRL?.isChecked = isBrl

            val strokeWidthActive = (2 * resources.displayMetrics.density).toInt()
            val strokeWidthInactive = (1 * resources.displayMetrics.density).toInt()
            val colorPrimary = ContextCompat.getColor(this, R.color.colorPrimary)
            val colorDivider = ContextCompat.getColor(this, R.color.dividerColor)

            cardEUR?.strokeColor = if (!isBrl) colorPrimary else colorDivider
            cardEUR?.strokeWidth = if (!isBrl) strokeWidthActive else strokeWidthInactive

            cardBRL?.strokeColor = if (isBrl) colorPrimary else colorDivider
            cardBRL?.strokeWidth = if (isBrl) strokeWidthActive else strokeWidthInactive
        }

        cardEUR?.setOnClickListener {
            FinanceiroUtils.dispararHapticFeedback(it)
            atualizarSelecaoVisual(Moeda.EUR)
        }

        cardBRL?.setOnClickListener {
            FinanceiroUtils.dispararHapticFeedback(it)
            atualizarSelecaoVisual(Moeda.BRL)
        }

        btnConfirmar?.setOnClickListener {
            FinanceiroUtils.dispararHapticFeedback(it)
            isShowingOnboardingMoeda = false
            val moedaDesc = if (moedaSelecionada == Moeda.BRL)
                getString(R.string.moeda_real_nome_completo)
            else
                getString(R.string.moeda_euro_nome_completo)

            UserPreferencesManager(this).salvarMoeda(moedaSelecionada) {
                showToast(getString(R.string.toast_moeda_configurada_sucesso, moedaDesc))
            }
            bottomSheetDialog.dismiss()
            prosseguirComCarregamento()
        }

        bottomSheetDialog.show()
    }


    private fun irParaMeuPerfil() {
        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        val emailClean = email.trim().lowercase()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
            val utilizador = db.utilizadorDao().buscarPorEmail(emailClean) ?: db.utilizadorDao().obterTodosUtilizadores().find { it.email.trim().lowercase() == emailClean }

            withContext(Dispatchers.Main) {
                if (utilizador != null) {
                    val intent = Intent(this@ResumoActivity, DetalhesPageActivity::class.java)
                    intent.putExtra("isOwnProfile", true)
                    intent.putExtra("id", utilizador.id)
                    intent.putExtra("name", utilizador.nome)
                    intent.putExtra("email", utilizador.email)
                    intent.putExtra("phone", utilizador.telemovel)
                    intent.putExtra("senha", utilizador.senha)
                    startActivity(intent)
                } else if (email == "CONVIDADO" || email == "convidado" || emailClean == "convidado") {
                    val intent = Intent(this@ResumoActivity, DetalhesPageActivity::class.java)
                    intent.putExtra("isOwnProfile", true)
                    intent.putExtra("isGuest", true)
                    intent.putExtra("name", getString(R.string.utilizador_convidado))
                    intent.putExtra("email", "convidado")
                    startActivity(intent)
                } else {
                    showToast(getString(R.string.toast_carregar_perfil))
                }
            }
        }
    }

    private class HighlightSpinnerAdapter<T>(
        context: Context,
        objects: Array<T>,
        private val getSelectedIndex: () -> Int
    ) : ArrayAdapter<T>(context, R.layout.spinner_selected_item, objects) {

        init {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent)
            val textView = view.findViewById<TextView>(android.R.id.text1) ?: (view as? TextView)

            val selectedIndex = getSelectedIndex()
            if (position == selectedIndex) {
                textView?.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
                textView?.setTypeface(null, Typeface.BOLD)
            } else {
                textView?.setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
                textView?.setTypeface(null, Typeface.NORMAL)
            }
            return view
        }
    }

    private fun obterMesSelecionadoCanonical(): String {
        val pos = binding.spinnerMes.selectedItemPosition
        return if (pos in meses.indices) meses[pos] else "Janeiro"
    }

    private fun configurarSpinners() {
        val mesesExibicao = meses.map { IdiomaUtils.formatarNomeMes(this, it) }.toTypedArray()
        val adapterMes = HighlightSpinnerAdapter(this, mesesExibicao) { binding.spinnerMes.selectedItemPosition }
        binding.spinnerMes.adapter = adapterMes

        val adapterAno = HighlightSpinnerAdapter(this, anos) { binding.spinnerAno.selectedItemPosition }
        binding.spinnerAno.adapter = adapterAno

        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val ultimoMesSalvo = sharedPref.getString(KEY_LAST_MONTH, null)
        val ultimoAnoSalvo = sharedPref.getInt(KEY_LAST_YEAR, 0)
        val cal = Calendar.getInstance()
        
        if (ultimoMesSalvo != null) {
            val index = meses.indexOf(ultimoMesSalvo)
            if (index != -1) binding.spinnerMes.setSelection(index)
        } else {
            binding.spinnerMes.setSelection(cal[Calendar.MONTH])
        }

        if (ultimoAnoSalvo != 0) {
            val index = anos.indexOf(ultimoAnoSalvo)
            if (index != -1) binding.spinnerAno.setSelection(index)
        } else {
            val index = anos.indexOf(cal[Calendar.YEAR])
            if (index != -1) binding.spinnerAno.setSelection(index)
        }

        val itemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mesSel = obterMesSelecionadoCanonical()
                val anoSel = binding.spinnerAno.selectedItem as Int
                if (lastLoadedMonth == mesSel && lastLoadedYear == anoSel) return
                
                sharedPref.edit {
                    putString(KEY_LAST_MONTH, mesSel)
                    putInt(KEY_LAST_YEAR, anoSel)
                }
                carregarDados(mesSel, anoSel)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerMes.onItemSelectedListener = itemSelectedListener
        binding.spinnerAno.onItemSelectedListener = itemSelectedListener

        binding.llMesSelector.setOnClickListener {
            mostrarDialogoSelecaoPeriodo()
        }
    }

    private fun mostrarDialogoSelecaoPeriodo() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_selecionar_periodo, binding.root as? ViewGroup, false)
        val llAnosContainer = dialogView.findViewById<LinearLayout>(R.id.llAnosContainer)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelarPeriodo)
        val btnAplicar = dialogView.findViewById<Button>(R.id.btnAplicarPeriodo)

        val botoesMeses = mapOf(
            "Janeiro" to dialogView.findViewById<MaterialButton>(R.id.btnMesJan),
            "Fevereiro" to dialogView.findViewById<MaterialButton>(R.id.btnMesFev),
            "Março" to dialogView.findViewById<MaterialButton>(R.id.btnMesMar),
            "Abril" to dialogView.findViewById<MaterialButton>(R.id.btnMesAbr),
            "Maio" to dialogView.findViewById<MaterialButton>(R.id.btnMesMai),
            "Junho" to dialogView.findViewById<MaterialButton>(R.id.btnMesJun),
            "Julho" to dialogView.findViewById<MaterialButton>(R.id.btnMesJul),
            "Agosto" to dialogView.findViewById<MaterialButton>(R.id.btnMesAgo),
            "Setembro" to dialogView.findViewById<MaterialButton>(R.id.btnMesSet),
            "Outubro" to dialogView.findViewById<MaterialButton>(R.id.btnMesOut),
            "Novembro" to dialogView.findViewById<MaterialButton>(R.id.btnMesNov),
            "Dezembro" to dialogView.findViewById<MaterialButton>(R.id.btnMesDez)
        )

        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        dialog.setContentView(dialogView)

        var anoTemp = binding.spinnerAno.selectedItem as? Int ?: anos[0]
        var mesTemp = binding.spinnerMes.selectedItem?.toString() ?: meses[0]

        fun atualizarEstiloBotoesMes() {
            botoesMeses.forEach { (nomeMes, btn) ->
                val selected = nomeMes.equals(mesTemp, ignoreCase = true)
                if (selected) {
                    btn?.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
                    btn?.setTextColor(ContextCompat.getColor(this, R.color.white))
                    btn?.setStrokeColorResource(R.color.colorPrimary)
                } else {
                    btn?.setBackgroundColor(ContextCompat.getColor(this, R.color.surfaceColor))
                    btn?.setTextColor(ContextCompat.getColor(this, R.color.textColorPrimary))
                    btn?.setStrokeColorResource(android.R.color.transparent)
                }
            }
        }

        botoesMeses.forEach { (nomeMes, btn) ->
            btn?.setOnClickListener {
                mesTemp = nomeMes
                atualizarEstiloBotoesMes()
            }
        }
        atualizarEstiloBotoesMes()

        fun atualizarEstiloBotoesAno() {
            for (i in 0 until llAnosContainer.childCount) {
                val btnChild = llAnosContainer.getChildAt(i) as? MaterialButton ?: continue
                val selected = (btnChild.text.toString() == anoTemp.toString())
                if (selected) {
                    btnChild.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
                    btnChild.setTextColor(ContextCompat.getColor(this, R.color.white))
                    btnChild.setStrokeColorResource(R.color.colorPrimary)
                } else {
                    btnChild.setBackgroundColor(ContextCompat.getColor(this, R.color.surfaceColor))
                    btnChild.setTextColor(ContextCompat.getColor(this, R.color.textColorPrimary))
                    btnChild.setStrokeColorResource(android.R.color.transparent)
                }
            }
        }

        val anosDisponiveis = FinanceiroUtils.obterAnosDisponiveis(transacoesAtuaisGrafico)
        llAnosContainer.removeAllViews()

        anosDisponiveis.forEach { ano ->
            val btnAno = MaterialButton(this).apply {
                text = ano.toString()
                textSize = 13f
                isCheckable = false
                strokeWidth = (1 * resources.displayMetrics.density).toInt()
                cornerRadius = (12 * resources.displayMetrics.density).toInt()
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (48 * resources.displayMetrics.density).toInt()
                ).apply {
                    setMargins(0, 0, (8 * resources.displayMetrics.density).toInt(), 0)
                }
                layoutParams = lp

                setOnClickListener {
                    anoTemp = ano
                    atualizarEstiloBotoesAno()
                }
            }
            llAnosContainer.addView(btnAno)
        }
        atualizarEstiloBotoesAno()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnAplicar.setOnClickListener {
            dialog.dismiss()
            val indexMes = meses.indexOf(mesTemp)
            val indexAno = anos.indexOf(anoTemp)
            if (indexMes != -1) binding.spinnerMes.setSelection(indexMes)
            if (indexAno != -1) binding.spinnerAno.setSelection(indexAno)

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                putString(KEY_LAST_MONTH, mesTemp)
                putInt(KEY_LAST_YEAR, anoTemp)
            }
            carregarDados(mesTemp, anoTemp)
        }

        dialog.show()
    }

    private fun carregarDados(mesSelecionado: String, anoSelecionado: Int) {
        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        lastLoadedMonth = mesSelecionado
        lastLoadedYear = anoSelecionado

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
            recurrenceMutex.withLock {
                processarRecorrencia(db, email, mesSelecionado, anoSelecionado)
            }

            // OBTER META ESPECÍFICA DO MÊS
            val metaObj = db.utilizadorDao().obterMetaPorMes(email, mesSelecionado, anoSelecionado)
            metaAtual = metaObj?.valor ?: 0.0

            val transacoes = db.utilizadorDao().obterTransacoesPorMes(email, mesSelecionado, anoSelecionado)
            
            // Totais Absolutos (Para os cards de categoria)
            // ‘BUG’ FIX: Poupança e Exterior não somam mais no card de Renda Geral nem no de Despesas Gerais
            val totalRenda = transacoes.asSequence()
                .filter { it.tipo == "RENDA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) && it.categoria != "Exterior" }
                .sumOf { it.valor }
                
            val totalDespesas = transacoes.asSequence()
                .filter { it.tipo == "DESPESA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) }
                .sumOf { it.valor }
            
            // Poupança realizada no mês atual (para a barra de Meta do Mês)
            val poupancaNoMes = FinanceiroUtils.calcularTotalPoupanca(transacoes)
            
            // Total Acumulado Geral de Poupança (para o campo 'Total poupança' no Balanço)
            val todasTransacoesDono = db.utilizadorDao().obterTransacoesPorDono(email)
            val totalPoupancaGeral = FinanceiroUtils.calcularTotalPoupanca(todasTransacoesDono)

            val totalExterior = transacoes.asSequence().filter { it.categoria == "Exterior" }.sumOf { it.valor }
            
            // Totais Pagos (Para o Saldo Final)
            val saldoPaga = FinanceiroUtils.calcularSaldoPago(transacoes)
            val percentagemGasta = FinanceiroUtils.calcularPercentagemGasta(totalRenda, totalDespesas)

            // Cálculos para Previsão de Fim de Mês
            val despesasPendentes = transacoes.filter { it.tipo == "DESPESA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) && !it.status }
            val totalDespesasPendentes = despesasPendentes.sumOf { it.valor }
            val rendasPendentes = transacoes.filter { it.tipo == "RENDA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) && !it.status }
            val totalRendasPendentes = rendasPendentes.sumOf { it.valor }
            val saldoProjetado = saldoPaga - totalDespesasPendentes + totalRendasPendentes

            // Comparativo com o Mês Anterior
            val mesIndexAtual = meses.indexOf(mesSelecionado)
            val (prevMesNome, prevAno) = if (mesIndexAtual > 0) {
                Pair(meses[mesIndexAtual - 1], anoSelecionado)
            } else {
                Pair(meses[11], anoSelecionado - 1)
            }

            val transacoesAnteriores = db.utilizadorDao().obterTransacoesPorMes(email, prevMesNome, prevAno)
            val despesasAnteriores = transacoesAnteriores.asSequence()
                .filter { it.tipo == "DESPESA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) }
                .sumOf { it.valor }

            withContext(Dispatchers.Main) {
                atualizarInterface(
                    renda = totalRenda,
                    despesa = totalDespesas,
                    poupanca = totalPoupancaGeral,
                    exterior = totalExterior,
                    saldo = saldoPaga,
                    percent = percentagemGasta,
                    prevMesNome = prevMesNome,
                    despesaAnterior = despesasAnteriores,
                    totalDespesasPendentes = totalDespesasPendentes,
                    numDespesasPendentes = despesasPendentes.size,
                    saldoProjetado = saldoProjetado,
                )
                configurarGrafico(transacoes)
                // A barra de meta do mês utiliza a poupança realizada neste mês específico
                atualizarBarraMeta(poupancaNoMes)
                animarEntradaCards()
            }
        }
    }

    private var jaAnimouCards = false

    private fun animarEntradaCards() {
        if (jaAnimouCards) return
        jaAnimouCards = true

        val cards = listOf(
            binding.cardRendaGasta,
            binding.cardResumoCategoria,
            binding.cardMetaPoupanca,
            binding.cardBalancoeSaldo
        )

        cards.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 30f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(400L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun atualizarBarraMeta(valorPoupado: Double) {
        val moedaAtual = UserPreferencesManager(this).obterMoedaAtual()
        binding.tvMetaValor.text = CurrencyFormatter.formatar(metaAtual, moedaAtual)

        if (metaAtual > 0.0) {
            binding.btnDefinirMeta.text = getString(R.string.btn_alterar_meta)
        } else {
            binding.btnDefinirMeta.text = getString(R.string.btn_definir_meta)
        }
        
        if (metaAtual > 0) {
            val valorRealPoupado = valorPoupado.coerceAtLeast(0.0)
            val progresso = ((valorRealPoupado / metaAtual) * 100).toInt().coerceIn(0, 100)
            
            // Animação suave na barra de progresso (Ideia 1)
            ObjectAnimator.ofInt(binding.pbMetaPoupanca, "progress", binding.pbMetaPoupanca.progress, progresso).apply {
                duration = 500L
                interpolator = DecelerateInterpolator()
                start()
            }

            val valorPoupadoFmt = CurrencyFormatter.formatar(valorRealPoupado, moedaAtual)
            binding.tvMetaStatus.text = getString(R.string.poupado_label, valorPoupadoFmt, progresso)
            
            if (progresso >= 100) {
                binding.tvMetaStatus.setTextColor(ContextCompat.getColor(this, R.color.colorPositive))
                binding.tvMetaStatus.text = getString(R.string.meta_atingida)
            } else {
                binding.tvMetaStatus.setTextColor(ContextCompat.getColor(this, R.color.textColorSecondary))
            }
        } else {
            binding.pbMetaPoupanca.progress = 0
            binding.tvMetaStatus.text = getString(R.string.meta_definir_aviso)
            binding.tvMetaStatus.setTextColor(ContextCompat.getColor(this, R.color.textColorSecondary))
        }
    }

    private fun configurarGrafico(transacoes: List<Transacao>) {
        transacoesAtuaisGrafico = transacoes
        val despesas = transacoes.filter { it.tipo == "DESPESA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) }
        val gastosPorCategoria = despesas.groupBy { it.categoria }
            .mapValues { it.value.sumOf { t -> t.valor }.toFloat() }
            .toList()
            .filter { it.second > 0f }
            .sortedByDescending { it.second }

        if (gastosPorCategoria.isEmpty()) {
            binding.cardResumoCategoria.visibility = View.GONE
            binding.pieChart.visibility = View.GONE
            binding.llCategoryDetails.visibility = View.GONE
            binding.btnVerMaisCategorias.visibility = View.GONE
            return
        }
        binding.cardResumoCategoria.visibility = View.VISIBLE
        binding.pieChart.visibility = View.VISIBLE
        binding.llCategoryDetails.visibility = View.VISIBLE

        // Se colapsado e existirem mais de 4 categorias, agrupar as restantes como "Outros"
        // para garantir correspondência 1-para-1 exata entre as fatias do gráfico e a legenda
        val itensGraficoELegenda = if (!isCategoriesExpanded && gastosPorCategoria.size > 4) {
            val top3 = gastosPorCategoria.take(3).toMutableList()
            val restoValor = gastosPorCategoria.drop(3).sumOf { it.second.toDouble() }.toFloat()
            top3.add(Pair(getString(R.string.grafico_categoria_outros), restoValor))
            top3
        } else {
            gastosPorCategoria
        }

        binding.btnVerMaisCategorias.visibility = if (gastosPorCategoria.size > 4) View.VISIBLE else View.GONE
        binding.btnVerMaisCategorias.text = if (isCategoriesExpanded)
            getString(R.string.btn_ver_menos_categorias)
        else
            getString(R.string.btn_ver_mais_categorias)

        val totalGastos = gastosPorCategoria.sumOf { it.second.toDouble() }
        val entries = itensGraficoELegenda.map { PieEntry(it.second, it.first) }
        
        // Cores diversificadas para despesas (sem verde, reservado para rendas/poupança)
        val expenseColors = listOf(
            "#7E57C2".toColorInt(), // Roxo / Violeta
            "#00897B".toColorInt(), // Azul-petróleo (Teal)
            "#FB8C00".toColorInt(), // Laranja
            "#00ACC1".toColorInt(), // Ciano
            "#3F51B5".toColorInt(), // Índigo
            "#E91E63".toColorInt(), // Rosa
            "#FF5722".toColorInt(), // Laranja escuro / Coral
            "#2196F3".toColorInt()  // Azul
        )

        val dataSet = PieDataSet(entries, "").apply {
            colors = expenseColors
            setDrawValues(false)
            isHighlightEnabled = false // Remove o ponto/artefato preto de destaque ao tocar
            sliceSpace = 3f // Linha fina de separação (slice space) entre fatias
        }

        val moedaAtual = UserPreferencesManager(this).obterMoedaAtual()
        val totalStr = CurrencyFormatter.formatar(totalGastos, moedaAtual)
        val centerTextRaw = getString(R.string.grafico_centro_total, totalStr)
        val centerString = SpannableString(centerTextRaw)
        val breakIndex = centerTextRaw.indexOf('\n')
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val mutedColor = if (isDark) "#B0B0B0".toColorInt() else "#666666".toColorInt()

        if (breakIndex != -1) {
            centerString.setSpan(ForegroundColorSpan(mutedColor), 0, breakIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            centerString.setSpan(RelativeSizeSpan(0.75f), 0, breakIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            centerString.setSpan(ForegroundColorSpan(textColor), breakIndex, centerString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            centerString.setSpan(RelativeSizeSpan(1.15f), breakIndex, centerString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            centerString.setSpan(StyleSpan(Typeface.BOLD), breakIndex, centerString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val pieData = PieData(dataSet)
        binding.pieChart.apply {
            data = pieData
            description.isEnabled = false
            centerText = centerString
            setCenterTextSize(15f)
            setHighlightPerTapEnabled(false)
            legend.isEnabled = false
            
            setHoleColor(0) 
            transparentCircleRadius = 52f
            holeRadius = 48f
            
            setDrawEntryLabels(false) 
            setExtraOffsets(5f, 5f, 5f, 5f)
            
            if (binding.pieChart.data == null) animateY(1000)
            invalidate()
        }

        // PREENCHER A Lista DE LEGENDA DETALHADA ABAIXO DO GRÁFICO
        binding.llCategoryDetails.removeAllViews()
        itensGraficoELegenda.forEachIndexed { index, pair ->
            val color = expenseColors[index % expenseColors.size]
            val percent = if (totalGastos > 0) (pair.second / totalGastos * 100).toInt() else 0
            adicionarItemCategoria(pair.first, pair.second.toDouble(), percent, color, moedaAtual)
        }
    }


    private fun adicionarItemCategoria(nome: String, valor: Double, percent: Int, cor: Int, moeda: Moeda) {
        val itemView = layoutInflater.inflate(R.layout.item_categoria_resumo, binding.llCategoryDetails, false)
        val prefs = UserPreferencesManager(this)
        val limite = prefs.obterLimiteCategoria(nome)

        itemView.findViewById<View>(R.id.vColorIndicator).background.setTint(cor)
        itemView.findViewById<TextView>(R.id.tvCategoryName).text = IdiomaUtils.formatarNomeCategoria(this, nome)

        val tvValRes = itemView.findViewById<TextView>(R.id.tvCategoryValue)
        val tvPercent = itemView.findViewById<TextView>(R.id.tvCategoryPercent)

        if (limite > 0.0) {
            val valorStr = CurrencyFormatter.formatar(valor, moeda)
            val limiteStr = CurrencyFormatter.formatar(limite, moeda)
            tvValRes.text = "$valorStr / $limiteStr"

            val pctLimite = ((valor / limite) * 100).toInt()
            when {
                pctLimite >= 100 -> {
                    tvValRes.setTextColor(ContextCompat.getColor(this, R.color.colorNegative))
                    tvPercent.text = "($pctLimite% ⚠️)"
                    tvPercent.setTextColor(ContextCompat.getColor(this, R.color.colorNegative))
                }
                pctLimite >= 80 -> {
                    tvValRes.setTextColor("#FF9800".toColorInt())
                    tvPercent.text = "($pctLimite%)"
                    tvPercent.setTextColor("#FF9800".toColorInt())
                }
                else -> {
                    tvValRes.setTextColor(ContextCompat.getColor(this, R.color.colorPositive))
                    tvPercent.text = "($pctLimite%)"
                    tvPercent.setTextColor(ContextCompat.getColor(this, R.color.textColorSecondary))
                }
            }
        } else {
            tvValRes.text = CurrencyFormatter.formatar(valor, moeda)
            tvPercent.text = String.format(Locale.getDefault(), "(%d%%)", percent)
        }

        itemView.setOnClickListener {
            abrirEditarCategoria(nome, limite)
        }

        binding.llCategoryDetails.addView(itemView)
    }

    private fun abrirEditarCategoria(nome: String, limiteAtual: Double) {
        val sheet = EditarCategoriaBottomSheet().apply {
            arguments = Bundle().apply {
                putParcelable("categoria", Categoria(nome = nome, limiteMensal = limiteAtual))
            }
        }
        sheet.onSalvarCategoriaListener = { novoNome, novoLimite ->
            val prefs = UserPreferencesManager(this@ResumoActivity)
            prefs.salvarLimiteCategoria(nome, novoLimite)
            if (novoNome != nome && novoNome.isNotBlank()) {
                prefs.salvarLimiteCategoria(novoNome, novoLimite)
            }
            val mesSel = lastLoadedMonth ?: obterMesSelecionadoCanonical()
            val anoSel = lastLoadedYear ?: (binding.spinnerAno.selectedItem as? Int ?: anos[0])
            carregarDados(mesSel, anoSel)
            if (novoLimite > 0.0) {
                showToast(getString(R.string.toast_categoria_limite_guardado, IdiomaUtils.formatarNomeCategoria(this, nome)))
            }
        }
        sheet.show(supportFragmentManager, "EditarCategoriaBottomSheet")
    }




    private fun atualizarAparenciaCabecalho() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val headerBgColor = if (isDark) ContextCompat.getColor(this, R.color.surfaceColor) else ContextCompat.getColor(this, R.color.headerColor)
        binding.viewHeader.setBackgroundColor(headerBgColor)

        val textColor = ContextCompat.getColor(this, R.color.textColorPrimary)
        binding.tvTitle.setTextColor(textColor)
        binding.btnMenu.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    private fun atualizarInterface(
        renda: Double,
        despesa: Double,
        poupanca: Double,
        exterior: Double,
        saldo: Double,
        percent: Int,
        prevMesNome: String,
        despesaAnterior: Double,
        totalDespesasPendentes: Double,
        numDespesasPendentes: Int,
        saldoProjetado: Double,
    ) {
        atualizarAparenciaCabecalho()

        val colorPositivo = ContextCompat.getColor(this, R.color.colorPositive)
        val colorNegativo = ContextCompat.getColor(this, R.color.colorNegative)
        val colorPadrao = ContextCompat.getColor(this, R.color.textColorSecondary)
        val colorAlertaAviso = "#FF9800".toColorInt()

        val moedaAtual = UserPreferencesManager(this).obterMoedaAtual()

        val isRendaZero = abs(renda) < 0.005
        binding.tvTotalRenda.text = CurrencyFormatter.formatarComSinal(renda, moedaAtual, forcarSinalPositivo = true)
        binding.tvTotalRenda.setTextColor(if (isRendaZero) colorPadrao else colorPositivo)

        val isDespesaZero = abs(despesa) < 0.005
        binding.tvTotalDespesas.text = CurrencyFormatter.formatarComSinal(-despesa, moedaAtual)
        binding.tvTotalDespesas.setTextColor(if (isDespesaZero) colorPadrao else colorNegativo)

        val colorPrimaryBrand = ContextCompat.getColor(this, R.color.colorPrimary)
        val isPoupancaZero = abs(poupanca) < 0.005
        binding.tvTotalPoupanca.text = CurrencyFormatter.formatar(poupanca, moedaAtual)
        binding.tvTotalPoupanca.setTextColor(if (isPoupancaZero) colorPadrao else colorPrimaryBrand)

        val isSaldoZero = abs(saldo) < 0.005
        binding.tvSaldoFinal.text = CurrencyFormatter.formatarComSinal(saldo, moedaAtual, forcarSinalPositivo = true)
        binding.tvSaldoFinal.setTextColor(if (isSaldoZero) colorPadrao else if (saldo < 0) colorNegativo else colorPositivo)

        binding.tvSpentValue.text = getString(R.string.gasto_total_format, CurrencyFormatter.formatar(despesa, moedaAtual))
        binding.tvPercentValue.text = String.format(Locale.getDefault(), "%d%%", percent)

        val colorPercent = when {
            percent > 100 -> colorNegativo
            percent >= 80 -> colorAlertaAviso
            else -> colorPositivo
        }
        binding.tvPercentValue.setTextColor(colorPercent)

        // 1. Alerta de teto orçamental (> 100%)
        if (percent > 100) {
            binding.cardRendaGasta.strokeColor = colorNegativo
            binding.cardRendaGasta.strokeWidth = (2 * resources.displayMetrics.density).toInt()
        } else {
            binding.cardRendaGasta.strokeWidth = 0
        }

        // 2. Comparativo com o mês anterior
        if (despesaAnterior > 0) {
            val diff = ((despesa - despesaAnterior) / despesaAnterior) * 100
            val diffAbs = abs(diff).roundToInt()
            val abrevMes = if (prevMesNome.length >= 3) prevMesNome.substring(0, 3) else prevMesNome

            when {
                diff < -0.5 -> {
                    binding.tvTagComparativoMes.text = getString(R.string.comparativo_mes_queda_fmt, diffAbs, abrevMes)
                    binding.tvTagComparativoMes.setTextColor(colorPositivo)
                    binding.tvTagComparativoMes.background = ContextCompat.getDrawable(this, R.drawable.badge_padrao_background)
                    binding.tvTagComparativoMes.backgroundTintList = ColorStateList.valueOf("#E8F5E9".toColorInt())
                    binding.tvTagComparativoMes.visibility = View.VISIBLE
                }
                diff > 0.5 -> {
                    binding.tvTagComparativoMes.text = getString(R.string.comparativo_mes_alta_fmt, diffAbs, abrevMes)
                    binding.tvTagComparativoMes.setTextColor(colorNegativo)
                    binding.tvTagComparativoMes.background = ContextCompat.getDrawable(this, R.drawable.badge_padrao_background)
                    binding.tvTagComparativoMes.backgroundTintList = ColorStateList.valueOf("#FFEBEE".toColorInt())
                    binding.tvTagComparativoMes.visibility = View.VISIBLE
                }
                else -> {
                    binding.tvTagComparativoMes.text = getString(R.string.comparativo_mes_neutro_fmt, abrevMes)
                    binding.tvTagComparativoMes.setTextColor(colorPadrao)
                    binding.tvTagComparativoMes.background = ContextCompat.getDrawable(this, R.drawable.badge_padrao_background)
                    binding.tvTagComparativoMes.backgroundTintList = null
                    binding.tvTagComparativoMes.visibility = View.VISIBLE
                }
            }
        } else {
            binding.tvTagComparativoMes.visibility = View.GONE
        }

        // 3. Previsão de final de mês
        val valorSaldoStr = CurrencyFormatter.formatarComSinal(saldoProjetado, moedaAtual, forcarSinalPositivo = (saldoProjetado >= 0))
        val pendenciasStr = CurrencyFormatter.formatar(totalDespesasPendentes, moedaAtual)

        if (saldoProjetado < 0) {
            binding.tvTagPrevisao.text = getString(R.string.tag_no_vermelho)
            binding.tvTagPrevisao.setTextColor(colorNegativo)
            binding.ivIconPrevisao.setColorFilter(colorNegativo)
            binding.tvPrevisaoDetalhes.text = if (numDespesasPendentes > 0) {
                getString(R.string.saldo_estimado_fmt, "$valorSaldoStr ${getString(R.string.previsao_pendencias_fmt, numDespesasPendentes, pendenciasStr)}")
            } else {
                getString(R.string.saldo_estimado_fmt, valorSaldoStr)
            }
        } else {
            binding.tvTagPrevisao.text = getString(R.string.tag_no_verde)
            binding.tvTagPrevisao.setTextColor(colorPositivo)
            binding.ivIconPrevisao.setColorFilter(colorPositivo)
            binding.tvPrevisaoDetalhes.text = if (numDespesasPendentes > 0) {
                getString(R.string.saldo_estimado_fmt, "$valorSaldoStr ${getString(R.string.previsao_pendencias_fmt, numDespesasPendentes, pendenciasStr)}")
            } else {
                getString(R.string.saldo_estimado_fmt, "$valorSaldoStr • ${getString(R.string.previsao_contas_em_dia)}")
            }
        }

        val balancoMensalEstimado = renda - despesa
        val projecao3Meses = saldo + (balancoMensalEstimado * 3)
        val projecao6Meses = saldo + (balancoMensalEstimado * 6)

        binding.tvProjecao3Meses.text = CurrencyFormatter.formatarComSinal(projecao3Meses, moedaAtual, forcarSinalPositivo = true)
        binding.tvProjecao3Meses.setTextColor(if (projecao3Meses >= 0) colorPositivo else colorNegativo)

        binding.tvProjecao6Meses.text = CurrencyFormatter.formatarComSinal(projecao6Meses, moedaAtual, forcarSinalPositivo = true)
        binding.tvProjecao6Meses.setTextColor(if (projecao6Meses >= 0) colorPositivo else colorNegativo)
    }

    private fun showToast(msg: String, isLong: Boolean = false) {
        ToastHelper.showCustomToast(this, msg, isLong)
    }

    private suspend fun processarRecorrencia(db: MinhaBaseDados, email: String, mesAlvo: String, anoAlvo: Int) {
        val recorrentes = db.utilizadorDao().obterTransacoesRecorrentes(email)
        val transacoesAtuaisNoMomento = db.utilizadorDao().obterTransacoesPorMes(email, mesAlvo, anoAlvo)
        
        val novas = FinanceiroUtils.gerarTransacoesRecorrentes(
            recorrentes = recorrentes.filter { it.donoEmail == email },
            existentesNoMes = transacoesAtuaisNoMomento,
            mesAlvo = mesAlvo,
            anoAlvo = anoAlvo,
            mesesArray = meses
        )

        withContext(Dispatchers.IO) {
            novas.forEach { 
                val novoId = db.utilizadorDao().inserirTransacao(it)
                val transacaoComId = it.copy(id = novoId.toInt())
                FirebaseManager.salvarTransacaoNoFirestore(transacaoComId)
            }
        }
    }

    private fun setupBackNavigation() {

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(enabled = true) {
                override fun handleOnBackPressed() {
                    if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                        binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                    } else {
                        // Fecha a aplicação completamente em vez de voltar para o ‘login’
                        finishAffinity()
                    }
                }
            },
        )
    }
}
