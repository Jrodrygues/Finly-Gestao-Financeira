package com.jesse.finly

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Obtém o tema manual guardado nas preferências
        val sharedPref = getSharedPreferences("FinlyAppPrefs", MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("DARK_MODE", false)
        val mode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        // Define o modo noturno globalmente
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
