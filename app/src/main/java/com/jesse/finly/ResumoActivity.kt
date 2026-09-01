package com.jesse.finly

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
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
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportarParaPDF() {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint()

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val logoBitmap = vectorToBitmap(R.drawable.ic_logo_full)
        logoBitmap?.let {
            canvas.drawBitmap(it, 450f, 20f, null)
        }

        titlePaint.textSize = 24f
        titlePaint.isFakeBoldText = true
        titlePaint.color = ContextCompat.getColor(this, R.color.colorAccent)
        canvas.drawText(getString(R.string.pdf_titulo), 40f, 60f, titlePaint)

        paint.textSize = 14f
        val mes = binding.spinnerMes.selectedItem.toString()
        val ano = binding.spinnerAno.selectedItem.toString()
        
        paint.isFakeBoldText = true
        canvas.drawText(getString(R.string.pdf_periodo, mes, ano), 40f, 100f, paint)
        
        paint.isFakeBoldText = false
        val startY = 140f
        val lineSpacing = 30f
        
        canvas.drawText(getString(R.string.pdf_renda_total, binding.tvTotalRenda.text), 40f, startY, paint)
        canvas.drawText(getString(R.string.pdf_despesa_total, binding.tvTotalDespesas.text), 40f, startY + lineSpacing, paint)
        canvas.drawText(getString(R.string.pdf_poupanca_total, binding.tvTotalPoupanca.text), 40f, startY + (lineSpacing * 2), paint)
        canvas.drawText(getString(R.string.pdf_meta_definida, metaAtual), 40f, startY + (lineSpacing * 3), paint)

        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText(getString(R.string.pdf_saldo_final, binding.tvSaldoFinal.text), 40f, startY + (lineSpacing * 5), paint)

        pdfDocument.finishPage(page)

        val nomeFicheiro = "Relatorio_${mes}_$ano.pdf"
        try {
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFicheiro)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                showToast("Relatório salvo em Downloads!", isLong = true)
            }
        } catch (_: Exception) {
            showToast("Erro ao gerar PDF")
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

    private fun vectorToBitmap(drawableId: Int): Bitmap? {
        return ContextCompat.getDrawable(this, drawableId)?.toBitmap(100, 100)
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
