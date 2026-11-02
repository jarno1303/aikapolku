# AikaPolku – UI/UX-tarkistuslista

- [ ] Näkymän tarkoitus ymmärretään heti
- [ ] Yksi selkeä päätoiminto
- [ ] Painikkeissa on selkeät nimet
- [ ] Ei tarpeettomia valintoja
- [ ] Kosketusalue vähintään 48 dp
- [ ] Teksti skaalautuu
- [ ] Kontrasti riittää
- [ ] TalkBack toimii
- [ ] Takaisin toimii oikein
- [ ] Virheelliset painallukset voi perua
- [ ] Ajastin toimii luotettavasti
- [ ] Äänet voi poistaa
- [ ] Tallennetut tiedot säilyvät
- [ ] Puhelin- ja tablettinäkymät tarkistettu
- [ ] Ei pakollista rekisteröitymistä
- [ ] Ei mainoksia
- [ ] Sovellus toimii offline-tilassa
- [ ] Ensikertalainen osaa käyttää ilman ohjeita

## Yhtenäinen käyttöliittymä (2.11.2026)
Kaikki näkymät noudattavat näitä. Yhteiset osat: `ui/Components.kt`, kuvakkeet `ui/AppIcons.kt` + `Icons.Rounded`.
- **Päätoiminto:** yksi täytetty `PrimaryButton` näkymää kohden.
- **Muut toiminnot:** `SecondaryButton` (kuvakkeella) tai `ChoiceRow`. Ei tekstilinkkilistoja.
- **2–3 vaihtoehtoa:** `SegmentedChoice`.
- **Harvinaiset rivikohtaiset toiminnot** (Muokkaa, Kopioi, Poista): `OverflowMenu` (⋮).
- **Kuvakkeet:** ei emojeja eikä merkkejä (📷 ✏ 🔒 ⚙ ▶ ● ↺ …) käyttöliittymässä.
- **Valintaikkunat:** otsikko, sisältö, alareunassa Peruuta + päätoiminto.
- **Ohjetekstit:** enintään yksi lyhyt rivi; pidemmät `InfoButton`-kuvakkeen taakse.
- **Takaisin:** vain yläreunasta.
