package com.jesse.finly

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
import androidx.annotation.RequiresApi
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.ActivityResumoBinding
import com.jesse.finly.models.MetaPoupanca
import com.jesse.finly.models.Utilizador
import com.jesse.finly.models.Transacao
import com.jesse.finly.notifications.NotificationHelper
import com.jesse.finly.utils.showToast
import com.jesse.finly.utils.FinanceiroUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
class ResumoActivity : AppCompatActivity() {
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
    private var lastLoadedYear: Int? = null
    private var metaAtual: Double = 0.0
    private var isCategoriesExpanded = false
    private var transacoesAtuaisGrafico: List<Transacao> = emptyList()


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
            intent.putExtra("MES_SELECIONADO", binding.spinnerMes.selectedItem.toString())
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
        input.setText(metaAtual.toString())
        
        // Criar o ícone com verde manualmente para garantir visibilidade
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_dashboard)?.mutate()
        icon?.setTint(ContextCompat.getColor(this, R.color.colorPrimary))

        MaterialAlertDialogBuilder(this)
            .setTitle("Meta de Poupança")
            .setIcon(icon)
            .setMessage("Quanto deseja poupar em ${binding.spinnerMes.selectedItem}?")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val novaMeta = input.text.toString().toDoubleOrNull() ?: 0.0
                salvarMeta(novaMeta)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun salvarMeta(valor: Double) {
        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        val mesSel = binding.spinnerMes.selectedItem.toString()
        val anoSel = binding.spinnerAno.selectedItem as Int
        val emailClean = email.trim().lowercase()
        
        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
            val dao = db.utilizadorDao()
            
            val metaExistente = dao.obterMetaPorMes(emailClean, mesSel, anoSel)
            val metaObj = metaExistente?.copy(valor = valor)
                ?: MetaPoupanca(mes = mesSel, ano = anoSel, valor = valor, donoEmail = emailClean)
            
            dao.salvarMeta(metaObj)

            // Sincronizar com o Firebase Firestore se não for convidado
            if (!FirebaseManager.isGuestEmail(emailClean)) {
                FirebaseManager.salvarMetaNoFirestore(metaObj)
            }
            
            withContext(Dispatchers.Main) {
                carregarDados(mesSel, anoSel)
                showToast("Meta guardada para $mesSel!")
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
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                        putBoolean("NOTIFICATIONS", perfilNuvem.notifications)
                        putBoolean("DARK_MODE", perfilNuvem.darkMode)
                        putBoolean("pref_biometric_ativa", perfilNuvem.biometricAtiva)
                        putStringSet("CUSTOM_CATEGORIES_$emailClean", HashSet(setUnido))
                    }
                    atualizarDrawerHeader()
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
                R.id.nav_gerir_categorias -> {
                    mostrarBottomSheetGerirCategorias()
                }
                R.id.nav_exportar -> {
                    mostrarOpcoesExportacao()
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
        customSet.add(catClean)
        prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", customSet) }

        sincronizarCategoriasNuvem(customSet)
        showToast(getString(R.string.toast_categoria_adicionada, catClean))
        val mesSel = binding.spinnerMes.selectedItem?.toString() ?: "Janeiro"
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
        val mesSel = binding.spinnerMes.selectedItem?.toString() ?: "Janeiro"
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
                        .setTitle("Eliminar Categoria")
                        .setMessage("Tem a certeza que deseja eliminar a categoria '$categoria'?")
                        .setPositiveButton(getString(R.string.btn_sim_eliminar)) { _, _ ->
                            onConfirm()
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                } else {
                    val quantidade = transacoesAfetadas.size
                    val mensagem = if (quantidade == 1) {
                        "1 transação será movida para a categoria Geral."
                    } else {
                        "$quantidade transações serão movidas para a categoria Geral."
                    }
                    MaterialAlertDialogBuilder(this@ResumoActivity)
                        .setTitle("Categoria em uso")
                        .setMessage(mensagem)
                        .setPositiveButton("Eliminar") { _, _ ->
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
                        .setNegativeButton("Cancelar") { dialog, _ ->
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

    private fun mostrarBottomSheetGerirCategorias() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val bsView = layoutInflater.inflate(R.layout.bottom_sheet_gerir_categorias, null)
        bottomSheetDialog.setContentView(bsView)
        bottomSheetDialog.window?.setDimAmount(0.85f)
        bottomSheetDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
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
            btnVerMaisPadrao?.text = if (isPadraoExpanded) "Ver menos" else "Ver mais categorias do sistema"

            for (catPadrao in padraoParaMostrar) {
                val itemView = layoutInflater.inflate(R.layout.item_categoria_custom, containerPadrao, false)
                val ivIcon = itemView.findViewById<ImageView>(R.id.ivIconCategoriaCustom)
                val tvNome = itemView.findViewById<TextView>(R.id.tvNomeCategoriaCustom)
                val tvTag = itemView.findViewById<TextView>(R.id.tvTagPadraoCustom)
                val btnRemover = itemView.findViewById<View>(R.id.btnRemoverCategoriaCustom)

                ivIcon?.setImageResource(obterIconeParaCategoria(catPadrao))
                tvNome?.text = catPadrao
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
                showToast("Digite o nome da categoria")
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

        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        val emailClean = email.trim().lowercase()

        if (emailClean.isEmpty() || emailClean == "convidado") {
            tvNome?.text = getString(R.string.utilizador_convidado)
            tvEmail?.text = getString(R.string.dados_apenas_locais)
            mostrarInicial(tvInitial, "Convidado")
            
            ivCloud?.setImageResource(R.drawable.ic_cloud_off)
            ivCloud?.setOnClickListener { showToast("Modo Convidado: Dados guardados apenas no dispositivo.") }
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
                    ivCloud?.setOnClickListener { showToast("Sincronizado: Os seus dados estão seguros na nuvem.") }
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
            .setTitle("Sair")
            .setIcon(icon)
            .setMessage("Deseja terminar a sessão?")
            .setPositiveButton("Sim") { _, _ ->
                // ENCERRAR NO FIREBASE
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { 
                    remove("EMAIL")
                    remove("NAME")
                    remove("LAST_LOGIN_TIMESTAMP")
                }
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun exportarParaPDF() {
        val mes = binding.spinnerMes.selectedItem.toString()
        val ano = binding.spinnerAno.selectedItem.toString()
        val transacoes = transacoesAtuaisGrafico

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
            c.drawText("Relatório Financeiro Mensal • $mes / $ano", 30f, 62f, subtitlePaint)

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
            c.drawText("Gerado por Finly em $dataHoje", 30f, 825f, labelPaint)
            c.drawText("Página $pNum", 525f, 825f, labelPaint)
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
            c.drawText("Item / Descrição", 40f, headerY, tableHeaderCellPaint)
            c.drawText("Categoria", 250f, headerY, tableHeaderCellPaint)
            c.drawText("Data", 370f, headerY, tableHeaderCellPaint)
            c.drawText("Valor", 555f, headerY, tableHeaderCellRightPaint)
        }

        drawHeaderAndFooter(canvas, pageNumber)

        // Card de Resumo no topo
        canvas.drawRoundRect(30f, 100f, 565f, 190f, 10f, 10f, cardBgPaint)
        canvas.drawRoundRect(30f, 100f, 565f, 190f, 10f, 10f, cardBorderPaint)

        val totalRendaVal = transacoes.filter { it.tipo == "RENDA" && it.categoria != "Poupança" && it.categoria != "Exterior" }.sumOf { it.valor }
        val despesas = transacoes.filter { it.tipo == "DESPESA" }
        val totalDespesasVal = despesas.sumOf { it.valor }
        val saldoVal = FinanceiroUtils.calcularSaldoPago(transacoes)

        val isRendaZero = abs(totalRendaVal) < 0.005
        val rendaText = FinanceiroUtils.formatarMoeda(totalRendaVal, isPositivo = if (isRendaZero) null else true)

        val isDespesaZero = abs(totalDespesasVal) < 0.005
        val despesaText = FinanceiroUtils.formatarMoeda(totalDespesasVal, isPositivo = if (isDespesaZero) null else false)

        val isSaldoZero = abs(saldoVal) < 0.005
        val saldoText = FinanceiroUtils.formatarMoeda(saldoVal, isPositivo = if (isSaldoZero) null else (saldoVal >= 0))

        val despesasPendentes = despesas.filter { !it.status }
        val totalDespesasPendentes = despesasPendentes.sumOf { it.valor }
        val statusPrevisao = if (saldoVal >= 0) "No Verde" else "No Vermelho"
        val detalhePrevisao = if (despesasPendentes.isNotEmpty()) {
            "${despesasPendentes.size} pendentes: " + FinanceiroUtils.formatarMoeda(totalDespesasPendentes)
        } else {
            "Contas em dia"
        }

        canvas.drawText("TOTAL RENDAS", 50f, 122f, labelPaint)
        canvas.drawText(rendaText, 50f, 142f, valueRendaPaint)

        canvas.drawText("TOTAL DESPESAS", 210f, 122f, labelPaint)
        canvas.drawText(despesaText, 210f, 142f, valueDespesaPaint)

        canvas.drawText("SALDO FINAL", 380f, 122f, labelPaint)
        val isSaldoNegativo = binding.tvSaldoFinal.currentTextColor == ContextCompat.getColor(this, R.color.colorNegative)
        val saldoPaint = if (isSaldoZero) labelPaint else if (isSaldoNegativo) valueDespesaPaint else valueRendaPaint
        canvas.drawText(saldoText, 380f, 142f, saldoPaint)

        canvas.drawLine(50f, 157f, 545f, 157f, linePaint)
        canvas.drawText("Previsão Fim de Mês: $statusPrevisao ($detalhePrevisao)", 50f, 175f, textPaint)

        currentY = 210f

        // 1. Resumo de Despesas por Categoria
        val gastosPorCategoria = despesas.groupBy { it.categoria }
            .mapValues { entry -> entry.value.sumOf { it.valor } }
            .toList()
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }

        if (gastosPorCategoria.isNotEmpty()) {
            canvas.drawText("RESUMO DE DESPESAS POR CATEGORIA", 30f, currentY, textBoldPaint)
            currentY += 12f

            canvas.drawRoundRect(30f, currentY, 565f, currentY + 22f, 4f, 4f, tableHeaderPaint)
            val miniHeaderY = currentY + 15f
            canvas.drawText("Categoria", 40f, miniHeaderY, tableHeaderCellPaint)
            canvas.drawText("Total Gasto", 320f, miniHeaderY, tableHeaderCellPaint)
            canvas.drawText("% do Total", 460f, miniHeaderY, tableHeaderCellPaint)
            currentY += 34f

            for ((cat, totalCat) in gastosPorCategoria) {
                checkPageBreak(20f)

                val catNome = if (cat.length > 28) cat.take(26) + ".." else cat
                val valorFormatado = String.format(Locale.getDefault(), "%.2f €", totalCat)
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

        canvas.drawText("RENDAS (ENTRADAS)", 30f, currentY, textBoldPaint)
        currentY += 12f
        drawTableHeader(canvas, currentY)
        currentY += 34f

        if (rendas.isEmpty()) {
            canvas.drawText("Nenhuma renda registada para este período.", 40f, currentY, labelPaint)
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
                val valorFormatado = String.format(Locale.getDefault(), "%.2f €", t.valor)

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

        canvas.drawText("DESPESAS (SAÍDAS)", 30f, currentY, textBoldPaint)
        currentY += 12f
        drawTableHeader(canvas, currentY)
        currentY += 34f

        if (despesasOrdenadas.isEmpty()) {
            canvas.drawText("Nenhuma despesa registada para este período.", 40f, currentY, labelPaint)
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
                val valorFormatado = String.format(Locale.getDefault(), "%.2f €", t.valor)

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
                showToast("Relatório salvo em Downloads!", isLong = true)
            } else {
                showToast("Erro ao guardar o relatório PDF.")
            }
        } catch (e: Exception) {
            showToast("Erro ao gerar PDF: ${e.message}")
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

    private fun exportarParaCSV() {
        val mes = binding.spinnerMes.selectedItem?.toString() ?: ""
        val ano = binding.spinnerAno.selectedItem?.toString() ?: ""
        val transacoes = transacoesAtuaisGrafico

        if (transacoes.isEmpty()) {
            showToast("Sem transações para exportar em $mes/$ano")
            return
        }

        val nomeFicheiro = "Relatorio_${mes}_$ano.csv"

        try {
            val sb = StringBuilder()
            // Adiciona BOM (Byte Order Mark) UTF-8 para garantir abertura correta no Excel sem problemas de acentuação
            sb.append("\uFEFF")
            // Cabeçalho CSV com separador; (padrão europeu/português para Excel)
            sb.append("Vencimento;Tipo;Descrição;Categoria;Valor (€);Estado\n")

            transacoes.sortedBy { extrairOrdemData(it.vencimento) }.forEach { t ->
                val itemEscaped = t.item.replace("\"", "\"\"")
                val catEscaped = t.categoria.replace("\"", "\"\"")
                val valorFmt = String.format(Locale.getDefault(), "%.2f", t.valor)
                val estado = if (t.status) "Pago" else "Pendente"

                sb.append("\"${t.vencimento}\";\"${t.tipo}\";\"$itemEscaped\";\"$catEscaped\";\"$valorFmt\";\"$estado\"\n")
            }

            val csvText = sb.toString()
            val bytes = csvText.toByteArray(Charsets.UTF_8)
            val ok = guardarFicheiroEmDownloads(nomeFicheiro, "text/csv", bytes)
            if (ok) {
                showToast("Ficheiro CSV salvo em Downloads!", isLong = true)
            } else {
                showToast("Erro ao guardar o ficheiro CSV.")
            }
        } catch (e: Exception) {
            showToast("Erro ao exportar CSV: ${e.message}")
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
        val mesAtual = binding.spinnerMes.selectedItem?.toString() ?: meses[0]
        val anoAtual = binding.spinnerAno.selectedItem as? Int ?: anos[0]
        carregarDados(mesAtual, anoAtual)
        atualizarDrawerHeader()
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
                } else if (email == "CONVIDADO") {
                    showToast(getString(R.string.toast_convidado_perfil))
                } else {
                    showToast("A carregar perfil... Tente novamente.")
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

    private fun configurarSpinners() {
        val adapterMes = HighlightSpinnerAdapter(this, meses) { binding.spinnerMes.selectedItemPosition }
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
                val mesSel = binding.spinnerMes.selectedItem.toString()
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
            // ‘BUG’ FIX: Poupança e Exterior não somam mais no card de Renda Geral
            val totalRenda = transacoes.asSequence()
                .filter { it.tipo == "RENDA" && it.categoria != "Poupança" && it.categoria != "Exterior" }
                .sumOf { it.valor }
                
            val totalDespesas = transacoes.asSequence().filter { it.tipo == "DESPESA" }.sumOf { it.valor }
            val totalPoupanca = transacoes.asSequence().filter { it.categoria == "Poupança" }.sumOf { it.valor }
            val totalExterior = transacoes.asSequence().filter { it.categoria == "Exterior" }.sumOf { it.valor }
            
            // Totais Pagos (Para o Saldo Final)
            val saldoPaga = FinanceiroUtils.calcularSaldoPago(transacoes)
            val percentagemGasta = FinanceiroUtils.calcularPercentagemGasta(totalRenda, totalDespesas)

            // Cálculos para Previsão de Fim de Mês
            val despesasPendentes = transacoes.filter { it.tipo == "DESPESA" && !it.status }
            val totalDespesasPendentes = despesasPendentes.sumOf { it.valor }
            val rendasPendentes = transacoes.filter { it.tipo == "RENDA" && !it.status }
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
            val despesasAnteriores = transacoesAnteriores.asSequence().filter { it.tipo == "DESPESA" }.sumOf { it.valor }

            withContext(Dispatchers.Main) {
                atualizarInterface(
                    renda = totalRenda,
                    despesa = totalDespesas,
                    poupanca = totalPoupanca,
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
                // A barra de meta continua baseada no que foi poupado (geralmente absoluto, mas mantendo lógica de categoria)
                atualizarBarraMeta(totalPoupanca)
            }
        }
    }

    private fun atualizarBarraMeta(valorPoupado: Double) {
        binding.tvMetaValor.text = String.format(Locale.getDefault(), "%.2f €", metaAtual)
        
        if (metaAtual > 0) {
            val progresso = ((valorPoupado / metaAtual) * 100).toInt().coerceAtMost(100)
            binding.pbMetaPoupanca.progress = progresso
            binding.tvMetaStatus.text = getString(R.string.poupado_label, valorPoupado, progresso)
            
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
        val despesas = transacoes.filter { it.tipo == "DESPESA" }
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
            top3.add(Pair("Outros", restoValor))
            top3
        } else {
            gastosPorCategoria
        }

        binding.btnVerMaisCategorias.visibility = if (gastosPorCategoria.size > 4) View.VISIBLE else View.GONE
        binding.btnVerMaisCategorias.text = if (isCategoriesExpanded) "Ver menos" else "Ver mais categorias"

        val totalGastos = gastosPorCategoria.sumOf { it.second.toDouble() }
        val entries = itensGraficoELegenda.map { PieEntry(it.second, it.first) }
        
        // Cores diversificadas para despesas (sem verde, reservado para rendas/poupança)
        val expenseColors = listOf(
            Color.parseColor("#7E57C2"), // Roxo / Violeta
            Color.parseColor("#00897B"), // Azul-petróleo (Teal)
            Color.parseColor("#FB8C00"), // Laranja
            Color.parseColor("#00ACC1"), // Ciano
            Color.parseColor("#3F51B5"), // Índigo
            Color.parseColor("#E91E63"), // Rosa
            Color.parseColor("#FF5722"), // Laranja escuro / Coral
            Color.parseColor("#2196F3")  // Azul
        )

        val dataSet = PieDataSet(entries, "").apply {
            colors = expenseColors
            setDrawValues(false)
            isHighlightEnabled = false // Remove o ponto/artefato preto de destaque ao tocar
            sliceSpace = 3f // Linha fina de separação (slice space) entre fatias
        }

        val totalStr = String.format(Locale.getDefault(), "%.2f €", totalGastos)
        val centerString = SpannableString("Total\n$totalStr")
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val mutedColor = if (isDark) Color.parseColor("#B0B0B0") else Color.parseColor("#666666")

        centerString.setSpan(ForegroundColorSpan(mutedColor), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        centerString.setSpan(RelativeSizeSpan(0.75f), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        centerString.setSpan(ForegroundColorSpan(textColor), 5, centerString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        centerString.setSpan(RelativeSizeSpan(1.15f), 5, centerString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        centerString.setSpan(StyleSpan(Typeface.BOLD), 5, centerString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

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
            adicionarItemCategoria(pair.first, pair.second.toDouble(), percent, color)
        }
    }


    private fun adicionarItemCategoria(nome: String, valor: Double, percent: Int, cor: Int) {
        val itemView = layoutInflater.inflate(R.layout.item_categoria_resumo, binding.llCategoryDetails, false)
        
        itemView.findViewById<View>(R.id.vColorIndicator).background.setTint(cor)
        itemView.findViewById<TextView>(R.id.tvCategoryName).text = nome
        itemView.findViewById<TextView>(R.id.tvCategoryValue).text = FinanceiroUtils.formatarMoeda(valor)
        itemView.findViewById<TextView>(R.id.tvCategoryPercent).text = String.format(Locale.getDefault(), "(%d%%)", percent)
        
        binding.llCategoryDetails.addView(itemView)
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
        val colorAlertaAviso = Color.parseColor("#FF9800")

        val isRendaZero = abs(renda) < 0.005
        binding.tvTotalRenda.text = FinanceiroUtils.formatarMoeda(renda, isPositivo = if (isRendaZero) null else true)
        binding.tvTotalRenda.setTextColor(if (isRendaZero) colorPadrao else colorPositivo)

        val isDespesaZero = abs(despesa) < 0.005
        binding.tvTotalDespesas.text = FinanceiroUtils.formatarMoeda(despesa, isPositivo = if (isDespesaZero) null else false)
        binding.tvTotalDespesas.setTextColor(if (isDespesaZero) colorPadrao else colorNegativo)

        binding.tvTotalPoupanca.text = FinanceiroUtils.formatarMoeda(poupanca)
        binding.tvTotalPoupanca.setTextColor(colorPadrao)

        val isSaldoZero = abs(saldo) < 0.005
        binding.tvSaldoFinal.text = FinanceiroUtils.formatarMoeda(saldo, isPositivo = if (isSaldoZero) null else (saldo >= 0))
        binding.tvSaldoFinal.setTextColor(if (isSaldoZero) colorPadrao else if (saldo < 0) colorNegativo else colorPositivo)

        binding.tvSpentValue.text = "Gasto total: " + FinanceiroUtils.formatarMoeda(despesa)
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
                    binding.tvTagComparativoMes.text = "↓ $diffAbs% vs. $abrevMes"
                    binding.tvTagComparativoMes.setTextColor(colorPositivo)
                    binding.tvTagComparativoMes.background = ContextCompat.getDrawable(this, R.drawable.badge_padrao_background)
                    binding.tvTagComparativoMes.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
                    binding.tvTagComparativoMes.visibility = View.VISIBLE
                }
                diff > 0.5 -> {
                    binding.tvTagComparativoMes.text = "↑ $diffAbs% vs. $abrevMes"
                    binding.tvTagComparativoMes.setTextColor(colorNegativo)
                    binding.tvTagComparativoMes.background = ContextCompat.getDrawable(this, R.drawable.badge_padrao_background)
                    binding.tvTagComparativoMes.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
                    binding.tvTagComparativoMes.visibility = View.VISIBLE
                }
                else -> {
                    binding.tvTagComparativoMes.text = "0% vs. $abrevMes"
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
        val valorDespesasPendentesFmt = FinanceiroUtils.formatarMoeda(totalDespesasPendentes)
        val isSaldoProjetadoZero = abs(saldoProjetado) < 0.005
        val saldoFmt = FinanceiroUtils.formatarMoeda(
            saldoProjetado,
            isPositivo = if (isSaldoProjetadoZero) null else (saldoProjetado >= 0)
        )

        if (saldoProjetado >= 0) {
            binding.tvTagPrevisao.text = "🟢 No Verde"
            binding.tvTagPrevisao.setTextColor(colorPositivo)
            binding.ivIconPrevisao.setColorFilter(colorPositivo)
            binding.tvPrevisaoDetalhes.text = if (numDespesasPendentes > 0) {
                "Saldo estimado: $saldoFmt ($numDespesasPendentes pendência(s): $valorDespesasPendentesFmt)"
            } else {
                "Saldo estimado: $saldoFmt (Contas em dia)"
            }
        } else {
            binding.tvTagPrevisao.text = "🔴 No Vermelho"
            binding.tvTagPrevisao.setTextColor(colorNegativo)
            binding.ivIconPrevisao.setColorFilter(colorNegativo)
            binding.tvPrevisaoDetalhes.text = if (numDespesasPendentes > 0) {
                "Saldo estimado: $saldoFmt ($numDespesasPendentes pendência(s): $valorDespesasPendentesFmt)"
            } else {
                "Saldo estimado: $saldoFmt"
            }
        }
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
