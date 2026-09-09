package com.jesse.finly.repository

import android.content.Context
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.models.MetaPoupanca
import com.jesse.finly.models.Transacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FinanceiroRepository(private val context: Context) {
    private val db = MinhaBaseDados.getDatabase(context)
    private val dao = db.utilizadorDao()

    fun obterTransacoesPorMesFlow(email: String, mes: String, ano: Int): Flow<List<Transacao>> {
        return dao.obterTransacoesPorMesFlow(email.trim().lowercase(), mes, ano)
    }

    fun obterTransacoesPorDonoFlow(email: String): Flow<List<Transacao>> {
        return dao.obterTransacoesPorDonoFlow(email.trim().lowercase())
    }

    fun obterMetaPorMesFlow(email: String, mes: String, ano: Int): Flow<MetaPoupanca?> {
        return dao.obterMetaPorMesFlow(email.trim().lowercase(), mes, ano)
    }

    suspend fun salvarTransacao(transacao: Transacao) = withContext(Dispatchers.IO) {
        if (transacao.id > 0) {
            dao.atualizarTransacao(transacao)
        } else {
            val novoId = dao.inserirTransacao(transacao).toInt()
            val comId = transacao.copy(id = novoId)
            FirebaseManager.salvarTransacaoNoFirestore(comId)
            return@withContext
        }
        FirebaseManager.salvarTransacaoNoFirestore(transacao)
    }

    suspend fun eliminarTransacao(transacao: Transacao) = withContext(Dispatchers.IO) {
        dao.apagarTransacao(transacao)
        FirebaseManager.eliminarTransacaoDoFirestore(transacao)
    }

    suspend fun salvarMeta(meta: MetaPoupanca) = withContext(Dispatchers.IO) {
        dao.salvarMeta(meta)
        if (!FirebaseManager.isGuestEmail(meta.donoEmail)) {
            FirebaseManager.salvarMetaNoFirestore(meta)
        }
    }

    suspend fun obterTransacoesPorMes(email: String, mes: String, ano: Int): List<Transacao> = withContext(Dispatchers.IO) {
        dao.obterTransacoesPorMes(email.trim().lowercase(), mes, ano)
    }

    suspend fun obterMetaPorMes(email: String, mes: String, ano: Int): MetaPoupanca? = withContext(Dispatchers.IO) {
        dao.obterMetaPorMes(email.trim().lowercase(), mes, ano)
    }
}
