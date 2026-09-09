package com.jesse.finly.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jesse.finly.database.FirebaseManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationHelper {
    private const val WORK_NAME = "LembreteDespesasWork"

    fun agendarWorkerNotificacoes(context: Context) {
        val sharedPref = context.getSharedPreferences("FinlyAppPrefs", Context.MODE_PRIVATE)
        val userEmail = sharedPref.getString("EMAIL", "") ?: ""

        if (FirebaseManager.isGuestEmail(userEmail)) {
            cancelarWorkerNotificacoes(context)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(requiresBatteryNotLow = true)
            .build()

        // Calcula o atraso para disparar exatamente às 09:00 locais
        val initialDelayMillis = calcularDelayAteAsNoveHoras()

        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }

    fun cancelarWorkerNotificacoes(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun calcularDelayAteAsNoveHoras(): Long {
        val agora = Calendar.getInstance()
        val alvo = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (agora.after(alvo)) {
            alvo.add(Calendar.DAY_OF_YEAR, 1)
        }

        return alvo.timeInMillis - agora.timeInMillis
    }
}
