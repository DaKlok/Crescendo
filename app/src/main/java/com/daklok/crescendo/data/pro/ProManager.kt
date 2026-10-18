package com.daklok.crescendo.data.pro

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
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
import com.daklok.crescendo.BuildConfig
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Funkcie len pre Pro (podľa toho sa v okne nákupu ukáže, čo používateľ práve chcel). */
enum class ProFeature(@StringRes val title: Int) {
    /** Viac ako [ProManager.FREE_PIECES] skladieb v knižnici. */
    LIBRARY(R.string.pro_f_library),
    VISUALIZER(R.string.pro_f_visualizer),
    /** Nahrávky (počúvanie) a nahrávanie – mikrofón aj MIDI klavír. */
    AUDIO(R.string.pro_f_audio),
    ANNOTATIONS(R.string.pro_f_annotations),
    EXPORT_PDF(R.string.pro_f_export),
    SYNC(R.string.pro_f_sync),
    SETLISTS(R.string.pro_f_setlists),
}

enum class ProPlan { MONTHLY, YEARLY, LIFETIME }

/** Ponuka z Google Play (cena v mene používateľa). */
data class ProOffer(
    val plan: ProPlan,
    val price: String,
    val details: ProductDetails,
    /** Pre predplatné: token základného plánu. */
    val offerToken: String?,
)

/**
 * Crescendo Pro cez Google Play Billing: doživotný nákup ([LIFETIME]) alebo predplatné
 * ([SUBSCRIPTION] so základnými plánmi „monthly“ a „yearly“). Stav sa pamätá v zariadení
 * (aby Pro fungovalo aj offline) a pri každom spustení sa overí v Google Play.
 * Stav sa nezálohuje – nákup patrí Google účtu a obnoví sa sám.
 */
class ProManager(private val app: CrescendoApp) : PurchasesUpdatedListener {

    companion object {
        const val LIFETIME = "crescendo_pro_lifetime"
        const val SUBSCRIPTION = "crescendo_pro"
        private const val PREFS = "pro_state"
        /** Koľko skladieb môže mať knižnica bez Pro. */
        const val FREE_PIECES = 10
    }

    private val prefs get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ladiaca verzia: Pro zapnuté ručne (na skúšanie bez nákupu). */
    var debugPro: Boolean
        get() = BuildConfig.DEBUG && prefs.getBoolean("debug_pro", false)
        set(v) {
            prefs.edit().putBoolean("debug_pro", v).apply()
            update()
        }

    private val purchased get() = prefs.getBoolean("pro", false)

    private val _isPro = MutableStateFlow(purchased || debugPro)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** Ktorý plán má používateľ (null = žiadny / neznámy). */
    private val _plan = MutableStateFlow(prefs.getString("plan", null)?.let { runCatching { ProPlan.valueOf(it) }.getOrNull() })
    val plan: StateFlow<ProPlan?> = _plan.asStateFlow()

    /** Žiadosť o zobrazenie okna Pro (funkcia, ktorú chcel použiť); null = zatvorené. */
    private val _paywall = MutableStateFlow<ProFeature?>(null)
    val paywall: StateFlow<ProFeature?> = _paywall.asStateFlow()

    private val _offers = MutableStateFlow<List<ProOffer>>(emptyList())
    val offers: StateFlow<List<ProOffer>> = _offers.asStateFlow()

    /** Hlásenie po nákupe / obnovení (spracuje okno Pro). */
    val message = MutableStateFlow<Int?>(null)

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private fun update() {
        _isPro.value = purchased || debugPro
    }

    private fun savePurchased(pro: Boolean, plan: ProPlan?) {
        prefs.edit().putBoolean("pro", pro).putString("plan", plan?.name).apply()
        _plan.value = plan
        update()
    }

    /** Počet skladieb v knižnici (pre limit vo verzii zadarmo). */
    val pieceCount: StateFlow<Int> = app.database.pieceDao().observeCount()
        .stateIn(app.appScope, SharingStarted.Eagerly, 0)

    /** Koľko skladieb sa ešte zmestí (bez Pro); pri Pro neobmedzene. */
    fun slotsLeft(): Int = if (_isPro.value) Int.MAX_VALUE else (FREE_PIECES - pieceCount.value).coerceAtLeast(0)

    /** Dá sa pridať [n] nových skladieb? Ak nie, otvorí okno Pro. */
    fun allowNewPieces(n: Int = 1): Boolean {
        if (n <= 0 || slotsLeft() >= n) return true
        _paywall.value = ProFeature.LIBRARY
        return false
    }

    /** Smie sa funkcia použiť? Ak nie, otvorí okno Pro a vráti false. */
    fun allow(feature: ProFeature): Boolean {
        if (_isPro.value) return true
        _paywall.value = feature
        return false
    }

    /** Okno Pro aj bez konkrétnej funkcie (z nastavení). */
    fun showPaywall(feature: ProFeature? = null) {
        _paywall.value = feature ?: ProFeature.LIBRARY
    }

    fun dismissPaywall() {
        _paywall.value = null
    }

    // ───────────── Google Play ─────────────

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            runCatching {
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }.onFailure { if (cont.isActive) cont.resume(false) }
        }
    }

    /** Overí nákupy v Google Play (pri štarte a „Obnoviť nákupy“). Vráti true, ak sa podarilo overiť. */
    suspend fun refresh(): Boolean {
        if (!connect()) return false
        val inapp = queryPurchases(BillingClient.ProductType.INAPP) ?: return false
        val subs = queryPurchases(BillingClient.ProductType.SUBS) ?: return false
        val all = (inapp + subs).filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        all.forEach { acknowledge(it) }
        val plan = when {
            all.any { LIFETIME in it.products } -> ProPlan.LIFETIME
            all.any { SUBSCRIPTION in it.products } -> _plan.value?.takeIf { it != ProPlan.LIFETIME } ?: ProPlan.MONTHLY
            else -> null
        }
        savePurchased(plan != null, plan)
        return true
    }

    private suspend fun queryPurchases(type: String): List<Purchase>? = suspendCancellableCoroutine { cont ->
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, list ->
            if (cont.isActive) cont.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) list else null)
        }
    }

    private fun acknowledge(p: Purchase) {
        if (p.isAcknowledged || p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { }
    }

    /** Načíta ceny z Google Play. */
    suspend fun loadOffers() {
        if (!connect()) return
        val lifetime = queryDetails(LIFETIME, BillingClient.ProductType.INAPP)
        val sub = queryDetails(SUBSCRIPTION, BillingClient.ProductType.SUBS)
        val out = ArrayList<ProOffer>()
        sub?.subscriptionOfferDetails
            ?.filter { it.offerId == null } // základné plány (bez akcií)
            ?.forEach { o ->
                val phase = o.pricingPhases.pricingPhaseList.lastOrNull() ?: return@forEach
                val plan = when {
                    o.basePlanId.contains("year", true) || phase.billingPeriod == "P1Y" -> ProPlan.YEARLY
                    else -> ProPlan.MONTHLY
                }
                out += ProOffer(plan, phase.formattedPrice, sub, o.offerToken)
            }
        lifetime?.oneTimePurchaseOfferDetails?.let { out += ProOffer(ProPlan.LIFETIME, it.formattedPrice, lifetime, null) }
        _offers.value = out.sortedBy { it.plan.ordinal }
    }

    private suspend fun queryDetails(id: String, type: String): ProductDetails? = suspendCancellableCoroutine { cont ->
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()))
            .build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (cont.isActive) cont.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) list.firstOrNull() else null)
        }
    }

    private var pendingPlan: ProPlan? = null

    /** Spustí nákup v Google Play. */
    fun buy(activity: Activity, offer: ProOffer): Boolean {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details)
        offer.offerToken?.let { product.setOfferToken(it) }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product.build())).build()
        pendingPlan = offer.plan
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val bought = purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                bought.forEach { acknowledge(it) }
                if (bought.isNotEmpty()) {
                    val plan = if (bought.any { LIFETIME in it.products }) ProPlan.LIFETIME else pendingPlan ?: ProPlan.MONTHLY
                    savePurchased(true, plan)
                    message.value = R.string.pro_thanks
                    _paywall.value = null
                } else if (purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                    message.value = R.string.pro_pending
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                savePurchased(true, pendingPlan)
                message.value = R.string.pro_restored
                _paywall.value = null
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> message.value = R.string.pro_error
        }
        pendingPlan = null
    }
}
