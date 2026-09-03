package com.jesse.finly

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.TextView
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
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.ActivityEvolucaoAnualBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

class EvolucaoAnualActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEvolucaoAnualBinding
    private val mesesArray = arrayOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
    private val mesesNomes = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    private val listaAnos = arrayOf("2024", "2025", "2026", "2027", "2028", "2029", "2030")

    private var rendaPorMes = FloatArray(12)
    private var despesaPorMes = FloatArray(12)
    private var anoAtualSelecionado = 2026

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEvolucaoAnualBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        val anoInicial = Calendar.getInstance().get(Calendar.YEAR)
        carregarDadosAnuais(anoInicial)
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

        val anoAtual = Calendar.getInstance().get(Calendar.YEAR).toString()
        binding.autoCompleteAno.setText(anoAtual, false)

        binding.autoCompleteAno.setOnItemClickListener { _, _, position, _ ->
            val anoSelecionado = listaAnos[position].toInt()
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

            val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            val textColor = if (isDark) Color.WHITE else Color.BLACK

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(mesesArray)
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
                zeroLineColor = if (isDark) Color.parseColor("#80FFFFFF") else Color.parseColor("#80000000")
                zeroLineWidth = 1.5f

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        if (value == 0f) return "0 €"
                        val prefix = if (value < 0) "-" else ""
                        val absVal = abs(value)
                        return if (absVal >= 1000)
                            String.format(Locale.getDefault(), "%s%.1fk €", prefix, absVal / 1000)
                        else String.format(Locale.getDefault(), "%s%.0f €", prefix, absVal)
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
                            atualizarCardDetalheMes(index)
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

        private val tvMes: TextView = findViewById(R.id.tvMarkerMes)
        private val tvRenda: TextView = findViewById(R.id.tvMarkerRenda)
        private val tvDespesa: TextView = findViewById(R.id.tvMarkerDespesa)

        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            if (e != null) {
                val index = e.x.toInt()
                if (index in 0..11) {
                    tvMes.text = mesesNomes[index]
                    val renda = rendaPorMes[index]
                    val despesa = despesaPorMes[index]
                    tvRenda.text = String.format(Locale.getDefault(), "%.0f€", renda)
                    tvDespesa.text = String.format(Locale.getDefault(), "%.0f€", despesa)
                }
            }
            super.refreshContent(e, highlight)
        }

        override fun getOffset(): MPPointF {
            return MPPointF(-(width / 2f), -height.toFloat() - 15f)
        }
    }

    private fun carregarDadosAnuais(ano: Int) {
        anoAtualSelecionado = ano
        val email = getSharedPreferences("FinlyAppPrefs", MODE_PRIVATE).getString("EMAIL", "") ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@EvolucaoAnualActivity)
            val transacoes = db.utilizadorDao().obterTransacoesPorDono(email)
                .filter { it.ano == ano }

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
                    .filter { it.tipo == "RENDA" && it.categoria != "Poupança" && it.categoria != "Exterior" }
                    .sumOf { it.valor }
                    .toFloat()

                val totalDespesa = transMes.asSequence()
                    .filter { it.tipo == "DESPESA" }
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

                binding.tvTotalRendaAnual.text = String.format(Locale.getDefault(), "%.2f €", somaRendaAnual)
                binding.tvTotalDespesaAnual.text = String.format(Locale.getDefault(), "%.2f €", somaDespesaAnual)

                // Saldo Anual com sinal negativo em vermelho se < 0
                if (saldoAnual < 0) {
                    binding.tvSaldoAnual.text = String.format(Locale.getDefault(), "-%.2f €",
                        abs(saldoAnual)
                    )
                    binding.tvSaldoAnual.setTextColor(corNegativo)
                } else {
                    binding.tvSaldoAnual.text = String.format(Locale.getDefault(), "%.2f €", saldoAnual)
                    binding.tvSaldoAnual.setTextColor(corPositivo)
                }

                // 1. PRIMEIRO atualizar o gráfico para que barChartAnual.data esteja preenchido
                atualizarGrafico(entriesSaldo, coresSaldo)

                // 2. DEPOIS atualizar o Card de Detalhe do mês atual
                val mesAtualIndex = Calendar.getInstance().get(Calendar.MONTH)
                atualizarCardDetalheMes(mesAtualIndex)
            }
        }
    }

    private fun atualizarCardDetalheMes(index: Int) {
        if (index !in 0..11) return
        val mesNome = mesesNomes[index]
        val renda = rendaPorMes[index].toDouble()
        val despesa = despesaPorMes[index].toDouble()
        val balanco = renda - despesa

        val corPositivo = ContextCompat.getColor(this, R.color.colorPositive)
        val corNegativo = ContextCompat.getColor(this, R.color.colorNegative)

        binding.tvTituloDetalheMes.text = "Detalhes: $mesNome de $anoAtualSelecionado"
        binding.tvRendaMesDetalhe.text = String.format(Locale.getDefault(), "%.2f €", renda)
        binding.tvDespesaMesDetalhe.text = String.format(Locale.getDefault(), "%.2f €", despesa)

        if (balanco < 0) {
            binding.tvSaldoMesDetalhe.text = String.format(Locale.getDefault(), "-%.2f €", abs(balanco))
            binding.tvSaldoMesDetalhe.setTextColor(corNegativo)
        } else {
            binding.tvSaldoMesDetalhe.text = String.format(Locale.getDefault(), "%.2f €", balanco)
            binding.tvSaldoMesDetalhe.setTextColor(corPositivo)
        }

        if (binding.barChartAnual.data != null) {
            try {
                binding.barChartAnual.highlightValue(Highlight(index.toFloat(), 0f, 0), false)
            } catch (e: Exception) {
                // Prevenir exceção do gráfico
            }
        }
    }

    private fun atualizarGrafico(saldos: List<BarEntry>, cores: List<Int>) {
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        val setSaldo = BarDataSet(saldos, "Saldo mensal").apply {
            this.colors = cores
            setDrawValues(true)
            valueTextColor = textColor
            valueTextSize = 10f
            highLightColor = ContextCompat.getColor(this@EvolucaoAnualActivity, R.color.colorPrimary)
            highLightAlpha = 150
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    if (value == 0f) return ""
                    val rounded = round(value).toInt()
                    val prefix = if (rounded < 0) "-" else ""
                    return String.format(Locale.getDefault(), "%s%d€", prefix, abs(rounded))
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

            val mesAtualIndex = Calendar.getInstance().get(Calendar.MONTH)
            val movePos = (mesAtualIndex - 2).coerceIn(0, 6).toFloat()
            moveViewToX(movePos)
            invalidate()
        }
    }
}
