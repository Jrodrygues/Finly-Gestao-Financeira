package com.jesse.finly.database

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jesse.finly.models.MetaPoupanca
import com.jesse.finly.models.Transacao
import com.jesse.finly.models.Utilizador
import kotlinx.coroutines.tasks.await

object FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var listenerTransacoes: ListenerRegistration? = null
    private var listenerPerfil: ListenerRegistration? = null

    fun isUserLoggedIn() = auth.currentUser != null
    fun getCurrentUserEmail() = auth.currentUser?.email

    /**
     * Verifica se o e-mail pertence ao modo Convidado ou Sistema local.
     * Dados de convidados NUNCA devem ir para o Firebase.
     */
    fun isGuestEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return true
        val clean = email.trim().lowercase()
        return clean == "convidado" || clean == "sistema"
    }

    /**
     * Inicia a escuta em tempo real para as transações do utilizador.
     * Sempre que algo mudar no Firebase, a função onUpdate será chamada.
     */
    fun monitorarTransacoes(email: String, onUpdate: (List<Transacao>) -> Unit) {
        listenerTransacoes?.remove()
        val emailClean = email.trim().lowercase()
        if (isGuestEmail(emailClean)) return

        listenerTransacoes = db.collection("utilizadores")
            .document(emailClean)
            .collection("transacoes")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseManager", "Erro no listener: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val lista = snapshot.toObjects(Transacao::class.java)
                    onUpdate(lista)
                }
            }
    }

    /**
     * Inicia a escuta em tempo real para o perfil (utilizador/categorias/configurações).
     */
    fun monitorarPerfil(email: String, onUpdate: (Utilizador) -> Unit) {
        listenerPerfil?.remove()
        val emailClean = email.trim().lowercase()
        if (isGuestEmail(emailClean)) return

        listenerPerfil = db.collection("utilizadores")
            .document(emailClean)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseManager", "Erro no listener de perfil: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(Utilizador::class.java)
                    if (user != null) {
                        onUpdate(user)
                    }
                }
            }
    }

    fun pararMonitoramento() {
        listenerTransacoes?.remove()
        listenerTransacoes = null
        listenerPerfil?.remove()
        listenerPerfil = null
    }

    /**
     * Sincroniza um utilizador local para o Firestore após o registo ou alteração de perfil.
     */
    suspend fun salvarUtilizadorNoFirestore(utilizador: Utilizador) {
        val email = utilizador.email.trim().lowercase()
        if (isGuestEmail(email) || isGuestEmail(utilizador.donoEmail)) return

        try {
            db.collection("utilizadores")
                .document(email)
                .set(utilizador)
                .await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Erro ao salvar utilizador: ${e.message}")
        }
    }

    /**
     * Sincroniza uma transação para o Firestore.
     */
    suspend fun salvarTransacaoNoFirestore(transacao: Transacao) {
        val email = transacao.donoEmail.trim().lowercase()
        if (isGuestEmail(email)) return

        try {
            db.collection("utilizadores")
                .document(email)
                .collection("transacoes")
                .document(transacao.id.toString())
                .set(transacao)
                .await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Erro ao salvar transação: ${e.message}")
        }
    }

    /**
     * Elimina uma transação do Firestore.
     */
    suspend fun eliminarTransacaoDoFirestore(transacao: Transacao) {
        val email = transacao.donoEmail.trim().lowercase()
        if (isGuestEmail(email)) return

        try {
            db.collection("utilizadores")
                .document(email)
                .collection("transacoes")
                .document(transacao.id.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Erro ao eliminar transação: ${e.message}")
        }
    }

    /**
     * Descarrega os dados do perfil do utilizador do Firestore.
     */
    suspend fun obterPerfilDoFirestore(email: String): Utilizador? {
        val emailClean = email.trim().lowercase()
        if (isGuestEmail(emailClean)) return null

        return try {
            val snapshot = db.collection("utilizadores")
                .document(emailClean)
                .get()
                .await()

            snapshot.toObject(Utilizador::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Erro ao obter perfil: ${e.message}")
            null
        }
    }

    /**
     * Move todos os dados de um e-mail para outro no Firestore.
     */
    suspend fun migrarDadosDeEmail(emailAntigo: String, emailNovo: String) {
        val old = emailAntigo.trim().lowercase()
        val new = emailNovo.trim().lowercase()
        if (old == new || isGuestEmail(old) || isGuestEmail(new)) return

        try {
            val oldUserRef = db.collection("utilizadores").document(old)
            val newUserRef = db.collection("utilizadores").document(new)

            val transacoesSnapshot = oldUserRef.collection("transacoes").get().await()
            val metasSnapshot = oldUserRef.collection("metas").get().await()

            db.runBatch { batch ->
                transacoesSnapshot.forEach { doc ->
                    val trans = doc.toObject(Transacao::class.java).copy(donoEmail = emailNovo)
                    batch.set(newUserRef.collection("transacoes").document(doc.id), trans)
                    batch.delete(doc.reference)
                }

                metasSnapshot.forEach { doc ->
                    val meta = doc.toObject(MetaPoupanca::class.java).copy(donoEmail = emailNovo)
                    batch.set(newUserRef.collection("metas").document(doc.id), meta)
                    batch.delete(doc.reference)
                }

                batch.delete(oldUserRef)
            }.await()

            Log.d("FirebaseManager", "Migração total concluída: $old -> $new")
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Erro na migração de e-mail: ${e.message}")
        }
    }

    /**
     * Descarrega todas as transações do Firestore para o Room (Sync inicial).
     */
    suspend fun descarregarTransacoesDoFirestore(email: String): List<Transacao> {
        val emailClean = email.trim().lowercase()
        if (isGuestEmail(emailClean)) return emptyList()

        return try {
            val snapshot = db.collection("utilizadores")
                .document(emailClean)
                .collection("transacoes")
                .get()
                .await()

            snapshot.toObjects(Transacao::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Erro ao descarregar transações: ${e.message}")
            emptyList()
        }
    }

    /**
     * Exclui todos os dados do utilizador do Firebase (Firestore, Storage e Auth).
     */
    suspend fun excluirContaTotal(email: String): Boolean {
        val emailClean = email.trim().lowercase()
        if (isGuestEmail(emailClean)) return false

        return try {
            val userRef = db.collection("utilizadores").document(emailClean)

            val transacoes = userRef.collection("transacoes").get().await()
            db.runBatch { batch ->
                transacoes.forEach { batch.delete(it.reference) }
            }.await()

            val metas = userRef.collection("metas").get().await()
            db.runBatch { batch ->
                metas.forEach { batch.delete(it.reference) }
            }.await()

            userRef.delete().await()

            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.email?.trim()?.lowercase() == emailClean) {
                currentUser.delete().await()
            }

            true
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Erro ao excluir conta total: ${e.message}")
            false
        }
    }

    /**
     * Remove o documento 'convidado' e suas subcoleções do Firestore se tiver sido criado anteriormente.
     */
    suspend fun limparConvidadoDoFirestore() {
        try {
            val userRef = db.collection("utilizadores").document("convidado")
            val transacoes = userRef.collection("transacoes").get().await()
            if (!transacoes.isEmpty) {
                db.runBatch { batch ->
                    transacoes.forEach { batch.delete(it.reference) }
                }.await()
            }
            val metas = userRef.collection("metas").get().await()
            if (!metas.isEmpty) {
                db.runBatch { batch ->
                    metas.forEach { batch.delete(it.reference) }
                }.await()
            }
            userRef.delete().await()
            Log.d("FirebaseManager", "Limpeza automática: documento 'convidado' eliminado do Firestore com sucesso.")
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Erro ao limpar convidado do Firestore: ${e.message}")
        }
    }
}
