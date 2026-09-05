package com.jesse.finly.utils

import android.content.Context
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
            writer.append("Relatório Mensal Finly - $mesAnoFormatado\n")
            writer.append("Moeda: ${moeda.nome}\n\n")

            // Cabeçalhos da Tabela (usando ';' como separador)
            writer.append("Data;Nome / Descrição;Categoria;Tipo;Valor (${moeda.simbolo});Pago\n")

            // Linhas de dados
            for (t in transacoes) {
                val tipoStr = if (t.tipo == "RENDA") "Renda" else "Despesa"
                val pagoStr = if (t.status) "Sim" else "Não"
                val valorStr = CurrencyFormatter.formatar(t.valor, moeda).replace(";", "")

                writer.append("${t.vencimento};\"${t.item.replace("\"", "\"\"")}\";\"${t.categoria.replace("\"", "\"\"")}\";$tipoStr;$valorStr;$pagoStr\n")
            }

            // Totais no rodapé do CSV
            val totalRenda = transacoes.filter { it.tipo == "RENDA" }.sumOf { it.valor }
            val totalDespesa = transacoes.filter { it.tipo == "DESPESA" }.sumOf { it.valor }
            val saldo = totalRenda - totalDespesa

            writer.append("\n")
            writer.append("Total Renda;;;;${CurrencyFormatter.formatar(totalRenda, moeda)};\n")
            writer.append("Total Despesa;;;;${CurrencyFormatter.formatar(totalDespesa, moeda)};\n")
            writer.append("Saldo Final;;;;${CurrencyFormatter.formatar(saldo, moeda)};\n")
        }

        return arquivo
    }
}
