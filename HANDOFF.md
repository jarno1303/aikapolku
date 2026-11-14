# HANDOFF – AikaPolku (päivitetty 14.11.2026)

Lue tämä ensin. Sitten `docs/TUTKIELMA_OPTIMAALINEN_SOVELLUS.md` (päälähde: periaatteet, tunnisteet A1–C7, G1–G6, X1–X4, Päätökset-loki).

## Rooli ja työtapa

- Claude toimii autismin kirjon, toiminnanohjauksen ja UX:n asiantuntijana: kriittinen, näyttöön perustuva, ei kehittäjää miellyttävä.
- Suomeksi, selkeää yleiskieltä. Yksi kysymys kerrallaan.
- Sovitaan ensin, sitten tehdään. Kerro malli ja taso ennen työtä (Opus Medium keskusteluun, Opus High rakenteeseen ja käyttöliittymään).
- Varmuuskopiohaara ennen muutoksia: `backup/<kuvaus>-<vvvvkkpp>`, pushattuna.
- Skripti tekee asetusvaiheet, ei JarnoL käsin.
- Iso koodimuutos: erillinen tarkastaja-agentti käy muutoksen läpi ennen käännöstä.

## Kääntäminen ja testaus

Pilvessä ei ole Android SDK:ta. JarnoL ajaa PC:llä:

```powershell
powershell -ExecutionPolicy Bypass -File "$env:USERPROFILE\AndroidStudioProjects\AikaPolku\tarkistukset\aja-aikapolku.ps1" -branch <haara> [-testit "*Nimi*"] [-eiTesteja] [-taysi]
```

- `-taysi` = myös lint. Tulos: `AikaPolku\tarkistukset\<aikaleima>\tulos.txt`, testien XML:t `testit\`, loki `aikapolku.log`.
- APK: `Desktop\AikaPolku-APK\UUSIN-AikaPolku.apk`. Asennus: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r <apk>`.
- Claude lukee tulokset liitetyn kansion kautta (`$HOME/mnt/AikaPolku/tarkistukset/...`). Tarkista aina uusimman kansion aikaleima: JarnoL liittää joskus vanhan tulosteen.
- Git-pushit tehdään pilvikloonista. PC:n repo synkataan: `git fetch; git reset origin/main` (PC:n git ei voi poistaa lukkotiedostoja).
- Skriptin source: `tools/pc/aja-aikapolku.ps1` (kopio PC:n `tarkistukset`-kansiossa). Pieni korjattava: konsolin merkistö (ä näkyy "├ñ").

## Haarat (ketjussa, kukin edellisen päällä)

| Haara | Sisältö | Tila |
|---|---|---|
| `claude/aikapolku-muutoskortti-2026-11-14` (2f168d3) | A3, A4: Pysyy samana, Kuuntele, Tarvitsen tauon, ilta-ennakko, muistutus, Palauta alkuperäinen + vahvistus | 348 testiä OK, lint 0 virhettä. **Puhelintesti 1–15 kuittaamatta** |
| `claude/aikapolku-laatuportti-2026-11-14` (49c8122) | `ClientQualityGateTest` (R1–R8) | OK |
| `claude/aikapolku-siirtymat-2026-11-14` (8c425ba) | A1 ennakkovaroitus, Tarvitsen tauon ajastimeen ja tapahtumakorttiin, R9 | 357 testiä OK, laatuportti 0 havaintoa. **Puhelintesti 1–15 kuittaamatta** |
| `backup/ennen-siirtymat-20261114` | varmuuskopio | – |

`main` ei vielä sisällä näitä koodimuutoksia (vain dokumentit). Kun puhelintestit on kuitattu: yhdistä siirtymähaara mainiin (sisältää kaksi edellistä), täysi tarkistus `-taysi`, päivitä tutkielman tilat `Testattu`.

## Avoimet puhelintestit

- **Muutoskortti 1–15** (kohdat 11–14 vaativat illan ja seuraavan päivän). Tapahtumia tarvitaan: "Luo esittelypäivä" + omat huomisen tapahtumat.
- **Siirtymät 1–15**: valmistelu ennakkovaroitus = 2 min ja lukitus päälle. Ajastin (varoitus, tauko pysäyttää, Jatka käynnistää, takaisin jättää pysäytetyksi, vaakatila), tapahtumakortti (tauko, ei tehdyssä), päiväohjelma (varoitus kuvan kanssa, piilotettu seuraava → vain "Pian vaihtuu"), asetus Ei, muutosnäkymän tauko.

## Seuraavaksi (sovittu järjestys)

1. Puhelintestien kuittaus ja VIKA-korjaukset → yhdistäminen mainiin.
2. **A2** "Tarvitsen vielä hetken" (lisäaika ohjaajan rajoissa, oletusrajat 0/3/5 min kerran – vahvistettava ohjaajilta). Huom: 14.11. tehty "siirtymät" kattoi A1:n ja taukopainikkeet, ei A2:ta.
3. A6 ruudunlukijan kuvaukset, A7 ohjaajan käyttöönottokortit, G4, G6 ohjattu aloitus, B6 Oikeudet ja päätökset -kortti.
4. A5 lähilaitesiirto (iso; suunnittelu ehkä Fablella).
5. Pilotti. Rinnalla yhteydenotto Autismisäätiöön (avoimet kysymykset tutkielmassa).

Laatuportin laajennukset (sovittu): kotinäkymä-sääntö, Ensin–Sitten-kulku, käsin tehtävien tarkistusten linkitys `docs/UX_TARKISTUSLISTA.md`:hen. Jokainen uusi asiakasnäkymä lisätään porttiin.

## Keskeiset päätökset (tarkemmin tutkielman Päätökset-lokissa)

- Ei INTERNET-oikeutta, ei pilvitiliä (P9, X2). Yksiköiden välinen viestintä hylätty (X4, asiakastietolaki 703/2023).
- Päiväni (C7): asiakkaan oma yhteenveto, asiakkaan puhelimeen ei kosketa; kotiverkkovaihtoehto avoin kysymys. Pysyvä jakopäätös asiakkaalta/huoltajalta/edunvalvojalta B6-kortin kautta.
- "Tarvitsen tauon" aina saatavilla ilman PIN-koodia kaikissa asiakkaan toimintonäkymissä.
