package com.stitchcraft.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

class BillingManager(
    context: Context,
    private val onProChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {
    companion object { const val PRO_PRODUCT_ID = ReleaseConfig.PRO_PRODUCT_ID }
    private val client = BillingClient.newBuilder(context).setListener(this).enablePendingPurchases().build()
    private var productDetails: ProductDetails? = null

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() = Unit
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct(); restore()
                }
            }
        })
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) productDetails = details.firstOrNull()
        }
    }

    fun purchase(activity: Activity) {
        val details = productDetails ?: return
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
    }

    fun restore() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onProChanged(purchases.any { it.products.contains(PRO_PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED })
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }.forEach(::acknowledge)
            }
        }
    }

    private fun acknowledge(p: Purchase) {
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases.orEmpty().forEach { if (it.purchaseState == Purchase.PurchaseState.PURCHASED) acknowledge(it) }
            onProChanged(purchases.orEmpty().any { it.products.contains(PRO_PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED })
        }
    }
    fun stop() {
        if (client.isReady) client.endConnection()
    }

}
