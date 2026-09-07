package com.jesse.finly.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jesse.finly.R
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.utils.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_AS_PAID = "com.jesse.finly.ACTION_MARK_AS_PAID"
        const val EXTRA_TRANS_ID = "extra_trans_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MARK_AS_PAID) {
            val transId = intent.getIntExtra(EXTRA_TRANS_ID, -1)
            if (transId <= 0) return

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = MinhaBaseDados.getDatabase(context)
                    val trans = db.utilizadorDao().obterTransacaoPorId(transId)

                    if (trans != null && !trans.status) {
                        val atualizada = trans.copy(status = true)
                        db.utilizadorDao().atualizarTransacao(atualizada)
                        FirebaseManager.salvarTransacaoNoFirestore(atualizada)

                        withContext(Dispatchers.Main) {
                            context.showToast(context.getString(R.string.toast_transacao_marcada_paga, trans.item))
                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.cancel(1)
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        context.showToast(context.getString(R.string.toast_erro_atualizar_transacao))
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
