package com.jesse.finly

import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.databinding.ActivityEvolucaoAnualBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class EvolucaoAnualActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEvolucaoAnualBinding
    private val mesesArray = arrayOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
    private val mesesNomes = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    private val listaAnos = arrayOf("2024", "2025", "2026", "2027", "2028", "2029", "2030")

    // NOVO: guarda renda/despesa de cada mês para usar no clique da barra
    private var rendaPorMes = FloatArray(12)
    private var despesaPorMes = FloatArray(12)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEvolucaoAnualBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                // Sem axisMinimum fixo em 0 — precisa aceitar valores negativos (meses no vermelho)
                this.textColor = textColor
                spaceTop = 15f
                spaceBottom = 15f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val absVal = kotlin.math.abs(value)
                        return if (absVal >= 1000)
                            String.format(Locale.getDefault(), "%.1fk €", absVal / 1000)
                        else "${absVal.toInt()} €"
                    }
                }

            }
            axisRight.isEnabled = false

            // Uma barra só por mês (saldo) não precisa de legenda Rendas/Despesas
            legend.isEnabled = false

            animateY(1000)

            // NOVO: Balão flutuante (MarkerView) ao tocar nas barras
            val marker = ChartMarkerView(this@EvolucaoAnualActivity)
            marker.chartView = this
            this.marker = marker
        }
    }

    inner class ChartMarkerView(context: android.content.Context) :
        com.github.mikephil.charting.components.MarkerView(context, R.layout.layout_chart_marker) {

        private val tvMes: android.widget.TextView = findViewById(R.id.tvMarkerMes)
        private val tvRenda: android.widget.TextView = findViewById(R.id.tvMarkerRenda)
        private val tvDespesa: android.widget.TextView = findViewById(R.id.tvMarkerDespesa)

        override fun refreshContent(e: com.github.mikephil.charting.data.Entry?, highlight: com.github.mikephil.charting.highlight.Highlight?) {
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

        override fun getOffset(): com.github.mikephil.charting.utils.MPPointF {
            return com.github.mikephil.charting.utils.MPPointF(-(width / 2f), -height.toFloat() - 15f)
        }
    }


    private fun carregarDadosAnuais(ano: Int) {
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

                // Atualizar o Card de Resumo no topo

                binding.tvTotalRendaAnual.text = String.format(Locale.getDefault(), "%.2f €", somaRendaAnual)
                binding.tvTotalDespesaAnual.text = String.format(Locale.getDefault(), "%.2f €", somaDespesaAnual)
                binding.tvSaldoAnual.text = String.format(Locale.getDefault(), "%.2f €", kotlin.math.abs(saldoAnual))

                binding.tvSaldoAnual.setTextColor(if (saldoAnual >= 0) corPositivo else corNegativo)


                atualizarGrafico(entriesSaldo, coresSaldo)
            }
        }
    }

    private fun atualizarGrafico(saldos: List<BarEntry>, cores: List<Int>) {
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        val setSaldo = BarDataSet(saldos, "Saldo mensal").apply {
            colors = cores
            setDrawValues(true)  // MUDOU: era false
            valueTextColor = textColor
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    if (value == 0f) return ""
                    return String.format(Locale.getDefault(), "%.0f€", kotlin.math.abs(value))
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

            moveViewToX(11f)
            invalidate()
        }
    }
}
