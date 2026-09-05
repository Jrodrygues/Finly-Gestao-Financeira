package com.jesse.finly.utils

import android.util.Patterns
import android.view.View
import android.view.HapticFeedbackConstants
import com.jesse.finly.models.Transacao
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

object FinanceiroUtils {

    /**
     * Valida se um e-mail tem um formato básico aceitável.
     */
    fun isEmailValido(email: String): Boolean {
        return email.isNotBlank() && 
               Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Formata um valor monetário de acordo com a moeda do utilizador (EUR / BRL).
     */
    fun formatarMoeda(valor: Double, isPositivo: Boolean? = null, codigoMoeda: String? = "EUR"): String {
        val moeda = Moeda.porCodigo(codigoMoeda)
        return when (isPositivo) {
            true -> CurrencyFormatter.formatarComSinal(abs(valor), moeda, forcarSinalPositivo = true)
            false -> CurrencyFormatter.formatarComSinal(-abs(valor), moeda)
            null -> CurrencyFormatter.formatar(valor, moeda)
        }
    }

    /**
     * Dispara vibração tátil curta e subtil (Haptic Feedback).
     */
    fun dispararHapticFeedback(view: View) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
    }

    /**
     * Calcula o saldo final baseado apenas nas transações que já foram marcadas como PAGAS (status = true).
     */
    fun calcularSaldoPago(transacoes: List<Transacao>): Double {
        val rendaPaga = transacoes.filter { it.tipo == "RENDA" && it.status }.sumOf { it.valor }
        val despesaPaga = transacoes.filter { it.tipo == "DESPESA" && it.status }.sumOf { it.valor }
        return rendaPaga - despesaPaga
    }

    /**
     * Calcula a percentagem de gastos em relação à renda total.
     */
    fun calcularPercentagemGasta(rendaTotal: Double, despesaTotal: Double): Int {
        if (rendaTotal <= 0) return 0
        return ((despesaTotal / rendaTotal) * 100).toInt()
    }

    /**
     * Filtra transações por tipo (RENDA/DESPESA) e termo de pesquisa.
     */
    fun filtrarTransacoes(lista: List<Transacao>, query: String, tipo: String? = null): List<Transacao> {
        return lista.filter { 
            (tipo == null || it.tipo == tipo) && 
            it.item.contains(query, ignoreCase = true)
        }
    }

    /**
     * Calcula dinamicamente a lista de anos disponíveis para seleção com base nas transações do utilizador.
     * Garante sempre um intervalo confortável (ano atual - 2 até +4) mais todos os anos com histórico.
     */
    fun obterAnosDisponiveis(transacoes: List<Transacao>): Array<Int> {
        val cal = Calendar.getInstance()
        val anoAtual = cal.get(Calendar.YEAR)
        val anosComDados = transacoes.map { it.ano }.filter { it in 2000..2100 }.toSet()
        val intervaloPadrao = (anoAtual - 2..anoAtual + 4).toSet()
        val todosAnos = (anosComDados + intervaloPadrao).sorted()
        return todosAnos.toTypedArray()
    }

    /**
     * Lógica robusta para preencher todos os meses faltantes.
     * Agora retorna uma lista com TODAS as ocorrências necessárias para cobrir o intervalo.
     */
    fun gerarTransacoesRecorrentes(
        recorrentes: List<Transacao>,
        existentesNoMes: List<Transacao>,
        mesAlvo: String,
        anoAlvo: Int,
        mesesArray: Array<String>
    ): List<Transacao> {
        val novasTransacoes = mutableListOf<Transacao>()
        val mesAlvoIndex = mesesArray.indexOfFirst { it.equals(mesAlvo, ignoreCase = true) }

        // Agrupar por nome para evitar processar duplicados que já são recorrentes
        val recorrentesUnicos = recorrentes.distinctBy { it.item.trim().lowercase() }

        recorrentesUnicos.forEach { rec ->
            val nomeFormatado = rec.item.trim().lowercase()
            val mesRecIndex = mesesArray.indexOfFirst { it.equals(rec.mes, ignoreCase = true) }
            
            if (mesAlvoIndex == -1 || mesRecIndex == -1) return@forEach

            // Distância total em meses desde a ocorrência que estamos a olhar até ao alvo
            val distanciaTotal = (anoAlvo - rec.ano) * 12 + (mesAlvoIndex - mesRecIndex)

            // Só processar se a transação recorrente for de um mês ANTERIOR ao alvo
            if (distanciaTotal > 0) {
                val jaExisteNoBanco = existentesNoMes.any { it.item.trim().lowercase() == nomeFormatado }
                val jaAdicionadoNestaRodada = novasTransacoes.any { it.item.trim().lowercase() == nomeFormatado }

                if (!jaExisteNoBanco && !jaAdicionadoNestaRodada) {
                    val podeGerar = when {
                        rec.parcelasRestantes == -1 -> true
                        rec.parcelasRestantes >= distanciaTotal -> true
                        else -> false
                    }

                    if (podeGerar) {
                        val diaOriginal = rec.vencimento.split("/").firstOrNull() ?: "01"
                        val novoVencimento = String.format(Locale.getDefault(), "%s/%02d", diaOriginal, mesAlvoIndex + 1)
                        
                        novasTransacoes.add(rec.copy(
                            id = 0,
                            mes = mesAlvo,
                            ano = anoAlvo,
                            vencimento = novoVencimento,
                            status = false,
                            recorrente = true,
                            parcelasRestantes = if (rec.parcelasRestantes > 0) rec.parcelasRestantes - distanciaTotal else -1,
                            parcelasTotais = rec.parcelasTotais
                        ))
                    }
                }
            }
        }
        return novasTransacoes
    }
}
