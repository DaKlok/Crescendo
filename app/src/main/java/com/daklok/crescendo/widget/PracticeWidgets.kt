package com.daklok.crescendo.widget

/** Widget „Týždeň“: dnešné cvičenie, posledných 7 dní v stĺpčekoch a tlačidlo pokračovať. */
class WeekWidget : PracticeWidget() {
    override val style: ArtStyle get() = ArtStyle.WEEK
}

/** Widget „Krúžok“: len dnešné minúty a séria dní. */
class RingWidget : PracticeWidget() {
    override val style: ArtStyle get() = ArtStyle.RING
}

/** Widget „Krúžok a skladba“: dnešné minúty, séria a pod nimi tlačidlo pokračovať. */
class RingPieceWidget : PracticeWidget() {
    override val style: ArtStyle get() = ArtStyle.RING_PIECE
}
