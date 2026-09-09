package com.jesse.finly.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jesse.finly.models.Transacao
import com.jesse.finly.repository.FinanceiroRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinanceiroRepository(application)

    private val _transacoes = MutableStateFlow<List<Transacao>>(emptyList())
    val transacoes: StateFlow<List<Transacao>> = _transacoes.asStateFlow()

    fun observarTransacoesMes(email: String, mes: String, ano: Int) {
        viewModelScope.launch {
            repository.obterTransacoesPorMesFlow(email, mes, ano).collect { list ->
                _transacoes.value = list
            }
        }
    }

    fun toggleStatus(transacao: Transacao) {
        viewModelScope.launch {
            val novaTransacao = transacao.copy(status = !transacao.status)
            repository.salvarTransacao(novaTransacao)
        }
    }

    fun apagarTransacao(transacao: Transacao) {
        viewModelScope.launch {
            repository.eliminarTransacao(transacao)
        }
    }

    fun restaurarTransacao(transacao: Transacao) {
        viewModelScope.launch {
            repository.salvarTransacao(transacao)
        }
    }
}
