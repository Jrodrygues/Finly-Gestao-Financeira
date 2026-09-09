package com.jesse.finly.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jesse.finly.models.MetaPoupanca
import com.jesse.finly.models.Transacao
import com.jesse.finly.repository.FinanceiroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ResumoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinanceiroRepository(application)

    private val _dadosMes = MutableStateFlow<Pair<List<Transacao>, MetaPoupanca?>>(Pair(emptyList(), null))
    val dadosMes: StateFlow<Pair<List<Transacao>, MetaPoupanca?>> = _dadosMes.asStateFlow()

    fun observarDadosMes(email: String, mes: String, ano: Int) {
        viewModelScope.launch {
            val transFlow = repository.obterTransacoesPorMesFlow(email, mes, ano)
            val metaFlow = repository.obterMetaPorMesFlow(email, mes, ano)

            transFlow.combine(metaFlow) { transList, meta ->
                Pair(transList, meta)
            }.collect { pair ->
                _dadosMes.value = pair
            }
        }
    }

    fun salvarMeta(meta: MetaPoupanca) {
        viewModelScope.launch {
            repository.salvarMeta(meta)
        }
    }
}
