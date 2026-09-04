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
import com.jesse.finly.ResumoActivity
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.models.Transacao
import java.util.Calendar
import java.util.Locale

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    data class ItemVencimento(val transacao: Transacao, val diffDias: Int)

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

        // Filtra despesas não pagas cujo vencimento está hoje, nos próximos 3 dias ou em atraso recente (-5..3)
        val despesasVencendo = mutableListOf<ItemVencimento>()
        val seenIds = mutableSetOf<Int>()

        transacoes.forEach { trans ->
            if (trans.tipo != "DESPESA" || trans.status) return@forEach
            if (seenIds.contains(trans.id)) return@forEach
            seenIds.add(trans.id)

            val partes = trans.vencimento.split("/")
            val dia = partes.getOrNull(0)?.toIntOrNull() ?: return@forEach
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

            // Filtra contas a vencer nos próximos 3 dias ou em atraso de até 5 dias
            if (diffDias in -5..3) {
                despesasVencendo.add(ItemVencimento(trans, diffDias))
            }
        }

        if (despesasVencendo.isNotEmpty()) {
            enviarNotificacao(despesasVencendo)
        }

        return Result.success()
    }

    private fun enviarNotificacao(itens: List<ItemVencimento>) {
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Ordenar por urgência: Hoje (0) > Atrasado (<0) > Amanhã (1) > Outros
        val itensOrdenados = itens.sortedWith(compareBy {
            when (it.diffDias) {
                0 -> 0
                in -5..-1 -> 1
                1 -> 2
                else -> 3
            }
        })

        val topItem = itensOrdenados.first()
        val t = topItem.transacao
        val diff = topItem.diffDias
        val valorFormatado = String.format(Locale.getDefault(), "%.2f €", t.valor)

        val titulo = when {
            diff == 0 -> "Finly: Conta a vencer hoje!"
            diff == 1 -> "Finly: Conta a vencer amanhã!"
            diff < 0 -> "Finly: Conta em atraso!"
            else -> "Finly: Lembrete de Pagamento"
        }

        val estadoStr = when {
            diff == 0 -> "vence hoje"
            diff == 1 -> "vence amanhã"
            diff < 0 -> "em atraso"
            else -> "vence em $diff dias"
        }

        val textoPrincipal = "\"${t.item}\" ($valorFormatado) - $estadoStr"

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(titulo)
            .setContentText(textoPrincipal)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (itens.size > 1) {
            builder.setSubText("+ ${itens.size - 1} outras contas")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                manager.notify(1, builder.build())
            }
        } else {
            manager.notify(1, builder.build())
        }
    }
}
