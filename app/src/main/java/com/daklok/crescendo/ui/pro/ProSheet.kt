package com.daklok.crescendo.ui.pro

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ripple
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.daklok.crescendo.ui.common.ExpressiveShapes
import com.daklok.crescendo.ui.common.decoShape
import androidx.compose.ui.unit.DpOffset
import com.daklok.crescendo.ui.common.Motion
import com.daklok.crescendo.ui.common.pressScale
import com.daklok.crescendo.ui.common.rememberHaptic
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daklok.crescendo.BuildConfig
import com.daklok.crescendo.CrescendoApp
import com.daklok.crescendo.R
import com.daklok.crescendo.data.pro.ProFeature
import com.daklok.crescendo.data.pro.ProOffer
import com.daklok.crescendo.data.pro.ProPlan
import kotlinx.coroutines.launch

/**
 * Okno Crescendo Pro – zobrazí sa, keď používateľ siahne po funkcii z Pro (alebo z nastavení).
 * Umiestnené raz v koreni aplikácie; otvára ho ProManager.allow().
 */
@Composable
fun ProPaywallHost() {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val pro = app.pro
    val request by pro.paywall.collectAsStateWithLifecycle()
    val message by pro.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            pro.message.value = null
        }
    }
    val feature = request ?: return
    ProSheet(feature = feature, onDismiss = { pro.dismissPaywall() })
}

/** Riadok porovnania: čo je zadarmo a čo v Pro. */
private class Row3(val feature: ProFeature?, val label: Int, val free: Int?, val pro: Int?)

private val COMPARISON = listOf(
    Row3(ProFeature.LIBRARY, R.string.pro_row_library, R.string.pro_row_library_free, R.string.pro_row_unlimited),
    Row3(ProFeature.ANNOTATIONS, R.string.pro_row_annotations, R.string.pro_row_annotations_free, R.string.pro_row_annotations_pro),
    Row3(ProFeature.VISUALIZER, R.string.pro_row_visualizer, null, null),
    Row3(ProFeature.AUDIO, R.string.pro_row_audio, R.string.pro_row_audio_free, null),
    Row3(ProFeature.EXPORT_PDF, R.string.pro_row_export, null, null),
    Row3(ProFeature.SYNC, R.string.pro_row_sync, R.string.pro_row_sync_free, null),
    Row3(ProFeature.SETLISTS, R.string.pro_row_setlists, null, null),
    // spoločné pre obe: -1 = ✓ aj zadarmo
    Row3(null, R.string.pro_row_basics, -1, null),
)

@Composable
private fun ProSheet(feature: ProFeature, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CrescendoApp
    val pro = app.pro
    val scope = rememberCoroutineScope()
    val isPro by pro.isPro.collectAsStateWithLifecycle()
    val plan by pro.plan.collectAsStateWithLifecycle()
    val offers by pro.offers.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(ProPlan.YEARLY) }
    var restoring by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pro.loadOffers()
        loading = false
    }
    LaunchedEffect(offers) {
        val available = offers.map { it.plan }
        if (available.isNotEmpty() && selected !in available) selected = available.first()
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val content = Modifier.fillMaxWidth().widthIn(max = 640.dp)

            Hero(isPro = isPro, feature = feature, modifier = content)
            Spacer(Modifier.height(16.dp))

            Comparison(highlight = if (isPro) null else feature, modifier = content)
            Spacer(Modifier.height(20.dp))

            if (isPro) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = content,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(
                                when (plan) {
                                    ProPlan.LIFETIME -> R.string.pro_plan_lifetime_owned
                                    null -> R.string.pro_active
                                    else -> R.string.pro_plan_sub_owned
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (plan != null && plan != ProPlan.LIFETIME) {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }) { Text(stringResource(R.string.pro_manage)) }
                        }
                    }
                }
            } else {
                // ── plány ──
                if (offers.isEmpty()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = content) {
                        Text(
                            stringResource(if (loading) R.string.pro_loading else R.string.pro_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                } else {
                    if (wide) {
                        Row(content, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            offers.forEach { o -> PlanCard(o, selected == o.plan, Modifier.weight(1f)) { selected = o.plan } }
                        }
                    } else {
                        Column(content, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            offers.forEach { o -> PlanCard(o, selected == o.plan, Modifier.fillMaxWidth()) { selected = o.plan } }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val chosen = offers.firstOrNull { it.plan == selected }
                    GradientButton(
                        text = stringResource(if (selected == ProPlan.LIFETIME) R.string.pro_buy_lifetime else R.string.pro_subscribe) +
                            (chosen?.let { " · " + it.price } ?: ""),
                        modifier = content,
                    ) {
                        val activity = context.findActivity() ?: return@GradientButton
                        chosen?.let { pro.buy(activity, it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(if (selected == ProPlan.LIFETIME) R.string.pro_note_lifetime else R.string.pro_note_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                TextButton(
                    enabled = !restoring,
                    onClick = {
                        restoring = true
                        scope.launch {
                            val ok = pro.refresh()
                            restoring = false
                            val msg = when {
                                !ok -> R.string.pro_error
                                pro.isPro.value -> R.string.pro_restored
                                else -> R.string.pro_nothing_to_restore
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text(stringResource(R.string.pro_restore)) }
            }

            if (BuildConfig.DEBUG) {
                // len v ladiacej verzii – na vyskúšanie Pro bez nákupu
                var debug by remember { mutableStateOf(pro.debugPro) }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pro_debug), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    com.daklok.crescendo.ui.common.ExpressiveSwitch(checked = debug, onCheckedChange = { debug = it; pro.debugPro = it })
                }
            }
        }
    }
}

/** Farebná hlavička: hviezda, názov a čo používateľ práve chcel. */
@Composable
private fun Hero(isPro: Boolean, feature: ProFeature, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(proBrush())
            // jemné výrazné tvary v pozadí (veľký vlnitý kruh a štvorlístok)
            .decoShape(
                shape = ExpressiveShapes.Cookie12,
                color = Color.White.copy(alpha = 0.08f),
                diameter = 210.dp,
                alignment = Alignment.TopEnd,
                offset = DpOffset(70.dp, (-60).dp),
                turn = { 14f },
            )
            .decoShape(
                shape = ExpressiveShapes.Clover,
                color = Color.White.copy(alpha = 0.06f),
                diameter = 110.dp,
                alignment = Alignment.BottomEnd,
                offset = DpOffset(24.dp, 40.dp),
                turn = { -10f },
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Box(
                Modifier
                    .size(60.dp)
                    .background(Color.White.copy(alpha = 0.22f), ExpressiveShapes.Cookie12),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Crescendo Pro", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isPro) stringResource(R.string.pro_have) else stringResource(R.string.pro_hero),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
            if (!isPro) {
                Spacer(Modifier.height(14.dp))
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                    Text(
                        stringResource(R.string.pro_needed, stringResource(feature.title)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/** Tabuľka Zadarmo / Pro; riadok s funkciou, ktorú používateľ chcel, je zvýraznený. */
@Composable
private fun Comparison(highlight: ProFeature?, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1.6f))
                Text(
                    stringResource(R.string.pro_free),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ProBadge() }
            }
            COMPARISON.forEach { r ->
                val hl = r.feature != null && r.feature == highlight
                val bg by animateColorAsState(
                    if (hl) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "rowBg",
                )
                Row(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(r.label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (hl) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1.6f),
                    )
                    Cell(r.free, isPro = false, modifier = Modifier.weight(1f))
                    Cell(r.pro, isPro = true, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Bunka: null = ✓ v Pro / — zadarmo, -1 = ✓, inak text. */
@Composable
private fun Cell(text: Int?, isPro: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            text == -1 || (text == null && isPro) -> Box(
                Modifier
                    .size(26.dp)
                    .background(
                        if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        if (isPro) ExpressiveShapes.Cookie else CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Check, null, Modifier.size(16.dp),
                    tint = if (isPro) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            text == null -> Icon(Icons.Outlined.Remove, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
            else -> Text(
                stringResource(text),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isPro) FontWeight.Bold else FontWeight.Normal,
                color = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Plán: vybraný má výraznejší rám, väčšie zaoblenie a fajku vo vlnitom kruhu. */
@Composable
private fun PlanCard(offer: ProOffer, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(
        if (selected) scheme.primary else scheme.outlineVariant,
        label = "planBorder",
    )
    val scale by animateFloatAsState(if (selected) 1f else 0.98f, label = "planScale")
    val corner by animateDpAsState(if (selected) 32.dp else 24.dp, Motion.bouncy(), label = "planCorner")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = modifier.scale(scale),
    ) {
        Box {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp).heightIn(min = 64.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            when (offer.plan) {
                                ProPlan.MONTHLY -> R.string.pro_plan_monthly
                                ProPlan.YEARLY -> R.string.pro_plan_yearly
                                ProPlan.LIFETIME -> R.string.pro_plan_lifetime
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (offer.plan == ProPlan.YEARLY) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.background(proBrush(), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(stringResource(R.string.pro_best), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    // miesto pre fajku vpravo hore
                    Spacer(Modifier.width(28.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(offer.price, style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(
                        when (offer.plan) {
                            ProPlan.MONTHLY -> R.string.pro_plan_monthly_desc
                            ProPlan.YEARLY -> R.string.pro_plan_yearly_desc
                            ProPlan.LIFETIME -> R.string.pro_plan_lifetime_desc
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = androidx.compose.animation.scaleIn(Motion.bouncy()) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(scheme.primary, ExpressiveShapes.Cookie),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Hlavné tlačidlo nákupu vo farbách Pro. */
@Composable
private fun GradientButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(60.dp)
            .pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(50))
            .background(proBrush())
            .clickable(interactionSource = interaction, indication = ripple(color = Color.White)) {
                haptic(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
