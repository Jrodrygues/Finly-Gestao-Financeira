package com.jesse.finly

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.jesse.finly.PaywallActivity
import com.jesse.finly.utils.FinanceiroUtils
import com.jesse.finly.utils.ToastHelper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.MPPointF
import androidx.core.content.edit
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.ActivityEvolucaoAnualBinding
import com.jesse.finly.models.Transacao
import com.jesse.finly.utils.CurrencyFormatter
import com.jesse.finly.utils.IdiomaUtils
import com.jesse.finly.utils.Moeda
import com.jesse.finly.utils.UserPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import kotlin.math.abs

class EvolucaoAnualActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEvolucaoAnualBinding
    private lateinit var prefsManager: UserPreferencesManager
    private var moedaAtual: Moeda = Moeda.EUR
    private val mesesNomes = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

    private val listaAnos by lazy {
        val anoAtual = java.time.Year.now().value
        val anoInicio = anoAtual - 1 // Histórico recente
        val anoFim = anoAtual + 4    // Projeções futuras
        (anoInicio..anoFim).toList().toTypedArray()
    }

    private var rendaPorMes = FloatArray(12)
    private var despesaPorMes = FloatArray(12)
    private var anoAtualSelecionado = 2026

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEvolucaoAnualBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = UserPreferencesManager(this)
        if (!prefsManager.isPremium()) {
            ToastHelper.showCustomToast(this, getString(R.string.toast_evolucao_premium))
            PaywallActivity.abrir(this)
            finish()
            return
        }

        moedaAtual = prefsManager.obterMoedaAtual()

        atualizarAparenciaCabecalho()

        binding.btnBackEvolucao.setOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.viewHeaderEvolucao.updatePadding(top = systemBars.top)
            binding.clMainEvolucao.updatePadding(bottom = systemBars.bottom)
            insets
        }

        configurarSeletorAno()
        configurarGraficoVazio()

        val anoInicial = Calendar.getInstance()[Calendar.YEAR]
        carregarDadosAnuais(anoInicial)
    }

    override fun onResume() {
        super.onResume()
        ativarSincronizacaoTempoReal()
        val novaMoeda = prefsManager.obterMoedaAtual()
        if (novaMoeda != moedaAtual) {
            moedaAtual = novaMoeda
            atualizarGraficoFormatters()
        } else {
            carregarDadosAnuais(anoAtualSelecionado)
        }
    }

    private fun ativarSincronizacaoTempoReal() {
        val email = getSharedPreferences("FinlyAppPrefs", MODE_PRIVATE).getString("EMAIL", "") ?: ""
        if (email.isEmpty() || FirebaseManager.isGuestEmail(email)) return

        FirebaseManager.monitorarPerfil(email) { perfilNuvem ->
            lifecycleScope.launch(Dispatchers.Main) {
                val novaMoeda = Moeda.porCodigo(perfilNuvem.moeda)
                if (novaMoeda != moedaAtual) {
                    moedaAtual = novaMoeda
                    getSharedPreferences("FinlyAppPrefs", MODE_PRIVATE).edit {
                        putString("MOEDA", novaMoeda.codigo)
                    }
                    atualizarGraficoFormatters()
                }
            }
        }
    }

    private fun atualizarGraficoFormatters() {
        configurarGraficoVazio()
        carregarDadosAnuais(anoAtualSelecionado)
    }

    private fun atualizarAparenciaCabecalho() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val headerBg = if (isDark) ContextCompat.getColor(this, R.color.surfaceColor) else ContextCompat.getColor(this, R.color.headerColor)
        binding.viewHeaderEvolucao.setBackgroundColor(headerBg)

        val textColor = ContextCompat.getColor(this, R.color.textColorPrimary)
        binding.tvTitleEvolucao.setTextColor(textColor)
        binding.btnBackEvolucao.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    private fun configurarSeletorAno() {
        val adapterAno = ArrayAdapter(this, R.layout.dropdown_item, listaAnos)
        binding.autoCompleteAno.setAdapter(adapterAno)

        val anoAtual = Calendar.getInstance()[Calendar.YEAR].toString()
        binding.autoCompleteAno.setText(anoAtual, false)

        binding.autoCompleteAno.setOnItemClickListener { _, _, position, _ ->
            val anoSelecionado = listaAnos[position]
            carregarDadosAnuais(anoSelecionado)
        }
    }

    private fun configurarGraficoVazio() {
        binding.barChartAnual.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(false)
            setPinchZoom(false)
            isScaleXEnabled = false
            isScaleYEnabled = false
            isDoubleTapToZoomEnabled = false
            setExtraOffsets(5f, 5f, 5f, 8f)

            val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            val textColor = if (isDark) Color.WHITE else Color.BLACK

            val mesesAbreviados = arrayOf(
                getString(R.string.mes_abrev_jan), getString(R.string.mes_abrev_fev),
                getString(R.string.mes_abrev_mar), getString(R.string.mes_abrev_abr),
                getString(R.string.mes_abrev_mai), getString(R.string.mes_abrev_jun),
                getString(R.string.mes_abrev_jul), getString(R.string.mes_abrev_ago),
                getString(R.string.mes_abrev_set), getString(R.string.mes_abrev_out),
                getString(R.string.mes_abrev_nov), getString(R.string.mes_abrev_dez)
            )

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(mesesAbreviados)
                this.textColor = textColor
                textSize = 10f
                axisMinimum = -0.5f
                axisMaximum = 11.5f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = if (isDark) "#33FFFFFF".toColorInt() else "#33000000".toColorInt()
                this.textColor = textColor
                spaceTop = 15f
                spaceBottom = 15f

                // Destaque da Linha de Base Zero
                setDrawZeroLine(true)
                zeroLineColor = if (isDark) "#80FFFFFF".toColorInt() else "#80000000".toColorInt()
                zeroLineWidth = 1.5f

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val formatadorInteiro = NumberFormat.getCurrencyInstance(moedaAtual.locale).apply {
                            maximumFractionDigits = 0
                        }
                        return formatadorInteiro.format(value.toDouble())
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e != null) {
                        val index = e.x.toInt()
                        if (index in 0..11) {
                            val userPrefs = UserPreferencesManager(this@EvolucaoAnualActivity)
                            val mesAtualIndex = Calendar.getInstance()[Calendar.MONTH]
                            val indexMinimoGratis = (mesAtualIndex - 2).coerceAtLeast(0)

                            if (!userPrefs.isPremium() && index < indexMinimoGratis) {
                                ToastHelper.showCustomToast(this@EvolucaoAnualActivity, getString(R.string.toast_historico_12_meses_premium))
                                PaywallActivity.abrir(this@EvolucaoAnualActivity)
                            } else {
                                atualizarCardDetalheMes(index)
                            }
                        }
                    }
                }

                override fun onNothingSelected() {}
            })

            animateY(1000)

            val marker = ChartMarkerView(this@EvolucaoAnualActivity)
            marker.chartView = this
            this.marker = marker
        }
    }

    inner class ChartMarkerView(context: Context) :
        MarkerView(context, R.layout.layout_chart_marker) {

        private val cardContainer: MaterialCardView? = findViewById(R.id.cardMarkerContainer)
        private val tvMarkerText: TextView? = findViewById(R.id.tvMarkerText)
        private val ivArrow: ImageView? = findViewById(R.id.ivMarkerArrow)

        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            if (e != null) {
                val index = e.x.toInt()
                if (index in 0..11) {
                    val mesNome = mesesNomes[index]
                    val renda = rendaPorMes[index].toDouble()
                    val despesa = despesaPorMes[index].toDouble()
                    val balanco = renda - despesa

                    val corPositivo = ContextCompat.getColor(context, R.color.colorPositive)
                    val corNegativo = ContextCompat.getColor(context, R.color.colorNegative)

                    val balancoStr = CurrencyFormatter.formatarComSinal(balanco, moedaAtual, forcarSinalPositivo = (balanco > 0))
                    val mesTraduzido = IdiomaUtils.formatarNomeMes(context, mesNome)
                    tvMarkerText?.text = context.getString(R.string.chart_marker_mes_balanco, mesTraduzido, balancoStr)

                    if (balanco < 0) {
                        tvMarkerText?.setTextColor(corNegativo)
                        cardContainer?.strokeColor = corNegativo
                        ivArrow?.setColorFilter(corNegativo)
                    } else {
                        tvMarkerText?.setTextColor(corPositivo)
                        cardContainer?.strokeColor = corPositivo
                        ivArrow?.setColorFilter(corPositivo)
                    }
                }
            }
            super.refreshContent(e, highlight)
        }

        override fun getOffset(): MPPointF {
            val entryY = chartView?.highlighted?.firstOrNull()?.y ?: 0f
            val yOffset = if (entryY < 0f) {
                -height.toFloat() - 35f
            } else {
                -height.toFloat() - 25f
            }
            return MPPointF(-(width / 2f), yOffset)
        }
    }

    private var flowJob: Job? = null

    private fun carregarDadosAnuais(ano: Int) {
        anoAtualSelecionado = ano
        val email = getSharedPreferences("FinlyAppPrefs", MODE_PRIVATE).getString("EMAIL", "") ?: ""
        if (email.isEmpty()) return

        flowJob?.cancel()
        flowJob = lifecycleScope.launch {
            val db = MinhaBaseDados.getDatabase(this@EvolucaoAnualActivity)
            db.utilizadorDao().obterTransacoesPorDonoFlow(email)
                .collect { todasDoDono ->
                    processarEExibirDados(todasDoDono.filter { it.ano == ano })
                }
        }
    }

    private suspend fun processarEExibirDados(transacoes: List<Transacao>) {
        var somaRendaAnual = 0.0
        var somaDespesaAnual = 0.0

        val tempRenda = FloatArray(12)
        val tempDespesa = FloatArray(12)

        val entriesSaldo = mutableListOf<BarEntry>()
        val coresSaldo = mutableListOf<Int>()

        val corPositivo = ContextCompat.getColor(this@EvolucaoAnualActivity, R.color.colorPositive)
        val corNegativo = ContextCompat.getColor(this@EvolucaoAnualActivity, R.color.colorNegative)

        for (i in 0..11) {
            val mesNome = mesesNomes[i]
            val transMes = transacoes.filter { it.mes == mesNome }

            val totalRenda = transMes.asSequence()
                .filter { it.tipo == "RENDA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) && it.categoria != "Exterior" }
                .sumOf { it.valor }
                .toFloat()

            val totalDespesa = transMes.asSequence()
                .filter { it.tipo == "DESPESA" && !FinanceiroUtils.isCategoriaPoupanca(it.categoria) }
                .sumOf { it.valor }
                .toFloat()

            somaRendaAnual += totalRenda.toDouble()
            somaDespesaAnual += totalDespesa.toDouble()

            tempRenda[i] = totalRenda
            tempDespesa[i] = totalDespesa

            val saldoMes = totalRenda - totalDespesa
            entriesSaldo.add(BarEntry(i.toFloat(), saldoMes))
            coresSaldo.add(if (saldoMes >= 0) corPositivo else corNegativo)
        }

        val saldoAnual = somaRendaAnual - somaDespesaAnual

        withContext(Dispatchers.Main) {
            rendaPorMes = tempRenda
            despesaPorMes = tempDespesa

            atualizarCardsResumoAnual(somaRendaAnual, somaDespesaAnual, saldoAnual)

            // 1. PRIMEIRO atualizar o gráfico para que barChartAnual.data esteja preenchido
            atualizarGrafico(entriesSaldo, coresSaldo)

            // 2. DEPOIS atualizar o Card de Detalhe do mês atual
            val mesAtualIndex = Calendar.getInstance()[Calendar.MONTH]
            atualizarCardDetalheMes(mesAtualIndex)
        }
    }

    private fun atualizarCardsResumoAnual(
        totalRendasAno: Double,
        totalDespesasAno: Double,
        saldoAcumuladoAno: Double
    ) {
        val corPositivo = ContextCompat.getColor(this, R.color.colorPositive)
        val corNegativo = ContextCompat.getColor(this, R.color.colorNegative)
        val corPadrao = ContextCompat.getColor(this, R.color.textColorSecondary)

        binding.tvTotalRendaAnual.text = CurrencyFormatter.formatarComSinal(totalRendasAno, moedaAtual, forcarSinalPositivo = true)
        binding.tvTotalDespesaAnual.text = CurrencyFormatter.formatarComSinal(-totalDespesasAno, moedaAtual)
        binding.tvSaldoAnual.text = CurrencyFormatter.formatarComSinal(saldoAcumuladoAno, moedaAtual, forcarSinalPositivo = true)

        val corSaldo = when {
            saldoAcumuladoAno > 0.001 -> corPositivo
            saldoAcumuladoAno < -0.001 -> corNegativo
            else -> corPadrao
        }
        binding.tvSaldoAnual.setTextColor(corSaldo)
    }

    private fun atualizarCardDetalheMes(index: Int) {
        if (index !in 0..11) return
        val mesNome = mesesNomes[index]
        val renda = rendaPorMes[index].toDouble()
        val despesa = despesaPorMes[index].toDouble()
        val balanco = renda - despesa

        val corPositivo = ContextCompat.getColor(this, R.color.colorPositive)
        val corNegativo = ContextCompat.getColor(this, R.color.colorNegative)

        val mesTraduzido = IdiomaUtils.formatarNomeMes(this, mesNome)
        binding.tvTituloDetalheMes.text = getString(R.string.detalhes_mes_ano_format, mesTraduzido, anoAtualSelecionado)
        binding.tvRendaMesDetalhe.text = CurrencyFormatter.formatarComSinal(renda, moedaAtual, forcarSinalPositivo = true)
        binding.tvDespesaMesDetalhe.text = CurrencyFormatter.formatarComSinal(-despesa, moedaAtual)

        val isBalancoZero = abs(balanco) < 0.005
        binding.tvSaldoMesDetalhe.text = CurrencyFormatter.formatarComSinal(balanco, moedaAtual, forcarSinalPositivo = true)
        binding.tvSaldoMesDetalhe.setTextColor(if (isBalancoZero) ContextCompat.getColor(this, R.color.textColorSecondary) else if (balanco < 0) corNegativo else corPositivo)

        if (binding.barChartAnual.data != null) {
            try {
                binding.barChartAnual.highlightValue(Highlight(index.toFloat(), 0f, 0), false)
            } catch (_: Exception) {
                // Prevenir exceção do gráfico
            }
        }
    }

    private fun atualizarGrafico(saldos: List<BarEntry>, cores: List<Int>) {
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        val setSaldo = BarDataSet(saldos, getString(R.string.chart_label_saldo_mensal)).apply {
            this.colors = cores
            setDrawValues(true)
            valueTextColor = textColor
            valueTextSize = 9f
            highLightColor = Color.WHITE
            highLightAlpha = 80
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    if (value == 0f) return ""
                    return CurrencyFormatter.formatarComSinal(value.toDouble(), moedaAtual)
                }
            }
        }

        val data = BarData(setSaldo)
        data.barWidth = 0.6f

        binding.barChartAnual.apply {
            this.data = data
            notifyDataSetChanged()

            setVisibleXRangeMaximum(6f)
            isDragEnabled = true

            val mesAtualIndex = Calendar.getInstance()[Calendar.MONTH]
            val movePos = (mesAtualIndex - 2).coerceIn(0, 6).toFloat()
            moveViewToX(movePos)
            invalidate()
        }
    }
}
