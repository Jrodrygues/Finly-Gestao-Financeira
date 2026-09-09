package com.jesse.finly

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.jesse.finly.adapters.TransacaoAdapter
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.HomeBinding
import com.jesse.finly.models.Transacao
import com.jesse.finly.utils.FinanceiroUtils
import com.google.android.material.tabs.TabLayout
import com.jesse.finly.utils.UserPreferencesManager
import com.jesse.finly.utils.CurrencyFormatter
import com.jesse.finly.utils.IdiomaUtils
import com.jesse.finly.utils.Moeda
import com.jesse.finly.utils.ToastHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var binding: HomeBinding
    private lateinit var prefsManager: UserPreferencesManager
    private var moedaAtual: Moeda = Moeda.EUR
    private var todasTransacoes = listOf<Transacao>()
    private var mesFiltro: String? = null
    private var anoFiltro: Int = 2026
    private var flowJob: Job? = null
    
    private var adapter: TransacaoAdapter? = null
    
    private val meses = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    private val recurrenceMutex = Mutex()

    companion object {
        private const val PREFS_NAME = "FinlyAppPrefs"
        private const val KEY_EMAIL = "EMAIL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = HomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = UserPreferencesManager(this)
        moedaAtual = prefsManager.obterMoedaAtual()


        // Configurar LayoutManager no onCreate apenas uma vez
        binding.rvTransacoes.layoutManager = LinearLayoutManager(this)

        // Obter o mês e ano filtrado vindos do Resumo ou das preferências compartilhadas
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val cal = Calendar.getInstance()
        mesFiltro = intent.getStringExtra("MES_SELECIONADO")
            ?: sharedPref.getString("ULTIMO_MES_SELECIONADO", null)
            ?: meses[cal[Calendar.MONTH]]
        anoFiltro = if (intent.hasExtra("ANO_SELECIONADO")) {
            intent.getIntExtra("ANO_SELECIONADO", cal[Calendar.YEAR])
        } else {
            val anoSalvo = sharedPref.getInt("ULTIMO_ANO_SELECIONADO", 0)
            if (anoSalvo != 0) anoSalvo else cal[Calendar.YEAR]
        }
        atualizarTituloMes()

        val isDetalhesMode = intent.getBooleanExtra("IS_DETALHES_MODE", false) || intent.hasExtra("MES_SELECIONADO")
        if (isDetalhesMode) {
            binding.btnBackMain.visibility = View.VISIBLE
            binding.tvScreenTitle.visibility = View.VISIBLE
            binding.btnBackMain.setOnClickListener { finish() }
            binding.tvScreenTitle.setOnClickListener { finish() }
        } else {
            binding.btnBackMain.visibility = View.GONE
            binding.tvScreenTitle.visibility = View.GONE
        }

        binding.btnMesAnterior.setOnClickListener { navegarMes(-1) }
        binding.btnMesProximo.setOnClickListener { navegarMes(1) }
        binding.cardMonthPill.setOnClickListener { mostrarDialogoSelecaoPeriodo() }
        binding.tvMainTitle.setOnClickListener { mostrarDialogoSelecaoPeriodo() }

        binding.fabAddTransacao.setOnClickListener {
            val intent = Intent(this, RegistoActivity::class.java)
            intent.putExtra("MES_ATUAL", mesFiltro)
            intent.putExtra("ANO_ATUAL", anoFiltro)
            startActivity(intent)
        }

        binding.btnEmptyAdd.setOnClickListener {
            val intent = Intent(this, RegistoActivity::class.java)
            intent.putExtra("MES_ATUAL", mesFiltro)
            intent.putExtra("ANO_ATUAL", anoFiltro)
            startActivity(intent)
        }

        binding.etSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filtrarLista(binding.tabFilter.selectedTabPosition)
                }

                override fun afterTextChanged(s: Editable?) {}
            },
        )

        binding.tabFilter.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    filtrarLista(tab?.position ?: 0)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            },
        )

        // Ajustar Insets para o cabeçalho e rodapé não ficarem tapados pelas barras do sistema
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Subir o rodapé (Saldo) para ficar acima dos botões nativos
            val bottomPadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
            binding.llSaldoFooter.updatePadding(bottom = systemBars.bottom + bottomPadding)
            
            // Empurrar o cabeçalho para baixo da barra de status (horas, bateria)
            binding.clHeader.updatePadding(top = systemBars.top)
            
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        val novaMoeda = prefsManager.obterMoedaAtual()
        if (novaMoeda != moedaAtual) {
            moedaAtual = novaMoeda
            adapter?.atualizarMoeda(moedaAtual)
        }
        val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val ultimoMes = sharedPref.getString("ULTIMO_MES_SELECIONADO", null)
        val ultimoAno = sharedPref.getInt("ULTIMO_ANO_SELECIONADO", 0)
        if (ultimoMes != null && (ultimoMes != mesFiltro || (ultimoAno != 0 && ultimoAno != anoFiltro))) {
            mesFiltro = ultimoMes
            if (ultimoAno != 0) anoFiltro = ultimoAno
            atualizarTituloMes()
        }
        ativarSincronizacaoTempoReal()
        carregarLista()
    }

    override fun onPause() {
        super.onPause()
        FirebaseManager.pararMonitoramento()
    }


    private fun ativarSincronizacaoTempoReal() {
        val currentEmail = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""
        if (currentEmail.isEmpty() || currentEmail == "CONVIDADO") return

        FirebaseManager.monitorarPerfil(currentEmail) { perfilNuvem ->
            lifecycleScope.launch(Dispatchers.Main) {
                val novaMoeda = Moeda.porCodigo(perfilNuvem.moeda)
                val moedaMudou = (novaMoeda != moedaAtual)
                moedaAtual = novaMoeda
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                    putString("MOEDA", novaMoeda.codigo)
                }
                adapter?.atualizarMoeda(moedaAtual)
                if (moedaMudou) {
                    carregarLista()
                }
            }
        }

        FirebaseManager.monitorarTransacoes(currentEmail) { listaNuvem ->
            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(this@MainActivity)
                val dao = db.utilizadorDao()
                
                // 1. Obter ‘IDs’ da nuvem para comparação
                val idsNuvem = listaNuvem.map { it.id }.toSet()
                
                // 2. Apagar localmente o que não existe mais na nuvem (Sync de Deleitados)
                val locais = dao.obterTransacoesPorDono(currentEmail)
                locais.forEach { transLocal ->
                    if (!idsNuvem.contains(transLocal.id)) {
                        dao.apagarTransacao(transLocal)
                    }
                }
                
                // 3. Inserir ou Atualizar o que veio da nuvem
                listaNuvem.forEach { trans ->
                    val existeLocal = dao.obterTransacaoPorId(trans.id)
                    if (existeLocal == null) {
                        dao.inserirTransacao(trans)
                    } else if (existeLocal != trans) {
                        dao.atualizarTransacao(trans)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    carregarLista()
                }
            }
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

        var anoTemp = anoFiltro
        var mesTemp = mesFiltro ?: meses[0]

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

        val anosDisponiveis = FinanceiroUtils.obterAnosDisponiveis(todasTransacoes)
        llAnosContainer.removeAllViews()

        anosDisponiveis.forEach { ano ->
            val btnAno = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
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
            mesFiltro = mesTemp
            anoFiltro = anoTemp
            atualizarTituloMes()
            carregarLista()

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                putString("ULTIMO_MES_SELECIONADO", mesFiltro)
                putInt("ULTIMO_ANO_SELECIONADO", anoFiltro)
            }
        }

        dialog.show()
    }

    private fun navegarMes(direcao: Int) {
        val indexAtual = meses.indexOf(mesFiltro)
        var novoIndex = indexAtual + direcao

        if (novoIndex < 0) {
            novoIndex = 11
            anoFiltro--
        } else if (novoIndex > 11) {
            novoIndex = 0
            anoFiltro++
        }

        mesFiltro = meses[novoIndex]
        atualizarTituloMes()
        carregarLista()

        // sincronizar com o ecrã de resumo para quando voltar
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putString("ULTIMO_MES_SELECIONADO", mesFiltro)
            putInt("ULTIMO_ANO_SELECIONADO", anoFiltro)
        }
    }

    private fun atualizarTituloMes() {
        val mesTraduzido = IdiomaUtils.formatarNomeMes(this, mesFiltro)
        binding.tvMainTitle.text = "$mesTraduzido $anoFiltro".uppercase(Locale.getDefault())
    }

    private fun carregarLista() {
        iniciarObservacaoFlow()
    }

    private fun iniciarObservacaoFlow() {
        val currentEmail = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""
        if (currentEmail.isEmpty() || currentEmail == "CONVIDADO" || mesFiltro == null) return

        flowJob?.cancel()
        flowJob = lifecycleScope.launch {
            val db = MinhaBaseDados.getDatabase(this@MainActivity)

            withContext(Dispatchers.IO) {
                recurrenceMutex.withLock {
                    mesFiltro?.let { processarRecorrencia(db, currentEmail, it, anoFiltro) }
                }
            }

            db.utilizadorDao().obterTransacoesPorMesFlow(currentEmail, mesFiltro!!, anoFiltro)
                .collect { transacoes ->
                    todasTransacoes = transacoes
                    filtrarLista(binding.tabFilter.selectedTabPosition)
                }
        }
    }

    private fun filtrarLista(posicao: Int) {
        val query = binding.etSearch.text.toString().trim().lowercase()

        val tipoFiltro = when (posicao) {
            1 -> "DESPESA"
            2 -> "RENDA"
            else -> null
        }

        val filtrada = FinanceiroUtils.filtrarTransacoes(todasTransacoes, query, tipoFiltro)
            .sortedBy { transacao ->
                transacao.vencimento.split("/").firstOrNull()?.toIntOrNull() ?: 0
            }

        atualizarRecycler(filtrada)
        atualizarSaldoVisual(posicao)
    }

    private fun atualizarRecycler(lista: List<Transacao>) {
        // Toggle Empty State & Column Headers
        if (lista.isEmpty()) {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvTransacoes.visibility = View.GONE
            binding.llColumnHeaders.visibility = View.GONE
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvTransacoes.visibility = View.VISIBLE
            binding.llColumnHeaders.visibility = View.VISIBLE
        }

        val codigoMoeda = UserPreferencesManager(this).obterMoedaAtual().codigo

        if (adapter == null) {
            adapter = TransacaoAdapter(
                lista,
                codigoMoeda,
                onItemClick = { transacao ->
                    val intent = Intent(this@MainActivity, DetalhesPageActivity::class.java)
                    intent.putExtra("isTransactionDetail", true)
                    intent.putExtra("id", transacao.id)
                    intent.putExtra("item", transacao.item)
                    intent.putExtra("valor", transacao.valor)
                    intent.putExtra("vencimento", transacao.vencimento)
                    intent.putExtra("tipo", transacao.tipo)
                    intent.putExtra("status", transacao.status)
                    intent.putExtra("categoria", transacao.categoria)
                    intent.putExtra("mes", transacao.mes)
                    intent.putExtra("ano", transacao.ano)
                    intent.putExtra("isRecorrente", transacao.recorrente)
                    intent.putExtra("parcelasRestantes", transacao.parcelasRestantes)
                    intent.putExtra("parcelasTotais", transacao.parcelasTotais)
                    startActivity(intent)
                }
            ) { 
                toggleStatusTransacao(it)
            }
            binding.rvTransacoes.adapter = adapter
        } else {
            adapter?.updateData(lista, codigoMoeda)
        }
    }

    private fun atualizarAparenciaCabecalho() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val headerBg = if (isDark) ContextCompat.getColor(this, R.color.surfaceColor) else ContextCompat.getColor(this, R.color.headerColor)
        binding.clHeader.setBackgroundColor(headerBg)

        val textColor = ContextCompat.getColor(this, R.color.textColorPrimary)
        binding.tvMainTitle.setTextColor(textColor)
        binding.btnMesAnterior.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))
        binding.btnMesProximo.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    private fun atualizarSaldoVisual(posicaoTab: Int) {
        atualizarAparenciaCabecalho()

        val rendaTotal = todasTransacoes.asSequence()
            .filter { it.tipo == "RENDA" && it.categoria != "Poupança" && it.categoria != "Exterior" }
            .sumOf { it.valor }

        val despesasPagas = todasTransacoes.asSequence()
            .filter { it.tipo == "DESPESA" && it.status }
            .sumOf { it.valor }

        val despesasAPagar = todasTransacoes.asSequence()
            .filter { it.tipo == "DESPESA" && !it.status }
            .sumOf { it.valor }

        val saldoDisponivel = rendaTotal - despesasPagas

        atualizarRodape(
            saldoDisponivel = saldoDisponivel,
            saldoInicial = rendaTotal,
            totalPago = despesasPagas,
            totalAPagar = despesasAPagar,
            posicaoTab = posicaoTab
        )
    }

    private fun atualizarRodape(
        saldoDisponivel: Double,
        saldoInicial: Double,
        totalPago: Double,
        totalAPagar: Double,
        posicaoTab: Int
    ) {
        val totalGeralDespesas = totalPago + totalAPagar
        val percentagemPaga = if (totalGeralDespesas > 0.001) {
            ((totalPago / totalGeralDespesas) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        binding.pbProgressoPagamento.progress = percentagemPaga

        val colorPositivo = ContextCompat.getColor(this, R.color.colorPositive)
        val colorNegativo = ContextCompat.getColor(this, R.color.colorNegative)
        val colorPadrao = ContextCompat.getColor(this, R.color.textColorSecondary)

        when (posicaoTab) {
            1 -> {
                // Aba Despesas
                val corDespesa = if (abs(totalGeralDespesas) < 0.001) colorPadrao else colorNegativo
                val valorDespesaStr = CurrencyFormatter.formatarComSinal(-totalGeralDespesas, moedaAtual)
                setSaldoColorido(getString(R.string.main_saldo_total_despesas_prefix), valorDespesaStr, corDespesa)

                val pagasStr = CurrencyFormatter.formatar(totalPago, moedaAtual)
                val aPagarStr = CurrencyFormatter.formatar(totalAPagar, moedaAtual)
                binding.tvDetalheSaldo.text = getString(R.string.main_saldo_detalhe_despesas, pagasStr, aPagarStr, percentagemPaga)
                binding.tvDetalheSaldo.visibility = View.VISIBLE
            }
            2 -> {
                // Aba Rendas
                val corRenda = if (abs(saldoInicial) < 0.001) colorPadrao else colorPositivo
                val valorRendaStr = CurrencyFormatter.formatarComSinal(saldoInicial, moedaAtual, forcarSinalPositivo = true)
                setSaldoColorido(getString(R.string.main_saldo_total_rendas_prefix), valorRendaStr, corRenda)
                binding.tvDetalheSaldo.visibility = View.GONE
            }
            else -> {
                // Aba Todas
                val (prefixo, corSaldo, valorSaldoStr) = when {
                    abs(saldoDisponivel) < 0.001 -> Triple(
                        getString(R.string.main_saldo_disponivel_prefix),
                        colorPadrao,
                        CurrencyFormatter.formatar(0.0, moedaAtual)
                    )
                    saldoDisponivel < -0.001 -> Triple(
                        getString(R.string.main_saldo_defice_prefix),
                        colorNegativo,
                        CurrencyFormatter.formatarComSinal(saldoDisponivel, moedaAtual)
                    )
                    else -> Triple(
                        getString(R.string.main_saldo_disponivel_prefix),
                        colorPositivo,
                        CurrencyFormatter.formatarComSinal(saldoDisponivel, moedaAtual, forcarSinalPositivo = true)
                    )
                }

                setSaldoColorido(prefixo, valorSaldoStr, corSaldo)

                val inicialStr = CurrencyFormatter.formatar(saldoInicial, moedaAtual)
                val pagoStr = CurrencyFormatter.formatar(totalPago, moedaAtual)
                val aPagarStr = CurrencyFormatter.formatar(totalAPagar, moedaAtual)

                binding.tvDetalheSaldo.text = getString(R.string.main_saldo_detalhe_todas, inicialStr, pagoStr, percentagemPaga, aPagarStr)
                binding.tvDetalheSaldo.visibility = View.VISIBLE
            }
        }
    }

    private fun setSaldoColorido(prefixo: String, valorTexto: String, corValor: Int) {
        val prefixoFormatado = if (prefixo.endsWith(": ")) prefixo else prefixo.trimEnd().removeSuffix(":") + ": "
        val fullText = prefixoFormatado + valorTexto
        val spannable = SpannableString(fullText)

        val start = prefixoFormatado.length
        val end = fullText.length
        spannable.setSpan(ForegroundColorSpan(corValor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.tvSaldoTotal.text = spannable
    }

    private fun toggleStatusTransacao(transacao: Transacao) {
        val index = todasTransacoes.indexOfFirst { it.id == transacao.id }
        if (index != -1) {
            val novaTransacao = transacao.copy(status = !transacao.status)
            val novaLista = todasTransacoes.toMutableList()
            novaLista[index] = novaTransacao
            todasTransacoes = novaLista
            filtrarLista(binding.tabFilter.selectedTabPosition)

            if (novaTransacao.status) {
                showToast(getString(R.string.toast_conta_marcada_paga))
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(this@MainActivity)
                db.utilizadorDao().atualizarTransacao(novaTransacao)
                FirebaseManager.salvarTransacaoNoFirestore(novaTransacao)
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
                // 1. Inserir localmente e obter o novo ID
                val novoId = db.utilizadorDao().inserirTransacao(it)
                val transacaoComId = it.copy(id = novoId.toInt())
                
                // 2. sincronizar com a Nuvem imediatamente
                FirebaseManager.salvarTransacaoNoFirestore(transacaoComId)
            }
        }
    }

    private fun showToast(msg: String, isLong: Boolean = false) {
        ToastHelper.showCustomToast(this, msg, isLong)
    }
}
