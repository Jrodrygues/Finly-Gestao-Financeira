package com.jesse.finly

import com.jesse.finly.models.Transacao
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class NotificationCalculationTest {

    private val mesesNomes = arrayOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )

    private fun calcularDiffDias(
        trans: Transacao,
        calHoje: Calendar,
    ): Int {
        val anoAtual = calHoje[Calendar.YEAR]
        val mesAtual = calHoje[Calendar.MONTH]

        val partes = trans.vencimento.split("/")
        val dia = partes.getOrNull(0)?.toIntOrNull() ?: return Int.MIN_VALUE
        val mes = partes.getOrNull(1)?.toIntOrNull() ?: run {
            val idx = mesesNomes.indexOfFirst { it.equals(trans.mes.trim(), ignoreCase = true) }
            if (idx != -1) idx + 1 else (mesAtual + 1)
        }
        val ano = partes.getOrNull(2)?.toIntOrNull() ?: if (trans.ano > 0) trans.ano else anoAtual

        val calVenc = Calendar.getInstance().apply {
            set(Calendar.YEAR, ano)
            set(Calendar.MONTH, mes - 1)
            set(Calendar.DAY_OF_MONTH, dia)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = calVenc.timeInMillis - calHoje.timeInMillis
        return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    }

    private fun gerarMensagemNotificacao(t: Transacao, diff: Int): Pair<String, String> {
        val valorFormatado = String.format(Locale.US, "%.2f €", t.valor)

        val titulo = when (diff) {
            0 -> "Finly: Conta a vencer hoje!"
            1 -> "Finly: Conta a vencer amanhã!"
            2 -> "Finly: Conta a vencer em 2 dias"
            in -5..-1 -> "Finly: Conta em atraso!"
            else -> "Finly: Lembrete de Pagamento"
        }

        val textoPrincipal = when (diff) {
            0 -> "O seu ${t.item} ($valorFormatado) vence hoje."
            1 -> "O seu ${t.item} ($valorFormatado) vence amanhã."
            2 -> "O seu ${t.item} ($valorFormatado) vence em 2 dias."
            in -5..-1 -> "O seu ${t.item} ($valorFormatado) está em atraso há ${-diff} dia(s)."
            else -> "O seu ${t.item} ($valorFormatado) vence em $diff dias."
        }

        return Pair(titulo, textoPrincipal)
    }

    @Test
    fun testDespesaVenceAmanha() {
        val hoje = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 15, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val trans = Transacao(
            item = "Arrendamento",
            valor = 500.0,
            tipo = "DESPESA",
            status = false,
            vencimento = "16/03/2026",
        )

        val diff = calcularDiffDias(trans, hoje)
        assertEquals(1, diff)

        val (titulo, msg) = gerarMensagemNotificacao(trans, diff)
        assertEquals("Finly: Conta a vencer amanhã!", titulo)
        assertEquals("O seu Arrendamento (500.00 €) vence amanhã.", msg)
    }

    @Test
    fun testDespesaVenceEmDoisDias() {
        val hoje = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 15, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val trans = Transacao(
            item = "Seguro do Carro",
            valor = 120.0,
            tipo = "DESPESA",
            status = false,
            vencimento = "17/03/2026",
        )

        val diff = calcularDiffDias(trans, hoje)
        assertEquals(2, diff)

        val (titulo, msg) = gerarMensagemNotificacao(trans, diff)
        assertEquals("Finly: Conta a vencer em 2 dias", titulo)
        assertEquals("O seu Seguro do Carro (120.00 €) vence em 2 dias.", msg)
    }
}
