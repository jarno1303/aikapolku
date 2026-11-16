# AikaPolku – kehityssuunnitelma

## Vaihe 1
- Android-projektin luominen
- Material 3 -teema
- Aloitusnäkymä
- Navigaatio

## Vaihe 2
- Visuaalinen ajastin
- Ajastimen tilat
- Valinnaiset ilmoitukset

## Vaihe 3
- Toimintokortit
- Ensin–Sitten
- Omat kuvat

## Vaihe 4
- Päiväohjelmat
- Room-tallennus
- Aikataulut ja toistot

## Vaihe 5
- Oman äänen tallennus
- Saavutettavuus
- Käyttäjätestit

Jakaminen, Chromecast ja maksut myöhemmin.

## Pilotin jälkeen (sovittu 16.11.2026)
1. **Kotinäytön widget** (ensimmäinen): asukas näkee puhelimen kotinäytöltä "Nyt" ja "Seuraavaksi" sekä käynnissä olevan ajan avaamatta sovellusta. Ei lukitusnäytölle (tietosuoja). Ajatus Tiimon widgeteistä.
2. Toimintatyyppien värikoodaus (muutama kiinteä väri, esim. ruokailu, ulkoilu, lepo) – harkitaan.
3. Värinä älykelloon ennen siirtymää – harkitaan.

## Kotiversio (sovittu 16.11.2026, toteutus pilotin jälkeen)
Kotiversio käsitellään pilotin jälkeen yhtenä kokonaisuutena. Pilotti tehdään yksikköversiolla.

1. **Kaksi sovellusta samasta koodista** (Android product flavors): `yksikko` ja `koti`, omat sovellustunnukset, nimet, kuvakkeet ja hinnoittelu. Noin 90 % koodista yhteistä; makukohtainen koodi omissa kansioissaan. Tarkistusskripti kääntää ja testaa molemmat.
   - Yksikköversio: yksiköt, alayksiköt, ryhmät, paikan laite, TV-näkymä, organisaation lisenssi.
   - Kotiversio: yksi koti, kevyt ohjattu aloitus (lapsi tai oma käyttö), ei yksikkö- eikä paikan laitteen toimintoja, kuluttajahinta.
   - Työmäärä: makujen perustus noin päivä, yksikkötoimintojen piilotus muutama päivä.
2. **Omaisnäkymä (kotiversion ydinominaisuus):** useampi aikuinen (kaksi kotia, isovanhemmat, koulunkäyntiavustaja, tilapäishoito) näkee tai muokkaa samaa viikkoa.
   - Salattu "postilaatikko": paketti salataan laitteiden pariliitosavaimilla, palvelin (EU, Hetzner) ei pysty lukemaan sisältöä.
   - Laitteet liitetään kerran samalla koodilla kuin nyt; pääkäyttäjä (huoltaja) päättää, saako omainen vain katsoa vai myös muokata.
   - Työmäärä noin 2–3 viikkoa sekä tietosuojaseloste ja palvelimen ylläpito.
   - **Ei yksikköversioon:** aikuisen asukkaan tiedot vaativat hänen suostumuksensa, ohjaajat hoitavat yhteydenpidon, ja kotiloman tarpeet hoituvat Päiväni-yhteenvedolla ja poissaolokortilla. Yksikköversion lupaus "tieto ei lähde talosta" säilyy.
