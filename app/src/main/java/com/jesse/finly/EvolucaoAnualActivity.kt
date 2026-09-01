package com.jesse.finly

import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
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
import com.jesse.finly.models.Transacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.Calendar
import java.util.Locale

class EvolucaoAnualActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEvolucaoAnualBinding
    private val mesesArray = arrayOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
    private val mesesNomes = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    private val listaAnos = arrayOf("2024", "2025", "2026", "2027", "2028", "2029", "2030")

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
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            setScaleEnabled(false)
            setDoubleTapToZoomEnabled(false)
            
            val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            val textColor = if (isDark) Color.WHITE else Color.BLACK

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelCount = 12
                valueFormatter = IndexAxisValueFormatter(mesesArray)
                this.textColor = textColor
                textSize = 10f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = if (isDark) Color.parseColor("#33FFFFFF") else Color.parseColor("#33000000")
                axisMinimum = 0f
                this.textColor = textColor
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value >= 1000) "${(value / 1000).toInt()}k €" else "${value.toInt()} €"
                    }
                }
            }
            axisRight.isEnabled = false
            
            legend.apply {
                this.textColor = textColor
                textSize = 12f
                formSize = 12f
                xEntrySpace = 20f
            }
            
            animateY(1000)
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

            val entriesRenda = mutableListOf<BarEntry>()
            val entriesDespesa = mutableListOf<BarEntry>()

            for (i in 0..11) {
                val mesNome = mesesNomes[i]
                val transMes = transacoes.filter { it.mes == mesNome }
                
                val totalRenda = transMes.filter { it.tipo == "RENDA" && it.categoria != "Poupança" && it.categoria != "Exterior" }.sumOf { it.valor }.toFloat()
                val totalDespesa = transMes.filter { it.tipo == "DESPESA" }.sumOf { it.valor }.toFloat()

                somaRendaAnual += totalRenda.toDouble()
                somaDespesaAnual += totalDespesa.toDouble()

                entriesRenda.add(BarEntry(i.toFloat(), if (totalRenda > 0) totalRenda else 0f))
                entriesDespesa.add(BarEntry(i.toFloat(), if (totalDespesa > 0) totalDespesa else 0f))
            }

            val saldoAnual = somaRendaAnual - somaDespesaAnual

            withContext(Dispatchers.Main) {
                // Atualizar o Card de Resumo no topo
                binding.tvTotalRendaAnual.text = String.format(Locale.getDefault(), "%.2f €", somaRendaAnual)
                binding.tvTotalDespesaAnual.text = String.format(Locale.getDefault(), "%.2f €", somaDespesaAnual)
                binding.tvSaldoAnual.text = String.format(Locale.getDefault(), "%.2f €", saldoAnual)
                
                val colorPositivo = ContextCompat.getColor(this@EvolucaoAnualActivity, R.color.colorPositive)
                val colorNegativo = ContextCompat.getColor(this@EvolucaoAnualActivity, R.color.colorNegative)
                binding.tvSaldoAnual.setTextColor(if (saldoAnual >= 0) colorPositivo else colorNegativo)

                atualizarGrafico(entriesRenda, entriesDespesa)
            }
        }
    }

    private fun atualizarGrafico(rendas: List<BarEntry>, despesas: List<BarEntry>) {
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        val labelColor = if (isDark) Color.WHITE else Color.BLACK

        val currencyFormatter = object : ValueFormatter() {
            private val mFormat = DecimalFormat("###,###,##0")
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) mFormat.format(value) else ""
            }
        }

        val setRenda = BarDataSet(rendas, "Rendas").apply {
            color = ContextCompat.getColor(this@EvolucaoAnualActivity, R.color.colorPositive)
            valueTextColor = labelColor
            valueTextSize = 9f
            valueFormatter = currencyFormatter
        }

        val setDespesa = BarDataSet(despesas, "Despesas").apply {
            color = ContextCompat.getColor(this@EvolucaoAnualActivity, R.color.colorNegative)
            valueTextColor = labelColor
            valueTextSize = 9f
            valueFormatter = currencyFormatter
        }

        val data = BarData(setRenda, setDespesa)
        
        val groupSpace = 0.26f
        val barSpace = 0.02f
        val barWidth = 0.35f

        data.barWidth = barWidth
        
        binding.barChartAnual.apply {
            this.data = data
            axisLeft.spaceTop = 20f
            
            groupBars(0f, groupSpace, barSpace)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = 12f
            xAxis.setCenterAxisLabels(true)
            
            invalidate()
        }
    }
}
