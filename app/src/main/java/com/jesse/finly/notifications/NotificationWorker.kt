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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jesse.finly.R
import com.jesse.finly.ResumoActivity
import com.jesse.finly.DetalhesPageActivity
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.models.Transacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    data class ItemVencimento(val transacao: Transacao, val diffDias: Int)

    private val mesesNomes = arrayOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sharedPref = applicationContext.getSharedPreferences("FinlyAppPrefs", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("NOTIFICATIONS", false)
        val userEmail = sharedPref.getString("EMAIL", "") ?: ""

        if (!notificationsEnabled || FirebaseManager.isGuestEmail(userEmail)) {
            return@withContext Result.success()
        }

        val db = MinhaBaseDados.getDatabase(applicationContext)
        val transacoes = db.utilizadorDao().obterTransacoesPorDono(userEmail)

        val calHoje = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val anoAtual = calHoje[Calendar.YEAR]
        val mesAtual = calHoje[Calendar.MONTH]

        val despesasVencendo = mutableListOf<ItemVencimento>()
        val seenIds = mutableSetOf<Int>()

        transacoes.forEach { trans ->
            if ((trans.tipo != "DESPESA") || trans.status) return@forEach
            if (!seenIds.add(trans.id)) return@forEach

            val partes = trans.vencimento.split("/")
            val dia = partes.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val mes = partes.getOrNull(1)?.toIntOrNull() ?: run {
                val idx = mesesNomes.indexOfFirst { it.equals(trans.mes.trim(), ignoreCase = true) }
                if (idx != -1) idx + 1 else (mesAtual + 1)
            }
            val ano = partes.getOrNull(2)?.toIntOrNull() ?: if (trans.ano > 0) trans.ano else anoAtual

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

            if ((diffDias >= -5) && (diffDias <= 3)) {
                despesasVencendo.add(ItemVencimento(trans, diffDias))
            }
        }

        if (despesasVencendo.isNotEmpty()) {
            enviarNotificacao(despesasVencendo)
        }

        Result.success()
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

        // Ordenar por urgência: Hoje (0) > Atrasado (<0) > Amanhã (1) > 2 Dias (2) > Outros
        val itensOrdenados = itens.sortedWith(
            compareBy { item ->
                when (item.diffDias) {
                    0 -> 0
                    in -5..-1 -> 1
                    1 -> 2
                    2 -> 3
                    else -> 4
                }
            },
        )

        val topItem = itensOrdenados.first()
        val t = topItem.transacao
        val diff = topItem.diffDias
        val valorFormatado = String.format(Locale.getDefault(), "%.2f €", t.valor)

        // 1. Título sem redundância do nome da app (o Android já exibe Finly no cabeçalho)
        val titulo = when (diff) {
            0 -> "Conta a vencer hoje!"
            1 -> "Conta a vencer amanhã!"
            2 -> "Conta a vencer em 2 dias"
            in -5..-1 -> "Conta em atraso!"
            else -> "Lembrete de Pagamento"
        }

        val textoPrincipal = when (diff) {
            0 -> "O seu ${t.item} ($valorFormatado) vence hoje."
            1 -> "O seu ${t.item} ($valorFormatado) vence amanhã."
            2 -> "O seu ${t.item} ($valorFormatado) vence em 2 dias."
            in -5..-1 -> "O seu ${t.item} ($valorFormatado) está em atraso há ${-diff} dia(s)."
            else -> "O seu ${t.item} ($valorFormatado) vence em $diff dias."
        }

        // 2. Ações Rápidas de Alto Valor UX
        // Ação A: [ Marcar como Paga ] em segundo plano via BroadcastReceiver
        val paidIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_AS_PAID
            putExtra(NotificationActionReceiver.EXTRA_TRANS_ID, t.id)
        }
        val paidPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            t.id,
            paidIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação B: [ Ver Detalhes ] abre direto no item em DetalhesPageActivity
        val detailIntent = Intent(applicationContext, DetalhesPageActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("isTransactionDetail", true)
            putExtra("id", t.id)
            putExtra("item", t.item)
            putExtra("valor", t.valor)
            putExtra("vencimento", t.vencimento)
            putExtra("tipo", t.tipo)
            putExtra("status", t.status)
            putExtra("categoria", t.categoria)
            putExtra("mes", t.mes)
            putExtra("ano", t.ano)
            putExtra("isRecorrente", t.recorrente)
            putExtra("parcelasRestantes", t.parcelasRestantes)
            putExtra("parcelasTotais", t.parcelasTotais)
        }
        val detailPendingIntent = PendingIntent.getActivity(
            applicationContext,
            t.id + 10000,
            detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
            .setContentTitle(titulo)
            .setContentText(textoPrincipal)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification_small, "Marcar como Paga", paidPendingIntent)
            .addAction(R.drawable.ic_notification_small, "Ver Detalhes", detailPendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)

        if (itens.size > 1) {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle("$titulo (${itens.size} pendências)")
                .setSummaryText("${itens.size} contas a acompanhar")

            itensOrdenados.take(5).forEach { item ->
                val vFmt = String.format(Locale.getDefault(), "%.2f €", item.transacao.valor)
                val infoDias = when (item.diffDias) {
                    0 -> "vence hoje"
                    1 -> "vence amanhã"
                    2 -> "vence em 2d"
                    in -5..-1 -> "${-item.diffDias}d atraso"
                    else -> "vence em ${item.diffDias}d"
                }
                inbox.addLine("• O seu ${item.transacao.item} ($vFmt): $infoDias")
            }

            if (itens.size > 5) {
                inbox.setSummaryText("+ ${itens.size - 5} outras contas")
            }

            builder.setStyle(inbox)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(textoPrincipal))
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
