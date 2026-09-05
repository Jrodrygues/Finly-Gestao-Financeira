package com.jesse.finly.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.jesse.finly.models.Transacao
import java.io.File
import java.io.FileOutputStream

class PdfExporter(private val context: Context) {

    private val prefsManager = UserPreferencesManager(context)

    fun gerarRelatorioPdf(
        mesAnoFormatado: String,
        transacoes: List<Transacao>
    ): File {
        val moeda = prefsManager.obterMoedaAtual()
        val pdfDocument = PdfDocument()

        // Página A4 padrão (595 x 842 pontos)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        // 1. Título e Cabeçalho do Documento
        paint.color = Color.parseColor("#121212")
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("Relatório Financeiro - $mesAnoFormatado", 40f, 50f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 11f
        paint.color = Color.DKGRAY
        canvas.drawText("Moeda selecionada: ${moeda.nome}", 40f, 70f, paint)

        // 2. Tabela de Transações
        var yPosition = 110f
        paint.color = Color.BLACK
        paint.isFakeBoldText = true
        paint.textSize = 10f

        // Cabeçalhos das colunas
        canvas.drawText("Data", 40f, yPosition, paint)
        canvas.drawText("Descrição", 100f, yPosition, paint)
        canvas.drawText("Categoria", 280f, yPosition, paint)
        canvas.drawText("Valor", 430f, yPosition, paint)
        canvas.drawText("Estado", 510f, yPosition, paint)

        // Linha divisória
        paint.strokeWidth = 1f
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, yPosition + 5f, 555f, yPosition + 5f, paint)

        yPosition += 25f
        paint.isFakeBoldText = false
        paint.color = Color.BLACK

        for (item in transacoes) {
            val valorFormatado = CurrencyFormatter.formatar(item.valor, moeda)
            val estadoStr = if (item.status) "Pago" else "Pendente"

            canvas.drawText(item.vencimento, 40f, yPosition, paint)
            canvas.drawText(item.item.take(25), 100f, yPosition, paint)
            canvas.drawText(item.categoria.take(18), 280f, yPosition, paint)
            canvas.drawText(valorFormatado, 430f, yPosition, paint)
            canvas.drawText(estadoStr, 510f, yPosition, paint)

            yPosition += 20f
            if (yPosition > 750f) break
        }

        // 3. Totais no Rodapé da Página
        yPosition += 20f
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, yPosition, 555f, yPosition, paint)
        yPosition += 25f

        val totalRenda = transacoes.filter { it.tipo == "RENDA" }.sumOf { it.valor }
        val totalDespesa = transacoes.filter { it.tipo == "DESPESA" }.sumOf { it.valor }
        val saldoFinal = totalRenda - totalDespesa

        paint.color = Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("Total de Rendas: ${CurrencyFormatter.formatar(totalRenda, moeda)}", 40f, yPosition, paint)
        canvas.drawText("Total de Despesas: ${CurrencyFormatter.formatar(totalDespesa, moeda)}", 220f, yPosition, paint)

        // Destaque para Saldo
        paint.color = if (saldoFinal >= 0) Color.parseColor("#00897B") else Color.RED
        canvas.drawText("Saldo: ${CurrencyFormatter.formatar(saldoFinal, moeda)}", 420f, yPosition, paint)

        pdfDocument.finishPage(page)

        val arquivoPdf = File(context.cacheDir, "Relatorio_${mesAnoFormatado.replace(" ", "_")}.pdf")
        FileOutputStream(arquivoPdf).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return arquivoPdf
    }
}
