package com.daklok.crescendo.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R
import com.daklok.crescendo.ui.common.AppMark
import com.daklok.crescendo.ui.common.IconBadge
import com.daklok.crescendo.ui.common.Motion
import kotlinx.coroutines.launch

private class OnboardingPage(val icon: ImageVector, val title: Int, val text: Int)

private val PAGES = listOf(
    OnboardingPage(Icons.Outlined.LibraryMusic, R.string.ob_library_title, R.string.ob_library_text),
    OnboardingPage(Icons.Outlined.SwapHoriz, R.string.ob_turn_title, R.string.ob_turn_text),
    OnboardingPage(Icons.Outlined.Draw, R.string.ob_notes_title, R.string.ob_notes_text),
    OnboardingPage(Icons.Outlined.CloudSync, R.string.ob_backup_title, R.string.ob_backup_text),
)

/** Úvodný sprievodca pri prvom spustení – štyri krátke strany, dá sa preskočiť. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AppMark(32.dp)
                Spacer(Modifier.width(12.dp))
                Text("Crescendo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.ob_skip)) }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
                val page = PAGES[i]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .padding(horizontal = 8.dp),
                    ) {
                        IconBadge(page.icon, active = true, size = 104.dp)
                        Spacer(Modifier.height(32.dp))
                        Text(
                            stringResource(page.title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(page.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    PAGES.indices.forEach { i ->
                        val selected = i == pager.currentPage
                        val w by animateDpAsState(if (selected) 28.dp else 10.dp, Motion.bouncy(), label = "dot")
                        val c by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            label = "dotColor",
                        )
                        Box(
                            Modifier
                                .size(width = w, height = 10.dp)
                                .background(c, CircleShape),
                        )
                    }
                }
                Button(
                    onClick = {
                        if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(stringResource(if (last) R.string.ob_start else R.string.ob_next))
                    if (!last) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
