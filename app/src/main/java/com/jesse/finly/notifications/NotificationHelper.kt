package com.jesse.finly.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jesse.finly.database.FirebaseManager
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

        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            12, TimeUnit.HOURS,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelarWorkerNotificacoes(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
