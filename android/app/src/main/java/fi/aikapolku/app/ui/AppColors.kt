package fi.aikapolku.app.ui

import androidx.compose.ui.graphics.Color

/**
 * Sovelluksen yhteiset värit. Aiemmin samat arvot oli määritelty erikseen
 * yhdeksään tiedostoon; näkymäkohtaiset poikkeavat sävyt (esim. kellotaulun
 * punainen) on jätetty omiin tiedostoihinsa.
 */
internal object AppColors {
    /** Pääväri: painikkeet, korostukset, valmis. */
    val Teal = Color(0xFF0F766E)
    /** Valitun kortin ja nykyisen vaiheen tausta. */
    val TealSoft = Color(0xFFE6F4F1)
    /** Sovelluksen tausta. */
    val Background = Color(0xFFF8FAFC)
    /** Leipäteksti. */
    val Ink = Color(0xFF1F2937)
    /** Toissijainen teksti. */
    val Muted = Color(0xFF475569)
    /** Reunat ja ääriviivat. */
    val Border = Color(0xFFCBD5E1)
    /** Ajan kuluminen / ajastimen rengas. */
    val Coral = Color(0xFFC2413A)

    /** Muutos: lämmin oranssi, erottuu tavallisten tapahtumien turkoosista. */
    val Change = Color(0xFFC2410C)
    val ChangeFill = Color(0xFFEA580C)
    val ChangeSoft = Color(0xFFFFEDD5)
}
