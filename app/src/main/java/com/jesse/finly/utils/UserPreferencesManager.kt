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
        private const val KEY_MOEDA_CONFIGURADA = "MOEDA_CONFIGURADA"
        private const val KEY_EMAIL = "EMAIL"
    }

    /**
     * Guarda a moeda instantaneamente em cache local (SharedPreferences & Room DB)
     * e sincroniza assincronamente com o Firebase Firestore.
     */
    fun salvarMoeda(moeda: Moeda, onComplete: ((Boolean) -> Unit)? = null) {
        // 1. Cache local instantânea em SharedPreferences
        prefs.edit {
            putString(KEY_MOEDA, moeda.codigo)
            putBoolean(KEY_MOEDA_CONFIGURADA, true)
        }

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
                        val updated = user.copy(moeda = moeda.codigo, moedaConfigurada = true)
                        db.utilizadorDao().atualizarUtilizador(updated)
                        FirebaseManager.salvarUtilizadorNoFirestore(updated)
                    }
                } catch (e: Exception) {
                    Log.e("UserPreferencesManager", "Erro ao atualizar Room/Firestore: ${e.message}")
                }
            }

            // 3. Sincronizar no documento do utilizador no Firestore
            firestore.collection("utilizadores").document(emailClean)
                .set(mapOf("moeda" to moeda.codigo, "moedaConfigurada" to true), SetOptions.merge())
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
     * Indica se o utilizador já definiu a sua moeda principal no onboarding/perfil.
     */
    fun isMoedaConfigurada(): Boolean {
        return prefs.getBoolean(KEY_MOEDA_CONFIGURADA, false)
    }

    /**
     * Sincroniza a moeda do Firestore para a cache local logo após o login.
     */
    /**
     * Sincroniza a moeda e a flag de configuração do Firestore para a cache local logo após o login.
     */
    fun sincronizarMoedaDoFirebase(email: String, onLoaded: ((Moeda) -> Unit)? = null) {
        val emailClean = email.trim().lowercase()
        if (FirebaseManager.isGuestEmail(emailClean)) return

        firestore.collection("utilizadores").document(emailClean).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val codigo = document.getString("moeda")
                    val moeda = Moeda.porCodigo(codigo)

                    // Lê o boolean do Firestore.
                    // Se o campo não existir, mas 'moeda' já estiver preenchida no documento, considera configurada (true)
                    val jaConfiguradoRemoto = document.getBoolean("moedaConfigurada")
                        ?: (!codigo.isNullOrEmpty())

                    // Grava AMBOS na cache local
                    prefs.edit {
                        putString(KEY_MOEDA, moeda.codigo)
                        putBoolean(KEY_MOEDA_CONFIGURADA, jaConfiguradoRemoto)
                    }

                    onLoaded?.invoke(moeda)
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserPreferencesManager", "Falha ao sincronizar moeda: ${e.message}")
            }
    }

    /**
     * Guarda o limite de gasto mensal para uma categoria específica.
     */
    fun salvarLimiteCategoria(nomeCategoria: String, limite: Double) {
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val key = "LIMITE_CAT_${email.trim().lowercase()}_$nomeCategoria"
        prefs.edit {
            putFloat(key, limite.toFloat())
        }
    }

    /**
     * Devolve o limite de gasto mensal configurado para uma categoria (0.0 se não tiver teto).
     */
    fun obterLimiteCategoria(nomeCategoria: String): Double {
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val key = "LIMITE_CAT_${email.trim().lowercase()}_$nomeCategoria"
        return prefs.getFloat(key, 0.0f).toDouble()
    }

    fun salvarHorarioNotificacao(hora: Int, minuto: Int) {
        prefs.edit {
            putInt("NOTIF_HORA", hora)
            putInt("NOTIF_MINUTO", minuto)
        }
    }

    fun obterHoraNotificacao(): Int {
        return prefs.getInt("NOTIF_HORA", 9)
    }

    fun obterMinutoNotificacao(): Int {
        return prefs.getInt("NOTIF_MINUTO", 0)
    }

    fun salvarIsPremium(isPremium: Boolean, isPlayStorePurchase: Boolean = false) {
        val jaTemTrialTime = prefs.contains("TRIAL_START_TIME")
        prefs.edit {
            putBoolean("IS_PREMIUM", isPremium)
            if (isPremium) {
                if (isPlayStorePurchase) {
                    remove("TRIAL_START_TIME")
                } else if (!jaTemTrialTime) {
                    putLong("TRIAL_START_TIME", System.currentTimeMillis())
                }
            } else {
                remove("TRIAL_START_TIME")
            }
        }

        // Sincronizar o estado Premium em tempo real com a coleção 'utilizadores' no Firestore
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        if (email.isNotBlank() && !FirebaseManager.isGuestEmail(email)) {
            val userRef = firestore.collection("utilizadores").document(email.trim().lowercase())
            userRef.set(mapOf("premium" to isPremium), SetOptions.merge())
                .addOnFailureListener { e ->
                    Log.e("UserPreferencesManager", "Erro ao salvar campo 'premium' no Firestore: ${e.message}")
                }
        }
    }

    fun isPremium(): Boolean {
        val isPremiumSaved = prefs.getBoolean("IS_PREMIUM", false)
        if (!isPremiumSaved) return false

        val startTime = prefs.getLong("TRIAL_START_TIME", 0L)
        if (startTime > 0L) {
            val diffMillis = System.currentTimeMillis() - startTime
            val diasPassados = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
            if (diasPassados >= 7) {
                return false
            }
        }
        return true
    }

    fun obterDiasRestantesTrial(): Int {
        val startTime = prefs.getLong("TRIAL_START_TIME", 0L)
        if (startTime <= 0L) return 7
        val diffMillis = System.currentTimeMillis() - startTime
        val diasPassados = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        val restantes = 7 - diasPassados
        return restantes.coerceIn(0, 7)
    }

    fun isTrialAtivo(): Boolean {
        return isPremium() && obterDiasRestantesTrial() > 0
    }

    fun obterQuantidadeTetosConfigurados(): Int {
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val prefix = "LIMITE_CAT_${email.trim().lowercase()}_"
        return prefs.all.keys.count { key ->
            key.startsWith(prefix) && (prefs.getFloat(key, 0.0f) > 0.0f)
        }
    }
}
