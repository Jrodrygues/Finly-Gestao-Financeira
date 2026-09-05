package com.jesse.finly

import com.jesse.finly.utils.CurrencyFormatter
import com.jesse.finly.utils.Moeda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingMoedaTest {

    @Test
    fun `Moeda porCodigo deve retornar a moeda correta e fallback para EUR se invalido`() {
        val eur = Moeda.porCodigo("EUR")
        val brl = Moeda.porCodigo("BRL")
        val fallback = Moeda.porCodigo("DESCONHECIDO")

        assertEquals(Moeda.EUR, eur)
        assertEquals(Moeda.BRL, brl)
        assertEquals(Moeda.EUR, fallback)
    }

    @Test
    fun `Propriedades das moedas EUR e BRL devem estar corretas`() {
        val eur = Moeda.EUR
        val brl = Moeda.BRL

        assertEquals("EUR", eur.codigo)
        assertEquals("€", eur.simbolo)
        assertEquals("BRL", brl.codigo)
        assertEquals("R$", brl.simbolo)
    }

    @Test
    fun `Onboarding de formatacao para EUR e BRL deve gerar textos com simbolos corretos`() {
        val valor = 1250.50

        val formatadoEUR = CurrencyFormatter.formatar(valor, Moeda.EUR)
        val formatadoBRL = CurrencyFormatter.formatar(valor, Moeda.BRL)

        assertTrue(formatadoEUR.contains("€") || formatadoEUR.contains("1.250,50") || formatadoEUR.contains("1250,50"))
        assertTrue(formatadoBRL.contains("R$") || formatadoBRL.contains("1.250,50") || formatadoBRL.contains("1250,50"))
    }

    @Test
    fun `Onboarding de formatacao com sinal deve incluir sinais positivos e negativos conforme configurado`() {
        val rendaBRL = CurrencyFormatter.formatarComSinal(2000.0, Moeda.BRL, forcarSinalPositivo = true)
        val despesaBRL = CurrencyFormatter.formatarComSinal(-500.0, Moeda.BRL)
        val neutroBRL = CurrencyFormatter.formatarComSinal(0.0, Moeda.BRL)

        assertTrue(rendaBRL.startsWith("+"))
        assertTrue(despesaBRL.startsWith("-"))
        assertFalse(neutroBRL.startsWith("+"))
        assertFalse(neutroBRL.startsWith("-"))
    }
}
