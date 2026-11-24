# AikaPolku – tutkielma: nykytila vs. optimaalinen toiminnanohjauksen sovellus

**Versio:** 1.8 · **Päivitetty:** 24.11.2026 · **Koodin tila arvioitaessa:** alkuperäinen arvio päähaara 3.11.2026 (5051179); tilat päivitetty haaran `claude/aikapolku-yksikkomalli-2026-11-15` mukaan (16.11. ja 24.11.2026)

> **Tämä tiedosto on päälähde.** Word-versio (`AikaPolku_tutkielma_optimaalinen_sovellus.docx`) tehdään tästä.
> Kun suunta tarkentuu, päivitetään ensin tämä tiedosto (tila-sarakkeet, Päätökset-loki, versio ja päivämäärä),
> sitten Word uudelleen. Kehityksessä viitataan tunnisteisiin (esim. `A3`) commit-viesteissä ja KEHITYSLOKI.md:ssä.

## Käyttö kehityksessä

- **Tunnisteet:** jokaisella kehitysehdotuksella on tunniste (A1–A7, B1–B6, C1–C7, G1–G6, X1–X4). Käytä sitä commitissa: `A3: muutoskorttiin "Mikä pysyy samana"`.
- **Tila:** `Ehdotettu` → `Päätetty` → `Työn alla` → `Valmis` → `Testattu` (tai `Hylätty` + syy Päätökset-lokiin).
- **Laatuportti:** `ClientQualityGateTest` tarkistaa jokaisessa käännöksessä asiakkaan näkymät periaatteita vasten (R1–R9: kosketusalue, valintojen määrä, ruudunlukija, ei hälyttäviä sanoja, Tarvitsen tauon aina saatavilla, 200 % tekstikoko, muutosnäkymän järjestys, muutoksen näkymisaika, ennakkovaroitus). Uusi asiakasnäkymä lisätään porttiin samalla kun se tehdään.
- **Näytön merkinnät:** **[T]** tutkimusnäyttö · **[K]** käytäntö / pienet tutkimukset, tulokset vaihtelevat · **[H]** suunnitteluhypoteesi, testataan pilotissa.
- **Periaatteet (luku 2)** ovat tarkistuslista jokaiselle uudelle ominaisuudelle. Tehtävänannon seitsemän kysymystä (luku 10) samoin.

---

## Tiivistelmä

AikaPolku kattaa arviolta **noin kaksi kolmasosaa** (≈ 3,4/5) siitä, mitä optimaalinen toiminnanohjauksen sovellus tarvitsee. Puuttuva kolmannes on pääosin lisättävää, ei uudelleen rakennettavaa.

**Vahvinta:** päivän ennakoitavuus (NYT-kortti, aikajana, viikko-ohjelma), ajan näyttäminen usealla tavalla, tehtävien vaiheistus ja ohjaajan työkalut.

**Suurimmat aukot:**

1. **Siirtymät:** käynnissä olevan toiminnan lopusta ei tule ennakkovaroitusta, eikä käyttäjä voi pyytää lisäaikaa.
2. **Muutosmalli puolivalmis:** puuttuvat "mikä pysyy ennallaan", ilta-ennakko, vaiheittainen ilmoitus ja uuteen tilanteeseen valmistautuminen.
3. **Käyttäjän oma ääni kapea:** apu ja tauko ovat, mutta ei valintaa, "en vielä" -viestiä eikä osallistumista suunnitteluun.

Ennen pilottia tarvitaan seitsemän korjausta (A1–A7), arviolta 13–20 työpäivää. Tärkeintä on silti pilotti oikeiden käyttäjien kanssa.

## Menetelmä

Arvio perustuu lähdekoodiin (~35 000 riviä Kotlinia), dokumentteihin ja tutkimuskirjallisuuteen. Sovellusta ei ole testattu oikeiden käyttäjien kanssa. Autismin kirjo on yksilöllinen; mikään alla ei päde kaikkiin, siksi säädettävyys on keskeinen mittari.

---

## 1. Keskeiset ongelmat

| Vaikeus | Arjessa | Näyttö |
| --- | --- | --- |
| Toiminnanohjaus | Aloittaminen, vaiheiden muistaminen, joustava vaihtaminen | [T] 235 tutkimuksen meta-analyysi: laaja-alainen kohtalainen ero (g = 0,48), pysyy kehityksen yli (Demetriou ym. 2018) |
| Epävarmuuden sietokyky | Tuntematon tai muuttuva tilanne kuormittaa | [T] Yhteys ahdistukseen vahva, r = 0,62 (Jenkinson ym. 2020) |
| Ajan hahmottaminen | Kesto ja "kauanko vielä" epäselviä | [T/K] Erot johdonmukaisimmin korkean tason aikakäsitteissä (Casassus ym. 2019) |
| Siirtymät | Mieluisan keskeyttäminen tai ennakoimaton järjestys kuormittaa | [K] Ennakkovaroitus auttaa osaa, tulokset vaihtelevat (Vasquez ym. 2017; Leon ym. 2023) |
| Aistisäätely | Äänet, värinä, visuaalinen häly | [T] Aistiyliherkkyys suuri ja erottava piirre, vaihtelee paljon (Ben-Sasson ym. 2019) |

**Johtopäätös:** epävarmuus on keskeinen kuormittaja. Sovelluksen pitää vähentää epävarmuutta joka hetkellä: mitä nyt, mitä seuraavaksi, kauanko ja mitä jos jokin muuttuu.

## 2. Ratkaisun periaatteet (tarkistuslista)

| # | Periaate | AikaPolku nyt |
| --- | --- | --- |
| P1 | Yksi asia kerrallaan, seuraava näkyvissä [T] | Toteutuu (NYT-kortti, aikajana 3 tilassa) |
| P2 | Aika näkyväksi [K] | Toteutuu (kellotaulu, rengas, numerot, rauhallinen) |
| P3 | Ei yllätyksiä ilman ennakkoa [T] | Toteutettu, odottaa puhelintestiä (muutoskortti, ilta-ennakko A4, ennakkovaroitus A1) |
| P4 | Säädettävyys yksilön mukaan [T] | Hyvin (profiilikohtaiset asetukset) |
| P5 | Rauhallisuus, aistikuorman välttäminen [T] | Osittain (ei yleistä kuormitusasetusta) |
| P6 | Käyttäjän ääni ja valta [T] | Osittain, odottaa puhelintestiä (apu, tauko, lisäaika A2, valinta kahdesta B1, En ymmärrä, oma tila) |
| P7 | Ohjaaja rakentaa, käyttäjä näkee olennaisen [K] | Toteutuu (roolit, PIN, omat etusivut) |
| P10 | Asukas näkee vain sen, mitä tapahtuu siellä, missä hän on; ei koskaan toisten tietoja [K] | Toteutettu 15.11.2026 yksikkömallissa (sijaintisääntö); paikan laite siirtää asukkaalle vain hänen tietonsa (16.11.). Odottaa puhelintestiä |
| P8 | Ei kontrollia, ei kilpailua [K] | Toteutuu (ei pisteitä, tauko aina vapaa) |
| P9 | Toimii ilman verkkoa, ei kerää tietoja [H] | Toteutuu: ei pilveä, ei internetiä, ei käyttäjätiliä. Verkko-oikeus (INTERNET) on käytössä vain lähiverkon salattuun siirtoon (paikan laite → asukkaan puhelin, asukkaan viestit ohjaajalle); vastaanotto hyväksyy vain lähiverkon yhteydet (24.11.). Androidin pilvivarmuuskopio pois |

## 3. Ominaisuudet alueittain

| Alue | AikaPolussa nyt | Puuttuu | Arvio /5 |
| --- | --- | --- | --- |
| Päivän ennakoitavuus | NYT-kortti, aikajana 3 tilassa, viikko-ohjelma, A/B-viikot, Tänään-lista | Huomisen esikatselu käyttäjälle | 4,5 |
| Ajan hahmottaminen | Kellotaulu, rengas, numerot, rauhallinen tila, 10 s laskenta | – | 4,5 |
| Aloitus ja vaiheistus | Päiväohjelmat osavaiheineen, Ensin–Sitten, Aloita, automaattinen ajan käynnistys | "Ensimmäinen pieni askel", videomalli | 4 |
| Ohjaajan työkalut | Pikamuutos, ryhmät, profiilit, vienti/tuonti, PDF | Siirto asiakkaan laitteeseen | 4 |
| Kommunikointi | Apu- ja taukopyyntö, omat äänitykset, puhesynteesi | Lisäaika, "en vielä", tunne, valinta | 3 |
| Muutosten hallinta | Peruttu/korvattu/siirretty, syy tekstinä ja äänenä, 3 ajoitusta, alkuperäinen yliviivattuna, Selvä-kuittaus | Mikä pysyy, ilta-ennakko, vaiheittaisuus, ihminen/paikka, valmistautuminen | 3 |
| Yksilöllisyys ja aistit | Profiilikohtaiset asetukset, äänet, oma kuva ja ääni | Kuva/teksti-taso, liikkeen vähentäminen, aikuiskuvasto | 3,5 |
| Siirtymät | Loppuääni, värinä, NYT-kortin vaihtuminen | Ennakkovaroitus lopusta, lisäaikapyyntö, "jatka myöhemmin" | 2 |
| Käyttäjän osallisuus | Tauko aina vapaa, profiilin itsevalinta | Valinta, oma ehdotus, mielipide | 2 |

## 4. Muutosten hallinta

### Kahdeksan vaatimusta

| Vaatimus | Nyt | Tila |
| --- | --- | --- |
| 1. Ilmoittaa ymmärrettävästi | Muutosmerkki, muutosnäkymä, puheviesti voi soida itsestään | Kunnossa |
| 2. Mikä muuttuu | Alkuperäinen yliviivattuna | Kunnossa |
| 3. Miksi | Syy tekstinä ja äänityksenä | Kunnossa |
| 4. Mikä säilyy | Ei korosteta | Puuttuu → A3 |
| 5. Mitä tilalle | Korvaava kortti tai ohjelma | Kunnossa |
| 6. Uusi aikataulu | Siirto vain saman päivän sisällä | Osittain → C5 |
| 7. Aikaa käsitellä | Selvä-kuittaus | Osittain → A3 |
| 8. Tutustuminen etukäteen | Ei | Puuttuu → C1 |

### Muutosnäkymän viisiosainen malli [H]

Aina sama rakenne samassa järjestyksessä:

1. **Mikä muuttuu** (alkuperäinen yliviivattuna) – on
2. **Miksi** (jos tiedetään) – on
3. **Mitä tilalle** (uusi kuva ja aika) – on
4. **Mikä pysyy samana** (2–3 seuraavaa ennallaan olevaa, vihreä merkki) – **puuttuu, tärkein lisäys**
5. **Mitä voin tehdä** ("Selvä", "Haluan kuulla uudestaan", "Tarvitsen tauon") – nyt vain Selvä

### Ajoitus

Nyt: heti, aamulla, tapahtuman aikaan (`ChangeReveal`). Lisättävä: **edellisenä iltana**, **vaiheittain** (esim. ilta + 15 min ennen) ja [H] **kun käyttäjä on valmis** (merkki odottaa, käyttäjä avaa itse). Valitaan asiakaskohtaisesti, ei yhteistä oletusta.

### Puuttuvat muutostyypit

Ihminen vaihtuu (kuva + nimi) · paikka vaihtuu (valokuva) · kesto muuttuu (uusi loppuaika) · usean päivän muutos.

### Valmistautuminen uuteen tilanteeseen

[T] Sosiaaliset tarinat ovat NCAEP:n näyttöön perustuva käytäntö (Steinbrenner ym. 2020). Ehdotus: **"Mitä tapahtuu" -kuvatarina** (3–6 omaa valokuvaa + lyhyt ääni), liitettynä viikko-ohjelman tapahtumaan ja avattavissa etukäteen.

### Haitat

Liian aikainen tieto voi pidentää jännitystä · syy voi lisätä huolta (ohje ohjaajalle syyn muotoilusta) · jokainen pieni muutos ei tarvitse korttia.

## 5. Siirtymät

[K] Kahden minuutin ennakkovaroitus ja ajastimen ääni auttoivat yhtä lasta (Vasquez ym. 2017), mutta tulokset vaihtelevat; pelkkä kuva-aikataulu ei aina riitä (Waters ym. 2009). Varoitus on siis säädettävä ja yhdistetty muuhun tukeen.

### Malliesimerkki: pelaaminen loppuu → ruoka [H]

1. **10 min ennen:** kellotaulun sektori vaihtaa väriä, pieni kuva seuraavasta. Ei ääntä.
2. **2 min ennen:** valittu ääni tai värinä + "2 min, sitten ruoka". Painikkeet **Selvä** ja **Tarvitsen vielä hetken**.
3. **Lisäaika:** ohjaajan asettama (0/3/5 min, korkeintaan kerran). Ei palkinto eikä rangaistus. Jos ei sallittu: "Pyydä aikuiselta".
4. **Loppu:** loppuääni + Valmis-näkymä + kortti **"Jatka myöhemmin"** (milloin toiminta palaa).
5. **Siirtymä:** NYT-kortti vaihtuu, ensimmäinen pieni askel näkyvissä.

Vältetään punaista hälytysväriä ja sanaa "myöhässä"; yliaika näytetään neutraalisti.

## 6. Käyttöliittymä

| Asiakkaan näkymä | Tarkoitus | Nyt |
| --- | --- | --- |
| NYT | Mitä nyt, kauanko, mitä seuraavaksi | On |
| Suoritus | Vaihe kerrallaan | On |
| Muutos | Viisiosainen malli | Osittain |
| Viesti | Apu, tauko, lisäaika, "en vielä", tunne | Osittain |
| Huominen | Mitä huomenna on | Puuttuu (B5) |
| Valinta | 2–4 kuvavaihtoehtoa | Puuttuu (B1) |

Tarkistettavaa: valmiiden kuvien (`CardArt`) tyyli aikuisille · liikkeen vähentäminen (ainakin puhelimen asetus huomioon) · 51 kuvalta puuttuu ruudunlukijan kuvaus (A6).

Lisättävät tasot profiiliin [H]: **kuva/teksti-taso** (vain kuva / kuva + sana / teksti) ja **aistikuorma-asetus** (hiljainen tila, vähennetty liike, pehmeät värit).

## 7. Ohjaajan työkalut

Vahvin osa. Puuttuu: **lähilaitesiirto** asiakkaan laitteeseen (A5), **valintakortti** (B1), **käyttöönottokortit** (A7).

[T] Visuaaliset aikataulut toimivat parhaiten yhdessä järjestelmällisen opettamisen kanssa (Knight ym. 2015) → sovellukseen 4–5 lyhyttä "Näin otat käyttöön" -korttia ohjaajalle.

[H] Mahdollinen seuranta (apu- ja taukopyynnöt) vain valinnaisena, käyttäjälle näkyvänä ja lyhytaikaisena – ei valvontaa.

## 8. Käyttäjän vaikutusmahdollisuudet

[T] Valinnan tarjoaminen voi vähentää kuormitusta ja lisätä osallistumista osalla käyttäjistä (Howell ym. 2019). Neuroaffirmatiivinen ote korostaa itsemääräämisoikeutta ja yhteistä päätöksentekoa (McVey ym. 2023).

### Viisi osallisuuden tasoa [H] (ohjaaja valitsee profiilissa)

1. **Kertoa tarpeensa:** apu, tauko (on) + "Tarvitsen vielä hetken" (A2), "En halua nyt", tunnekortti (C2)
2. **Valita kahdesta** (B1)
3. **Valita järjestys** (Ensin–Sitten-pohjalla) (B1)
4. **Ehdottaa** (C3)
5. **Suunnitella itse rajoissa** (C3)

Ei: valintaa joka kohtaan · rangaistusta "en halua" -viestistä · kieltäytymisen pysyvää tallennusta käyttäjän tietämättä.

---

## 9. Kehitysehdotukset ja tila

### A. Ennen pilottia

| ID | Korjaus | Miksi | Työ | Tila |
| --- | --- | --- | --- | --- |
| A1 | Ennakkovaroitus käynnissä olevan toiminnan lopusta (säädettävä, kuva seuraavasta) | Siirtymät heikoin alue | 2–3 pv | Valmis (odottaa puhelintestiä) |
| A2 | "Tarvitsen vielä hetken" ohjaajan rajoissa | Käyttäjän keino neuvotella | 2 pv | Valmis (odottaa puhelintestiä) |
| A3 | Muutoskorttiin "Mikä pysyy samana" + valinnat "Haluan kuulla uudestaan", "Tarvitsen tauon" | Kohdistuu epävarmuuteen | 1–2 pv | Valmis (odottaa puhelintestiä) |
| A4 | Ilta-ennakko ja vaiheittainen ilmoitus muutokselle | Toivottu, puuttuu koodista | 1–2 pv | Valmis (odottaa puhelintestiä) |
| A5 | Lähilaitesiirto ohjaajalta asiakkaan laitteeseen | Ryhmät eivät muuten toimi yksiköissä | 5–8 pv | Valmis 16.11.2026 paikan laitteena (Y7, `docs/PAIKAN_LAITE_SUUNNITELMA.md`). **Odottaa kahden laitteen testiä** |
| A6 | Ruudunlukijan kuvaukset toimintakorteille | Saavutettavuus | 1 pv | Ehdotettu |
| A7 | Ohjaajan käyttöönottokortit | Visuaalinen tuki toimii opetuksen kanssa | 1–2 pv | Ehdotettu |

### B. Pilotin aikana palautteen mukaan

| ID | Ehdotus | Tila |
| --- | --- | --- |
| B1 | Valintakortti (2 vaihtoehtoa, myös järjestyksen valinta) | Osittain: valinta kahdesta päiväohjelmaan ja Sitten-korttiin tehty 15.11. (odottaa puhelintestiä); järjestyksen valinta puuttuu |
| B2 | "Jatka myöhemmin" -kortti | Ehdotettu |
| B3 | Muutostyypit: ihminen vaihtuu, paikka vaihtuu | Ehdotettu |
| B4 | Kuva/teksti-taso ja aistikuorma-asetus profiiliin | Ehdotettu |
| B5 | Huominen-näkymä asiakkaalle | Ehdotettu |
| B6 | **Oikeudet ja päätökset -kortti** asiakkaan profiiliin (luku 12): kuka päättää tietojen jakamisesta, mitä asiakas saa tehdä itse, mikä säilyy aina asiakkaalla, viimeisin tarkistus. Kysytään myös ohjatussa aloituksessa (G6). Edellytys C7:n kotiverkkovaihtoehdolle | Ehdotettu |

### C. Myöhemmin

| ID | Ehdotus | Tila |
| --- | --- | --- |
| C1 | "Mitä tapahtuu" -kuvatarina uuteen tilanteeseen | Ehdotettu |
| C2 | Tunnekortti ja "En halua nyt" | Osittain: oma tila (asiakkaan oma tieto) ja "En halua kumpaakaan" valinnassa tehty 15.11. |
| C3 | Käyttäjän ehdotukset ja oma suunnittelu rajoissa | Ehdotettu |
| C4 | Kesto muuttuu -muutostyyppi | Ehdotettu |
| C5 | Usean päivän muutokset | Ehdotettu |
| C6 | Aikuisten kuvasto, valinnainen seurantanäkymä | Osittain: käytön seuranta pilottia varten (laitekohtainen, oletus pois, vain lukumäärät) tehty 15.11. |
| C7 | **Päiväni:** asiakkaan oma kuvallinen yhteenveto päivästä (tehdyt, muutokset, tauot, oma valinta 😊/😐/🙁), jonka asiakas näyttää itse kotona tai asumisyksikössä (digitaalinen reissuvihko). Tieto pysyy asiakkaan laitteessa; henkilökunta ei kirjoita siihen havaintoja tai arvioita. Arvio 3–5 pv, pilotin jälkeen | Ehdotettu |

### X. Ei tehdä

| ID | Mitä | Miksi |
| --- | --- | --- |
| X1 | Pisteet, tasot, palkintotalous | Ristiriidassa periaatteen P8 kanssa |
| X2 | Pakollinen pilvitili tai verkkopalvelu | Vesittäisi tietosuojaedun (P9) |
| X3 | Tekoälyn tekemät aikataulut asiakkaalle | Ihminen päättää muutoksista |
| X4 | Yksiköiden välinen viestintä asiakkaan päivästä (esim. asumisyksikkö ↔ päivätoiminta) | Tekisi sovelluksesta sosiaalihuollon asiakastietoja käsittelevän järjestelmän (asiakastietolaki 703/2023: olennaiset vaatimukset, luokittelu), vaatisi palvelimen ja luovutusperusteen; vesittäisi P9:n. Tarve katetaan C7:llä asiakkaan omassa laitteessa. Rajanveto varmistettava juristilta, jos asiaan palataan |

## 10. Kriittinen arviointi

Tehtävänannon seitsemän kysymystä jokaiselle ominaisuudelle: ratkaiseeko todellisen ongelman · vähentääkö kuormitusta · lisääkö ennakoitavuutta · parantaako itsenäisyyttä · lisääkö turvallisuuden tunnetta · onko helppo · voiko aiheuttaa haittaa.

| Nykyinen ominaisuus | Ongelma | Ehdotus |
| --- | --- | --- |
| Ajan säätäminen PIN-lukon takana | Vie käyttäjältä kaiken vallan aikaan | Pidä lukko, lisää rajattu lisäaika (A2) |
| 10 s laskenta | Voi lisätä kiirettä | Valinnainen, oletuksena pois |
| Valmis vasta kun aika loppunut | Voi turhauttaa | Vaihtoehtona, ei oletuksena (nykyinen kolmen tilan valinta oikea) |
| Muutos näkyy heti (oletus) | Osalle aikainen tieto pidentää jännitystä | Kysy ajoitus profiilia luotaessa |
| Laaja asetusvalikko | Uusi ohjaaja ei tiedä, mistä aloittaa | Ohjattu aloitus: 5 kysymystä asiakkaasta |
| Kaksi roolia samassa laitteessa | Asiakas voi nähdä ohjaajan näkymän | Paikan laite (A5) ja vapaan roolinvaihdon poisto (16.11.) tehty; testataan pilotissa |
| Valmiiden kuvien tyyli | Voi tuntua lapselliselta aikuiselle | Aikuisprofiilissa oletuksena omat valokuvat |

**Kokonaisarvio:** AikaPolussa näkyy ammattilaisen ote (tauko aina vapaa, roolit erillään, asiakaskohtainen ajoitus). Heikkous ei ole väärissä ratkaisuissa vaan siinä, että käyttäjän kokemusta ei ole vielä kuultu. Tärkein seuraava askel on pilotti, jossa myös autistiset aikuiset ovat mukana itseään edustamassa.

## 11. Grafiikka ja ohjattu aloitus

Arvioitu 14.11.2026 kaikki 38 valmista kuvaa (`res/drawable-nodpi`).

**Toimii:** tyyli on johdonmukainen ja rauhallinen (selkeä ääriviiva, pehmeät värit, ei visuaalista hälyä). Vaihekuvissa on yksi toiminto per kuva. Esineisiin perustuvat kortit (ruoka, wc, peliohjain, tiimalasi) sopivat kaiken ikäisille.

**Korjattavaa:** kaikki ihmishahmot ovat sama kiharatukkainen poika tai nuori mies · taustavärit vaihtelevat ilman merkitystä · osa kuvista on vilkkaita kohtauksia · uusille toiminnoille puuttuvat kuvat · asiakkaan muualla käyttämät symbolit (esim. kommunikointikansio) eivät ole sovelluksen oletus.

[K] Alan käytäntö: sama asia samalla kuvalla kaikkialla asiakkaan arjessa. Sovelluksen pitää tukea asiakkaan jo käyttämiä kuvia, ei korvata niitä.

| ID | Ehdotus | Miksi | Vaihe | Tila |
| --- | --- | --- | --- | --- |
| G1 | Hahmot: aikuisprofiilissa oletuksena esinekortit ja omat valokuvat; myöhemmin 3–4 hahmovaihtoehtoa (ikä, sukupuoli) profiilissa | Samaistuminen ja aikuisen arvokkuus | B | Ehdotettu |
| G2 | Taustaväri: joko johdonmukainen luokka (ruoka, hygienia, liikunta, lepo) tai yksi neutraali väri | Osa käyttäjistä etsii väreistä merkitystä | B | Valmis 16.11.2026: värikoodaus, kuusi luokkaa merkkeineen, asukaskohtainen päälle/pois (odottaa puhelintestiä) |
| G3 | Vilkkaiden kohtauskuvien yksinkertaistus (esim. ryhmätilanne, puhekuplat) | Vähemmän tulkittavaa | C | Ehdotettu |
| G4 | Uudet kuvat: lisäaika, "pysyy samana" -merkki, ihminen vaihtuu, valinta, jatka myöhemmin | Tarvitaan A1–A3:lle ja B1–B3:lle | A (toimintojen mukana) | Osittain: lisäajan viestikuva tehty (A2) |
| G5 | Asiakkaan omat symbolit: käyttöönotto-ohjeeseen "käytä samoja kuvia kuin muualla arjessa"; symbolipankit vain lisenssitarkistuksen jälkeen (monet ei-kaupallisia); nykyisten kuvien käyttöoikeudet dokumentoidaan ennen myyntiä | Johdonmukaisuus ja juridinen varmuus | A (ohje A7:n mukana), B (pankit) | Ehdotettu |
| G6 | Ohjattu aloitus ohjaajalle: 5 kysymystä asiakkaasta (ikä, lukutaito, aistiherkkyys, muutosten ennakointi, omatoimisuus) → asetukset valmiiksi; lisäksi Oikeudet ja päätökset -kortin kysymykset (B6) | Ohjaajan puolella on paljon valintoja | A | Osittain: ohjattu aloitus yksikölle (yksiköt, asukkaat, yhteiset asetukset, perusrutiinit, kopiointi) tehty 15.–16.11.; B6:n kysymykset puuttuvat |

**Järjestys:** toimintojen aukot (A) ensin, kuvamuutokset kohdennetusti pilottia varten, laaja UI/UX-viilaus vasta pilotin palautteen jälkeen.

---

## 12. Päiväni (C7) – tarkennus

Asiakkaan oma puhelin on monelle autistiselle ihmiselle hyvin henkilökohtainen: **kenenkään ei tarvitse koskaan pidellä asiakkaan laitetta.**

**Periaatteet**

1. **Sisältö syntyy itsestään** siitä, mitä päivän aikana tallentuu jo nyt (tehty-kuittaukset, muutokset, tauot). Henkilökunta ei kirjoita asiakkaan laitteeseen mitään.
2. **Tieto liikkuu ilman kosketusta:** kotona huoltajan puhelin tai kodin tabletti AikaPolun Koti-tilassa; asiakkaan laite lähettää Päiväni-yhteenvedon lähisiirrolla, kun asiakas tulee kotiin (sama tekniikka kuin A5).
3. **Pariutus QR-koodilla niin, että puhelin pysyy asiakkaan kädessä:** asiakas näyttää koodin omalta ruudultaan, toinen skannaa omalla laitteellaan. Asiakas voi tehdä tämän myös itse.

**Asiakkaan päätösvalta:** asetuksena jaetaanko automaattisesti, vain "Lähetä kotiin" -painikkeella vai ei koskaan; kertakielto "Tänään en halua jakaa". Varakeinona "Lähetä kotiin" kuvana (jakaminen kuvana on jo olemassa; tieto kulkee silloin muiden sovellusten kautta, siksi toissijainen). Jos asiakas käyttää yksikön tablettia, Päiväni siirretään samalla tavalla eikä henkilökohtaista laitetta käsitellä.

**Kotiverkkovaihtoehto (avoin kysymys Autismisäätiölle)**

Kun asiakkaan laite palaa asumisyksikön wifiin, se voisi lähettää Päiväni-yhteenvedon lähiverkossa yksikön **kiinteälle ohjaajatabletille** (ei ohjaajien puhelimiin: Androidin taustarajoitusten vuoksi lukittu puhelin ei vastaanota luotettavasti; puhelimet hakevat tabletilta). Ei internetiä eikä palvelinta.

Ennen toteutusta ratkaistava:

1. **Ei automaattista raporttia:** kotiverkkoon palatessa asiakkaalta kysytään isoilla painikkeilla "Näytetäänkö päiväsi ohjaajille? Kyllä / Ei", tai asiakas valitsee asetuksissa aina / ei koskaan. Kysymys tulee siirtymähetkellä, joten sen pitää voida kytkeä pois.
2. **Tieto yksiköltä toiselle:** päivätoiminnan tieto päätyy asumisyksikön henkilökunnalle; hoidossa käytettynä se alkaa muistuttaa sosiaalihuollon asiakastietoa (vrt. X4). Selvitettävä juristin ja Autismisäätiön tietosuojavastaavan kanssa.
3. **Organisaation verkko:** automaattinen asiakastiedon lähetys yksikön wifissä vaatii Autismisäätiön tietohallinnon hyväksynnän.

Esittelyssä kysymyksenä: "Olisiko tästä teille hyötyä, ja millä ehdoilla se teillä sallittaisiin?" Toteutus aikaisintaan A5:n ja perus-Päivänin jälkeen.

**Pysyvä päätös jakamisesta (ei kysymystä joka kerta)**

Jakamisesta päätetään kerran, ja sovellus toimii sen mukaan kysymättä uudelleen kotiverkkoon palatessa. Ehdot:

- **Kuka päättää:** täysi-ikäinen asiakas ensisijaisesti itse; edunvalvojan tehtävä on yleensä taloudellinen ja henkilöä koskeviin asioihin vain, jos niin on määrätty eikä asiakas kykene ymmärtämään asian merkitystä; täysi-ikäisen vanhemmalla ei ole automaattista päätösvaltaa; alaikäisellä huoltaja, mutta lapsen mielipide ja kielto-oikeus säilyvät. Salassa pidettävän tiedon luovutus perustuu lakiin tai suostumukseen.
- **Rajattu:** mitä (Päiväni-yhteenveto), kenelle (yksikön ohjaajatabletti), milloin (kotiverkkoon palatessa). Ei laajaa "kaikki kaikille" -lupaa.
- **Kirjattu:** kuka päätti, milloin ja millä laajuudella.
- **Asiakas voi perua itse** yhdellä painalluksella omasta laitteestaan; "Tänään en halua jakaa" toimii edelleen kertakieltona.
- **Näkyvä:** asiakas näkee merkinnän, kun jakaminen tapahtui ("Päiväsi näytettiin ohjaajille klo 15.02").
- **Tarkistetaan** säännöllisesti, esim. asiakassuunnitelman tarkistuksessa; sovellus muistuttaa ohjaajaa.
- **Suostumuslomake on yksikön** (esim. Autismisäätiö), koska tieto päätyy sen käyttöön; AikaPolku toteuttaa sovitun päätöksen ja pitää siitä kirjaa. Varmistetaan yksikön tietosuojavastaavalta.

**Oikeudet ja päätökset -kortti (B6)**

Asiakkaan profiiliin yksi kortti, joka kokoaa oikeudet näkyviksi ja tarkistettaviksi:

1. **Kuka päättää tietojen jakamisesta:** Asiakas itse (oletus) · Asiakas tuettuna (tuettu päätöksenteko, YK:n vammaissopimuksen henki) · Huoltaja (alaikäinen; lapsen mielipide ja kielto-oikeus säilyvät) · Laillinen edustaja (vain jos henkilöä koskevat asiat on määrätty; ohjaaja merkitsee määräyksen tarkistetuksi, asiakirjaa ei tallenneta).
2. **Mitä asiakas saa tehdä itse:** profiilin vaihto, ajan säätö ja lisäaikapyyntö (A2), Valmis-kuittaus, Päivänin jakaminen tai kertakielto. Kokoaa nykyiset hajallaan olevat asetukset yhteen.
3. **Mikä säilyy aina asiakkaalla:** tauko aina vapaa, kertakielto jakamiselle, jakamisen merkintä näkyy hänelle.
4. **Viimeisin tarkistus:** milloin ja kuka; muistutus ohjaajalle sovitun ajan kuluttua.

Rajat: muutokset vain ohjaajan PIN-koodilla ja jokainen muutos kirjataan (kuka, milloin). Sovellukseen ei tallenneta diagnooseja, tuomioistuimen päätöksiä eikä perusteluja asiakkaan kyvyistä, vain päätös ja päättäjä.

## Päätökset-loki

Kirjaa tähän jokainen suunnan tarkennus: päivämäärä, tunniste, päätös, perustelu. Uusin ylimpänä.

| Päivä | ID | Päätös | Perustelu |
| --- | --- | --- | --- |
| 24.11.2026 | P9 | Tietoturvakorjaukset: vastaanotto hyväksyy vain lähiverkon yhteydet (ei mobiilidataa eikä internetiä), viestirivien kokoraja, salaisuuksia (laiteavaimet, varmuuskopion salasana) ei koskaan tallenneta salaamatta – jos salaus epäonnistuu, pariliitos tai käyttöönotto epäonnistuu näkyvästi. P9:n kuvaus korjattu: verkko-oikeus on käytössä lähiverkon siirtoa varten | Siirto toi verkko-oikeuden; lupaus "ei internetiä" pitää paikkansa vain, kun vastaanotto on rajattu lähiverkkoon |
| 24.11.2026 | – | Uusia ominaisuuksia ei lisätä ennen puhelintestejä, kahden laitteen siirtotestiä ja yhdistämistä päähaaraan | 14.–16.11. tehty paljon; mitään ei ole vielä kuitattu puhelimella |
| 16.11.2026 | A5, Y7 | Paikan laite: jokaisella paikalla (koti, päivätoiminta, työpaja) voi olla oma laite; asukkaan puhelin hakee vain omat tietonsa sen paikan laitteelta, jonka verkossa on. Pilotissa paikallinen malli, suunnittelu ja muokkaus paikan laitteella; EU-pilvi harkitaan pilotin jälkeen | Tieto pysyy talossa; ohjaajan puhelin palvelimena on hauras |
| 16.11.2026 | – | Tietosuoja ennen pilottia: T1 avaimet Keystoreen, T2 lukitusnäytön ilmoitukset, T3 pakollinen viennin salasana, T4 paikan laitteella ohjaajatila vain näytön lukituksella, T5 ohjaajan näkymien piilotus, T6 kaikkien tietojen poisto; päivittäinen salattu automaattinen varmuuskopio valittuun kansioon | `docs/TIETOSUOJA_PUUTELISTA.md` |
| 16.11.2026 | G2 | Värikoodaus, kello korteissa ja kotinäytön widget tehty ennen pilottia | Sovellus näyttää pilotissa siltä kuin sen pitää |
| 16.11.2026 | G6, Y8 | Ohjattuun aloitukseen yhteiset asetukset ja perusrutiinit kaikille asukkaille sekä kopiointi muille | Ohjaajan työ yksikköä perustettaessa |
| 16.11.2026 | – | Asiakkaan teksteissä "aikuinen" korvattu asukaskohtaisella sanalla (ohjaaja/läheinen/aikuinen); testitilan vapaa roolinvaihto oletuksena pois | Asumisyksikön asukkaat ovat aikuisia; roolit selviksi |
| 16.11.2026 | – | Kotiversio (omaisnäkymä) omana sovelluksena samasta koodista, käsitellään kokonaisuutena pilotin jälkeen | Yksikkömalli ja kotikäyttö ovat eri tarpeita |
| 15.11.2026 | B1, C2, C6 | Valinta kahdesta, odotetaan-tila, oma tila (asiakkaan oma tieto), En ymmärrä, viestijono, tuen taso, käytön seuranta pilottiin; Androidin automaattinen varmuuskopio pois | Palautteen ja auditoinnin muutokset ennen pilottia |
| 15.11.2026 | P10, Y1–Y8 | **Yksikkömalli** (suunnitelma `docs/YKSIKKOMALLI_SUUNNITELMA.md`). Y1 yksikkö ja alayksiköt (esim. Villa Metsäketo → Omena, Appelsiini, Päärynä), yhteinen runko (ruokailut). Y2 päivän toiminnat omina ryhminään (päivätoiminta, työpaja); asukas yhteen tai ei mihinkään; samaan aikaan eri toimintoja eri asukkailla. Y3 asukkaan oma aika saman toiminnan sisällä poikkeuksena (erillinen ryhmä vain oikeille vuoroille). P10 asukas näkee vain sen, mitä tapahtuu siellä, missä hän on (sijainti viikko-ohjelmasta, ei paikannusta) eikä koskaan toisten tietoja. Y4 peruttu toiminta = kotona, ellei muuta määritellä. Y5 ohjaaja valitsee asukaskohtaisesti, kuinka paljon ja pitkälle asukas näkee (toiminnan aikana yksi kortti / runko). Y6 siirtymäkortit ehdotuksina, ohjaaja aktivoi. Y7 yksikön oma laite palvelimena lähiverkossa (ei ohjaajan puhelin, ei pilvi nyt); TV-näkymässä vain yhteiset asiat. Y8 kaikki ohjatussa aloituksessa kerran, esikatselun kanssa | Asukasta kuormittaa tieto, joka ei koske häntä; arki on yksikössä yhteistä ruokailujen osalta mutta päivät eroavat. Ohjaajan puhelin palvelimena on hauras (vuorot, lepotila); oma laite pitää tiedot talossa ja sopii P9:ään. Pilvi myöhemmin, jos tarve. Asiakastietolain soveltuminen selvitettävä ennen myyntiä (V7) |
| 15.11.2026 | A2, G4 | A2 toteutettu: "Tarvitsen vielä hetken" ajastimessa ja päiväohjelmassa, kun aika on loppumassa tai loppunut. Ohjaajan rajat Ei/2/3/5 min (oletus 3) ja kerran/kahdesti toimintoa kohden (oletus kerran); lisäaika ilman PIN-koodia. Kun lisäaikaa ei voi antaa, painike näyttää viestin aikuiselle eikä pysäytä aikaa. Uusi viestikuva (G4 osittain: lisäaika) | Käyttäjän keino neuvotella ajasta ilman, että ajan säätö avautuu kokonaan; ei porsaanreikää loputtomaan aikaan. Oletukset vahvistetaan ohjaajilta pilotissa |
| 14.11.2026 | A1, P | A1 toteutettu: ennakkovaroitus "Pian vaihtuu – Seuraavaksi" ajastimessa ja päiväohjelmassa, asetus Ei/2/5/10 min (oletus 5), ei ääntä. Lisäksi "Tarvitsen tauon" ajastimeen ja tapahtumakorttiin (aina ilman PIN-koodia, pysäyttää ajan). A2 (lisäaika) yhä tekemättä | Siirtymät heikoin alue; tauko aina vapaa (laatuportti R5) |
| 14.11.2026 | – | Automaattinen laatuportti (ClientQualityGateTest, R1–R9) | Sovitut periaatteet pysyvät voimassa jokaisessa käännöksessä; ei korvaa käyttäjäpalautetta |
| 14.11.2026 | A3, A4 | Toteutettu: Pysyy samana -osio, Kuuntele, Tarvitsen tauon, ilta-ennakko (klo 18), muistutus muutokselle, Palauta alkuperäinen -vahvistus | Muutoksen epävarmuus; ohjaajan virheen korjaus turvallisesti |
| 14.11.2026 | B6, C7 | Oikeudet ja päätökset -kortti (B6) ja pysyvän jakopäätöksen malli kirjattu lukuun 12; liitetty ohjattuun aloitukseen (G6) | Oikeudet näkyviksi ja tarkistettaviksi; asiakas päättää ensisijaisesti itse |
| 14.11.2026 | C7 | Päiväni tarkennettu (luku 12): kosketukseton malli ja kotiverkkovaihtoehto avoimena kysymyksenä Autismisäätiölle | Asiakkaan laite on henkilökohtainen; ei automaattista raportointia ilman asiakkaan valintaa |
| 14.11.2026 | C7, X4 | Päiväni kirjattu ehdotukseksi (toteutus pilotin jälkeen); yksiköiden välinen viestintä hylätty (X4) | Asiakkaan oma kerronta ja itsemääräämisoikeus; asiakastietolain vaatimukset ja P9 |
| 14.11.2026 | G1–G6 | Grafiikan ja ohjatun aloituksen ehdotukset kirjattu (v1.1), tila Ehdotettu | Kuva-arvio 38 kuvasta ja ohjaajapuolen kuormitus |
| 14.11.2026 | – | Tutkielma v1.0 laadittu; kaikki ehdotukset tilassa Ehdotettu (A5 päätetty jo aiemmin) | Lähtötaso ennen priorisoinnin vahvistamista |

## Avoimet kysymykset

- Autismisäätiölle: olisiko kotiverkkovaihtoehdosta (luku 12) hyötyä, ja millä tietosuoja- ja tietohallintoehdoilla se sallittaisiin?

- Hyväksytäänkö A1–A7 ennen pilottia, ja missä järjestyksessä suhteessa käyttöliittymän yhtenäistämiseen (osa 3) ja kuvakkeeseen?
- Mikä yksikkö pilottiin, ja ketkä autistiset aikuiset mukaan arvioimaan?
- Lisäajan oletusrajat (0/3/5 min, kerran) – vahvistetaan ohjaajien kanssa.
- Android 16:n paikallisverkon käyttölupa: tarkistettava, kun targetSdk nostetaan (nyt 35).

## Lähteet

- Ben-Sasson, A. ym. (2019). Update of a Meta-analysis of Sensory Symptoms in ASD. *J Autism Dev Disord* 49(12). https://cris.haifa.ac.il/en/publications/update-of-a-meta-analysis-of-sensory-symptoms-in-asd-a-new-decade/
- Casassus, M. ym. (2019). Time perception and autistic spectrum condition: A systematic review. *Autism Research* 12(10). https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6852160/
- Demetriou, E. A. ym. (2018). Autism spectrum disorders: a meta-analysis of executive function. *Molecular Psychiatry* 23(5). https://www.omicsdi.org/dataset/biostudies-literature/S-EPMC5984099
- Howell, M., Dounavi, K. & Storey, C. (2019). To choose or not to choose? *Review J Autism Dev Disord* 6(1). https://link.springer.com/article/10.1007/s40489-018-00154-7
- Jenkinson, R. (2019). Uncertainty and Negative Affect in Individuals with ASD. Väitöskirja, University of Sheffield. https://etheses.whiterose.ac.uk/24827
- Jenkinson, R., Milne, E. & Thompson, A. (2020). Intolerance of uncertainty and anxiety in autism. *Autism* 24(8). https://orca.cardiff.ac.uk/id/eprint/140692/
- Knight, V., Sartini, E. & Spriggs, A. D. (2015). Evaluating Visual Activity Schedules as Evidence-Based Practice. *J Autism Dev Disord* 45. https://cbmt.franciscan.edu/wp-content/uploads/sites/32/2021/07/Knight-et-al.-2015_Visual-Schedules.pdf
- Leon, Y. ym. (2023). Problem behavior occasioned by variable-sequence transitions. *Behavioral Interventions*. https://ifp.nyu.edu/2023/journal-article-abstracts/bin-1968/
- McVey, A. J. ym. (2023). Mindshift in autism. *Frontiers in Psychiatry* 14. https://www.frontiersin.org/articles/10.3389/fpsyt.2023.1251058
- Steinbrenner, J. R. ym. (2020). Evidence-Based Practices for Children, Youth, and Young Adults with Autism. NCAEP. https://ncaep.fpg.unc.edu/sites/ncaep.fpg.unc.edu/files/imce/documents/EBP%20Executive%20Summary.pdf
- Vasquez, S. ym. (2017). Effects of Advance Notice on Problem Behavior Occasioned by Interruptions. *Behavior Analysis in Practice* 10. https://link.springer.com/article/10.1007/s40617-017-0187-7
- Waters, M. B. ym. (2009). Visual schedules and extinction plus DRO (tiivistelmä). *JABA* 42. https://behavioristbookclub.com/aba-research/aba-fundamentals/separate-and-combined-effects-of-visual-schedules-and-extinction-plus/

Versiohistoria: v1.8 (24.11.2026) tilat päivitetty 15.–16.11. töiden mukaan (A5, B1, C2, C6, G2, G6, P10), P9 tarkennettu (verkko-oikeus lähiverkon siirtoon), tietoturvakorjaukset · v1.7 (15.11.2026) yksikkömalli (P10, Y1–Y8), A5 korvattu · v1.6 (15.11.2026) A2 toteutettu (odottaa puhelintestiä), lisäajan viestikuva · v1.5 (14.11.2026) A1, A3, A4 toteutettu (odottaa puhelintestiä), laatuportti · v1.4 (14.11.2026) B6 Oikeudet ja päätökset -kortti, pysyvä jakopäätös · v1.3 (14.11.2026) luku 12: Päiväni-tarkennus ja kotiverkkovaihtoehto · v1.2 (14.11.2026) C7 Päiväni, X4 yksiköiden välinen viestintä · v1.1 (14.11.2026) luku 11: grafiikka ja ohjattu aloitus (G1–G6) · v1.0 (14.11.2026) ensimmäinen versio.
