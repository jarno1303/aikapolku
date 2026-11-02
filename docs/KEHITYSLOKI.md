# AikaPolku – kehitysloki

## 31.10.2026
- Tuotekonsepti määritelty
- UI/UX asetettu ensisijaiseksi vaatimukseksi
- Ajastin, Ensin–Sitten ja Päiväohjelmat päätetty ydintoiminnoiksi
- Ääniohjeet suunniteltu valinnaisiksi
- Android Kotlin / Jetpack Compose valittu
- Dokumentaatiorakenne perustettu

## Seuraava vaihe
- Luodaan Android Studio -projekti
- Rakennetaan kolme päänäkymää
- Toteutetaan ajastimen ensimmäinen toimiva versio

## P0-03
- Lisatty -1 min ja +1 min.
- Nollaa palauttaa valitun alkuperaisen ajan.
- Lisatty Takaisin aloitukseen -painike.
- Puhelintesti odottaa tarkistusta.


## P0-04
- Ajastimen ulkoasua tiivistetty.
- Ajastimen lisäasetukset avattavassa osiossa.
- Ensin-Sitten tukee omia valokuvia.
- Toiminnon nimeä ja kuvausta voi muokata.
- Ensin-toiminnolle voi käynnistää ajastimen.
- Kuvista tallennetaan paikallinen kopio.
- Puhelintestin tulokset odottavat.

## P0-04b
- Ensin-Sitten-ajastin nayttaa molemmat kuvat.
- Ajastinympyraa pienennetty.
- Turhat ajan pikavalinnat piilotettu toimintotilassa.
- Takaisin palauttaa Ensin-Sitten-nakymaan ja pysayttaa ajastimen.
- Puhelintesti odottaa hyvaksyntaa.

## P0-05
- Ajastinympyra toimii Aloita/Tauko/Jatka-painikkeena.
- Ensin-Sitten-ajastimen ylimaarainen toimintopainike poistettu.
- Ajan saato avattavan valikon takana.
- Ajastimen lopussa valmis toiminto pienenee.
- Seuraava toiminto korostuu kuvallisena.
- Siirtyminen vaatii kayttajan vahvistuksen.
- Seuraava toiminto siirtyy ENSIN-kortiksi.
- Puhelintesti odottaa tarkistusta.

## P0-06
- Ohjatun ajastimen nollaus vaatii vahvistuksen.
- Ajastinympyran kosketuspalaute rajattu ympyran muotoon.
- Ensin-Sitten-ketjulla on erillinen viimeinen toiminto.
- Viimeinen toiminto esitetaan ilman tyhjaa seuraavaa korttia.
- Valmis lopettaa ohjelman.
- Uusi toiminto lisataan vain kayttajan valinnasta.
- Uuden ketjun ajastin edellyttaa kahta valittua toimintoa.
- Puhelintesti odottaa hyvaksyntaa.

## P0-07
- Kortin muokkausohje siirretty i-painikkeen taakse.
- Ensin-Sitten-ajastinympyraa suurennettu.
- Ajan saato siirretty kellotaulun sisaan.
- Ajastimen asetukset siirretty kellotaulun sisaan.
- Asetukset avautuvat erilliseen ikkunaan.
- Nollauksen vahvistus sailyy.
- Puhelintesti odottaa hyvaksyntaa.

## P0-08
- Ensin-Sitten-otsikko muutettu visuaaliseksi toimintopoluksi.
- Ajastinkelloon lisatty pehmea pinta, varjo ja liukuvarikeha.
- Viimeisen toiminnon nakyma tiivistetty.
- Turha alareunan paluupainike piilotettu viimeisessa vaiheessa.
- Viimeisen toiminnon kaksi paatoimintoa vierekkain.
- Omat valokuvat ja aiemmat toiminnallisuudet sailytetty.
- Puhelintesti odottaa hyvaksyntaa.

## P0-09 – Korttien viimeistely
- Kuvallisten korttien ulkoasu uusittu.
- ENSIN/SITTEN-merkinnat kuvan paalla.
- Toimintojen nimet selkeammin esilla.
- Ajastusnakymaan kompaktit kortit.
- Viimeisen vaiheen otsikko ja kuva yhdistetty.
- Turhat selitetekstit poistettu.
- Paapainikkeet mukautuvat nayton leveyteen.
- Pienelle naytolle kompaktimpi kello.
- Puhelintesti odottaa hyvaksyntaa.

## P0-11 – Kayttoliittyman uudistus
- Ajastin omaan Kotlin-komponenttiin.
- Kuvakortit omaan komponenttitiedostoon.
- Yhteinen varipaletti ja kuvankasittely erotettu.
- Viimeinen toiminto ja lopetusnakyma erotettu.
- ENSIN/SITTEN-merkintojen toistoa vahennetty.
- Kellon numerot keskitetty.
- 10–1 visuaalinen lahtolaskenta sailyy.
- Ajan saato ja asetukset kellon sisalla.
- Ensin-Sitten-nakymaa tiivistetty.
- Ilman ajastinta siirtyminen vahvistetaan.
- Puhelintesti odottaa hyvaksyntaa.

## P0-12 – Analoginen ajastinkiekko
- Uusi ajastin alkuperaisesta P0-11:sta erilliseen tiedostoon.
- 60 minuutin kellotaulu ja minuuttimerkinnat.
- Punainen jaljella olevan ajan sektori.
- Digitaalinen aika kellotaulun alapuolella.
- Ensin-Sitten-kuvat kompakteina kellon ylapuolella.
- Tumma Tauko/Jatka-painike.
- Kellotaulun kosketus toimii myos Tauko/Jatka-toimintona.
- Viimeisten 10 sekunnin suuri laskenta.
- Ajan saato, asetukset ja nollauksen vahvistus sailytetty.
- Puhelintesti odottaa hyvaksyntaa.

## P0-13 – Kosketettava minuuttikiekko
- Kellotaulua voi saataa sormella vetamalla.
- Saato toimii 1–60 minuutin asteikolla.
- Vastapaivaan aika lisaantyy.
- Myotapaivaan aika vahenee.
- Ajan muuttaminen keskeyttaa kaynnissa olevan ajastimen.
- +1 min ja -1 min -painikkeet poistettu.
- Nollaa on oma painike ja vaatii vahvistuksen.
- Tauko/Jatka on erillinen painike.
- Puhelintesti odottaa hyvaksyntaa.

## P0-14 – Ajastimella tai ilman aikarajaa
- Ensin-Sitten-nakyman minuuttipainikkeet poistettu.
- Kaksi etenemistapaa: Ajastimella ja Ilman aikarajaa.
- Ajastin avautuu kaynnistymatta automaattisesti.
- Aika saadetaan kellotaulusta.
- Manuaalisessa tilassa nykyinen toiminto pysyy nakyvissa.
- Valmis – seuraavaan siirtaa viimeiseen toimintoon.
- Takaisin palauttaa Ensin-Sitten-valintaan.
- Puhelintesti odottaa hyvaksyntaa.

## P0-15 – Pysyva toimintokorttikirjasto
- Omien korttien luonti, muokkaus ja poisto.
- Korttien valinta Ensin-Sitten-toimintoon.
- Nykyisen kortin tallennus kirjastoon.
- Korttien nimet, kuvaukset ja kuvapolut paikalliseen tallennukseen.
- Valitut kuvat kopioidaan sovelluksen omaan kansioon.
- Viimeksi valittujen Ensin-Sitten-korttien pysyva tallennus.
- Ei uusia Gradle-riippuvuuksia.
- Puhelintesti odottaa hyvaksyntaa.

## P0-15b – Kortit, kellotaulu ja aanimerkki
- ENSIN/SITTEN siirretty kuvien ylapuolelle.
- Kellotaulun numerot siirretty sisaaanpain.
- Paattymisaanen valinta asetuksiin.
- Aanivaihtoehdot: pois, lyhyt ja selkea.
- Aanen esikuuntelu.
- Aani oletuksena pois paalta.
- Puhelintesti odottaa tarkistusta.

## P0-16 – Päiväohjelmat
- Päiväohjelmien luonti ja tallennus.
- Tallennettujen ohjelmien muokkaus ja poisto.
- Omat ja valmiit toimintokortit.
- Toimintojen järjestyksen muuttaminen.
- Käsin, kestolla tai kellonajalla määritettävät vaiheet.
- Tallennetun päiväohjelman käynnistys.
- Kestoajastimen tauotus ja jatkaminen.
- Voimakkaampi mediaäänikanavaa käyttävä päättymisääni.
- Puhelintesti odottaa hyväksyntää.

## P0-16b
- Kellotaulun saato estetty ajastuksen aikana.
- Ajan saato sallittu, kun ajastin ei ole kaynnissa.
- Paattymisaanen laukaisu siirretty ajastimen ydinlogiikkaan.
- Aanimerkki kayttaa mediaaanikanavaa.
- Selkea piippaus toistetaan kolme kertaa.
- Aaniasetus sailyy oletuksena pois paalta.
- Puhelintesti odottaa hyvaksyntaa.

## 1.11.2026 – Versionhallinta, testit ja korjaukset
- Projekti GitHubiin (jarno1303/aikapolku), .gitignore ja README.
- 74 yksikkötestiä (Robolectric): tallennus, ajastin, kellotaulu, rutiinipohjat, asetukset.
- Korjaus: ajastin ei kaadu eikä esty, jos hälytys- tai ilmoituslupa puuttuu.

## P0-21e – Ohjaajan lukitus
- PIN-koodi (4 numeroa, suolattu tiiviste, heikot hylätään).
- Ohjaajan toiminnot piilossa lukittuna: ajan muutos, nollaus, lisäaika, lopetus, muokkaus, asetukset.
- Tarvitsen apua, Tarvitsen tauon, Valmis, ohitus ja Aloita/Jatka aina käytössä (itsemääräämisoikeus).
- Ajastimen Tauko ja muut pysäytykset lukittuna vain PIN-koodilla.
- Väärien yritysten esto kasvavalla viiveellä; lukko taustalle siirryttäessä.
- Avaus kiinteät 2 min PIN-koodista (kosketukset eivät pidennä); yläpalkki avauksen ajan.
- Lukko heti päälle PIN-koodin asettamisen jälkeen.
- Unohtunut PIN nollataan puhelimen omalla näytön lukituksella.
- Minuuttipikavalinnat poistettu; kellotaulun säätö vain pysäytettynä.
- Yläpalkki ja Takaisin-painike tilarivin alle (Android 15+ reunasta reunaan -näkymä).
- Ohjaajan asetukset aloitussivulta; kertakysymys ensimmäisen ohjelman jälkeen.
- Puhelintesti odottaa hyväksyntää.

## P0-22a – Kuvakeskeinen suoritusnäkymä
- Iso kuva ja nimi; eteneminen palloina ja tekstinä.
- Tarvitsen apua / tauon heti kuvan alla, sitten Valmis, sitten Seuraavaksi.
- Aikamerkki on samalla Aloita/Tauko-painike; Tauko lukittuna PIN-koodilla.
- Apu- ja taukopyyntö koko näytön viestinä; Jatka palauttaa ajan.
- Osavaiheiden työkuvat (pukeutuminen, hampaiden pesu) ja uudet ruokakuvat.
- Osavaihe Laita hammasharja takaisin (vanha nimi tunnistetaan).
- Puhelintestin korjaus: näkymä mahtuu ruudulle suurellakin fontilla; apu/tauko kuvake tekstin yllä.
- Ensin–Sitten lukittuna: 🔒 kuvakkeena, Muokkaa piilossa.
- Puhelintesti odottaa hyväksyntää.
- Kuvasarja valmis: 31 yhtenäistä kuvaa (kortit, osavaiheet, apu- ja taukoviesti).

## P0-23 – Päiväohjelman luonti vaiheittain
- 1 Valitse: kuvaruudukko luokittain, napautusjärjestys = ohjelman järjestys.
- 2 Järjestä: veto kahvasta, poisto ja Kumoa, osavaiheet avattavissa.
- 3 Ajat: omaan tahtiin, kestot tai aloitusaika + kestot; kiinteät kellonajat.
- 4 Tallenna: esikatselu ja nimiehdotus.
- Vanhat ohjelmat toimivat ennallaan; uudet testit (ProgramTimingTest, tallennus).
- Puhelintesti odottaa hyväksyntää.

## Siivous – rakenne, kuollut koodi ja optimointi
- Kuollut koodi poistettu: vanhat ajastimet (P011/P012), MainActivityn vanhat näkymät, käyttämättömät korttinäkymät ja mallipohjan värit (~2 700 riviä).
- Koodi jaettu alipaketteihin (data, timer, programs, programs.editor, programs.run, firstthen, cards, caregiver, voice, ui); tiedostot ja luokat nimetty sisällön mukaan (esim. P018Timer → BackgroundTimer, AP16Store → ProgramStore).
- Päiväohjelmanäkymästä erotettu lista, valmis-näkymä ja Lisää-valikko omiksi osikseen.
- Yhteiset värit yhteen paikkaan (AppColors); kaksi äänittäjää ja kaksi kuvan kopiointia yhdistetty.
- Kuvat puretaan taustasäikeessä ja säilytetään välimuistissa (sujuvampi kuvaruudukko ja suoritusnäkymä).
- Tallennetut tiedot, asetukset ja ulkoasu ennallaan. Puhelintesti odottaa hyväksyntää.

## Kieliversiovalmius ja laatu
- Kaikki käyttäjälle näkyvät tekstit resursseiksi (strings.xml, suomi oletuksena; monikkomuodot plurals-resursseina).
- Valmiiden korttien, osavaiheiden ja päiväohjelmapohjien nimet käännettäviä; vanhat suomenkieliset nimet tunnistetaan aina.
- Puheäänen kieli resurssina (tts_locale). Ohje uuden kielen lisäämiseen: docs/KAANNOKSET.md.
- Valitsematon Ensin–Sitten-kortti tallennetaan kielestä riippumattomasti (vanha "Valitse toiminto" tunnistetaan).
- Tyypitetyt tilat merkkijonojen sijaan: StepMode, TimeMode, AppPage, ProgramsView, SupportKind, PinProblem (tallennusmuoto ennallaan).
- Tilaluokat: ProgramRunController (päiväohjelman suoritus), FirstThenCards ja FlowTimer (Ensin–Sitten ja ajastin).
- Uudet testit: suorituksen säännöt (15), Ensin–Sitten-kulku (8), käyttöliittymätestit (3), käännösten tunnistus.
- Puhelintesti odottaa hyväksyntää.

## Kellotaulun napautus
- Napautus kellotaululla asettaa ajan lähimpään 5 minuuttiin (esim. 45 kohdalle = 45 min); veto säätää minuutin tarkkuudella.
- Samat lukitussäännöt kuin vedolla: vain pysäytettynä ja ohjaajan sallimana. 4 uutta testiä.
- Korjaus (puhelintesti): kortin ja osavaiheen kuva vaihtuu heti samassa näkymässä.

## Suoritusnäkymän tiivistys (puhelintesti)
- Otsake yhdellä rivillä ("NYT · Hampaiden pesu") ja eteneminen palloina sen alla; "Vaihe x / n" ja "Tehtävä x / n" vain ruudunlukijalle.
- Pitkä otsikko pienenee (22–30 sp, enintään 2 riviä), jotta kuva saa enemmän tilaa.
- Seuraavaksi-rivi yhdellä rivillä pienellä kuvalla; "Seuraava tehtävä" ja "Seuraavaksi" yhdistetty.
- Kaikki valmista -näkymä: iso merkki, ohjelman nimi ja tehdyt tehtävät pikkukuvina.

## Vaihe 1: käyttäjäprofiilit
- Jokaisella profiililla on omat päiväohjelmat, kortit, Ensin–Sitten-valinnat, suoritus- ja ääniasetukset sekä ajastimen näyttötapa. Ohjaajan lukitus on laitekohtainen.
- Vanha data kuuluu sellaisenaan ensimmäiseen profiiliin (oletusprofiili käyttää vanhoja tiedostonimiä), joten siirtoa ei tarvita eikä mitään katoa.
- Profiilit hallitaan ohjaajan asetuksissa: lisäys, nimi ja kuva, käyttöönotto ja poisto. Poisto poistaa profiilin tiedot ja omat kuvat, joita muut profiilit eivät käytä.
- Aloitusnäkymässä näkyy nykyinen profiili, kun profiileja on useampi. Vaihto vaatii PIN-koodin, ellei ohjaaja ole sallinut käyttäjän valita profiilia itse.
- Vaihdettaessa käynnissä olevat ajastimet pysäytetään.
- Korjaus: ajastimen tallennusavaimet eivät enää tule käännettävistä teksteistä (endElapsed, endWall).
- Uudet testit: ProfilesTest (7), ProfileFilesTest (4; myös Windows-polut).

## Vaihe 2: viikko-ohjelma ja Tänään
- Päiväohjelmat-näkymässä välilehdet Ohjelmat ja Viikko. Viikolle lisätään tapahtumia: kellonaika ja sisältö (päiväohjelma tai yksittäinen kortti). Uuden tapahtuman voi lisätä monelle päivälle kerralla (ma–pe, joka päivä).
- Vuoroviikot (A ja B) kytkimellä; ohjaaja valitsee, kumpi viikko on nyt, ja viikot vuorottelevat itsestään.
- Aloitusnäkymän Tänään-lista näkyy, kun päivälle on tapahtumia: nykyinen tapahtuma korostettuna ("Aloita"), menneet himmeinä. Päiväohjelma käynnistyy suoraan, kortti näytetään isona.
- Viikko-ohjelma on profiilikohtainen. Päiväohjelman poisto poistaa sen tapahtumat viikolta (vahvistuksessa maininta).
- Viikonpäivien nimet tulevat laitteen kielestä (java.time), joten niitä ei tarvitse kääntää.
- Uudet testit: WeekScheduleTest (7), WeekStoreTest (4), TodaySectionTest (2).

## Vaihe 3: ilmoitukset ja kuittaus
- Tapahtumakohtainen ilmoitus: alkaessa tai 5/10/15 min ennen. Korteille oletuksena päällä, päiväohjelmille pois.
- Ilmoituksessa kortin tai ohjelman kuva, nimi ja kellonaika; useamman profiilin laitteessa profiilin nimi. Napautus avaa tapahtuman, jos se kuuluu nykyiseen profiiliin (profiilia ei vaihdeta ohi PIN-koodin).
- Korttitapahtuman voi kuitata ilmoituksen Valmis-painikkeella tai sovelluksessa. Tänään-listassa "✓ Tehty 20.04"; ohjaajalle Viikko-välilehdellä "Kuittaukset: eilen ja tänään" (Tehty / Ei kuitattu / Tulossa).
- Kuittaukset säilyvät vain eiliseltä ja tältä päivältä. AikaPolku muistuttaa, mutta se ei ole lääkehoidon kirjaamisväline (teksti näkyy sovelluksessa).
- Ajastettuna on aina vain seuraava ilmoitushetki kaikista profiileista. Ajastus uusitaan käynnistyksessä, viikon muuttuessa, profiilin poistossa, puhelimen käynnistyessä, kellonajan/aikavyöhykkeen muuttuessa ja sovelluksen päivittyessä.
- Uudet testit: RemindersTest (7), WeekStoreTest +2.

## Korjaus: tehdyt tapahtumat ja luotettavat muistutukset (puhelintesti OnePlus 12R)
- Tehty tapahtuma näkyy Tänään-listassa himmeänä "✓ Tehty 07.50" ilman Aloita-painiketta; korostus siirtyy seuraavaan tekemättömään. Ensimmäinen kuittausaika säilyy (uusi Valmis ei muuta sitä); kuitattu kortti avautuu katseltavaksi Takaisin-painikkeella.
- Tänään-listasta käynnistetty päiväohjelma merkitään tehdyksi, kun se viedään loppuun.
- Aloita on nimen alla, joten nimi ei katkea.
- Muistutukset herätyskellon tavoin (setAlarmClock): OnePlus ja muut tiukan akunhallinnan puhelimet toimittavat ne ajallaan. Muistutuskanava luodaan heti.
- Ohjaajan asetuksiin "Muistutusten tila": ilmoitukset, tarkat hälytykset ja akun optimointi, korjauspainikkeet ja testimuistutus (1 min).
- Uudet testit: korostus tehtyjen ohi, ensimmäinen kuittausaika säilyy, tehty ilman Aloita-painiketta.

## Tänään-lista aina tiiviinä
- Aloitusnäkymässä enintään: "✓ Tehty tänään: N" -rivi, korostettu nyt-tapahtuma ja kaksi seuraavaa tekemätöntä sekä "Koko päivä (N) ›".
- Koko päivä -näkymässä kaikki tapahtumat: tehdyt ("✓ Tehty 07.50"), väliin jääneet ja tulevat. Väliin jääneet eivät kasaannu aloitusnäkymään.
- Uudet testit: tiivis lista (logiikka ja käyttöliittymä).

## Tiiviimpi aloitusnäkymä
- Tänään-osion otsikko ja yhteenveto yhdellä rivillä ("Tänään · Maanantai   ✓ 1/1 ›"), joka avaa koko päivän. Kun kaikki on tehty, osio on pelkkä tämä rivi.
- Pienempi logo ja otsikko yläreunassa, pienemmät välit: toiminnot näkyvät ilman vieritystä.

## Aloitusnäkymä ilman logoa
- Logo, sovelluksen nimi ja iskulause poistettu aloitusnäkymästä: ylimpänä päivän tilanne ("Tänään · Maanantai ✓ 1/1 ›").
- Profiili oikeassa yläkulmassa pienenä kuvana (vain, kun profiileja on useampi); napautus avaa vaihdon samoin säännöin.
- Ilman päivän tapahtumia ylimpänä "Mitä haluat tehdä?" ja profiilikuva.

## Aloitusnäkymä: vain päivä, valikko ja toiminnot
- Ylärivillä päivän nimi (ja tehty/kaikki-laskuri, kun tapahtumia on) sekä oikeassa yläkulmassa valikko: profiilikuva (useampi profiili) tai valikkokuvake. Valikossa "Vaihda profiilia" ja "Ohjaajan asetukset".
- Poistettu: "Mitä haluat tehdä?", erillinen Ohjaajan asetukset -linkki ja alareunan esittelyteksti. Ensin–Sitten-kortin alateksti lyhennetty ("Kaksi asiaa järjestyksessä").
- Kuitatussa kortissa ei enää toista Takaisin-painiketta (yläreunan Takaisin riittää).

## Yhtenäinen käyttöliittymä, osa 1/3
- Yhteiset osat (PrimaryButton, SecondaryButton, SegmentedChoice, ChoiceRow, OverflowMenu, InfoButton, RoundIconButton) ja kuvakkeet (Icons.Rounded + omat AppIcons: kamera, kuva, kaiutin, mikrofoni, tauko, avoin lukko). Säännöt: docs/UX_TARKISTUSLISTA.md.
- Ensin–Sitten: ⓘ kuvakkeena; "Miten haluat edetä?" ja ⏱/✋-kortit korvattu valitsimella Ajastimella / Omaan tahtiin. Valitse toiminto -ikkuna: nykyinen kortti (muokkaus), Oma kuva + Omat kortit samanlaisina painikkeina, valmiit kortit kuvineen. "Tallenna kirjastoon" siirtyi muokkaukseen ("Tallenna myös omiin kortteihin").
- Omat kortit: rivin napautus valitsee, Muokkaa/Kopioi/Poista ⋮-valikossa; "Tee valmiista kortista oma" poistettu. Kortin muokkaus: Valmis kuva / Oma kuva -painikkeet, puheviesti mikrofoni- ja kaiutinkuvakkeilla.
- Ajastin (pysty ja vaaka): nollaus, aloitus/tauko, lukko ja asetukset kuvakkeina; "TOIMINTOPOLKU" poistettu.
- Lukkopainike ja "Ohjaaja auki" -palkki kuvakkeilla. Alareunan "Takaisin aloitukseen" poistettu. Kirjoitusvirheet korjattu. 15 käyttämätöntä tekstiä poistettu.

## Yhtenäinen käyttöliittymä, osa 2/3
- Yläpalkki kaikissa näkymissä: takaisin-nuoli, sivun otsikko ja lukkopainike aina samassa paikassa (oikealla, kun ohjaajan toiminnot ovat lukossa). Erilliset sivuotsikot ja sisällön lukkopainikkeet poistettu. Muokkain ja suoritus vaihtavat otsikon (ScreenTitle); muokkaimen nuoli palaa edelliseen vaiheeseen tai kysyy hylkäämisestä (BackOverride).
- Ajastin: rengas ilman koloja (ei taustalaatikkoa numeroille), kellotaulu ruudun levyinen, ohjaimet symmetrisesti.
- Päiväohjelmat: Ohjelmat/Viikko-valitsin, tyhjä tila kuvalla, ohjelmakortissa kuva, Käynnistä ja ⋮ (Tulosta, Muokkaa, Poista); valmiit pohjat "Uusi päiväohjelma" -ikkunassa.
- Muokkain: Edellinen/Seuraava-painikkeet, vaiheiden ✓ kuvakkeena, ohje ⓘ:n takana, ≡/✕/🔒 kuvakkeina, ajan kulku valitsimella, kellonajat kynäkuvakkeella, osavaiheet nuolilla.
- Suoritus: Muut toiminnot kuvakeriveinä, tukiviesti ilman "VIESTI"-otsikkoa, Toista ja Jatka kuvakkeilla, näkymä- ja ääniasetukset valitsimilla, äänitys mikrofonikuvakkeella.
- Kaikki valmista -näkymä yhteinen (päiväohjelma ja Ensin–Sitten). Valmis-painikkeet yhtenäisesti "✓ Valmis" kuvakkeella. 11 käyttämätöntä tekstiä poistettu.

## Korjaus: Valmis-painike, ohitus ja Jaa kuvana (puhelintesti)
- Valmis, kun kestotehtävän aikaa on jäljellä: ohjaajan valinta (Näkymä ja viestiäänet, profiilikohtainen) Aina / Kysy ensin (oletus) / Ajan jälkeen. "Ajan jälkeen": painike himmeä ja näyttää jäljellä olevan ajan; jos aikaa ei ole käynnistetty, painike käynnistää sen. Avattu ohjaajan lukko sallii aina (kysyen). Tarvitsen apua ja Tarvitsen tauon toimivat aina.
- Vahinkonaputusten esto: Valmis ei reagoi 1,5 s uuden vaiheen alussa.
- Ohita tehtävä siirretty Muut toiminnot -ikkunaan; ohitetut näkyvät himmeinä Kaikki valmista -näkymässä.
- Päiväohjelmakortin nimi näkyy (pyöreä käynnistyspainike). Ohjelman ⋮-valikkoon "Jaa kuvana" (PNG Androidin jakovalikkoon, FileProvider välimuistista).
- Suorituksen aikapillerin lukko- ja tauko-merkit kuvakkeina.
- Uudet testit: ohitus, esto ja Valmis-asetuksen kaikki tilat (7).

## Ensin–Sitten: selkeämmät kortit ja kortit vuorokauden mukaan (puhelintesti)
- Korjaus: kulun päätyttyä (valmis tai paluu aloitukseen) Ensin–Sitten näyttää taas alkuperäisen parin. Aiemmin molemmissa korteissa jäi näkymään viimeinen toiminto (esim. "Ruokailu"). Kesken oleva kulku säilyy näytön kääntämisen yli.
- Viimeisen toiminnon otsikko "VIIMEINEN TOIMINTO" → "NYT".
- Uusi korttipari: kummassakin kortissa värillinen otsikkopalkki (ENSIN tumma, SITTEN vaalea), välissä iso nuoli ympyrässä, Ensin-kortissa paksumpi reunus. Sama ulkoasu valinnassa, ajastimen yläosassa ja Omaan tahtiin -näkymässä. "1 ENSIN → 2 SITTEN" -rivi poistettu; ohje ⓘ yläpalkissa (TopBarAction).
- Kortit ryhmitelty vuorokauden mukaan otsikoin: Aamu, Päivä, Ilta, Milloin tahansa – sekä Ensin–Sitten-valinnassa että päiväohjelman muokkaimessa. Omat kortit ryhmänsä alussa; oma kortti korvaa samannimisen valmiin. Vanhat sisältöluokat poistettu.
- Omalle kortille valitaan Ajankohta (oletus Milloin tahansa; valmiin kortin pohjalta tehty saa sen ajankohdan). Vanhat kortit ovat Milloin tahansa.
- Tulossa käyttäjän kuvilla: Lounas, Päivällinen, Iltapala, Lääkkeet.
- Uudet testit: kulun palautus ja tilan tallennus (4), ryhmittely (5), ajankohdan tallennus (1).

## Korjaus: kortin nimi, ajastinnäkymän mitoitus ja uudet ruokailukortit (puhelintesti)
- Kortin nimi ei enää katkea kesken sanan ("Pukeutumine / n"): nimi pienenee mahtumaan (FitText).
- Ajastin: korttipari tiiviimpi (kuva 64 dp) ja kellotaulun koko rajataan myös ruudun korkeuden mukaan, jotta kortit, kellotaulu ja painikkeet mahtuvat samalle ruudulle.
- Ajastimen päättymisilmoitus: otsikko "Aika on päättynyt", tekstinä toiminto (ei katkea; puhelin näyttää sovelluksen nimen itse). Luokka hälytys.
- Uudet valmiit kortit: Lounas (Päivä), Päivällinen ja Iltapala (Ilta). Valmiissa pohjissa koulupäivä ja päivätoiminta käyttävät Lounasta, iltarutiini Iltapalaa.
- Uudet testit: sanan katkeaminen (3), uusien korttien ryhmät (1). Valmiiden korttien määrä testeissä 21 → 24.

## Korjaus: ajastin ei pysähdy huomaamatta (puhelintesti)
- Periaate: aika pysähtyy vain selvästi merkitystä taukopainikkeesta.
- Takaisin ajastimesta ei enää pysäytä aikaa. Kesken oleva Ensin-ajastus (käynnissä, tauolla tai aika päättynyt) näkyy aloitus- ja Ensin–Sitten-näkymässä rivinä "Ajastin käynnissä · Aamupala · 3.12 ›", josta palataan samaan ajastukseen. Ensin–Sitten-näkymän pääpainike on silloin "Palaa ajastimeen"; "Aloita aika alusta" erikseen. Aloitusnäkymän Ajastin avaa kesken olevan ajastuksen.
- Keskipainike näyttää aina tauon tai aloituksen kuvakkeen; lukittuna tauossa on pieni lukkomerkki (aiemmin pelkkä lukko näytti lukon avaamiselta, mutta PIN-koodin jälkeen aika pysähtyi).
- Ajan valinta kellotaululta toimii ennen käynnistystä kaikissa näyttötavoissa (ennen vain "Numerot ja rengas"). Asetettu kesto näkyy kellotaulun alla ("Aika: 5 min"; rauhallisessa tilassa vain ohjaajalle).
- Rauhallinen: rengas neljässä osassa, yksi poistuu kerrallaan, joten eteneminen näkyy ilman numeroita.
- Sama vaaka-asennon ajastimeen. Uudet testit: kesken oleva ajastus (2), rauhallisen osat (1).

## Vaihe 4: muutoskortti
- Ohjaaja tekee koko päivän näkymässä (Tänään / Huomenna) tapahtuman ⋮-valikosta muutoksen yhdelle päivälle: Peruttu, Tilalle muuta (kortti tai päiväohjelma) tai Uusi aika (saman päivän sisällä). Syy tekstinä ja äänitteenä (valinnainen automaattitoisto). Viikko-ohjelma ei muutu. Muutoksen voi muokata tai perua.
- Milloin käyttäjä näkee muutoksen valitaan muutoskohtaisesti: Heti (huomisen muutos jo tänään), Muutospäivän aamuna tai Vasta tapahtuman aikaan (aikaisin alkuperäinen/uusi aika tai ennakkomuistutus). Edellinen valinta on valmiina (profiilikohtainen).
- "Näytä mikä korvattiin" (oletus päällä): pois päältä käyttäjä näkee vain uuden toiminnan ja muutosmerkin. Perutussa alkuperäinen näkyy aina.
- Muutoksen vakiomerkki: oranssi kolmio ja kaksi vastakkaista nuolta, sama joka paikassa. Muutosväri oranssi erottuu tavallisesta turkoosista.
- Käyttäjä: aloitusnäkymän päivän otsikon alla muutosrivi ("MUUTOS · Tänään / Huomenna"), muutosnäkymä (ruksattu alkuperäinen → uusi, uusi aika, syy, Kuuntele, Selvä). Selvä kuittaa muutoksen nähdyksi. Päivän listoissa muutettu tapahtuma merkillä, peruttu yliviivattuna ja himmeänä.
- Muistutukset seuraavat muutosta: siirretty uuteen aikaan, korvattu uudella sisällöllä ("Muutos: Ulkoilu"), peruttu muutosilmoituksena alkuperäiseen aikaan ("Muutos: Uinti on peruttu").
- Ohjaaja: Viikko-välilehdellä Muutokset-kortti (tänään ja huomenna, milloin näkyy käyttäjälle, nähty klo). Kuittauksissa korvatut uudella nimellä, perutut pois.
- Muutokset ovat profiilikohtaisia (ProfileScope.CHANGES), yli viikon vanhat ja poistettujen tapahtumien muutokset siivotaan. Koskee vain tätä puhelinta (ei synkronointia).
- Huom: ilman ohjaajan lukitusta ohjaajan ja käyttäjän näkymää ei erotella; ajastettu näkyminen toimii vain käyttäjän näkymässä.
- Uudet testit: muutoslogiikka ja muistutukset (13), tallennus (4).

## Vaihe 5: vienti ja tuonti
- Ohjaajan asetuksissa "Vienti ja tuonti" (PIN-koodin takana). Viedään kaikki profiilit (varmuuskopio, näkyy kun profiileja on useampi), tämä profiili tai valitut päiväohjelmat; päiväohjelman ⋮-valikossa "Vie tiedostoksi".
- Tiedosto .aikapolku: zip (data.json + media/). Mukana päiväohjelmat, omat kortit, viikko-ohjelma, profiilin asetukset sekä niiden kuvat ja äänitteet. Kuvat pienennetään (pisin sivu 1600 px). Ei viedä: kuittaukset, päivän muutokset, suorituksen edistyminen, Ensin–Sitten-valinta, ohjaajan PIN-koodi.
- Salasanasuojaus: AES-256-GCM, avain PBKDF2-HMAC-SHA256 (120 000 kierrosta), vähintään 6 merkkiä. Oletuksena päällä profiilien viennissä, pois ohjelmapohjissa. Muokattu tai väärällä salasanalla avattu tiedosto torjutaan.
- "Ilman henkilötietoja" ohjelmapohjille (oletus päällä): omat valokuvat korvataan valmiilla kuvalla vaiheen nimen mukaan tai jätetään pois, äänitteet jätetään pois. Muuten muistutus henkilötiedoista ennen jakamista.
- Jaa (Androidin jakovalikko) tai Tallenna laitteelle. Tuonti: Tuo tiedostosta tai avaus suoraan sähköpostin liitteestä (vaatii ohjaajan PIN-koodin). Esikatselu (profiilit, ohjelmat, kortit, viikko-ohjelma), salasana tarvittaessa.
- Kohde: uutena profiilina (oletus profiileille, mitään ei korvata; samanniminen profiili saa "(2)") tai nykyiseen profiiliin (oletus ohjelmapohjille): ohjelmat ja kortit lisätään, täysin samanlaiset ohitetaan, samannimiset saavat "(2)"; viikko-ohjelma korvataan vain erikseen valittaessa, ja sen tapahtumat osoittavat tuotuihin ohjelmiin. Kaikkien profiilien tiedosto tuodaan aina uusina profiileina.
- Uudemman version tiedosto torjutaan selkeällä viestillä (formaatin versio 1).
- Uudet testit: paketointi, salaus ja mediaviittaukset (8, myös Windows-polut), yhdistäminen ja anonymisointi (5), vienti–tuonti kokonaisuutena (3).

## Pikamuutos (muutoskortin helpompi teko)
- Ohjaajalle "Tee muutos" aloitusnäkymän valikossa (PIN-koodi lukittuna). Myös koko päivän näkymän ⋮-valikko vie samaan näkymään valmiiksi valitulla tapahtumalla. Vanha muutosikkuna poistettu.
- Kolme vaihetta isoin painikkein: Mikä muuttuu? (Tänään / Huomenna, tapahtumat kuvineen; päiväohjelmassa Koko ohjelma tai yksittäinen vaihe) → Mitä tilalle? (Peruttu, Siirrä aikaa, toinen päiväohjelma tai kortti samasta kuvallisesta valinnasta kuin Ensin–Sitten) → Valmis? (yhteenveto). Syy, äänite, näkymisen ajankohta ja "Näytä mikä korvattiin" Lisävalinnoissa (kiinni, edellisen muutoksen arvoilla). Olemassa olevan muutoksen voi muokata tai perua.
- Päiväohjelman vaiheen muutos myös etukäteen: vaihe perutaan tai korvataan kortilla sinä päivänä. Ohjelmaa käynnistettäessä (ja keskeytynyttä jatkettaessa) perutut vaiheet ohitetaan ja näkyvät lopussa ohitettuina, korvatut vaihtuvat (ajoitus säilyy, osavaiheet kortin mukaan). Suorituksessa muutosmerkki ja "Muutos · ennen: Uinti". Laskuri ("2/4") ei laske peruttuja.
- Kesken suorituksen: Muut toiminnot → "Muutos tähän vaiheeseen" (ohjaaja): Ohitetaan tänään tai kortti tilalle. Tallentuu päivän muutokseksi (säilyy, jos ohjelma keskeytyy) ja merkitään nähdyksi.
- Ensin–Sitten: kun Sitten-kortti vaihdetaan Ensin-ajastuksen ollessa kesken, kysytään "Näytetäänkö muutoksena?". Näytä muutos → muutosnäkymä (vanha ruksattuna → uusi, Selvä) ja paluu ajastimeen.
- Aloitusnäkymän muutosrivit, päivän lista ja ohjaajan Muutokset-kortti näyttävät myös vaihemuutokset ("Koulupäivä: Uinti → Ulkoilu"). Muistutukset eivät muutu vaihemuutoksesta.
- Uudet testit: vaihemuutokset (7), suoritus perutuin ja korvatuin vaihein (5).

## Muutos näkyväksi aloitusnäkymään
- Ohjaajalle (lukko auki tai lukitus ei käytössä) jokaisella Tänään-rivillä oranssi muutosmerkki: napautus avaa pikamuutoksen suoraan tälle tapahtumalle (kortti: Mitä tilalle?, päiväohjelma: koko ohjelma tai vaihe). Nykyinen muutos on pohjana.
- Aloitusnäkymässä Tänään-listan alla iso "Tee muutos" -painike (myös huominen ja listan ulkopuoliset tapahtumat). Lukittuna kumpikaan ei näy; valikon "Tee muutos" säilyy.
- Automaattinen käyttöliittymätesti (ChangeFlowUiTest, 6 testiä, ajetaan skriptissä ilman puhelinta): muutosmerkki ja Tee muutos -painike näkyvät ohjaajalle ja avaavat pikamuutoksen; kortin korvaus; päiväohjelman vaiheen peruutus etukäteen ja sen ohitus suorituksessa; muutos huomiselle; lukittuna merkit piilossa ja MUUTOS-rivi näkyy; Selvä kuittaa muutoksen.

## Roolit: asiakas ja ohjaaja selvästi erillään
- Kaksi roolia: asiakas (oletus) ja ohjaaja. Asiakkaalla vain oma päivä (Tänään, muutosrivit, Ajastin, Ensin–Sitten, Päiväohjelmat); ei ohjaajan merkkejä, painikkeita eikä muokkausta – myöskään ilman PIN-lukitusta.
- Ohjaajan tila: tumma palkki kaikissa näkymissä ("OHJAAJAN TILA", asiakkaan nimi, PIN-avauksen aika, Asiakkaaksi). Oma etusivu: Tänään muutosmerkein, Tee muutos ja Ohjaajan työkalut (Viikko-ohjelma suoraan Viikko-välilehdelle, Päiväohjelmat, Omat kortit, Asetukset, Katso asiakkaana).
- Katso asiakkaana: asiakkaan näkymä esikatseluna, paluu ohjaajaksi ilman PIN-koodia.
- Testitila (Ohjaajan asetukset → Roolit, oletus päällä kehitysvaiheessa): yläreunassa aina "Testitila · asiakkaan näkymä – Ohjaajaksi" ja ohjaajan palkissa Asiakkaaksi; vaihto ilman PIN-koodia, rooli säilyy taustalle siirryttäessä. Pois päältä: ohjaajaksi PIN-koodilla (jos lukitus käytössä), aikakatkaisu ja taustalle siirtyminen palauttavat asiakkaaksi.
- Asiakkaaksi vaihdettaessa ohjaajan näkymistä palataan aloitukseen. Asiakkaan valikossa vain profiilin vaihto (jos sallittu) ja Ohjaajan tila.
- Korjattu: valintaympyrät otsikon kohdalle (muutoksen näkyminen, vientivalinnat); Lisävalinnat-kuvaus vain suljettuna.
- Uudet testit: roolit (5), käyttöliittymätestiin ohjaajan ja asiakkaan etusivun erot.
