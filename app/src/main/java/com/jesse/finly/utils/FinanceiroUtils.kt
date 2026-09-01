package com.jesse.finly.utils

import com.jesse.finly.models.Transacao

object FinanceiroUtils {

    /**
     * Valida se um e-mail tem um formato básico aceitável.
     */
    fun isEmailValido(email: String): Boolean {
        return email.isNotBlank() && 
               android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
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
    fun filtrarTransacoes(lista: List<Transacao>, tipo: String?, query: String): List<Transacao> {
        return lista.filter { 
            (tipo == null || it.tipo == tipo) && 
            it.item.contains(query, ignoreCase = true)
        }
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
                        val novoVencimento = String.format(java.util.Locale.getDefault(), "%s/%02d", diaOriginal, mesAlvoIndex + 1)
                        
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
