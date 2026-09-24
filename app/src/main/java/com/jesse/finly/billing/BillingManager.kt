package com.jesse.finly.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.jesse.finly.utils.UserPreferencesManager

class BillingManager(
    private val context: Context,
    private val onPremiumStatusChanged: ((Boolean) -> Unit)? = null
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    companion object {
        const val PRODUCT_SUB_MONTHLY = "finly_sub_monthly"
        const val PRODUCT_SUB_ANNUAL = "finly_sub_annual"
        private const val TAG = "BillingManager"
    }

    init {
        inicializarBillingClient()
    }

    private fun inicializarBillingClient() {
        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingParams)
            .build()

        conectarAoGooglePlay()
    }

    fun conectarAoGooglePlay() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Conectado à Google Play Billing com sucesso!")
                    carregarProdutosEAssinaturas()
                    verificarAssinaturasAtivas()
                } else {
                    Log.e(TAG, "Erro ao conectar à Google Play Billing: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Serviço da Google Play Billing desconectado.")
            }
        })
    }

    private fun carregarProdutosEAssinaturas() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_SUB_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_SUB_ANNUAL)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val list = productDetailsResult.productDetailsList
                list?.forEach { details ->
                    productDetailsMap[details.productId] = details
                }
                Log.d(TAG, "Produtos da Google Play carregados: ${list?.size ?: 0}")
            }
        }
    }

    fun verificarAssinaturasAtivas() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasActiveSub = purchasesList.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                salvarStatusPremium(hasActiveSub)
            }
        }
    }

    fun iniciarCompraAssinatura(activity: Activity, productId: String): Boolean {
        val productDetails = productDetailsMap[productId] ?: return false
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient?.launchBillingFlow(activity, billingFlowParams)
        return result?.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                processarCompra(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "Utilizador cancelou a compra.")
        } else {
            Log.e(TAG, "Erro ao processar compra: ${billingResult.debugMessage}")
        }
    }

    private fun processarCompra(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        salvarStatusPremium(true)
                    }
                }
            } else {
                salvarStatusPremium(true)
            }
        }
    }

    private fun salvarStatusPremium(isPremium: Boolean) {
        val prefs = UserPreferencesManager(context)
        prefs.salvarIsPremium(isPremium, isPlayStorePurchase = true)
        onPremiumStatusChanged?.invoke(isPremium)
    }

    fun fechar() {
        billingClient?.endConnection()
    }
}
