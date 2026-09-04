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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
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
import com.jesse.finly.utils.showToast
import com.google.android.material.tabs.TabLayout
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
    private var todasTransacoes = listOf<Transacao>()
    private var mesFiltro: String? = null
    private var anoFiltro: Int = 2026
    
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
        val cgAnos = dialogView.findViewById<ChipGroup>(R.id.cgAnos)
        val cgMeses = dialogView.findViewById<ChipGroup>(R.id.cgMeses)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelarPeriodo)
        val btnAplicar = dialogView.findViewById<Button>(R.id.btnAplicarPeriodo)

        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        dialog.setContentView(dialogView)

        var anoTemp = anoFiltro
        var mesTemp = mesFiltro ?: meses[0]

        // Obter anos dinâmicos com base nas transações + ano atual + próximo ano
        val anosDisponiveis = FinanceiroUtils.obterAnosDisponiveis(todasTransacoes)

        cgAnos.removeAllViews()
        anosDisponiveis.forEach { ano ->
            val chip = Chip(this).apply {
                text = ano.toString()
                isCheckable = true
                isChecked = (ano == anoTemp)
                setChipBackgroundColorResource(if (isChecked) R.color.colorPrimary else R.color.surfaceColor)
                setTextColor(ContextCompat.getColor(context, if (isChecked) R.color.white else R.color.textColorPrimary))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        anoTemp = ano
                        for (i in 0 until cgAnos.childCount) {
                            val child = cgAnos.getChildAt(i) as? Chip
                            val selected = child?.text.toString() == anoTemp.toString()
                            child?.setChipBackgroundColorResource(if (selected) R.color.colorPrimary else R.color.surfaceColor)
                            child?.setTextColor(ContextCompat.getColor(this@MainActivity, if (selected) R.color.white else R.color.textColorPrimary))
                        }
                    }
                }
            }
            cgAnos.addView(chip)
        }

        cgMeses.removeAllViews()
        meses.forEach { mes ->
            val chip = Chip(this).apply {
                text = mes
                isCheckable = true
                isChecked = mes.equals(mesTemp, ignoreCase = true)
                setChipBackgroundColorResource(if (isChecked) R.color.colorPrimary else R.color.surfaceColor)
                setTextColor(ContextCompat.getColor(context, if (isChecked) R.color.white else R.color.textColorPrimary))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        mesTemp = mes
                        for (i in 0 until cgMeses.childCount) {
                            val child = cgMeses.getChildAt(i) as? Chip
                            val selected = child?.text.toString().equals(mesTemp, ignoreCase = true)
                            child?.setChipBackgroundColorResource(if (selected) R.color.colorPrimary else R.color.surfaceColor)
                            child?.setTextColor(ContextCompat.getColor(this@MainActivity, if (selected) R.color.white else R.color.textColorPrimary))
                        }
                    }
                }
            }
            cgMeses.addView(chip)
        }

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
        binding.tvMainTitle.text = String.format(Locale.getDefault(), "%s %d", mesFiltro?.uppercase(Locale.getDefault()), anoFiltro)
    }

    private fun carregarLista() {
        val currentEmail = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@MainActivity)

            recurrenceMutex.withLock {
                mesFiltro?.let { processarRecorrencia(db, currentEmail, it, anoFiltro) }
            }

            val todasDoDono = db.utilizadorDao().obterTransacoesPorDono(currentEmail)

            val transacoesFiltradas = if (mesFiltro != null) {
                todasDoDono.filter { (it.mes == mesFiltro) && (it.ano == anoFiltro) }
            } else {
                todasDoDono
            }

            withContext(Dispatchers.Main) {
                todasTransacoes = transacoesFiltradas
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

        if (adapter == null) {
            adapter = TransacaoAdapter(
                lista,
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
            adapter?.updateData(lista)
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

        val despesaTotal = despesasPagas + despesasAPagar
        val saldoDisponivel = rendaTotal - despesasPagas

        val colorPositivo = ContextCompat.getColor(this, R.color.colorPositive)
        val colorNegativo = ContextCompat.getColor(this, R.color.colorNegative)
        val colorPadrao = ContextCompat.getColor(this, R.color.textColorSecondary)

        val progressoPercent = if (despesaTotal > 0) ((despesasPagas / despesaTotal) * 100).toInt().coerceIn(0, 100) else 0
        binding.pbProgressoPagamento.progress = progressoPercent

        when (posicaoTab) {
            1 -> {
                // Aba Despesas
                val corDespesa = if (abs(despesaTotal) < 0.005) colorPadrao else colorNegativo
                setSaldoColorido("Total Despesas: ", despesaTotal, corDespesa)
                binding.tvDetalheSaldo.text = String.format(
                    Locale.getDefault(),
                    "Pagas: %.2f €  |  A Pagar: %.2f €  (%d%% pagas)",
                    despesasPagas, despesasAPagar, progressoPercent
                )
                binding.tvDetalheSaldo.visibility = View.VISIBLE
            }
            2 -> {
                // Aba Rendas
                val corRenda = if (abs(rendaTotal) < 0.005) colorPadrao else colorPositivo
                setSaldoColorido("Total Rendas: ", rendaTotal, corRenda)
                binding.tvDetalheSaldo.visibility = View.GONE
            }
            else -> {
                // Aba Todas: Saldo Disponível (ou Défice / A Descoberto se negativo)
                when {
                    abs(saldoDisponivel) < 0.005 -> setSaldoColorido("Saldo Disponível: ", 0.0, colorPadrao)
                    saldoDisponivel < 0 -> setSaldoColorido("Défice / A Descoberto: -", abs(saldoDisponivel), colorNegativo)
                    else -> setSaldoColorido("Saldo Disponível: ", saldoDisponivel, colorPositivo)
                }

                binding.tvDetalheSaldo.text = String.format(
                    Locale.getDefault(),
                    "Inicial: %.2f €  |  Pago: %.2f € (%d%%)  |  A Pagar: %.2f €",
                    rendaTotal, despesasPagas, progressoPercent, despesasAPagar
                )
                binding.tvDetalheSaldo.visibility = View.VISIBLE
            }
        }
    }

    private fun setSaldoColorido(prefixo: String, valor: Double, corValor: Int) {
        // Garante que o prefixo termine com ": " e remove o sinal de menos do valor
        val prefixoFormatado = if (prefixo.endsWith(": ")) prefixo else prefixo.trimEnd().removeSuffix(":") + ": "
        val valorAbsoluto = kotlin.math.abs(valor)
        
        val valorTexto = String.format(Locale.getDefault(), "%.2f €", valorAbsoluto)
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
                showToast("Conta marcada como paga!")
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
}
