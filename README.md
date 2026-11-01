# AikaPolku

Offline-first Android-sovellus arjen rytmittämiseen ja toiminnanohjaukseen:
**Mitä teen nyt? Mitä seuraavaksi? Paljonko aikaa on jäljellä? Miten voin ilmaista, mitä tarvitsen?**

Ydintoiminnot: analoginen ajastin, Ensin–Sitten, toimintokorttikirjasto omilla kuvilla ja äänillä,
päiväohjelmat osavaiheineen, apu- ja taukopyynnöt sekä PDF-tuloste.

Ei käyttäjätiliä, ei mainoksia, ei verkkoyhteyspakkoa.

## Rakenne

| Polku | Sisältö |
|---|---|
| `android/` | Android Studio -projekti (Kotlin, Jetpack Compose, Material 3) |
| `android/docs/` | Kehityssuunnitelma (AIKAPOLKU-AAC-ROADMAP-001, uusin versio) |
| `docs/` | Design system ja MVP-määrittely, kehitysloki, UX-tarkistuslista |

### Koodi (`android/app/src/main/java/fi/aikapolku/app/`)

| Paketti | Sisältö |
|---|---|
| *(juuri)* | `MainActivity`, `AikaPolkuApp` (navigointi), `HomeScreen`, ajastimen hälytysvastaanottimet |
| `data` | Tietomalli ja tallennus: päiväohjelmat (`ProgramModel`, `ProgramStore`, `ProgramTiming`), kortit (`CardStore`), valmiit pohjat, asetukset |
| `timer` | Taustalla toimiva ajastin (`BackgroundTimer`), ajastinnäkymät, kellotaulu, ajan esitystavat, valmis-ääni |
| `programs` | Päiväohjelmien lista ja suoritus (`ProgramsScreen`), tulostus, osavaihemuokkain |
| `programs.editor` | Päiväohjelman luonti: Valitse, Järjestä, Ajat, Tallenna |
| `programs.run` | Suoritusnäkymä, aikamerkki, apu- ja taukopainikkeet, viestinäkymä |
| `firstthen` | Ensin–Sitten, kulkutila ja lopetusnäkymät |
| `cards` | Valmiit kuvat (`CardArt`, `SubstepArt`), kuvien näyttö ja välimuisti (`CardPhoto`), korttikirjasto |
| `caregiver` | Ohjaajan PIN-lukitus ja sen näkymät |
| `voice` | Puhe, äänitys ja toisto |
| `ui` | Yhteiset värit (`AppColors`) ja teema |

Tallennusavaimet (SharedPreferences) ja JSON-muoto ovat ennallaan vanhoista versioista;
`P018AlarmReceiver`/`P018BootReceiver` säilyttävät nimensä, koska ajastetut hälytykset viittaavat niihin.

## Kääntäminen

Avaa `android/` Android Studiossa tai:

```
cd android
gradlew.bat assembleDebug
```

Sovellus ei ole lääkinnällinen laite eikä täysimittainen AAC-järjestelmä.

© Bon Vivo