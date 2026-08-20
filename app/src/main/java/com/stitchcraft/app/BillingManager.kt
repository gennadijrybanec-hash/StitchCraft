package com.stitchcraft.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

/** Google Play Billing integration for the non-consumable lifetime Pro entitlement. */
class BillingManager(
    context: Context,
    private val onProChanged: (Boolean) -> Unit,
    private val onMessage: (String) -> Unit = {}
) : PurchasesUpdatedListener {
    companion object { const val PRO_PRODUCT_ID = ReleaseConfig.PRO_PRODUCT_ID }

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    private var productDetails: ProductDetails? = null

    fun start() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() = Unit
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    restore(showMessage = false)
                }
            }
        })
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            productDetails = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                queryResult.productDetailsList.firstOrNull { it.productId == PRO_PRODUCT_ID }
            } else null
        }
    }

    fun purchase(activity: Activity) {
        if (!client.isReady) {
            onMessage("Google Play пока недоступен. Повторите попытку.")
            start()
            return
        }
        val details = productDetails
        if (details == null) {
            onMessage("Покупка Pro станет доступна после настройки товара в Google Play.")
            queryProduct()
            return
        }
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
            ?.takeIf { it.isNotBlank() }
            ?.let(builder::setOfferToken)
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(builder.build()))
                .build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage("Не удалось открыть Google Play: ${result.debugMessage}")
        }
    }

    fun restore(showMessage: Boolean = true) {
        if (!client.isReady) {
            if (showMessage) onMessage("Google Play пока недоступен. Повторите попытку.")
            start()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.filter(::isOwnedPro)
                onProChanged(owned.isNotEmpty())
                owned.filter { !it.isAcknowledged }.forEach(::acknowledge)
                if (showMessage) onMessage(if (owned.isNotEmpty()) "Покупка Pro восстановлена" else "Покупка Pro не найдена")
            } else if (showMessage) {
                onMessage("Не удалось проверить покупку: ${result.debugMessage}")
            }
        }
    }

    private fun isOwnedPro(purchase: Purchase): Boolean =
        purchase.products.contains(PRO_PRODUCT_ID) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED

    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onProChanged(true)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val owned = purchases.orEmpty().filter(::isOwnedPro)
                owned.forEach { purchase ->
                    onProChanged(true)
                    acknowledge(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore(showMessage = true)
            else -> onMessage("Покупка не завершена: ${result.debugMessage}")
        }
    }

    fun stop() {
        if (client.isReady) client.endConnection()
    }
}
