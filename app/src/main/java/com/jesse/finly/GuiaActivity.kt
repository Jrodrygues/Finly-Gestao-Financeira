package com.jesse.finly

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.jesse.finly.databinding.ActivityGuiaBinding

class GuiaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGuiaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityGuiaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackGuia.setOnClickListener {
            finish()
        }

        // Ajustar Insets para o cabeçalho respeitar a barra de status
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Empurra o cabeçalho para baixo da barra de status
            binding.viewHeaderGuia.updatePadding(top = systemBars.top)
            
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }
        
        // Garantir transparência e visibilidade dos ícones da barra de navegação
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = 
            androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES

    }
}
