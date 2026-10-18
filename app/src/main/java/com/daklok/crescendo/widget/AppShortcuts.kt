package com.daklok.crescendo.widget

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.daklok.crescendo.R
import com.daklok.crescendo.data.PieceEntity

/**
 * Skratky aplikácie (dlhé podržanie ikony): denník cvičenia a naposledy otvorené skladby.
 */
object AppShortcuts {
    private const val PRACTICE_ID = "practice"

    /** Po otvorení skladby: pridá ju medzi skratky (najnovšia hore) a obnoví widget. */
    fun pieceOpened(context: Context, piece: PieceEntity) {
        runCatching {
            val shortcut = ShortcutInfoCompat.Builder(context, "piece_${piece.id}")
                .setShortLabel(piece.title.take(24).ifBlank { "♪" })
                .setLongLabel(piece.title.take(48).ifBlank { "♪" })
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_piece))
                .setIntent(PracticeWidget.openPieceIntent(context, piece.id))
                .setRank(1)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            ensurePractice(context)
        }
        PracticeWidget.update(context)
    }

    /** Skratka na denník cvičenia (vždy navrchu). */
    fun ensurePractice(context: Context) {
        runCatching {
            val shortcut = ShortcutInfoCompat.Builder(context, PRACTICE_ID)
                .setShortLabel(context.getString(R.string.practice))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_practice))
                .setIntent(PracticeWidget.openPracticeIntent(context))
                .setRank(0)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }

    /** Zmazaná skladba – zmizne aj zo skratiek. */
    fun pieceDeleted(context: Context, pieceId: Long) {
        runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, listOf("piece_$pieceId")) }
        PracticeWidget.update(context)
    }
}
