package com.jesse.finly

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import java.util.Locale

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

        // Obter o mês e ano filtrado vindos do Resumo
        val cal = java.util.Calendar.getInstance()
        mesFiltro = intent.getStringExtra("MES_SELECIONADO") ?: meses[cal[java.util.Calendar.MONTH]]
        anoFiltro = intent.getIntExtra("ANO_SELECIONADO", cal[java.util.Calendar.YEAR])
        atualizarTituloMes()

        binding.btnMesAnterior.setOnClickListener { navegarMes(-1) }
        binding.btnMesProximo.setOnClickListener { navegarMes(1) }

        binding.fabAddTransacao.setOnClickListener {
            val intent = Intent(this, RegistoActivity::class.java)
            intent.putExtra("MES_ATUAL", mesFiltro)
            intent.putExtra("ANO_ATUAL", anoFiltro)
            startActivity(intent)
        }

        binding.tvMainTitle.setOnClickListener {
            finish()
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
                
                // 1. Obter IDs da nuvem para comparação
                val idsNuvem = listaNuvem.map { it.id }.toSet()
                
                // 2. Apagar localmente o que não existe mais na nuvem (Sync de Deletados)
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

        // Sincronizar com o ecrã de resumo para quando voltar
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putString("ULTIMO_MES_SELECIONADO", mesFiltro)
            putInt("ULTIMO_ANO_SELECIONADO", anoFiltro)
            apply()
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

        val filtrada = FinanceiroUtils.filtrarTransacoes(todasTransacoes, tipoFiltro, query)
            .sortedBy { transacao ->
                transacao.vencimento.split("/").firstOrNull()?.toIntOrNull() ?: 0
            }

        atualizarRecycler(filtrada)
        atualizarSaldoVisual(posicao)
    }

    private fun atualizarRecycler(lista: List<Transacao>) {
        // Toggle Empty State
        if (lista.isEmpty()) {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvTransacoes.visibility = View.GONE
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvTransacoes.visibility = View.VISIBLE
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
                },
                onToggleStatus = { 
                    toggleStatusTransacao(it)
                }
            )
            binding.rvTransacoes.adapter = adapter
        } else {
            adapter?.updateData(lista)
        }
    }

    private fun atualizarSaldoVisual(posicaoTab: Int) {
        // Cálculo para o Saldo Final (Apenas Pagos)
        val saldoFinalPaga = FinanceiroUtils.calcularSaldoPago(todasTransacoes)

        // Cálculo para Totais Absolutos (Tudo o que foi lançado)
        // BUG FIX: Poupanca e Exterior nao somam mais no total de Renda da Home
        val rendaTotal = todasTransacoes.asSequence()
            .filter { it.tipo == "RENDA" && it.categoria != "Poupança" && it.categoria != "Exterior" }
            .sumOf { it.valor }
        val despesaTotal = todasTransacoes.asSequence().filter { it.tipo == "DESPESA" }.sumOf { it.valor }

        val colorPositivo = ContextCompat.getColor(this, R.color.colorPositive)
        val colorNegativo = ContextCompat.getColor(this, R.color.colorNegative)

        when (posicaoTab) {
            1 -> {
                // Aba Despesas: Mostra Total de Despesas (Tudo)
                setSaldoColorido(getString(R.string.total_despesas), despesaTotal, colorNegativo)
                binding.tvDetalheSaldo.visibility = View.GONE
            }
            2 -> {
                // Aba Rendas: Mostra Total de Rendas (Tudo)
                setSaldoColorido(getString(R.string.total_rendas), rendaTotal, colorPositivo)
                binding.tvDetalheSaldo.visibility = View.GONE
            }
            else -> {
                // Aba Todas: Saldo Final (Pagos) | Detalhe (Tudo)
                val corSaldo = if (saldoFinalPaga >= 0) colorPositivo else colorNegativo
                setSaldoColorido(getString(R.string.saldo_final_label), saldoFinalPaga, corSaldo)
                
                binding.tvDetalheSaldo.text = String.format(Locale.getDefault(), "Rendas: %.2f € | Despesas: %.2f €", rendaTotal, despesaTotal)
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
                
                // 2. Sincronizar com a Nuvem imediatamente
                FirebaseManager.salvarTransacaoNoFirestore(transacaoComId)
            }
        }
    }
}
