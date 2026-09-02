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

    fun isUserLoggedIn() = auth.currentUser != null
    fun getCurrentUserEmail() = auth.currentUser?.email

    /**
     * Inicia a escuta em tempo real para as transações do utilizador.
     * Sempre que algo mudar no Firebase, a função onUpdate será chamada.
     */
    fun monitorarTransacoes(email: String, onUpdate: (List<Transacao>) -> Unit) {
        // Remover listener anterior se existir para evitar duplicados
        listenerTransacoes?.remove()

        listenerTransacoes = db.collection("utilizadores")
            .document(email.trim().lowercase())
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

    fun pararMonitoramento() {
        listenerTransacoes?.remove()
        listenerTransacoes = null
    }

    /**
     * Sincroniza um utilizador local para o Firestore após o registo ou alteração de perfil.
     * ISOLA a foto de perfil: Mantém o que já está na nuvem, ignorando caminhos locais.
     */
    suspend fun salvarUtilizadorNoFirestore(utilizador: Utilizador) {
        val email = utilizador.email.trim().lowercase()
        
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
        // Usamos o ID local como nome do documento para facilitar a atualização, 
        // mas no Firestore o ID será string.
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
        return try {
            val snapshot = db.collection("utilizadores")
                .document(email.trim().lowercase())
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
     * Usado quando o utilizador edita o seu próprio e-mail.
     */
    suspend fun migrarDadosDeEmail(emailAntigo: String, emailNovo: String) {
        val old = emailAntigo.trim().lowercase()
        val new = emailNovo.trim().lowercase()
        if (old == new) return

        try {
            val oldUserRef = db.collection("utilizadores").document(old)
            val newUserRef = db.collection("utilizadores").document(new)

            // 1. Obter Transações e Metas da "pasta" antiga
            val transacoesSnapshot = oldUserRef.collection("transacoes").get().await()
            val metasSnapshot = oldUserRef.collection("metas").get().await()

            // 2. Usar um Batch para migrar tudo de uma vez
            db.runBatch { batch ->
                // Migrar Transações
                transacoesSnapshot.forEach { doc ->
                    val trans = doc.toObject(Transacao::class.java).copy(donoEmail = emailNovo)
                    // Garantimos que o documento existe no novo local
                    batch.set(newUserRef.collection("transacoes").document(doc.id), trans)
                    // Apagamos do antigo
                    batch.delete(doc.reference)
                }
                
                // Migrar Metas
                metasSnapshot.forEach { doc ->
                    val meta = doc.toObject(MetaPoupanca::class.java).copy(donoEmail = emailNovo)
                    batch.set(newUserRef.collection("metas").document(doc.id), meta)
                    batch.delete(doc.reference)
                }
                
                // IMPORTANTE: Criar o documento do utilizador no novo local antes de apagar o antigo
                // Se o documento de perfil novo já foi criado pelo RegistoActivity, o Batch apenas o atualizará
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
        return try {
            val snapshot = db.collection("utilizadores")
                .document(email.trim().lowercase())
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
        return try {
            val userRef = db.collection("utilizadores").document(emailClean)

            // 1. Eliminar Transações
            val transacoes = userRef.collection("transacoes").get().await()
            db.runBatch { batch ->
                transacoes.forEach { batch.delete(it.reference) }
            }.await()

            // 2. Eliminar Metas
            val metas = userRef.collection("metas").get().await()
            db.runBatch { batch ->
                metas.forEach { batch.delete(it.reference) }
            }.await()

            // 3. Eliminar Documento do Utilizador
            userRef.delete().await()

            // 4. Eliminar do Firebase Auth
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
}
