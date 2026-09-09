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
import com.jesse.finly.SplashActivity
import com.jesse.finly.ResumoActivity
import com.jesse.finly.DetalhesPageActivity
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import com.jesse.finly.models.Transacao
import com.jesse.finly.utils.CurrencyFormatter
import com.jesse.finly.utils.UserPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

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

            // Focar estritamente em contas que VENCEM HOJE (0) ou estão EM ATRASO (-5 a -1)
            if (diffDias >= -5 && diffDias <= 0) {
                despesasVencendo.add(ItemVencimento(trans, diffDias))
            }
        }

        if (despesasVencendo.isNotEmpty()) {
            enviarNotificacao(despesasVencendo)
        } else {
            // Se não há mais contas pendentes (foram pagas noutro dispositivo), cancela a notificação ativa
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(1)
        }

        Result.success()
    }

    private fun enviarNotificacao(itens: List<ItemVencimento>) {
        val channelId = "VencimentoDespesas"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.notif_canal_vencimento_nome),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = applicationContext.getString(R.string.notif_canal_vencimento_desc)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_ACTIVITY", "RESUMO")
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val itensOrdenados = itens.sortedWith(
            compareBy { item ->
                when (item.diffDias) {
                    0 -> 0
                    in -5..-1 -> 1
                    else -> 2
                }
            },
        )

        val totalContas = itens.size
        val valorTotal = itens.sumOf { it.transacao.valor }
        val moedaAtual = UserPreferencesManager(applicationContext).obterMoedaAtual()
        val valorTotalFmt = CurrencyFormatter.formatar(valorTotal, moedaAtual)

        val temAtraso = itens.any { it.diffDias < 0 }

        val titulo = if (totalContas == 1) {
            val diff = itens.first().diffDias
            when {
                diff < 0 -> applicationContext.getString(R.string.notif_titulo_em_atraso)
                else -> applicationContext.getString(R.string.notif_titulo_vence_hoje)
            }
        } else {
            when {
                temAtraso -> applicationContext.getString(R.string.notif_titulo_em_atraso_plural, totalContas)
                else -> applicationContext.getString(R.string.notif_titulo_vence_hoje_plural, totalContas)
            }
        }

        val topItem = itensOrdenados.first()
        val t = topItem.transacao
        val topValorFmt = CurrencyFormatter.formatar(t.valor, moedaAtual)

        val textoResumo = if (totalContas == 1) {
            applicationContext.getString(R.string.notif_texto_resumo_single, t.item, topValorFmt)
        } else {
            applicationContext.getString(R.string.notif_texto_resumo_multi, valorTotalFmt)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_finly_notification)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
            .setContentTitle(titulo)
            .setContentText(textoResumo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)

        if (totalContas > 1) {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(titulo)

            // Linhas limpas sem repetição de texto redundante
            itensOrdenados.take(4).forEach { item ->
                val vFmt = CurrencyFormatter.formatar(item.transacao.valor, moedaAtual)
                inboxStyle.addLine(applicationContext.getString(R.string.notif_inbox_linha_limpa, item.transacao.item, vFmt))
            }

            if (totalContas > 4) {
                inboxStyle.setSummaryText(applicationContext.getString(R.string.notif_resumo_outras_contas, totalContas - 4))
            }

            builder.setStyle(inboxStyle)

            val allIds = itens.map { it.transacao.id }.toIntArray()
            val paidIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_AS_PAID
                putExtra(NotificationActionReceiver.EXTRA_TRANS_IDS, allIds)
            }
            val paidPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                t.id,
                paidIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                R.drawable.ic_finly_notification,
                applicationContext.getString(R.string.notif_btn_marcar_pagas),
                paidPendingIntent
            )
            builder.addAction(
                R.drawable.ic_finly_notification,
                applicationContext.getString(R.string.notif_btn_ver_todas),
                pendingIntent
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(textoResumo))

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

            val detailIntent = Intent(applicationContext, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("TARGET_ACTIVITY", "DETALHES")
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

            builder.addAction(
                R.drawable.ic_finly_notification,
                applicationContext.getString(R.string.notif_btn_marcar_paga),
                paidPendingIntent
            )
            builder.addAction(
                R.drawable.ic_finly_notification,
                applicationContext.getString(R.string.notif_btn_ver_detalhes),
                detailPendingIntent
            )
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
