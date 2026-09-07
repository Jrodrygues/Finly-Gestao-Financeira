package com.jesse.finly.utils

import android.content.Context
import com.jesse.finly.R
import com.jesse.finly.models.Transacao
import java.io.File
import java.io.FileWriter

class CsvExporter(private val context: Context) {

    private val prefsManager = UserPreferencesManager(context)

    fun exportarTransacoesMes(
        mesAnoFormatado: String,
        transacoes: List<Transacao>
    ): File {
        val moeda = prefsManager.obterMoedaAtual()
        val nomeArquivo = "Relatorio_${mesAnoFormatado.replace(" ", "_")}.csv"
        val arquivo = File(context.cacheDir, nomeArquivo)

        FileWriter(arquivo).use { writer ->
            // BOM UTF-8 para correta interpretação de caracteres acentuados no Excel
            writer.append("\uFEFF")
            // Cabeçalho de Metadados
            writer.append("${context.getString(R.string.pdf_titulo)} - $mesAnoFormatado\n")
            writer.append("${context.getString(R.string.pdf_moeda_selecionada, moeda.nome)}\n\n")

            // Cabeçalhos da Tabela (usando ';' como separador)
            writer.append(context.getString(R.string.csv_cabecalho_fmt, moeda.simbolo))

            // Linhas de dados
            for (t in transacoes) {
                val tipoStr = if (t.tipo == "RENDA") context.getString(R.string.rb_renda) else context.getString(
                    R.string.rb_despesa)
                val pagoStr = if (t.status) context.getString(R.string.label_sim) else context.getString(
                    R.string.label_nao)
                val valorStr = CurrencyFormatter.formatar(t.valor, moeda).replace(";", "")

                writer.append("${t.vencimento};\"${t.item.replace("\"", "\"\"")}\";\"${t.categoria.replace("\"", "\"\"")}\";$tipoStr;$valorStr;$pagoStr\n")
            }

            // Totais no rodapé do CSV
            val totalRenda = transacoes.filter { it.tipo == "RENDA" }.sumOf { it.valor }
            val totalDespesa = transacoes.filter { it.tipo == "DESPESA" }.sumOf { it.valor }
            val saldo = totalRenda - totalDespesa

            writer.append("\n")
            writer.append("${context.getString(R.string.total_rendas)};;;;${CurrencyFormatter.formatar(totalRenda, moeda)};\n")
            writer.append("${context.getString(R.string.total_despesas)};;;;${CurrencyFormatter.formatar(totalDespesa, moeda)};\n")
            writer.append("${context.getString(R.string.saldo_final_label)};;;;${CurrencyFormatter.formatar(saldo, moeda)};\n")
        }

        return arquivo
    }
}
