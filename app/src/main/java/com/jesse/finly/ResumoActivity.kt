package com.jesse.finly

import android.content.ContentValues
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
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
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.ActivityResumoBinding
import com.jesse.finly.models.MetaPoupanca
import com.jesse.finly.models.Transacao
import com.jesse.finly.utils.FinanceiroUtils
import com.jesse.finly.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class ResumoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResumoBinding
    private val meses = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    private val anos = (2020..(Calendar.getInstance()[Calendar.YEAR] + 10)).toList().toTypedArray()
    
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
            startActivity(intent)
        }

        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        binding.btnExportarPDF.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportarParaPDF()
            } else {
                showToast("Exportação PDF requer Android 10+")
            }
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
        
        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
            val dao = db.utilizadorDao()
            
            // Tentar encontrar meta existente para atualizar ou criar uma.
            val metaExistente = dao.obterMetaPorMes(email, mesSel, anoSel)
            val metaObj = metaExistente?.copy(valor = valor)
                ?: MetaPoupanca(mes = mesSel, ano = anoSel, valor = valor, donoEmail = email)
            
            dao.salvarMeta(metaObj)
            
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

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. sincronizar PERFIL (TEMA E NOTIFICAÇÕES Apenas)
            val perfilNuvem = FirebaseManager.obterPerfilDoFirestore(emailClean)
            if (perfilNuvem != null) {
                val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
                val perfilLocal = db.utilizadorDao().buscarPorEmail(emailClean)
                
                db.utilizadorDao().atualizarUtilizador(perfilNuvem.copy(id = perfilLocal?.id ?: perfilNuvem.id))
                
                withContext(Dispatchers.Main) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                        putBoolean("DARK_MODE", perfilNuvem.darkMode)
                        putBoolean("NOTIFICATIONS", perfilNuvem.notifications)
                    }
                    AppCompatDelegate.setDefaultNightMode(
                        if (perfilNuvem.darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
                    )
                    atualizarDrawerHeader()
                }
            }

            // 2. sincronizar TRANSAÇÕES
            val transacoesNuvem = FirebaseManager.descarregarTransacoesDoFirestore(email)
            if (transacoesNuvem.isNotEmpty()) {
                val db = MinhaBaseDados.getDatabase(this@ResumoActivity)
                val dao = db.utilizadorDao()
                
                transacoesNuvem.forEach { trans ->
                    // Inserir ou atualizar localmente
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        exportarParaPDF()
                    } else {
                        showToast("Exportação PDF requer Android 10+")
                    }
                }
                R.id.nav_sair -> {
                    mostrarDialogSair()
                }
            }
            binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }

        // 2. Carregar dados do cabeçalho
        atualizarDrawerHeader()
    }

    private fun obterCategoriasCustomizadas(): MutableList<String> {
        val prefs = getSharedPreferences("PreferenciasDaMinhaApp", MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet()) ?: emptySet()
        return customSet.filter { it.isNotBlank() }.toMutableList()
    }

    private fun salvarNovaCategoria(nome: String) {
        val catClean = nome.trim()
        if (catClean.isEmpty()) return

        val prefs = getSharedPreferences("PreferenciasDaMinhaApp", MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet())?.toMutableSet() ?: mutableSetOf()
        customSet.add(catClean)
        prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", customSet) }

        showToast(getString(R.string.toast_categoria_adicionada, catClean))
        val mesSel = binding.spinnerMes.selectedItem?.toString() ?: "Janeiro"
        val anoSel = binding.spinnerAno.selectedItem?.toString()?.toIntOrNull() ?: 2026
        carregarDados(mesSel, anoSel)
    }

    private fun removerCategoriaCustomizada(categoria: String) {
        val prefs = getSharedPreferences("PreferenciasDaMinhaApp", MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet())?.toMutableSet() ?: mutableSetOf()
        customSet.remove(categoria)
        prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", customSet) }

        showToast(getString(R.string.toast_categoria_removida, categoria))
        val mesSel = binding.spinnerMes.selectedItem?.toString() ?: "Janeiro"
        val anoSel = binding.spinnerAno.selectedItem?.toString()?.toIntOrNull() ?: 2026
        carregarDados(mesSel, anoSel)
    }

    private fun mostrarBottomSheetGerirCategorias() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val bsView = layoutInflater.inflate(R.layout.bottom_sheet_gerir_categorias, binding.drawerLayout, false)
        bottomSheetDialog.setContentView(bsView)
        bottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(
            Color.TRANSPARENT)

        val etNova = bsView.findViewById<TextInputEditText>(R.id.etNovaCategoriaBS)
        val btnAdd = bsView.findViewById<MaterialButton>(R.id.btnAdicionarCategoriaBS)
        val btnFechar = bsView.findViewById<MaterialButton>(R.id.btnFecharBS)
        val containerCustom = bsView.findViewById<LinearLayout>(R.id.containerCategoriasCustom)
        val tvSemCategorias = bsView.findViewById<TextView>(R.id.tvSemCategoriasCustom)

        fun atualizarListaCustom() {
            containerCustom.removeAllViews()
            val customList = obterCategoriasCustomizadas()

            if (customList.isEmpty()) {
                tvSemCategorias.visibility = View.VISIBLE
            } else {
                tvSemCategorias.visibility = View.GONE
                for (cat in customList) {
                    val itemView = layoutInflater.inflate(R.layout.item_categoria_custom, containerCustom, false)
                    val tvNome = itemView.findViewById<TextView>(R.id.tvNomeCategoriaCustom)
                    val btnRemover = itemView.findViewById<View>(R.id.btnRemoverCategoriaCustom)

                    tvNome.text = cat
                    btnRemover.setOnClickListener {
                        removerCategoriaCustomizada(cat)
                        atualizarListaCustom()
                    }
                    containerCustom.addView(itemView)
                }
            }
        }

        atualizarListaCustom()

        btnAdd.setOnClickListener {
            val novaCat = etNova.text.toString().trim()
            if (novaCat.isNotEmpty()) {
                salvarNovaCategoria(novaCat)
                etNova.setText("")
                atualizarListaCustom()
            } else {
                showToast("Digite o nome da categoria")
            }
        }

        btnFechar.setOnClickListener {
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
        val headerPaint = Paint().apply { color = "#0F3D3A".toColorInt() }
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
            textSize = 13f
            isFakeBoldText = true
        }
        val valueDespesaPaint = Paint().apply {
            color = "#C62828".toColorInt()
            textSize = 13f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = "#333333".toColorInt()
            textSize = 10f
        }
        val textBoldPaint = Paint().apply {
            color = "#0F3D3A".toColorInt()
            textSize = 11f
            isFakeBoldText = true
        }
        val tableHeaderPaint = Paint().apply { color = "#0F3D3A".toColorInt() }
        val tableHeaderCellPaint = Paint().apply {
            color = Color.WHITE
            textSize = 10f
            isFakeBoldText = true
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
            // Header Bar
            c.drawRect(0f, 0f, pageWidth.toFloat(), 85f, headerPaint)
            c.drawText("Finly", 30f, 40f, titlePaint)
            c.drawText("Relatório Financeiro Mensal • $mes / $ano", 30f, 62f, subtitlePaint)

            // Logo vetorial (SVG) desenhado diretamente no Canvas do PDF (Qualidade 100% Cristalina)
            val logoVector = ContextCompat.getDrawable(this, R.drawable.ic_logo_full_transp)
                ?: ContextCompat.getDrawable(this, R.drawable.ic_logo_full)
            logoVector?.let {
                it.setBounds(490, 10, 565, 75)
                it.draw(c)
            }




            // Footer
            c.drawLine(30f, 810f, 565f, 810f, linePaint)
            val dataHoje = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date())
            c.drawText("Gerado por Finly em $dataHoje", 30f, 825f, labelPaint)
            c.drawText("Página $pNum", 525f, 825f, labelPaint)
        }

        // Desenhar Header & Footer da primeira página
        drawHeaderAndFooter(canvas, pageNumber)

        // Card de Resumo no topo
        canvas.drawRoundRect(30f, 100f, 565f, 190f, 10f, 10f, cardBgPaint)
        canvas.drawRoundRect(30f, 100f, 565f, 190f, 10f, 10f, cardBorderPaint)

        val rendaText = binding.tvTotalRenda.text.toString()
        val despesaText = binding.tvTotalDespesas.text.toString()
        val poupancaText = binding.tvTotalPoupanca.text.toString()
        val saldoText = binding.tvSaldoFinal.text.toString()

        // Coluna 1: Rendas
        canvas.drawText("TOTAL RENDAS", 50f, 122f, labelPaint)
        canvas.drawText(rendaText, 50f, 142f, valueRendaPaint)

        // Coluna 2: Despesas
        canvas.drawText("TOTAL DESPESAS", 210f, 122f, labelPaint)
        canvas.drawText(despesaText, 210f, 142f, valueDespesaPaint)

        // Coluna 3: Saldo Final
        canvas.drawText("SALDO FINAL", 380f, 122f, labelPaint)
        val isSaldoNegativo = binding.tvSaldoFinal.currentTextColor == ContextCompat.getColor(this, R.color.colorNegative)
        val saldoPaint = if (isSaldoNegativo) valueDespesaPaint else valueRendaPaint
        canvas.drawText(saldoText, 380f, 142f, saldoPaint)

        // Linha secundária do Card: Poupança & Meta
        canvas.drawLine(50f, 157f, 545f, 157f, linePaint)
        val metaFormatada = String.format(Locale.getDefault(), "%.2f €", metaAtual)
        canvas.drawText("Poupança Real: $poupancaText   |   Meta do Mês: $metaFormatada", 50f, 175f, textPaint)

        // Tabela de Transações
        canvas.drawText("EXTRATO DETALHADO DO MÊS", 30f, 215f, textBoldPaint)

        fun drawTableHeader(c: Canvas, y: Float) {
            c.drawRoundRect(30f, y, 565f, y + 22f, 4f, 4f, tableHeaderPaint)
            val headerY = y + 15f
            c.drawText("Item / Descrição", 40f, headerY, tableHeaderCellPaint)
            c.drawText("Categoria", 240f, headerY, tableHeaderCellPaint)
            c.drawText("Data", 360f, headerY, tableHeaderCellPaint)
            c.drawText("Tipo", 440f, headerY, tableHeaderCellPaint)
            c.drawText("Valor", 515f, headerY, tableHeaderCellPaint)
        }

        var currentY = 230f
        drawTableHeader(canvas, currentY)
        currentY += 38f

        val paintRendaRight = Paint(valueRendaPaint).apply {
            textSize = 10f
            textAlign = Paint.Align.RIGHT
        }
        val paintDespesaRight = Paint(valueDespesaPaint).apply {
            textSize = 10f
            textAlign = Paint.Align.RIGHT
        }

        val rowHeight = 22f

        if (transacoes.isEmpty()) {
            canvas.drawText("Nenhuma transação registada para este período.", 40f, currentY, labelPaint)
        } else {
            for (t in transacoes) {
                // Checar estouro de página
                if (currentY > 780f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas

                    drawHeaderAndFooter(canvas, pageNumber)
                    currentY = 110f
                    drawTableHeader(canvas, currentY)
                    currentY += 38f
                }

                // Descrição
                val itemNome = if (t.item.length > 25) t.item.take(23) + ".." else t.item
                canvas.drawText(itemNome, 40f, currentY, textPaint)

                // Categoria
                val catNome = if (t.categoria.length > 18) t.categoria.take(16) + ".." else t.categoria
                canvas.drawText(catNome, 240f, currentY, labelPaint)

                // Data
                canvas.drawText(t.vencimento, 360f, currentY, labelPaint)

                // Tipo
                canvas.drawText(t.tipo, 440f, currentY, labelPaint)

                // Valor
                val valorFormatado = String.format(Locale.getDefault(), "%.2f €", t.valor)
                val valPaint = if (t.tipo == "RENDA") paintRendaRight else paintDespesaRight
                canvas.drawText(valorFormatado, 555f, currentY, valPaint)

                // Linha divisória
                canvas.drawLine(30f, currentY + 6f, 565f, currentY + 6f, linePaint)
                currentY += rowHeight
            }
        }

        pdfDocument.finishPage(page)

        val nomeFicheiro = "Relatorio_${mes}_$ano.pdf"
        try {
            val resolver = contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFicheiro)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            }

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                showToast("Relatório salvo em Downloads!", isLong = true)
            } else {
                showToast("Erro ao criar ficheiro PDF")
            }
        } catch (e: Exception) {
            showToast("Erro ao gerar PDF: ${e.message}")
        } finally {
            pdfDocument.close()
        }
    }

    override fun onResume() {

        super.onResume()
        ativarSincronizacaoTempoReal()
        prosseguirComCarregamento()
        atualizarDrawerHeader()
    }

    override fun onPause() {
        super.onPause()
        FirebaseManager.pararMonitoramento()
    }

    private fun ativarSincronizacaoTempoReal() {
        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("EMAIL", "") ?: ""
        if (email.isEmpty() || email == "CONVIDADO") return

        FirebaseManager.monitorarTransacoes(email) { listaNuvem ->
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

    private fun configurarSpinners() {
        val adapterMes = ArrayAdapter(this, android.R.layout.simple_spinner_item, meses)
        adapterMes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMes.adapter = adapterMes

        val adapterAno = ArrayAdapter(this, android.R.layout.simple_spinner_item, anos)
        adapterAno.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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

            withContext(Dispatchers.Main) {
                atualizarInterface(totalRenda, totalDespesas, totalPoupanca, totalExterior, saldoPaga, percentagemGasta)
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
            .sortedByDescending { it.second }

        if (gastosPorCategoria.isEmpty()) {
            binding.pieChart.visibility = View.GONE
            binding.llCategoryDetails.visibility = View.GONE
            binding.btnVerMaisCategorias.visibility = View.GONE
            return
        }
        binding.pieChart.visibility = View.VISIBLE
        binding.llCategoryDetails.visibility = View.VISIBLE

        // Lógica de expansão/colapso
        val itensParaMostrar = if (isCategoriesExpanded || gastosPorCategoria.size <= 4) {
            gastosPorCategoria
        } else {
            gastosPorCategoria.take(4)
        }

        binding.btnVerMaisCategorias.visibility = if (gastosPorCategoria.size > 4) View.VISIBLE else View.GONE
        binding.btnVerMaisCategorias.text = if (isCategoriesExpanded) "Ver menos" else "Ver mais categorias"

        val totalGastos = gastosPorCategoria.sumOf { it.second.toDouble() }
        val entries = gastosPorCategoria.map { PieEntry(it.second, it.first) }
        
        // Cores diversificadas
        val colors = mutableListOf<Int>()
        for (c in ColorTemplate.MATERIAL_COLORS) colors.add(c)
        for (c in ColorTemplate.VORDIPLOM_COLORS) colors.add(c)

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            setDrawValues(false)
        }

        val pieData = PieData(dataSet)
        binding.pieChart.apply {
            data = pieData
            description.isEnabled = false
            centerText = "Gastos"
            setCenterTextSize(16f)
            
            val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            val textColor = if (isDark) Color.WHITE else Color.BLACK
            
            setCenterTextColor(textColor)
            legend.isEnabled = false
            
            setHoleColor(0) 
            transparentCircleRadius = 52f
            holeRadius = 48f
            
            setDrawEntryLabels(false) 
            setExtraOffsets(5f, 5f, 5f, 5f)
            
            // Só anima na primeira vez ou quando mudar de mês
            if (binding.pieChart.data == null) animateY(1000)
            invalidate()
        }

        // PREENCHER A Lista DE LEGENDA DETALHADA ABAIXO DO GRÁFICO
        binding.llCategoryDetails.removeAllViews()
        itensParaMostrar.forEach { pair ->
            // Encontrar a cor original correta baseada no index do gráfico completo
            val corOriginalIndex = gastosPorCategoria.indexOf(pair)
            val color = colors[corOriginalIndex % colors.size]
            
            val percent = if (totalGastos > 0) (pair.second / totalGastos * 100).toInt() else 0
            adicionarItemCategoria(pair.first, pair.second.toDouble(), percent, color)
        }
    }


    private fun adicionarItemCategoria(nome: String, valor: Double, percent: Int, cor: Int) {
        val itemView = layoutInflater.inflate(R.layout.item_categoria_resumo, binding.llCategoryDetails, false)
        
        itemView.findViewById<View>(R.id.vColorIndicator).background.setTint(cor)
        itemView.findViewById<TextView>(R.id.tvCategoryName).text = nome
        itemView.findViewById<TextView>(R.id.tvCategoryValue).text = String.format(Locale.getDefault(), "%.2f €", valor)
        itemView.findViewById<TextView>(R.id.tvCategoryPercent).text = String.format(Locale.getDefault(), "(%d%%)", percent)
        
        binding.llCategoryDetails.addView(itemView)
    }




    private fun atualizarInterface(renda: Double, despesa: Double, poupanca: Double, exterior: Double, saldo: Double, percent: Int) {
        val colorPositivo = ContextCompat.getColor(this, R.color.colorPositive)
        val colorNegativo = ContextCompat.getColor(this, R.color.colorNegative)
        val colorPadrao = ContextCompat.getColor(this, R.color.textColorPrimary)

        // Usar valor absoluto para remover sinal de menos
        binding.tvTotalRenda.text = String.format(Locale.getDefault(), "%.2f €", kotlin.math.abs(renda))
        binding.tvTotalRenda.setTextColor(colorPositivo)

        binding.tvTotalDespesas.text = String.format(Locale.getDefault(), "%.2f €", kotlin.math.abs(despesa))
        binding.tvTotalDespesas.setTextColor(colorNegativo)

        binding.tvTotalPoupanca.text = String.format(Locale.getDefault(), "%.2f €", kotlin.math.abs(poupanca))
        binding.tvTotalPoupanca.setTextColor(colorPadrao)
        
        binding.tvTotalBrasil.text = String.format(Locale.getDefault(), "%.2f €", kotlin.math.abs(exterior))
        binding.tvTotalBrasil.setTextColor(colorPadrao)
        
        binding.tvSaldoFinal.text = String.format(Locale.getDefault(), "%.2f €", kotlin.math.abs(saldo))
        binding.tvPercentValue.text = String.format(Locale.getDefault(), "%d%%", percent)
        binding.tvSpentValue.text = String.format(Locale.getDefault(), "%.2f €", kotlin.math.abs(despesa))
        
        binding.tvSaldoFinal.setTextColor(if (saldo < 0) colorNegativo else colorPositivo)
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
