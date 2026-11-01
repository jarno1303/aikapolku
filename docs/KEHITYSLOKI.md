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
- Puhelintesti odottaa hyväksyntää.
