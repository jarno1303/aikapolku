# AikaPolku – yksikkömalli: toteutussuunnitelma

**Versio:** 1.0 · **Päivitetty:** 15.11.2026 · **Tila:** sovittu, toteutus alkaa lisäaikahaaran (A2) testauksen ja yhdistämisen jälkeen

Päätökset on kirjattu tutkielman Päätökset-lokiin (15.11.2026, tunnisteet P10, Y1–Y8). Tämä tiedosto kertoo, miten ne rakennetaan.

---

## 1. Mitä rakennetaan

Asumisyksikkö, jossa on useita asukkaita, rakennetaan kerroksista:

| Kerros | Esimerkki | Kenelle |
| --- | --- | --- |
| **Yksikkö ja alayksiköt** | Villa Metsäketo → Omena, Appelsiini, Päärynä | Kaikki asukkaat. Yhteinen runko: ruokailut, iltapala, hiljaisuus (arki ja viikonloppu erikseen) |
| **Päivän toiminnat** | Päivätoiminta ma–pe 9–14, Työpaja ma/ke/pe | Asukas liitetään yhteen toimintaan tai ei mihinkään. Asukkaalla voi olla oma aika (esim. klo 10–13) |
| **Asukkaan omat** | Lääkkeet, harrastus tiistaisin, poikkeukset | Yksi asukas |

Periaatteet (tutkielma P10, Y1–Y8):

1. **Asukas näkee vain sen, mitä tapahtuu siellä, missä hän on (P10).** Sijainti päätellään viikko-ohjelmasta, ei paikannuksesta. Toiminnan aikana yksikön kohdat eivät näy hänelle eivätkä yksikön muutokset koske häntä.
2. **Asukas näkee aina vain omat tietonsa.** Ohjaaja näkee usean asukkaan tiedot oman (ala)yksikkönsä rajoissa.
3. **Peruttu toiminta = asukas on kotona**, ellei muuta määritellä. Yksikön kohdat palaavat näkyviin muutoksena.
4. **Ohjaaja valitsee asukaskohtaisesti, kuinka paljon ja kuinka pitkälle asukas näkee** (aikajana nyt ja Asiakkaan näkymä -asetukset + uusi: toiminnan aikana yksi kortti / toiminnan runko).
5. **Siirtymäkortit** ("Lähtö päivätoimintaan", "Paluu kotiin") ovat valmiina ehdotuksina; ohjaaja ottaa käyttöön tarvittaessa.
6. **Näkymä laitteen mukaan:** asukkaan laite, ohjaajan puhelin, yhteinen TV. TV:ssä vain yhteiset asiat: ei nimiä, ei kuvia asukkaista, ei poissaoloja, ei kenenkään omia tapahtumia.
7. **Yksikön oma laite on palvelin.** Pysyvästi päällä oleva laite (TV:n Android-laite tai seinätabletti) jakaa tiedot yksikön Wi-Fi-verkossa. Tiedot eivät lähde talosta. Verkkolupa vain lähiverkkoon (P9 tarkennettu). Pilvivälitys voidaan lisätä myöhemmin samalla siirtomallilla.
8. **Kaikki tämä tehdään ohjatussa aloituksessa kerran**, esikatselun kanssa.

---

## 2. Vaiheet

Jokainen vaihe: varmuuskopiohaara → toteutus → erillinen tarkastaja → käännös ja testit PC:llä → puhelintesti → yhdistäminen. Laatuporttiin lisätään jokainen uusi asiakasnäkymä.

| Vaihe | Sisältö | Arvio | Malli |
| --- | --- | --- | --- |
| **V0** | Lisäaikahaaran (A2) käännös, testit, puhelintestit ja yhdistäminen mainiin | – | Opus Medium |
| **V1 Tietomalli** | Yksikkö ja alayksiköt; ryhmälle laji (yksikkö / toiminta); poikkeus "osallistuu eri aikaan" (alku, loppu, viikonpäivät); päällekkäisyysvaroitus; vanhojen ryhmien siirto uuteen malliin. Puhdas logiikka + testit | 2–3 pv | Opus High |
| **V2 Sijaintisääntö** | Asukkaan sijainti kellonaikana (koti / toiminta); yksikön kohdat piiloon toiminnan aikana; peruttu toiminta → koti + muutos; muutokset vain paikalla oleville; asukkaan asetus "toiminnan aikana: yksi kortti / runko"; siirtymäkortit ehdotuksina; ohjaajalle piilotetut himmeinä "Ei näy asukkaalle" | 3–4 pv | Opus High |
| **V3 Tänään yksikössä** | Ohjaajan etusivulle: kuka on missä ja milloin palaa, montako syö kotona | 1–2 pv | Opus High |
| **V4 Ohjattu aloitus** | Yksikön nimi ja asukkaat → yhteiset ruokailuajat → toiminnat ja kuka käy missä (ja oma aika) → kunkin tuen taso ja kuinka pitkälle näkee → esikatselu asukkaana. Hyödyntää nykyisiä pohjia (Päivätoiminta, Asumisen arki, Koulupäivä) | 3–4 pv | Opus High |
| **V5a Siirron kokeilu** | Pieni koe kahdella laitteella: löytäminen yksikön verkossa, parinmuodostus, yksi muutos perille. Tarkistetaan pilottiyksikön Wi-Fi (sallitaanko laitteiden välinen liikenne) | 1–2 pv | Opus High |
| **V5b Tiedonsiirto** | Laiteroolit (yksikön laite / ohjaaja / asukas); parinmuodostus QR-koodilla ja salattu yhteys; muutosloki ja ristiriitojen ratkaisu (ohjaajien verkon ulkopuolella tekemät muutokset); **yksikön laite lähettää asukkaan laitteeseen vain hänen omat tietonsa**; "viimeksi päivitetty" ohjaajalle; korvaa A5:n | 8–12 pv | Opus High (arkkitehtuuri halutessa Fable) |
| **V6 TV-näkymä** | Yhteinen näkymä yksikön laitteessa: päivän yhteiset kohdat isoina, yksikön muutokset, ei henkilötietoja; vaakatila, luettavuus etäältä | 2–3 pv | Opus High |
| **V7 Tietosuoja** | Tietosuojaseloste (vain lähiverkko), selvitys asiakastietolain 703/2023 soveltumisesta (THL / juristi), vaikutustenarvioinnin pohja yksiköille | ei koodia | Opus Medium |

**Yhteensä noin 20–30 työpäivää** (V1–V6). Suurin riski on V5b; siksi V5a tehdään ensin pienenä kokeena.

### Pilotti välissä?

V1–V4 toimivat yhdessä laitteessa (ohjaaja vaihtaa asukkaan profiilia). Pienen pilotin voi aloittaa jo niiden jälkeen esimerkiksi yhden asukkaan kanssa, ja V5–V6 rakennetaan pilotin rinnalla. Päätetään, kun V4 on valmis.

---

## 3. Tekniset linjaukset

- **Sijainti on puhdas funktio:** `sijainti(asukas, hetki) = toiminta, jonka (oma) aika kattaa hetken ja jota ei ole peruttu, muuten koti`. Testattavissa ilman laitetta; laatuportti saa säännön R11 ("toiminnan aikana yksikön kohtia ei näy").
- **Ryhmät säilyvät:** nykyinen `Group` (jäsenet, runko, poikkeukset SKIP/REPLACE) saa lajin ja yksikön; uusi poikkeuslaji TIME. Tapahtumatunnukset `grp:<ryhmä>:<tapahtuma>` pysyvät, joten kuittaukset ja muutokset toimivat ennallaan.
- **Asukkaan tiedot rajataan lähteellä:** yksikön laite ei koskaan lähetä asukkaan laitteeseen toisten tietoja (ei edes piilotettuina). Tämä testataan erikseen.
- **TV:n sisältö kootaan vain yksikkölajin ryhmän rungosta ja sen muutoksista;** asukasprofiileja ei lueta lainkaan.
- **Siirtomalli** suunnitellaan niin, että lähiverkon tilalle tai rinnalle voi myöhemmin tulla pilvivälitys ilman tietomallin muutosta.

---

## 4. Myöhemmin (ei nyt)

- Asukkaan laite kahdessa paikassa (kotiyksikkö + toisen organisaation päivätoiminta): kumpikin vie vain omat tapahtumansa, tieto ei kulje paikkojen välillä (linjassa X4:n kanssa).
- Varoitus, jos muutos ei ehdi pitkään poissa olevan asukkaan laitteeseen (esim. viikonloppukotikäynti): vain jos pilotti osoittaa tarpeen.
- Pilvivälitys, jos asiakkaat sitä vaativat.
