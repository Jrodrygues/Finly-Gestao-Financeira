package com.jesse.finly.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.jesse.finly.database.MinhaBaseDados
import java.text.SimpleDateFormat
import java.util.*

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val sharedPref = applicationContext.getSharedPreferences("FinlyAppPrefs", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("NOTIFICATIONS", false)
        val userEmail = sharedPref.getString("EMAIL", "") ?: ""

        if (!notificationsEnabled || userEmail.isEmpty()) return Result.success()

        val db = MinhaBaseDados.getDatabase(applicationContext)
        val transacoes = db.utilizadorDao().obterTransacoesPorDono(userEmail)

        val amanha = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dataAmanhaStr = dateFormat.format(amanha.time)

        val despesasVencendo = transacoes.filter { 
            (it.tipo == "DESPESA") && !it.status && (it.vencimento == dataAmanhaStr)
        }

        if (despesasVencendo.isNotEmpty()) {
            enviarNotificacao(despesasVencendo.size)
        }

        return Result.success()
    }

    private fun enviarNotificacao(quantidade: Int) {
        val channelId = "VencimentoDespesas"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Lembrete de Vencimento", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Atenção: Contas a vencer!")
            .setContentText("Você tem $quantidade despesa(s) vencendo amanhã. Não esqueça de pagar!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                manager.notify(1, builder.build())
            }
        } else {
            manager.notify(1, builder.build())
        }
    }
}
