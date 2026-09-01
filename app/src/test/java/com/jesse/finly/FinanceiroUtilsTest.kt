package com.jesse.finly

import com.jesse.finly.models.Transacao
import com.jesse.finly.utils.FinanceiroUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceiroUtilsTest {

    @Test
    fun `Calcular saldo pago deve subtrair despesas de rendas apenas se status for true`() {
        val transacoes = listOf(
            Transacao(item = "Salário", valor = 1000.0, tipo = "RENDA", status = true, mes = "Janeiro", ano = 2026, vencimento = "01/01", donoEmail = "t@t.com"),
            Transacao(item = "Freelance", valor = 200.0, tipo = "RENDA", status = false, mes = "Janeiro", ano = 2026, vencimento = "05/01", donoEmail = "t@t.com"),
            Transacao(item = "Aluguer", valor = 400.0, tipo = "DESPESA", status = true, mes = "Janeiro", ano = 2026, vencimento = "10/01", donoEmail = "t@t.com"),
            Transacao(item = "Luz", valor = 50.0, tipo = "DESPESA", status = false, mes = "Janeiro", ano = 2026, vencimento = "15/01", donoEmail = "t@t.com")
        )

        // Deve somar 1000 (renda paga) e subtrair 400 (despesa paga) = 600
        val saldo = FinanceiroUtils.calcularSaldoPago(transacoes)
        assertEquals(600.0, saldo, 0.01)
    }

    @Test
    fun `Calcular percentagem gasta deve retornar valor correto`() {
        val renda = 2000.0
        val despesa = 500.0
        
        val percent = FinanceiroUtils.calcularPercentagemGasta(renda, despesa)
        assertEquals(25, percent)
    }

    @Test
    fun `Calcular percentagem gasta com renda zero deve retornar zero e nao crashar`() {
        val percent = FinanceiroUtils.calcularPercentagemGasta(0.0, 500.0)
        assertEquals(0, percent)
    }

    @Test
    fun `Filtrar transacoes deve retornar apenas itens que contem a query`() {
        val transacoes = listOf(
            Transacao(item = "Mercado", valor = 50.0, tipo = "DESPESA", status = true, mes = "Janeiro", ano = 2026, vencimento = "01/01", donoEmail = "t@t.com"),
            Transacao(item = "Farmacia", valor = 20.0, tipo = "DESPESA", status = true, mes = "Janeiro", ano = 2026, vencimento = "01/01", donoEmail = "t@t.com")
        )

        val resultado = FinanceiroUtils.filtrarTransacoes(transacoes, null, "Farm")
        assertEquals(1, resultado.size)
        assertEquals("Farmacia", resultado[0].item)
    }

    @Test
    fun `Gerar transacoes recorrentes deve criar novo item se nao existir no mes alvo`() {
        val meses = arrayOf("Janeiro", "Fevereiro", "Março")
        val recorrentes = listOf(
            Transacao(item = "Internet", valor = 30.0, tipo = "DESPESA", status = true, mes = "Janeiro", ano = 2026, vencimento = "01/01", donoEmail = "t@t.com", recorrente = true, parcelasRestantes = -1)
        )
        val existentesEmFevereiro = emptyList<Transacao>()

        val novas = FinanceiroUtils.gerarTransacoesRecorrentes(recorrentes, existentesEmFevereiro, "Fevereiro", 2026, meses)
        
        assertEquals(1, novas.size)
        assertEquals("Internet", novas[0].item)
        assertEquals("Fevereiro", novas[0].mes)
        assertEquals("01/02", novas[0].vencimento)
    }

    @Test
    fun `Gerar transacoes recorrentes nao deve criar duplicados se ja existe no mes alvo`() {
        val meses = arrayOf("Janeiro", "Fevereiro")
        val recorrentes = listOf(
            Transacao(item = "Internet", valor = 30.0, tipo = "DESPESA", status = true, mes = "Janeiro", ano = 2026, vencimento = "01/01", donoEmail = "t@t.com", recorrente = true, parcelasRestantes = -1)
        )
        val existentesEmFevereiro = listOf(
            Transacao(item = "Internet", valor = 30.0, tipo = "DESPESA", status = false, mes = "Fevereiro", ano = 2026, vencimento = "01/02", donoEmail = "t@t.com")
        )

        val novas = FinanceiroUtils.gerarTransacoesRecorrentes(recorrentes, existentesEmFevereiro, "Fevereiro", 2026, meses)
        
        assertTrue(novas.isEmpty())
    }
}
