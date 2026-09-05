package com.jesse.finly.utils

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UserPreferencesManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val PREFS_NAME = "FinlyAppPrefs"
        private const val KEY_MOEDA = "MOEDA"
        private const val KEY_EMAIL = "EMAIL"
    }

    /**
     * Guarda a moeda instantaneamente em cache local (SharedPreferences & Room DB)
     * e sincroniza assincronamente com o Firebase Firestore.
     */
    fun salvarMoeda(moeda: Moeda, onComplete: ((Boolean) -> Unit)? = null) {
        // 1. Cache local instantânea em SharedPreferences
        prefs.edit { putString(KEY_MOEDA, moeda.codigo) }

        var email = prefs.getString(KEY_EMAIL, "") ?: ""
        if (email.isEmpty() || email == "CONVIDADO") {
            email = FirebaseAuth.getInstance().currentUser?.email ?: ""
        }
        val emailClean = email.trim().lowercase()

        // 2. Persistir localmente no Room Database e no Firestore
        if (emailClean.isNotEmpty() && !FirebaseManager.isGuestEmail(emailClean)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = MinhaBaseDados.getDatabase(context)
                    val user = db.utilizadorDao().buscarPorEmail(emailClean)
                    if (user != null) {
                        val updated = user.copy(moeda = moeda.codigo)
                        db.utilizadorDao().atualizarUtilizador(updated)
                        FirebaseManager.salvarUtilizadorNoFirestore(updated)
                    }
                } catch (e: Exception) {
                    Log.e("UserPreferencesManager", "Erro ao atualizar Room/Firestore: ${e.message}")
                }
            }

            // 3. Sincronizar no documento do utilizador no Firestore
            firestore.collection("utilizadores").document(emailClean)
                .set(mapOf("moeda" to moeda.codigo), SetOptions.merge())
                .addOnSuccessListener { onComplete?.invoke(true) }
                .addOnFailureListener { onComplete?.invoke(false) }
        } else {
            onComplete?.invoke(true)
        }
    }

    /**
     * Devolve a moeda atualmente ativa na cache local sem qualquer atraso de rede.
     */
    fun obterMoedaAtual(): Moeda {
        val codigo = prefs.getString(KEY_MOEDA, null)
        return Moeda.porCodigo(codigo)
    }

    /**
     * Sincroniza a moeda do Firestore para a cache local logo após o login.
     */
    fun sincronizarMoedaDoFirebase(email: String, onLoaded: ((Moeda) -> Unit)? = null) {
        val emailClean = email.trim().lowercase()
        if (FirebaseManager.isGuestEmail(emailClean)) return

        firestore.collection("utilizadores").document(emailClean).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val codigo = document.getString("moeda")
                    val moeda = Moeda.porCodigo(codigo)
                    prefs.edit { putString(KEY_MOEDA, moeda.codigo) }
                    onLoaded?.invoke(moeda)
                }
            }
    }
}
