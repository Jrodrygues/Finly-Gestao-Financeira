package com.jesse.finly

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.jesse.finly.notifications.NotificationHelper

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val sharedPref = getSharedPreferences("FinlyAppPrefs", MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("DARK_MODE", false)
        val mode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)

        val notificationsEnabled = sharedPref.getBoolean("NOTIFICATIONS", false)
        if (notificationsEnabled) {
            NotificationHelper.agendarWorkerNotificacoes(this)
        }
    }
}
