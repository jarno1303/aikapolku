# HANDOFF – AikaPolku (päivitetty 24.11.2026)

Lue tämä ensin. Sitten `docs/TUTKIELMA_OPTIMAALINEN_SOVELLUS.md` (v1.8), `docs/YKSIKKOMALLI_SUUNNITELMA.md`, `docs/PAIKAN_LAITE_SUUNNITELMA.md` ja `docs/TIETOSUOJA_PUUTELISTA.md` (päälähde: periaatteet, tunnisteet A1–C7, G1–G6, X1–X4, Päätökset-loki).

## Rooli ja työtapa

- Claude toimii autismin kirjon, toiminnanohjauksen ja UX:n asiantuntijana: kriittinen, näyttöön perustuva, ei kehittäjää miellyttävä.
- Suomeksi, selkeää yleiskieltä. Yksi kysymys kerrallaan.
- Sovitaan ensin, sitten tehdään. Kerro malli ja taso ennen työtä (Opus Medium keskusteluun, Opus High rakenteeseen ja käyttöliittymään).
- Varmuuskopiohaara ennen muutoksia: `varmuus/ennen-<kuvaus>-<vvvvkkpp>`, pushattuna.
- Skripti tekee asetusvaiheet, ei JarnoL käsin.
- Iso koodimuutos: erillinen tarkastaja-agentti käy muutoksen läpi ennen käännöstä.

## Kääntäminen ja testaus

Pilvessä ei ole Android SDK:ta. JarnoL ajaa PC:llä:

```powershell
powershell -ExecutionPolicy Bypass -File "$env:USERPROFILE\AndroidStudioProjects\AikaPolku\tarkistukset\aja-aikapolku.ps1" -branch <haara> [-testit "*Nimi*"] [-eiTesteja] [-taysi]
```

- `-laitetesti` = puhelintestit kaapelilla, `-kuvat` = kuvakaappaukset oppaaseen ja esitykseen. Kahden puhelimen testi: `tarkistukset\kahden-puhelimen-testi.ps1 -vaihe asenna` / `-vaihe lokit` (source `tools/pc/`).
- `-taysi` = myös lint. Tulos: `AikaPolku\tarkistukset\<aikaleima>\tulos.txt`, testien XML:t `testit\`, loki `aikapolku.log`.
- APK: `Desktop\AikaPolku-APK\UUSIN-AikaPolku.apk`. Asennus: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r <apk>`.
- Claude lukee tulokset liitetyn kansion kautta (`$HOME/mnt/AikaPolku/tarkistukset/...`). Tarkista aina uusimman kansion aikaleima: JarnoL liittää joskus vanhan tulosteen.
- Git-pushit tehdään pilvikloonista. PC:n repo synkataan: `git fetch; git reset origin/main` (PC:n git ei voi poistaa lukkotiedostoja).
- Skriptin source: `tools/pc/aja-aikapolku.ps1` (kopio PC:n `tarkistukset`-kansiossa). Pieni korjattava: konsolin merkistö (ä näkyy "├ñ").

## Haarat

Kaikki työ on yhdessä ketjussa; uusin haara sisältää kaikki aiemmat (muutoskortti, laatuportti, siirtymät, lisäaika, testit).

| Haara | Sisältö | Tila |
|---|---|---|
| `claude/aikapolku-yksikkomalli-2026-11-15` | **Uusin.** A1–A4, A2, yksikkömalli V1–V4, valinta/odotus/oma tila, yksikön yhteys, palautemuutokset, vaakatila ja tabletit, paikan laite (A5), tietosuoja T1–T6, ohjatun aloituksen laajennus, värikoodaus, kello, widget, materiaalit; 24.11. tietoturvakorjaukset ja dokumenttien ajantasaistus | Täysi tarkistus OK 24.11. (3530241): käännös, kaikki yksikkötestit, lint 0 virhettä. **Puhelintestit kuittaamatta** |
| `main` | Vain dokumentit 15.11. asti | Yhdistetään vasta puhelintestien jälkeen |
| `varmuus/ennen-ajantasaistus-20261124` | Varmuuskopio ennen 24.11. muutoksia (13ba513) | – |
| `varmuus/*`, `varmuuskopio/*`, `backup/*` | Aiemmat varmuuskopiot | Voi siivota mainiin yhdistämisen jälkeen |

## Avoimet puhelintestit (mitään ei ole kuitattu)

Valmistelu: uusin APK (`aja-aikapolku.ps1 -branch claude/aikapolku-yksikkomalli-2026-11-15`), lukitus päälle, asiakkaan rooli. Automaattiset puhelintestit: `-laitetesti` (EnsinSittenLisaaika, PaivaohjelmaLisaaika, Muutoskortti, ProgramDay, Ennakkoaani, KuvaJaKello, Yhteys).

1. **Muutoskortti A3–A4 (1–15)**, kohdat 11–14 vaativat illan ja seuraavan päivän. "Luo esittelypäivä" + omat huomisen tapahtumat.
2. **Siirtymät A1 (1–15):** ennakkovaroitus 2 min; ajastin (varoitus, tauko pysäyttää, Jatka, vaakatila), tapahtumakortti, päiväohjelma, asetus Ei.
3. **Lisäaika A2 (1–12):** Asetukset → Aika: ennakkovaroitus 2 min, lisäaika 2 min, kerran. Painike ilmestyy loppuvaiheessa, toinen painallus = viesti ohjaajalle ja aika jatkuu, asetus kahdesti, vaakatila, päiväohjelma mahtuu ilman vieritystä, Tarvitsen tauon ilman PIN-koodia.
4. **Yksikkömalli:** ohjattu aloitus (yksikkö, alayksiköt, asukkaat, ruokailut, toiminnat, yhteiset asetukset, perusrutiinit), Tänään yksikössä, sijaintisääntö (asukas näkee vain oman paikkansa), loma ja poikkeuspäivät, kopioi muille.
5. **Asukkaan oma ääni:** valinta kahdesta, Odotetaan, oma tila, En ymmärrä, Kerro ohjaajalle + viestijono (verkko pois → "Lähtee, kun yhteys palaa" → verkko takaisin).
6. **Värikoodaus, kello korteissa, widget:** luokat ja merkit, asukaskohtainen pois, kello Nyt-kortissa, pieni ja leveä widget (päivittyy tapahtuman rajalla, napautus ei käynnistä alusta).
7. **Tietosuoja:** paikan laite ilman näytön lukitusta → ohjaajatila estetty; lukitusnäytön ilmoitus ilman nimeä; vienti vaatii salasanan; ohjaajan näkymä piilossa sovellusvaihtajassa; automaattinen varmuuskopio kansioon ja palautus; Poista kaikki tiedot.
8. **Vaakatila ja tabletti:** asiakkaan näkymät mahtuvat ilman vieritystä (paitsi koko päivän aikajana).

### Kahden laitteen siirtotesti (A5)

**Automaattinen (24.11.2026):** molemmat laitteet USB:llä samassa Wi-Fissä, sitten
`aja-aikapolku.ps1 -branch <haara> -kaksiLaitetta`. Kääntää sovelluksen ja testipaketin, tunnistaa tabletin paikan laitteeksi (tai `-paikka <sarjanumero>`), ajaa `KaksiLaitettaPaikkaDeviceTest`- ja `KaksiLaitettaAsukasDeviceTest`-testit yhtä aikaa ja kerää tulokset: `tarkistukset\<aika>\kaksi-laitetta.txt` (yhteenveto ja kohdat), `paikka-kohdat.txt`, `asukas-kohdat.txt`, logcatit. Laitteiden omat tiedot otetaan talteen ja palautetaan. Kattaa alla olevat kohdat 1–9 (ei näytön ulkoasua). JarnoL:n laitteet: tabletti BRP-NX1M (paikan laite), OnePlus 12R CPH2609 (asukkaan puhelin).

**Käsin** (ulkoasun tarkistus):

`kahden-puhelimen-testi.ps1 -vaihe asenna` (molemmat USB:llä, sama Wi-Fi). Sitten:
1. Laite A: Yksikön yhteys → "Tämä laite on paikan laite"; luo yksikkö ja kaksi asukasta (Aino, Eero).
2. Laite B (Ainon puhelin): Liitä paikkaan → sama koodi molemmilla → A:lla valitaan Aino.
3. B saa Ainon päivän; Eeron tietoja ei näy missään.
4. A: muuta Ainon tapahtumaa → B:ssä muutos noin minuutin sisällä; A näyttää "perillä klo …".
5. B: käynnistä päiväohjelma, A: muuta ohjelmaa → B ei vaihdu kesken, vaihtuu ohjelman jälkeen.
6. B: "Hae tiedot nyt" toimii; omat kuvat ja äänet siirtyvät, toisella kerralla vain uudet.
7. B: Kerro ohjaajalle → viesti näkyy A:lla (tai ohjaajan puhelimella) ilmoituksena.
8. B Wi-Fi pois (vain mobiilidata) → ei siirtoa, ei kaatumista; Wi-Fi takaisin → siirto jatkuu.
9. A: poista liitos → B ei enää saa tietoja.
10. `kahden-puhelimen-testi.ps1 -vaihe lokit` ja kerro, mitkä kohdat eivät toimineet ja mihin aikaan.

## Seuraavaksi (sovittu 24.11.2026)

**Uusia ominaisuuksia ei lisätä ennen kohtia 1–4.**

1. ~~24.11. muutosten käännös ja testit PC:llä~~ – tehty 24.11. (3530241), kaikki OK.
2. Puhelintestit (yllä) ja kahden laitteen siirtotesti → VIKA-korjaukset.
3. Yhdistäminen mainiin, täysi tarkistus, tutkielman tilat `Testattu`.
4. Uudet kuvakaappaukset (`-kuvat`) ja materiaalit (opas, esitys, muistiinpanot) niistä uudelleen.
5. Sen jälkeen: A6 ruudunlukijan kuvaukset, A7 ohjaajan käyttöönottokortit, B6 Oikeudet ja päätökset -kortti, T7 Android 16:n lähiverkkolupa (ennen targetSdk-nostoa).
6. Pilotti ja yhteydenotto Autismisäätiöön (avoimet kysymykset tutkielmassa).

Ennen kauppaa (ei pilotille): versionCode, julkaisuallekirjoitus, R8 (minify) ja muut kielet.

Laatuportin laajennukset (sovittu): kotinäkymä-sääntö, Ensin–Sitten-kulku, käsin tehtävien tarkistusten linkitys `docs/UX_TARKISTUSLISTA.md`:hen. Jokainen uusi asiakasnäkymä lisätään porttiin.

## Keskeiset päätökset (tarkemmin tutkielman Päätökset-lokissa)

- Ei pilveä, ei internetiä, ei käyttäjätiliä (P9, X2). INTERNET-oikeus on käytössä vain lähiverkon salattuun siirtoon; vastaanotto hyväksyy vain lähiverkon yhteydet (24.11.).
- Paikan laite (A5): asukkaan puhelin hakee vain omat tietonsa paikan laitteelta samassa verkossa; pilotissa paikallinen malli, EU-pilvi harkitaan myöhemmin.
- Salaisuuksia ei koskaan tallenneta salaamatta (Keystore). Yksiköiden välinen viestintä hylätty (X4, asiakastietolaki 703/2023).
- Päiväni (C7): asiakkaan oma yhteenveto, asiakkaan puhelimeen ei kosketa; kotiverkkovaihtoehto avoin kysymys. Pysyvä jakopäätös asiakkaalta/huoltajalta/edunvalvojalta B6-kortin kautta.
- "Tarvitsen tauon" aina saatavilla ilman PIN-koodia kaikissa asiakkaan toimintonäkymissä.
- Kotiversio (omaisnäkymä) omana sovelluksena samasta koodista pilotin jälkeen (`docs/KEHITYS_SUUNNITELMA.md`).
