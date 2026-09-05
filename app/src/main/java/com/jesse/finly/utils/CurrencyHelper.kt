package com.jesse.finly.utils

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

enum class Moeda(val codigo: String, val simbolo: String, val nome: String, val locale: Locale) {
    EUR("EUR", "€", "Euro (€)", Locale("pt", "PT")),
    BRL("BRL", "R$", "Real Brasileiro (R$)", Locale("pt", "BR"));

    companion object {
        fun porCodigo(codigo: String?): Moeda =
            entries.find { it.codigo.equals(codigo, ignoreCase = true) } ?: EUR
    }
}

object CurrencyFormatter {
    fun formatar(valor: Double, moeda: Moeda): String {
        val formatador = NumberFormat.getCurrencyInstance(moeda.locale)
        return formatador.format(valor)
    }

    fun formatar(valor: Double, codigoMoeda: String?): String {
        return formatar(valor, Moeda.porCodigo(codigoMoeda))
    }

    fun formatarComSinal(valor: Double, moeda: Moeda, forcarSinalPositivo: Boolean = false): String {
        if (abs(valor) < 0.001) {
            return formatar(0.0, moeda) // Retorna neutro: "0,00 €" ou "R$ 0,00"
        }

        val valorFormatado = formatar(abs(valor), moeda)

        return when {
            valor < 0 -> "-$valorFormatado"
            (forcarSinalPositivo && valor > 0) -> "+$valorFormatado"
            else -> valorFormatado
        }
    }

    fun formatarComSinal(valor: Double, codigoMoeda: String?, forcarSinalPositivo: Boolean = false): String {
        return formatarComSinal(valor, Moeda.porCodigo(codigoMoeda), forcarSinalPositivo)
    }
}

fun formatarComSinal(valor: Double, moeda: Moeda, forcarSinalPositivo: Boolean = false): String {
    return CurrencyFormatter.formatarComSinal(valor, moeda, forcarSinalPositivo)
}

