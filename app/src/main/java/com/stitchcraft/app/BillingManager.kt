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
    companion object {
        const val PRO_PRODUCT_ID = ReleaseConfig.PRO_PRODUCT_ID
        private const val PRO_PURCHASE_OPTION_ID = "pro-lifetime"
    }

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady) {
            restore(showMessage = false)
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() = Unit

            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    restore(showMessage = false)
                }
            }
        })
    }

    /**
     * Query ProductDetails immediately before each purchase attempt.
     * Google recommends not caching ProductDetails because stale details can make
     * launchBillingFlow() fail. This is also important after a product has just been
     * activated or its purchase options/prices have changed in Play Console.
     */
    fun purchase(activity: Activity) {
        onMessage("Подключение к Google Play…")

        if (client.isReady) {
            queryAndLaunchPurchase(activity)
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                onMessage("Связь с Google Play прервана. Повторите попытку.")
            }

            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAndLaunchPurchase(activity)
                } else {
                    onMessage(billingError("Google Play Billing недоступен", result))
                }
            }
        })
    }

    private fun queryAndLaunchPurchase(activity: Activity) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onMessage(billingError("Не удалось получить StitchCraft Pro", result))
                return@queryProductDetailsAsync
            }

            val details = queryResult.productDetailsList
                .firstOrNull { it.productId == PRO_PRODUCT_ID }

            if (details == null) {
                onMessage(
                    "Google Play пока не возвращает товар $PRO_PRODUCT_ID. " +
                        "Проверьте, что приложение установлено из тестовой версии Google Play и используется аккаунт тестировщика."
                )
                return@queryProductDetailsAsync
            }

            val offers = details.oneTimePurchaseOfferDetailsList.orEmpty()
            val selectedOffer = offers.firstOrNull { it.purchaseOptionId == PRO_PURCHASE_OPTION_ID }
                ?: offers.firstOrNull()

           val offerToken = selectedOffer?.offerToken?.takeIf { it.isNotBlank() }

if (offerToken == null) {
    onMessage("Для StitchCraft Pro не найден активный способ покупки в Google Play.")
    return@queryProductDetailsAsync
}

val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
    .setProductDetails(details)
    .setOfferToken(offerToken)
                .build()

            activity.runOnUiThread {
                val launchResult = client.launchBillingFlow(
                    activity,
                    BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(listOf(productParams))
                        .build()
                )

                if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    onMessage(billingError("Не удалось открыть окно покупки Google Play", launchResult))
                }
            }
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
                if (showMessage) {
                    onMessage(if (owned.isNotEmpty()) "Покупка Pro восстановлена" else "Покупка Pro не найдена")
                }
            } else if (showMessage) {
                onMessage(billingError("Не удалось проверить покупку", result))
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
            BillingClient.BillingResponseCode.USER_CANCELED -> onMessage("Покупка отменена")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore(showMessage = true)
            else -> onMessage(billingError("Покупка не завершена", result))
        }
    }

    private fun billingError(prefix: String, result: BillingResult): String {
        val details = result.debugMessage.takeIf { it.isNotBlank() } ?: "код ${result.responseCode}"
        return "$prefix: $details"
    }

    fun stop() {
        if (client.isReady) client.endConnection()
    }
}
