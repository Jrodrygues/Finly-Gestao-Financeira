package com.jesse.finly

import android.content.Context
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.models.Transacao
import com.jesse.finly.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationUtilsTest {

    fun inserirTransacoesTesteParaNotificacao(context: Context) {
        val sharedPref = context.getSharedPreferences("FinlyAppPrefs", Context.MODE_PRIVATE)
        val userEmail = sharedPref.getString("EMAIL", "teste@finly.com") ?: "teste@finly.com"

        sharedPref.edit().putBoolean("NOTIFICATIONS", true).apply()

        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("d/M/yyyy", Locale.getDefault())

        val dao = MinhaBaseDados.getDatabase(context).utilizadorDao()

        CoroutineScope(Dispatchers.IO).launch {
            // 1. Conta que vence HOJE (diff = 0)
            val hojeStr = fmt.format(cal.time)
            dao.inserirTransacao(
                Transacao(
                    item = "Internet Fibra",
                    valor = 34.99,
                    tipo = "DESPESA",
                    status = false,
                    vencimento = hojeStr,
                    donoEmail = userEmail
                )
            )

            // 2. Conta que vence AMANHÃ (diff = 1)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val amanhaStr = fmt.format(cal.time)
            cal.time = Date()
            dao.inserirTransacao(
                Transacao(
                    item = "Eletricidade",
                    valor = 62.50,
                    tipo = "DESPESA",
                    status = false,
                    vencimento = amanhaStr,
                    donoEmail = userEmail
                )
            )

            // 3. Conta em ATRASO há 2 dias (diff = -2)
            cal.add(Calendar.DAY_OF_MONTH, -2)
            val atrasoStr = fmt.format(cal.time)
            dao.inserirTransacao(
                Transacao(
                    item = "Água",
                    valor = 18.20,
                    tipo = "DESPESA",
                    status = false,
                    vencimento = atrasoStr,
                    donoEmail = userEmail
                )
            )

            NotificationHelper.agendarWorkerNotificacoes(context)
        }
    }
}
