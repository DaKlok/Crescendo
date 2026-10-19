package com.daklok.crescendo.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daklok.crescendo.R

/** Výška hornej lišty na všetkých obrazovkách (rovnaká ako v knižnici, aby prechody nepreskakovali). */
val ScreenTopBarHeight = 72.dp

/**
 * Horná lišta podobrazoviek (katalóg, cvičenie, setlisty): tonálne tlačidlo späť, výrazný nadpis
 * s voliteľným podnadpisom a akcie vpravo ako tonálne tlačidlá. Jeden riadok šetrí výšku na tablete.
 */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack,
    navigationDescription: String = stringResource(R.string.back),
    containerColor: Color = MaterialTheme.colorScheme.background,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val compact = isCompactWidth()
    TopAppBar(
        modifier = modifier,
        expandedHeight = ScreenTopBarHeight,
        title = {
            Column(Modifier.animateContentSize(Motion.snappy())) {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            TonalIconButton(
                icon = navigationIcon,
                contentDescription = navigationDescription,
                onClick = onBack,
                modifier = Modifier.padding(start = 12.dp, end = 10.dp),
            )
        },
        actions = {
            Row(
                Modifier.padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}
