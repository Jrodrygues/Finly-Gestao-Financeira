package com.jesse.finly.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.jesse.finly.R
import com.jesse.finly.ResumoActivity
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.models.Transacao
import java.util.Calendar

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val sharedPref = applicationContext.getSharedPreferences("FinlyAppPrefs", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("NOTIFICATIONS", false)
        val userEmail = sharedPref.getString("EMAIL", "") ?: ""

        if (!notificationsEnabled || FirebaseManager.isGuestEmail(userEmail)) {
            return Result.success()
        }

        val db = MinhaBaseDados.getDatabase(applicationContext)
        val transacoes = db.utilizadorDao().obterTransacoesPorDono(userEmail)

        val calHoje = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val anoAtual = calHoje.get(Calendar.YEAR)
        val mesAtual = calHoje.get(Calendar.MONTH)

        // Filtra despesas não pagas cujo vencimento está hoje, nos próximos 3 dias ou em atraso recente
        val despesasVencendo = transacoes.filter { trans ->
            if (trans.tipo != "DESPESA" || trans.status) return@filter false

            val partes = trans.vencimento.split("/")
            val dia = partes.getOrNull(0)?.toIntOrNull() ?: return@filter false
            val mes = partes.getOrNull(1)?.toIntOrNull() ?: (mesAtual + 1)
            val ano = partes.getOrNull(2)?.toIntOrNull() ?: anoAtual

            val calVenc = Calendar.getInstance().apply {
                set(Calendar.YEAR, ano)
                set(Calendar.MONTH, mes - 1)
                set(Calendar.DAY_OF_MONTH, dia)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = calVenc.timeInMillis - calHoje.timeInMillis
            val diffDias = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

            // Alerta para contas que vencem hoje (0), amanhã (1), nos próximos 3 dias (2..3) ou atrasadas há até 5 dias (-5..-1)
            diffDias in -5..3
        }

        if (despesasVencendo.isNotEmpty()) {
            enviarNotificacao(despesasVencendo)
        }

        return Result.success()
    }

    private fun enviarNotificacao(despesas: List<Transacao>) {
        val channelId = "VencimentoDespesas"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Lembrete de Vencimento",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notificações para contas a vencer ou em atraso"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, ResumoActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
        )

        val quantidade = despesas.size
        val mensagemText = if (quantidade == 1) {
            "Você tem 1 conta (${despesas[0].item}) próxima do vencimento!"
        } else {
            "Você tem $quantidade contas próximas do vencimento ou pendentes!"
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_logo_finly)
            .setContentTitle("Finly: Lembrete de Pagamento")
            .setContentText(mensagemText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensagemText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                manager.notify(1, builder.build())
            }
        } else {
            manager.notify(1, builder.build())
        }
    }
}
