package com.jesse.finly.`interface`

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jesse.finly.models.MetaPoupanca
import com.jesse.finly.models.Transacao
import com.jesse.finly.models.Utilizador
import kotlinx.coroutines.flow.Flow

@Dao
interface UtilizadorDAO {
    // --- Lógica de Metas ---
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun salvarMeta(meta: MetaPoupanca)

    @Query("SELECT * FROM tabela_metas WHERE donoEmail = :email AND mes = :mes AND ano = :ano LIMIT 1")
    fun obterMetaPorMes(email: String, mes: String, ano: Int): MetaPoupanca?

    @Query("SELECT * FROM tabela_metas WHERE donoEmail = :email AND mes = :mes AND ano = :ano LIMIT 1")
    fun obterMetaPorMesFlow(email: String, mes: String, ano: Int): Flow<MetaPoupanca?>

    // --- Lógica de Utilizadores (Login/Contas) ---
    @Insert
    fun insertUtilizador(utilizador: Utilizador)

    @Update
    fun atualizarUtilizador(utilizador: Utilizador)

    @Query("SELECT * FROM Tabela_utilizadores WHERE email = :email AND senha = :senha LIMIT 1")
    fun validarLogin(email: String, senha: String): Utilizador?

    @Query("SELECT * FROM Tabela_utilizadores WHERE email = :email LIMIT 1")
    fun buscarPorEmail(email: String): Utilizador?

    @Query("SELECT * FROM Tabela_utilizadores")
    fun obterTodosUtilizadores(): List<Utilizador>

    @Delete
    fun apagarUtilizador(utilizador: Utilizador)

    @Query("DELETE FROM tabela_transacoes WHERE donoEmail = :email")
    fun apagarTodasTransacoesDoDono(email: String)


    // --- Lógica de Transações (Listagem Financeira) ---
    @Insert
    fun inserirTransacao(transacao: Transacao): Long

    @Update
    fun atualizarTransacao(transacao: Transacao)

    @Delete
    fun apagarTransacao(transacao: Transacao)

    @Query("SELECT * FROM tabela_transacoes WHERE donoEmail = :email ORDER BY id ASC")
    fun obterTransacoesPorDono(email: String): List<Transacao>

    @Query("SELECT * FROM tabela_transacoes WHERE donoEmail = :email ORDER BY id ASC")
    fun obterTransacoesPorDonoFlow(email: String): Flow<List<Transacao>>

    @Query("SELECT * FROM tabela_transacoes WHERE donoEmail = :email AND mes = :mes AND ano = :ano")
    fun obterTransacoesPorMes(email: String, mes: String, ano: Int): List<Transacao>

    @Query("SELECT * FROM tabela_transacoes WHERE donoEmail = :email AND mes = :mes AND ano = :ano")
    fun obterTransacoesPorMesFlow(email: String, mes: String, ano: Int): Flow<List<Transacao>>

    @Query("SELECT * FROM tabela_transacoes WHERE donoEmail = :email AND recorrente = 1")
    fun obterTransacoesRecorrentes(email: String): List<Transacao>
    @Query("SELECT * FROM tabela_transacoes WHERE id = :id LIMIT 1")
    fun obterTransacaoPorId(id: Int): Transacao?
}
