# AikaPolku – yksilöllisen toiminnanohjauksen kehityssuunnitelma

**Dokumenttitunnus:** AIKAPOLKU-AAC-ROADMAP-001  
**Versio:** 1.4  
**Päiväys:** 1.11.2026  
**Projektin lähtöversio:** AikaPolku UI Refresh 1.0 + P0-20a + P0-21d + lupakorjaus  
**Viimeisin toteutusvaihe:** P0-21e – ohjaajan lukitus  
**Tila:** P0-21d testattu käyttäjän puhelimella 1.11.2026. Yksikkötestit (Robolectric) ja lupakorjaus mainissa. P0-21e:n yksikkötestit ajetaan skriptillä; käyttöliittymän hyväksymistesti puhelimella on tekemättä.

## 1. Tuotteen tarkoitus

AikaPolku on offline-first Android-sovellus, jonka ydinkysymykset ovat:

1. **Mitä teen nyt?**
2. **Mitä teen seuraavaksi?**
3. **Kuinka paljon aikaa on jäljellä – jos haluan nähdä ajan?**
4. **Miten voin ilmaista, mitä tarvitsen?**

Käyttöympäristöjä ovat koti, varhaiskasvatus, koulu, päivätoiminta, asumispalvelut ja itsenäinen arki. Kohderyhmä ei rajaudu ikään tai diagnoosiin; toiminnot valitaan yksilöllisten tarpeiden ja käyttäjän toiveiden perusteella. Sovellus ei ole täysimittainen AAC-järjestelmä eikä lääkinnällinen laite. Sitä ei saa markkinoida diagnoosin hoidoksi.

## 2. Keskeiset suunnitteluperiaatteet

- Käyttäjän oma tahto ja oikeus kieltäytyä, pyytää apua tai taukoa ovat keskeisiä.
- Ei automaattista epäonnistumismerkintää, jos aika loppuu.
- Käytön ei tule edellyttää numeroiden tai puheen ymmärtämistä.
- Käytetään samaa kuvakieltä ajastimessa, Ensin–Sitten-näkymässä, päiväohjelmissa ja PDF:ssä.
- Kaikki tärkeät toiminnot toimivat ilman käyttäjätiliä tai verkkoyhteyttä; **Androidin synteettisen puheen offline-toimivuus riippuu laitteen puhemoottorista ja kielipaketista.**
- Toimintapainikkeet ovat suuria ja saavutettavia; kuva ei ole ainoa tiedonvälitystapa.
- Äänet, animaatiot ja ajan näyttäminen ovat valinnaisia.
- Pääsääntöisesti vain yksi nykyinen tehtävä korostuu. Seuraava tehtävä on silti nähtävissä.

## 3. Olemassa olevat ominaisuudet: lähtötilanne 1.11.2026

| Ominaisuus | Tila | Huomio |
|---|---|---|
| Analoginen ajastin | Toteutettu | Taustatoiminnan kattava hyväksymistesti tarvitaan |
| Ensin–Sitten | Toteutettu | Voidaan hyödyntää omia toimintokortteja |
| 21 yhtenäistä oletuskorttia, mm. Aamukokous ja Ulkoilu | Toteutettu UI Refresh 1.0:ssa | Käytettävyyttä eri kohderyhmillä ei ole vielä mitattu |
| Omat valokuvat ja tallennetut puheohjeet | Toteutettu | Varmistettava säilyminen päivityksissä |
| Päiväohjelmat | Toteutettu | Käsin, kellonajalla tai kestolla ohjattavat vaiheet |
| Neljä valmista rutiinipohjaa | Toteutettu P0-20a:ssa | Aamutoimet, Iltatoimet, Koulupäivä, Päivätoiminta |
| Ajan näyttötavat | Toteutettu P0-20a:ssa | Numerot, vain rengas, rauhallinen |
| Vaiheen +5 min ja ohitus | Toteutettu P0-20a:ssa | Ajan päättyminen ei kuittaa tehtävää |
| Päiväohjelman PDF ja Android-tulostus | Toteutettu | PDF-tallennus testattu; fyysinen tulostus erikseen |
| UI Refresh 1.0 | Käännetty ja asennettu | Visuaalinen loppuhyväksyntä jatkuu |

## 4. P0-21 – ohjattu toimintanäkymä (ensimmäinen toteutus)

### 4.1 Käyttötapaus

**NYT: Pukeutuminen**  
**VAIHE 2/5: Housut**  
**Aikaa jäljellä:** numerot / pienenevä rengas / rauhallinen näkymä  
**Tarvitsen apua** – **Tarvitsen tauon**  
**Valmis – seuraava osavaihe** / **Ohita osavaihe**

### 4.2 Toiminnallisuus

1. Yhteen päiväohjelman tehtävään voidaan lisätä enintään 16 tekstimuotoista osavaihetta.
2. Osavaiheet etenevät kuittauksella. Suoritusnäkymä näyttää vaiheen numeron ja selkeän nimen.
3. Ajastin koskee **koko tehtävää**, ei jokaista osavaihetta erikseen. Osavaiheen vaihtuminen ei nollaa käynnissä olevaa ajastinta.
4. Tehtävän viimeisen osavaiheen jälkeen siirrytään seuraavaan päiväohjelman tehtävään.
5. Käyttäjä voi ohittaa osavaiheen ilman negatiivista palautetta.
6. **Tarvitsen apua** ja **Tarvitsen tauon** avaavat isotekstisen viestin. Jos ajastin on käynnissä, ajastin keskeytetään ja loppuhälytys perutaan kunnes käyttäjä jatkaa ajastinta.
7. Viestin voi näyttää toiselle henkilölle ja pyytää Androidin puhemoottoria sanomaan viestin ääneen. Viesti **ei** lähde verkossa ohjaajalle tai muulle vastaanottajalle.
8. Alkuperäiset ohjelmat ilman osavaiheita säilyvät toiminnaltaan ennallaan.
9. Osavaiheen etenemisindeksi tallennetaan olemassa olevan päiväohjelman etenemistilan yhteyteen. Se palautetaan sovelluksen uudelleen avaamisen jälkeen, mikäli sama ohjelma on yhä käynnissä.
10. Pukeutuminen saa uudessa rutiinipohjassa oletusosavaiheet **Sukat → Housut → Paita → Takki → Kengät**. Hampaiden pesulle annetaan kolmen osavaiheen malli. Oletusosavaiheita voi muokata tai poistaa.

### 4.3 Tämän ensimmäisen version rajaukset

- Osavaiheilla on tällä hetkellä **teksti** ja päätehtävän yhteinen kuvakortti; oma erillinen kuva jokaiselle osavaiheelle suunnitellaan P0-22-vaiheeseen.
- Osavaiheilla ei vielä ole erillistä ajastinta eikä omaa puheäänitallennetta.
- Viestipainikkeet eivät muodosta etäilmoitusta. Puhetoisto edellyttää Androidin toimivaa suomenkielistä puhemoottoria.
- Ensimmäinen versio ei sisällä vielä **En halua nyt** / **Liikaa melua** -painikkeita tai mukautettua viestikirjastoa. Näitä arvioidaan käytettävyystestissä.
- Aika ei automaattisesti käynnisty uudelleen viesti-ikkunan sulkemisen jälkeen.
- Osavaiheita ei tällä hetkellä tulosteta päiväohjelman PDF:n erillisiksi riveiksi. Se on seuraavan toteutuserän tehtävä.

### 4.4 Tietomalli ja yhteensopivuus

`AP16Step` saa taaksepäin yhteensopivan kentän `subSteps: List<String> = emptyList()`. Uudet versiot lukevat vanhat JSON-ohjelmat ilman migraation pakollista ajamista. Sarjallistus lisätään sekä `AP16Store`-tallennukseen että `rememberSaveable`-luonnoksen tilaan. Etenemisindeksi tallennetaan `aikapolku_program_progress_v1`-asetuksiin avaimella `sub_step_index`.

**Tietoturva:** viestitekstit tai osavaiheet eivät lähetä automaattisesti tietoa palvelimelle. Omia kuvia ja äänitteitä ei siirretä.

## 4A. P0-21b – nykyisen tehtävän selkeä suoritusnäkymä

- Päiväohjelman suoritusnäkymä sisältää vain nykyisen tehtävän pääkortin, ajan ja seuraavan toiminnon sekä kiinnitetyt apu-, tauko- ja Valmis-painikkeet.
- Osavaihetta vaihdettaessa yläosan vieritys palautuu ensimmäiseen kohtaan.
- Harvemmin käytetyt toiminnot ovat Lisää-valikon takana.
- P0-21b on käyttäjän kuvakaappauksen perusteella käytössä, mutta ulkoasun viimeistelyssä havaittiin suuri tyhjä tila ja tarve konfiguroida tulevan vaiheen näkyvyys.

## 4B. P0-21c – toiminnon ennakointi ja valittavat viestiäänet

### 4B.1 Visuaalinen periaate

- Nykyinen tehtävä on edelleen ainoa **aktiivinen** tehtävä ja näytön pääsisältö.
- Seuraavan osavaiheen tai tehtävän esikatselu näytetään vain, jos käyttäjä valitsee sen. Kolme tilaa: **Piilota**, **Vain nimi**, **Himmennetty kuvakortti**.
- Oletus: **Himmennetty kuvakortti**. Se ei reagoi napautukseen eikä käynnistä tehtävää.
- Jos nykyisessä tehtävässä on osavaiheita, esikatselu kertoo ensin seuraavan osavaiheen; vasta viimeisessä osavaiheessa seuraavan päätehtävän.
- Himmennys toteutetaan hillityllä pintavärillä ja pienemmällä visuaalisella painoarvolla, **ei varsinaisella sumeutuksella**, jotta kuva ja teksti säilyvät tunnistettavina.
- Nykyisen tehtävän kuvan kokoa kasvatetaan kohtuullisesti; tyhjää tilaa vähennetään ilman koristeita tai lisäpainikkeita.
- Päätoiminnot jäävät näkyvään alapalkkiin: **Tarvitsen apua**, **Tarvitsen tauon**, **Valmis** ja **Lisää**.

### 4B.2 Viestipainikkeiden kolme äänivaihtoehtoa

Jokaiselle viestille (**Tarvitsen apua**, **Tarvitsen tauon**) asetetaan erikseen:

1. **Ei ääntä** – oletus; suuri viestiteksti näkyy aina.
2. **Laitteen puheääni** – Androidin TextToSpeech, suomeksi, mikäli suomenkielinen puheääni on asennettu.
3. **Oma äänitys** – puhelimen mikrofonilla tallennettu viesti; valittavissa vasta, kun tallenne on olemassa.

Äänen valinta tapahtuu suoritusnäkymän **Lisää → Näkymä ja viestiäänet** -asetuksissa. Painallus keskeyttää edelleen käynnissä olevan ajastimen. Jos ääni on käytössä, viesti yritetään toistaa automaattisesti *vasta käyttäjän nimenomaisen viestipainalluksen jälkeen*. Viestin voi toistaa uudelleen viesti-ikkunasta.

**TTS ei tarkoita automaattisesti neural-ääntä.** Puhemoottorin laatu ja verkkoriippumattomuus määräytyvät puhelimen asennetuista puhepaketeista. Erillistä pilvipuhepalvelua, analytiikkaa tai verkkolähetystä ei toteuteta P0-21c:ssa.

**Tietosuoja:** Äänitykset tallennetaan vain sovelluksen omaan yksityiseen hakemistoon. Mikrofonilupa pyydetään vasta tallennusta aloitettaessa. Ääniä ei lähetetä palvelimelle. Käyttäjän aiemmat päiväohjelmat ja korttiäänitteet säilyvät erillään.

### 4B.3 Rajaukset

- Valinnat ovat tässä vaiheessa tämän Android-laitteen yhteisiä paikallisia asetuksia, eivät henkilökohtaisia käyttäjäprofiileja.
- Ei puheäänen latausta, keinotekoisia pilvipalveluääniä, äänen jäljittelyä tai palvelinsynkronointia.
- Eri osavaiheiden omat kuvat ja äänet kuuluvat edelleen P0-22:een.
- Ei muutoksia ajastuksen taustalogiikkaan, kuittausmenettelyyn tai PDF-vientiin.

### 4B.4 Hyväksymistestit

- [ ] `assembleDebug` ja `installDebug` onnistuvat P0-21b-lähtöversiosta.
- [ ] Näytä seuraava: Piilota / Vain nimi / Himmennetty kortti vaihtuvat ja asetus pysyy sovelluksen uudelleenavaamisen jälkeen.
- [ ] Pukeutuminen: Sukat → Housut esikatseluna; viimeisessä osavaiheessa seuraava päätehtävä.
- [ ] Seuraavan vaiheen esikatselukorttia ei voi painaa aktiiviseksi.
- [ ] Viestipainike näyttää suuren tekstin myös Ei ääntä -tilassa.
- [ ] TTS-viesti kuuluu, jos laitteella on sopiva puhemoottori, eikä lähde itsestään ennen painallusta.
- [ ] Äänitys voidaan aloittaa, lopettaa, tallentaa ja toistaa kummallekin viestille erikseen.
- [ ] Apu-/taukopyynnön jälkeen ajastin pysyy tauolla; jatko vaatii uuden painalluksen.
- [ ] Päiväohjelmat, osavaiheet, käyttäjän kortit ja PDF-tallennus toimivat ennallaan.
- [ ] Suuri fontti, vaakatila ja näytön alareunan toimintopainikkeet eivät peity.


## 4.5 P0-21d: Puheäänien valinta ja painikkeiden viimeistely

**Päätös 1.11.2026:** Androidin puheääniä valitaan niiden todellisen saatavuuden perusteella. Androidin `Voice`-tietomalli ei ilmoita luotettavaa sukupuolta, joten sovellus ei väitä yksittäistä ääntä mies- tai naisääneksi. Näytetään kaikki laitteen tarjoamat suomenkieliset äänet, esikuuntelu ja tieto siitä, tarvitseeko ääni verkkoyhteyden. Tallennetaan valitun äänen tekninen tunniste ja nopeus paikallisesti. Äänen puuttuessa käytetään suomenkielistä laiteoletusta.

- Viestipainikkeiden asetukset: **Ei ääntä / Puheääni / Oma ääni** apu- ja taukiviestille erikseen.
- Erillinen **Valitse ja kuuntele puheääni** -näkymä; äänten määrä riippuu Androidin TTS-moottorista.
- Puheen nopeus välillä 0,8–1,2 ×. Asetukset tallentuvat paikallisesti ja toimivat seuraavallakin käynnistyskerralla.
- Ennen TTS-alustuksen valmistumista pyydetty viesti asetetaan jonoon, jotta ensimmäinen painallus ei katoaisi.
- Varsinaisen suorituksen alareuna: kaksi samanlaista apu-/taukopainiketta, suuri **Valmis**, toissijainen **Lisää**. Ei ylimääräisiä pysyviä säätimiä.
- Ajastimen käynnistyspainikkeessa lukee tilanteen mukaan **Aloita**, **Tauko** tai **Jatka**, ei kaksiselitteistä "Aloita / Jatka".
- Äänipainikkeet eivät muutu maksullisiksi. Ei käyttäjätiliä, pilvilähetystä eikä uusia käyttöoikeuksia.

**P0-21d:n hyväksyntä:** Android-käännös ja asennus onnistuvat; puhelimen suomenkieliset TTS-äänet listautuvat; vähintään yksi vaihtoehto voidaan esikuunnella, mikäli moottori tarjoaa ääniä; ääni ja puhenopeus pysyvät tallessa; apu-/taukopainikkeiden valittu ääni kuuluu; oma tallenne toimii edelleen; vaiheiden eteneminen ja ajastus eivät muutu; suurella fontilla painikkeet pysyvät näkyvissä.

## 4C. P0-21e – Ohjaajan lukitus (päätös 1.11.2026)

**Tausta:** käyttäjätestissä havaittiin, että ajastimen minuuttipikavalinnat olivat näkyvissä ajastimen käydessä ja painallus muutti käynnissä olevan ajan. Ajan muuttaminen, ohjelmien muokkaus ja asetukset kuuluvat ohjaajalle.

**Aina käyttäjän käytössä, ei koskaan lukittu:** Tarvitsen apua, Tarvitsen tauon, Valmis / seuraava osavaihe, Ohita osavaihe, ajastimen Aloita/Tauko/Jatka, päiväohjelman käynnistys, koko ohjelman katselu ja tulostus.

**Lukituksen takana (kun lukitus on päällä):** ajan muuttaminen (pikavalinnat, kellotaulun säätö, +5 min lisäaikaa), ajastimen nollaus, päiväohjelman lopetus, päiväohjelmien ja korttien luonti/muokkaus/poisto, Ensin–Sitten-korttien valinta ja "+ Lisää vaihe" sekä kaikki asetukset. Lukittuna nämä piilotetaan; tilalla on pieni 🔒-painike.

**Ilman lukitustakin:** minuuttipikavalinnat ja kellotaulun säätö näkyvät vain, kun ajastin on pysähdyksissä.

**PIN-koodi ja turvallisuus:**
- 4 numeroa; liian helpot (esim. 1111, 1234, 4321) hylätään.
- Tallennetaan vain suolattu PBKDF2-tiiviste sovelluksen yksityisiin asetuksiin.
- Viisi väärää yritystä → esto 30 s; toistuvat estot pitenevät (60 s, 2 min …, enintään 15 min). Kellon siirto ei pidennä estoa yli enimmäisajan.
- Avaus voimassa 2 minuuttia viimeisestä kosketuksesta; lukko menee päälle aina, kun sovellus siirtyy taustalle. Poikkeus: ohjaajan itse avaama valokuvanvalitsin ei lukitse (2 minuutin raja on silti voimassa).
- Unohtunut PIN nollataan puhelimen omalla näytön lukituksella (PIN, kuvio, sormenjälki). Jos puhelimessa ei ole näytön lukitusta, siitä varoitetaan jo PIN-koodia asetettaessa.

**Käyttöönotto:** oletuksena pois. Kun ensimmäinen päiväohjelma tallennetaan, kysytään kerran: "Käyttääkö tätä laitetta myös joku muu?" Lukituksen voi ottaa käyttöön, vaihtaa tai poistaa aloitussivun kohdasta Ohjaajan asetukset. Asetussivulla on vinkki Androidin näytön kiinnityksestä.

**Toteutus:** linjaus yhdessä paikassa (`AppAction`, `CaregiverAccessState`), PIN ja estot `CaregiverLockStore`, avaus `CaregiverSession`, käyttöliittymä `CaregiverLockUi.kt`. Uusia riippuvuuksia ei tullut. Käyttämättömät +1 min / −1 min -toiminnot poistettiin aktiivisesta ajastimesta.

**Rajaukset:** muokkausnäkymä jätetään auki, jos lukko menee päälle kesken muokkauksen, jotta tallentamattomat muutokset eivät katoa. Sovelluksesta poistumista ei estetä (ks. näytön kiinnitys). Ohita osavaihe pysyy käyttäjän oikeutena; lukittavuus arvioidaan piloteissa.

**Hyväksymistestit puhelimella:**
- [ ] Ilman lukitusta: pikavalinnat katoavat ajastimen käynnistyessä ja palaavat pysäytettäessä.
- [ ] Ota lukitus käyttöön (heikko PIN hylätään, eri PIN-koodit huomataan).
- [ ] Lukittuna: ajastimessa ei pikavalintoja, kellotaulun säätöä, Nollaa- eikä Asetukset-painiketta; 🔒 avaa PIN-kyselyn.
- [ ] Lukittuna: Ensin–Sitten-kortit eivät avaudu muokattaviksi; "+ Lisää vaihe" ei näy.
- [ ] Lukittuna: päiväohjelmia ei voi luoda, muokata eikä poistaa; Käynnistä ja Tulosta toimivat.
- [ ] Lukittuna ajossa: Lisää-valikossa vain Ohita ja Näytä koko ohjelma sekä 🔒 Ohjaajan toiminnot; apu, tauko ja Valmis toimivat.
- [ ] Viisi väärää PIN-koodia → esto ja odotusaika näkyvät.
- [ ] Avaus lukittuu 2 minuutin käyttämättömyyden jälkeen ja kun sovellus siirretään taustalle.
- [ ] Unohditko PIN-koodin? → puhelimen oma lukitus → lukitus poistuu, ohjelmat säilyvät.
- [ ] Ensimmäisen päiväohjelman tallennuksen jälkeen kysymys näytetään kerran.

## 5. Jatkokehitys ja priorisointi

| Vaihe | Sisältö | Prioriteetti | Päätös |
|---|---|---|---|
| P0-21 / 21b / 21c / 21d | Viestipainikkeet + osavaiheet + selkeä suoritusnäkymä + esikatselu + viestiäänet ja puheäänen valinta | 1 | P0-21c käytössä, P0-21d testattava |
| P0-21e | Ohjaajan lukitus: PIN, piilotetut ohjaajan toiminnot, avaus 2 min | 1 | Toteutettu, puhelintesti tekemättä |
| P0-22 | Osavaiheiden omat kuvat, ääni ja tarvittaessa kestot; kuvallinen muokkaus | 2 | Suunniteltu |
| P0-23 | Junior, Standard ja erityisen pelkistetty käyttötila; ohjattu muokkaus | 3 | Suunniteltu |
| P0-24 | Suunnitelma B / poikkeuspäivä / ennakointikortit | 4 | Suunniteltu |
| P0-25 | Paikallinen vienti, varmuuskopiointi ja palautus | 5 | Suunniteltu |
| P0-26 | Tumma tila, viimeistelty kontrasti ja ääni-/aistiasetukset | 6 | Suunniteltu |
| P1 | Viestikirjaston yksilöllinen muokkaus, erilaiset siirtymäajastimet, monen profiilin hallinta | Myöhemmin | Testitulosten mukaan |
| P2 | Vapaaehtoinen pilvisynkronointi ja organisaatiotoiminnot | Myöhemmin | Erillinen kustannus- ja tietosuojapäätös |

### 5.1 P0-22:n erillinen määrittely

- Osavaihe voi olla `id + nimi + kuva + oma ääni + mahdollinen kesto`.
- Kuvan pitää tukea käyttäjän omaa valokuvaa, valmista kuvakorttia ja tekstiä.
- Etenemisen aikarakenne päätetään käyttäjätestin jälkeen: yhteinen koko tehtävän ajastin on edelleen oletus, osavaihekohtainen ajastin on valinnainen.
- Osavaiheet tulevat PDF:ään vain käyttäjän valinnalla, jotta tuloste ei muutu liian pitkäksi.

## 5.2 P0-25 tarkennus: jako- ja varmuuskopiopaketti (päätös 1.11.2026)

- Ohjelmat jaetaan ja varmuuskopioidaan yhtenä `.aikapolku`-tiedostona (ZIP): `ohjelma.json`, `kuvat/`, `aanet/`.
- Paketti sisältää omat valokuvat ja äänitteet, myös osavaiheiden (P0-22) kuvat ja äänet. Henkilökohtainen paketti on kohderyhmälle tärkeä.
- Kuvat pienennetään jaon yhteydessä (pisin sivu noin 1024 px, JPEG). Tavoite: tyypillinen päiväohjelma muutama megatavu.
- Jako Androidin jakovalikolla (WhatsApp asiakirjana, sähköposti, Drive, Quick Share). Vastaanotto avaamalla tiedosto Aikapolussa.
- Paketissa on formaatin versionumero. Tuonti tarkistaa rakenteen, koon ja version ennen tallennusta.
- Pakettiin ei tallenneta henkilön nimeä eikä diagnoosia. Jakokanavasta päättää ja vastaa jakava organisaatio.
- Ei palvelinta, käyttäjätiliä eikä automaattista päivitystä. Pilvipohjainen jakelu arvioidaan pilottien jälkeen (P2).
- QR-koodi on kevyt lisävaihtoehto vain valmiita kortteja käyttäville rutiineille.
- **Rajaus:** pakettien salasanasuojaus toteutetaan myöhemmässä vaiheessa.
- **Edellytys:** tallennusmuodon versiointi (Room tai versioitu JSON) ennen P0-25:tä tai samassa vaiheessa.

## 6. Free/Premium-linjaus

**Free (suositus):** ajastin kaikkine näyttötapoineen, Ensin–Sitten, muutama muokattava ohjelma, perusosavaiheet, käyttäjän viestipainikkeet ja niihin liittyvät olennaiset saavutettavuusasetukset, perusääni sekä perustuloste. Käyttäjälle pitää jäädä mahdollisuus sanoa **ei**, pyytää apua ja pyytää taukoa ilman maksua.

**Premium (suositus, ei vielä ohjelmoitu):** rajattomat rutiinit, laajemmat mallikirjastot, monipuoliset profiili- ja osavaihemallit, erilliset tulostusasettelut, laajempi toistuvien ohjelmien hallinta sekä mahdolliset ammattilaisominaisuudet. **Alustava kertamaksu 9,99 €**, jonka kysyntä on vielä testattava. Rajattoman pilvipalvelun sisällyttämisestä kertamaksuun ei tehdä lupausta.

## 7. Saavutettavuus ja eettisyys

- Viestipainikkeet eivät saa olla maksumuurin takana.
- Sama näkymä ei sovi kaikille: Junior/Standard on valinta, ei diagnoosiin perustuva automaattinen luokitus.
- Käyttäjää ei pakoteta katsomaan väheneviä numeroita. Ajan näyttö voidaan valita pois.
- Vältetään punaisia virheilmoituksia ja kielteistä suorituksen pisteytystä.
- Painikkeet vähintään 48 dp, riittävä kontrasti, ruudunlukijalle ymmärrettävät nimet.
- Ei automaattisia rangaistuksia tai kuittausta ajastimen päättymisestä.
- Sovellus tukee keskustelua ja ohjausta; se ei korvaa käyttäjän henkilökohtaista kommunikointimenetelmää tai henkilöstön läsnäoloa.
- Toteutuksessa huomioitava erityisesti Androidin tekstin suurennus, näytön kierto ja TTS-kielipaketin puuttuminen.

## 8. P0-21:n hyväksymistestit

**Käännös ja tietojen säilyminen**

- [ ] `assembleDebug` onnistuu.
- [ ] `installDebug` onnistuu oikealle puhelimelle.
- [ ] Aiemmat päiväohjelmat avautuvat ja vanhat kuvat/äänet toimivat.
- [ ] Uusi muokattu ohjelma säilyttää osavaiheet sovelluksen sulkemisen ja uudelleen avaamisen jälkeen.

**Osavaiheet**

- [ ] Luo aamutoimien malli, jossa Pukeutuminen sisältää viisi osavaihetta.
- [ ] Avaa ohjelma; näytöllä näkyy selvästi `VAIHE 1/5: Sukat`.
- [ ] Paina Valmis; näkyy `VAIHE 2/5: Housut`.
- [ ] Osavaiheen siirtyminen ei käynnistä tai nollaa koko tehtävän ajastinta.
- [ ] Ohita osavaihe toimii ilman erillistä ilmoitusta epäonnistumisesta.
- [ ] Viimeinen osavaihe siirtää seuraavaan päätehtävään.
- [ ] Osavaiheen sijainti palautuu, kun ohjelma avataan uudelleen.

**Viestipainikkeet**

- [ ] Tarvitsen apua näyttää suuren tekstin.
- [ ] Tarvitsen tauon näyttää suuren tekstin.
- [ ] Käynnissä oleva ajastin pysähtyy viestiä pyydettäessä.
- [ ] Ajastus jatkuu vain uudesta Aloita/Jatka-painalluksesta.
- [ ] Androidin puhetoisto toimii, jos suomen puhemoottori on käytettävissä.
- [ ] Ilman puhemoottoria viesti on silti näytettävissä.

**Visuaalisuus ja yleinen toiminta**

- [ ] Ajan numerot, rengas ja rauhallinen tila toimivat kaikissa osavaiheissa.
- [ ] Suuri fontti ja vaakatila eivät peitä tukipainikkeita.
- [ ] PDF-vienti toimii edelleen ja näyttää päätehtävät.
- [ ] Ensin–Sitten-toiminto, ajastin ja korttikirjasto eivät rikkoudu.

## 9. Muutoshallinta

1. P0-21d:n aloituspisteenä käytetään **asennettua P0-21c-koodia**. Asennus tarkistaa muutettavien lähdetiedostojen SHA-256-tiivisteet ennen muokkausta.
2. Käännösvirheen sattuessa lähdekoodit palautetaan varmuuskopiosta automaattisesti.
3. Gradle-käännös ei muuta käyttäjän omia kuvia, puheviestejä eikä päiväohjelmien sovellusdataa.
4. Uudet Kotlin-tiedostot ja tämä dokumentti tallennetaan erikseen `docs/`-kansioon.
5. Androidin todellinen käännös ja hyväksymistesti toteutetaan Windows-koneella.
6. Työ voidaan merkitä hyväksytyksi vasta, kun käyttäjä on testannut sen omalla laitteellaan.

## 10. Päätösloki

| Päivä | Päätös |
|---|---|
| 1.11.2026 | P0-21 ensimmäisenä: nykyinen tehtävä + osavaiheet + aika + apu-/taukopyynnöt samassa näkymässä. |
| 1.11.2026 | Perusviestipainikkeet jätetään maksuttomiksi. |
| 1.11.2026 | Ajan esitystapa on käyttäjän valittavissa; ajastuksen toimintaa ei muuteta. |
| 1.11.2026 | Osavaiheiden omat kuvat ja äänet kuuluvat P0-22-jatkototeutukseen. |
| 1.11.2026 | Junior/Standard-tilat, poikkeuspäivät ja synkronointi rajataan erillisiin päivityksiin. |
| 1.11.2026 | P0-21b:n visuaalisen testin perusteella nykyisen tehtävän kuva suurennetaan ja seuraavan vaiheen näkyvyys tehdään valittavaksi. |
| 1.11.2026 | Viestipainikkeiden ääni on valittavissa: hiljainen, laitteen TTS tai oma paikallinen äänitys. Oletuksena hiljainen. |
| 1.11.2026 | P0-21d: puheäänten esikuuntelu ja valinta, säädettävä puhenopeus sekä kompaktimpi painikeasettelu. |
| 1.11.2026 | P0-25: ohjelmien jako ja varmuuskopio `.aikapolku`-pakettina (ZIP) omine kuvineen ja äänineen. Ei palvelinta. |
| 1.11.2026 | Pakettien salasanasuojaus siirretään myöhempään vaiheeseen. |
| 1.11.2026 | Premium-hinta alustavasti 9,99 € kertamaksu; hinta tarkistetaan myöhemmin. |
| 1.11.2026 | Projekti Git-versionhallintaan: github.com/jarno1303/aikapolku. |
| 1.11.2026 | Yksikkötestit (Robolectric) ja korjaus: ajastin ei kaadu eikä esty, jos hälytys- tai ilmoituslupa puuttuu. |
| 1.11.2026 | P0-21e: ohjaajan lukitus (PIN). Apu, tauko, Valmis, ohitus ja Aloita/Tauko eivät ole koskaan lukossa. Oletuksena pois; kerran kysytään ensimmäisen ohjelman tallennuksen jälkeen. |
| 1.11.2026 | Ajan pikavalinnat ja kellotaulun säätö näkyvät vain ajastimen ollessa pysähdyksissä. |

---

*Tämä dokumentti toimii jatkokeskusteluissa referenssinä nimellä **AIKAPOLKU-AAC-ROADMAP-001**. Päivitetään dokumentin versionumeroa ja päätöslokia, kun muutoksia hyväksytään.*
