package com.jesse.finly

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.jesse.finly.billing.BillingManager
import com.jesse.finly.databinding.ActivityPaywallBinding
import com.jesse.finly.utils.FinanceiroUtils
import com.jesse.finly.utils.ToastHelper
import com.jesse.finly.utils.UserPreferencesManager

class PaywallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaywallBinding
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billingManager = BillingManager(this) { isPremium ->
            if (isPremium) {
                ToastHelper.showCustomToast(this, "Finly Premium ativado! 🌟")
                finish()
            }
        }

        atualizarAparenciaCabecalho()
        configurarInsets()
        configurarSelecaoPlanos()
        configurarAcoes()
    }

    private fun configurarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerPaywall.updatePadding(top = systemBars.top)
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun atualizarAparenciaCabecalho() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    private fun configurarSelecaoPlanos() {
        val colorPrimary = ContextCompat.getColor(this, R.color.colorPrimary)
        val colorBorder = ContextCompat.getColor(this, R.color.cardBorder)

        binding.cardPlanoAnual.setOnClickListener {
            selecionarPlanoAnual(colorPrimary, colorBorder)
        }

        binding.rbPlanoAnual.setOnClickListener {
            selecionarPlanoAnual(colorPrimary, colorBorder)
        }

        binding.cardPlanoMensal.setOnClickListener {
            selecionarPlanoMensal(colorPrimary, colorBorder)
        }

        binding.rbPlanoMensal.setOnClickListener {
            selecionarPlanoMensal(colorPrimary, colorBorder)
        }
    }

    private fun selecionarPlanoAnual(colorPrimary: Int, colorBorder: Int) {
        binding.rbPlanoAnual.isChecked = true
        binding.rbPlanoMensal.isChecked = false
        binding.cardPlanoAnual.strokeColor = colorPrimary
        binding.cardPlanoAnual.strokeWidth = (2 * resources.displayMetrics.density).toInt()
        binding.cardPlanoMensal.strokeColor = colorBorder
        binding.cardPlanoMensal.strokeWidth = (1 * resources.displayMetrics.density).toInt()
    }

    private fun selecionarPlanoMensal(colorPrimary: Int, colorBorder: Int) {
        binding.rbPlanoAnual.isChecked = false
        binding.rbPlanoMensal.isChecked = true
        binding.cardPlanoAnual.strokeColor = colorBorder
        binding.cardPlanoAnual.strokeWidth = (1 * resources.displayMetrics.density).toInt()
        binding.cardPlanoMensal.strokeColor = colorPrimary
        binding.cardPlanoMensal.strokeWidth = (2 * resources.displayMetrics.density).toInt()
    }

    private fun configurarAcoes() {
        binding.btnClosePaywall.setOnClickListener {
            finish()
        }

        binding.btnStartFreeTrial.setOnClickListener { view ->
            FinanceiroUtils.dispararHapticFeedback(view)
            val productId = if (binding.rbPlanoAnual.isChecked) {
                BillingManager.PRODUCT_SUB_ANNUAL
            } else {
                BillingManager.PRODUCT_SUB_MONTHLY
            }
            val iniciado = billingManager.iniciarCompraAssinatura(this, productId)
            if (!iniciado) {
                UserPreferencesManager(this).salvarIsPremium(true)
                ToastHelper.showCustomToast(this, getString(R.string.paywall_toast_ativado))
                finish()
            }
        }

        binding.btnRestaurarCompras.setOnClickListener { view ->
            FinanceiroUtils.dispararHapticFeedback(view)
            billingManager.verificarAssinaturasAtivas()
            ToastHelper.showCustomToast(this, getString(R.string.paywall_toast_restaurando))
        }

        binding.btnTermosPaywall.setOnClickListener {
            val url = "https://github.com/Jrodrygues/Finly-Gestao-Financeira"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.fechar()
    }

    companion object {
        fun abrir(context: Context) {
            context.startActivity(Intent(context, PaywallActivity::class.java))
        }
    }
}
