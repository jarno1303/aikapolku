// AikaPolku – Ohjaajan käyttöopas (asumisyksiköt ja päivätoiminta)
const L = require('./lib');
const { h1, h2, h3, para, bullet, steps, box, shot, table, spacer, cover, toc } = L;

const kansi = cover({
  title: 'Ohjaajan käyttöopas',
  subtitle: 'AikaPolku asumisyksikössä ja päivätoiminnassa',
  audience: 'Ohjaajille, jotka ottavat AikaPolun käyttöön ja käyttävät sitä päivittäin',
  version: 'Versio 1.0 · marraskuu 2026'
});

const sisalto = [
  ...toc(),

  // 1
  h1('1. AikaPolku lyhyesti'),
  para('AikaPolku on toiminnanohjauksen tuki arkeen. Se näyttää asiakkaalle, mitä tehdään nyt, mitä seuraavaksi ja kauanko jotain kestää – kuvin, lyhyin sanoin ja halutessa äänellä. Ohjaaja rakentaa asiakkaan päivän kerran, ja sovellus pitää sen ajan tasalla.'),
  para('Sovellus on tehty autismikirjon ihmisille, kehitysvammaisille ja aivovamman saaneille sekä heidän kanssaan työskenteleville. Sama sovellus toimii asumisyksikössä, päivätoiminnassa ja kotona.'),
  h2('Periaatteet, joille sovellus rakentuu'),
  bullet('**Asiakas näkee vain omat tietonsa.** Ohjaaja voi nähdä usean asiakkaan päivän, asiakas ei koskaan näe toisten tietoja.'),
  bullet('**Sijainti ratkaisee, mitä asiakas näkee.** Kun asukas on päivätoiminnassa, kodin tapahtumat eivät kuormita häntä.'),
  bullet('**Ohjaaja valitsee kerran.** Näkymä, äänet ja tuen määrä valitaan asiakaskohtaisesti, ja valinnat pysyvät.'),
  bullet('**Itsemääräämisoikeus.** Tarvitsen tauon ja Tarvitsen apua toimivat aina ilman PIN-koodia. Asiakkaan oma tila on hänen omaa tietoaan.'),
  bullet('**Rauhallisuus.** Asiakkaan ruudulla on enintään viisi painiketta kerrallaan, ja kaikki näkyy ilman vierittämistä (paitsi halutessa koko päivän aikajana).'),
  bullet('**Ei pilveä.** Tiedot ovat laitteella. Yksikön laitteet voivat kokeiluvaiheessa välittää viestejä toisilleen samassa verkossa salattuna.'),
  box('miksi', 'Ennakointi ja toiminnan jäsentäminen auttavat toiminnanohjauksen vaikeuksiin. Kun ihminen tietää, mitä tehdään, milloin ja missä, siirtymät helpottuvat.'),

  h2('Mitä AikaPolku ei ole'),
  para('AikaPolku on avustava työväline arjen ennakointiin ja toiminnanohjaukseen. Rajaukset kannattaa käydä läpi koko työyhteisön kanssa ennen käyttöönottoa.'),
  bullet('**Ei asiakastietojärjestelmä eikä kirjaamisen väline.** Asiakastiedot, hoitosuunnitelmat ja viralliset kirjaukset tehdään yksikön omaan asiakastietojärjestelmään. AikaPolun kuittaukset ja viestit eivät ole kirjauksia.'),
  bullet('**Ei lääkityksen hallintaa.** Kortti "Lääkkeet klo 8" voi muistuttaa asiakasta, mutta lääkkeenantoa ei kuitata eikä kirjata AikaPolussa. Lääkehoito toteutetaan yksikön lääkehoitosuunnitelman mukaan.'),
  bullet('**Ei korvaa ihmistä.** Sovellus tukee ennakointia, mutta ohjaaja on aina läsnä siirtymissä, muutoksissa ja silloin, kun asiakas pyytää apua.'),
  bullet('**Ei valvontaväline.** Asiakkaan oma tila ja valinnat ovat hänen omiaan. Sovellus ei raportoi asiakkaasta ohjaajalle automaattisesti.'),

  h2('Sanasto'),
  table(['Sana', 'Mitä se tarkoittaa'], [
    ['Yksikkö', 'Asumisyksikkö, esimerkiksi Villa Metsämaja. Yksikön runko (esim. ruokailut) koskee kaikkia sen asukkaita.'],
    ['Alayksikkö', 'Yksikön osa, esimerkiksi Omena ja Päärynä. Asukas kuuluu yhteen alayksikköön.'],
    ['Päivän toiminta', 'Päivätoiminta, työpaja tai muu toiminta, jossa asukas on tiettyinä aikoina poissa kotoa.'],
    ['Ryhmä', 'Yhteinen viikkorunko, joka näkyy jäsenille aina.'],
    ['Runko', 'Toistuvat kohdat viikonpäivittäin (esim. aamupala klo 8).'],
    ['NYT-kortti', 'Asiakkaan etusivun iso kortti: mitä tehdään nyt.'],
    ['Päiväohjelma', 'Vaihe vaiheelta etenevä ohjelma (esim. aamutoimet), jossa voi olla osavaiheita ja aikoja.'],
    ['Ensin–Sitten', 'Kaksi korttia: ensin tämä, sitten tuo. Usein ajastimen kanssa.'],
    ['Muutos', 'Päivän poikkeus: peruttu, tilalle muuta tai uusi aika. Asiakas näkee sen muutoskorttina.'],
  ], [2400, 6626]),

  // 2
  h1('2. Ennen aloitusta'),
  h2('Laitteet'),
  para('Asiakkaalla on oma puhelin tai tabletti. Ohjaajalla on yleensä puhelin. Asiakkaan laitteeseen tehdään asiakkaan tiedot, ja ohjaaja käyttää ohjaajan roolia PIN-koodilla.'),
  h2('Roolit: ohjaaja ja asiakas'),
  para('Sovelluksessa on kaksi roolia. **Asiakkaan roolissa** näkyy vain asiakkaan oma päivä ja hänen omat toimintonsa. **Ohjaajan roolissa** näkyvät asetukset, muokkaus, muutokset ja yksikön tilanne.'),
  ...steps([
    'Avaa ohjaajan rooli yläreunan lukosta ja anna PIN-koodi.',
    'Kun olet valmis, palauta asiakkaan rooli: **Asiakkaaksi** tai Ensin–Sittenissä **Anna asiakkaalle**.',
  ]),
  ...shot('o01-ohjaajan-etusivu'),
  h2('PIN-koodi'),
  ...steps([
    'Avaa **Asetukset** → **Puhelimen asetukset** → **Ohjaajan lukitus**.',
    'Valitse **Ota lukitus käyttöön** ja anna PIN-koodi kahdesti.',
    'Kerro koodi vain yksikön ohjaajille.',
  ]),
  box('huom', 'Testitilassa roolin voi vaihtaa ilman PIN-koodia. Kytke testitila pois (**Asetukset** → **Roolit**), ennen kuin laite annetaan asiakkaalle.'),
  ...shot('o23-lukitus'),

  // 3
  h1('3. Yksikön luominen ohjatusti'),
  para('Ohjattu aloitus rakentaa koko yksikön kerralla: yksikön ja alayksiköt, asukkaat, yhteiset ruokailut, päivän toiminnat ja asukkaiden näkymän. Tähän menee noin 15 minuuttia.'),
  para('Esimerkkinä on kuvitteellinen **Villa Metsämaja**, jossa on alayksiköt **Omena** ja **Päärynä**, kummassakin kuusi asukasta.'),
  ...steps(['Avaa ohjaajan etusivulta **Yksiköt ja ryhmät**.', 'Valitse **Luo yksikkö ohjatusti**.']),
  ...shot('o02-yksikot-ryhmat'),
  h2('Vaihe 1: Yksikkö ja alayksiköt'),
  ...steps(['Kirjoita yksikön nimi, esimerkiksi Villa Metsämaja.', 'Lisää alayksiköt, esimerkiksi Omena ja Päärynä. Jos yksikössä ei ole osia, jätä tämä tyhjäksi.', 'Valitse **Jatka**.']),
  ...shot('o03-ohjattu-1-yksikko'),
  h2('Vaihe 2: Asukkaat'),
  ...steps(['Kirjoita asukkaiden nimet alayksiköittäin.', 'Tarkista, että jokainen asukas on oikeassa alayksikössä.', 'Valitse **Jatka**.']),
  ...shot('o04-ohjattu-2-asukkaat'),
  box('vinkki', 'Kuvan asukkaalle voi lisätä myöhemmin asiakkaan asetuksista. Kuvan voi ottaa suoraan kameralla (**Ota kuva**); se tallentuu vain sovellukseen, ei puhelimen kuvagalleriaan.'),
  h2('Vaihe 3: Yhteiset ruokailut'),
  para('Valmiina ovat aamupala, lounas, välipala, päivällinen ja iltapala. Muuta aikoja tarpeen mukaan; viikonlopulle voi antaa eri ajan.'),
  ...steps(['Tarkista ruokailujen ajat arkena ja viikonloppuna.', 'Valitse, tuleeko ruokailuista muistutus ja kuinka monta minuuttia ennen.', 'Valitse **Jatka**.']),
  ...shot('o05-ohjattu-3-ruokailut'),
  h2('Vaihe 4: Päivän toiminnat'),
  para('Lisää toiminnat, joissa osa asukkaista käy: esimerkiksi **Päivätoiminta Tammi** ma–pe 9–14 ja **Työpaja Verstas** ti ja to 9–15.'),
  ...steps([
    'Valitse **Lisää toiminta** ja anna nimi ja kuva.',
    'Valitse päivät ja ajat. Jos ajat ovat eri päivinä eri, poista valinta **Sama aika kaikille päiville** ja anna ajat päiväkohtaisesti.',
    'Valitse toimintaan osallistuvat asukkaat.',
    'Valitse **Jatka**.'
  ]),
  ...shot('o06-ohjattu-4-toiminnat'),
  box('miksi', 'Kun asukas on toiminnassa, kodin tapahtumat piilotetaan hänen näkymästään. Hän näkee toiminnan yhtenä korttina tai sen rungon – sen mukaan, mitä hänelle valitaan.'),
  h2('Vaihe 5: Asukkaiden näkymä'),
  para('Valitse oletus, jota käytetään kaikille uusille asukkaille. Sitä voi muuttaa asukaskohtaisesti myöhemmin.'),
  table(['Valinta', 'Asukas näkee', 'Sopii, kun'], [
    ['Koko päivä (oletus)', 'NYT-kortin ja koko päivän aikajanan; pitkää päivää vieritetään', 'asukas haluaa nähdä pidemmälle'],
    ['Nyt ja kaksi seuraavaa', 'NYT-kortin ja kaksi seuraavaa; mahtuu aina ruudulle', 'koko päivän näkeminen kuormittaa'],
    ['Ei aikajanaa', 'vain NYT-kortin', 'rauhallisin vaihtoehto on tarpeen'],
  ], [2300, 3800, 2926]),
  ...shot('o07-ohjattu-5-tuki'),
  h2('Vaihe 6: Esikatselu ja tallennus'),
  ...steps(['Tarkista yhteenveto: alayksiköt, asukkaat, ruokailut ja toiminnat.', 'Valitse **Tallenna**. Asukkaiden profiilit ja yksikön runko luodaan.']),
  ...shot('o08-ohjattu-6-esikatselu'),

  // 4
  h1('4. Yksiköt, alayksiköt ja ryhmät jälkikäteen'),
  para('Kaikkea ohjatussa aloituksessa tehtyä voi muokata **Yksiköt ja ryhmät** -näkymässä.'),
  h2('Uusi yksikkö, toiminta tai ryhmä'),
  ...steps(['Valitse **Lisää yksikkö, toiminta tai ryhmä**.', 'Valitse **Mikä tämä on?**: Yksikkö, Päivän toiminta tai Ryhmä.', 'Alayksikölle valitse **Kuuluu yksikköön** ja yläyksikkö.', 'Valitse asiakkaat ja tallenna.']),
  h2('Asukkaan oma aika toiminnassa'),
  para('Jos asukas on toiminnassa eri aikaan kuin muut, avaa toiminta ja napauta asukasta. Valitse päivät ja asukkaan oma aika. Sovellus varoittaa, jos asukkaalla on päällekkäisiä toimintoja.'),
  ...shot('o09-toiminta-oma-aika'),
  h2('Lähtö- ja paluukortit'),
  para('Toiminnalle voi ottaa käyttöön siirtymäkortit: **Lähtö** 10 minuuttia ennen alkua ja **Paluu kotiin** lopussa. Ne valmistavat asukasta siirtymään.'),
  h2('Laitteen rajaus alayksikköön'),
  para('Jos ohjaajan puhelin on käytössä vain yhdessä alayksikössä, rajaa näkymä: **Yksiköt ja ryhmät** → **Tämä laite** → valitse alayksikkö. Silloin luettelot ja Tänään yksikössä -näkymä näyttävät vain sen asukkaat.'),

  // 5
  h1('5. Asiakkaat ja asiakkaan näkymä'),
  h2('Asiakkaan valinta'),
  para('Asetukset koskevat aina valittua asiakasta. Valitse asiakas **Asetukset**-sivun yläosasta. Nimi näkyy jokaisessa asetusikkunassa.'),
  h2('Asiakkaan näkymä'),
  ...steps(['Avaa **Asetukset** → **Asiakkaan asetukset** → **Etusivu**.', 'Valitse päivän aikajana: Koko päivä, Nyt ja kaksi seuraavaa tai Ei aikajanaa.', 'Valitse, näkyvätkö kellonajat.', 'Valitse päivätoiminnan aikainen näkymä: **Yksi kortti** (rauhallisin) tai **Toiminnan runko**.', 'Valitse väliajan kortti, joka näkyy, kun edellinen on tehty eikä seuraava ole vielä alkanut.']),
  ...shot('o20-asetukset-nakyma'),
  ...shot('a01-nyt-kortti'),
  box('vinkki', 'Valitse **Katso asiakkaana** ohjaajan etusivulta, niin näet asiakkaan näkymän muuttamatta mitään.'),

  // 6
  h1('6. Ruokailut ja muistutukset'),
  para('Yksikön runko sisältää yhteiset ruokailut. Ne näkyvät kaikille yksikön asukkaille, paitsi kun asukas on päivän toiminnassa tai lomalla.'),
  ...steps(['Avaa **Yksiköt ja ryhmät** ja yksikkö (esim. Villa Metsämaja).', 'Valitse **Ryhmän runko** ja ruokailu.', 'Muuta aikaa, päiviä tai muistutusta ja tallenna.']),
  box('huom', 'Muistutukset vaativat ilmoitusten luvan. Tarkista tila kohdasta **Asetukset** → **Puhelimen asetukset** → muistutusten tila.'),

  // 7
  h1('7. Viikko-ohjelma'),
  para('Asiakkaan viikossa näkyvät hänen omat kohtansa ja yksikön, toimintojen ja ryhmien kohdat.'),
  ...steps(['Valitse ohjaajan etusivulta **Asiakkaan viikko**.', 'Valitse päivä ja **+**.', 'Valitse kortti tai päiväohjelma, aika ja toisto (esim. joka maanantai tai vuoroviikoin).', 'Valitse, tuleeko muistutus.', 'Tallenna.']),
  ...shot('o12-asiakkaan-viikko'),
  ...shot('o13-tapahtuma'),
  box('vinkki', 'Lääkkeet ja muut tärkeät asiat merkitään viikko-ohjelmaan muistutuksen kanssa. Asiakas kuittaa ne tehdyiksi, ja kuittaus näkyy vielä seuraavana aamuna.'),

  // 8
  h1('8. Päiväohjelmat'),
  para('Päiväohjelma ohjaa vaihe vaiheelta, esimerkiksi aamutoimet. Vaiheessa voi olla osavaiheita (Kengät, Takki), oma ääni ja kesto.'),
  h2('Päiväohjelman tekeminen'),
  ...steps(['Valitse **Päiväohjelmat** → uusi ohjelma tai valmis pohja.', 'Valitse kortit vaiheiksi.', 'Järjestä vaiheet vetämällä ja lisää tarvittaessa osavaiheet.', 'Valitse ajat: omaan tahtiin, kesto tai kellonaika.', 'Anna nimi ja tallenna.']),
  ...shot('o14-paivaohjelma-editori'),
  h2('Valinta: asiakas valitsee kahdesta'),
  para('Vaiheeseen voi lisätä toisen vaihtoehdon. Asiakas näkee kaksi korttia, napauttaa haluamaansa ja aloittaa **Aloita**-painikkeella. Aika ja ääni alkavat vasta valinnan jälkeen.'),
  ...steps(['Valitse vaiheen alta **Anna valita: lisää toinen vaihtoehto**.', 'Valitse kortti omista korteista.', 'Poista vaihtoehto rististä, jos sitä ei enää tarvita.']),
  ...shot('o15-valinta-editori'),
  ...shot('a05-valinta'),
  para('Asetuksissa (**Viestit** → **Valitse**) voi sallia vastauksen **En halua kumpaakaan**. Silloin asiakas saa viestin, jonka hän voi näyttää aikuiselle: Haluaisin jotain muuta.'),
  h2('Valmis-painike, kun aikaa on jäljellä'),
  table(['Asetus', 'Mitä tapahtuu'], [
    ['Aina sallittu', 'Valmis siirtää heti seuraavaan.'],
    ['Kysy ensin (oletus)', 'Asiakkaalta kysytään: Aikaa on vielä 3:20. Oletko valmis?'],
    ['Vasta ajan päätyttyä', 'Valmis tulee käyttöön, kun aika on kulunut.'],
  ], [2800, 6226]),
  para('Ohjaaja voi aina kuitata vaiheen valmiiksi varmistuksen kautta. Sama asetus koskee Ensin–Sitten-ajastinta.'),
  ...shot('a02-paivaohjelma'),

  // 9
  h1('9. Ensin–Sitten ja ajastin'),
  para('Ensin–Sitten näyttää kaksi korttia: ensin tämä, sitten tuo. Ensin-toiminnolle voi asettaa ajan.'),
  ...steps([
    'Valitse ohjaajan etusivulta **Valmistele asiakkaalle** → **Ensin–Sitten**.',
    'Valitse kortit. Sitten-kortille voi lisätä toisen vaihtoehdon, jolloin asiakas valitsee.',
    'Valitse **Siirry kellotauluun** ja aseta aika: **−** ja **+** muuttavat minuutin, pitkä painallus viisi minuuttia, pikavalinnat 5, 10, 15 ja 30 min.',
    'Valitse **Anna asiakkaalle**. Rooli vaihtuu, ja asiakas aloittaa itse.'
  ]),
  ...shot('o16-ensin-sitten'),
  ...shot('o17-ajastin-ohjaaja'),
  ...shot('a09-ajastin'),
  box('vinkki', 'Kellotaulua voi myös napauttaa (lähin viisi minuuttia) tai vetää (minuutin tarkkuus). Ajan voi asettaa vain ohjaaja, eikä käynnissä olevaa aikaa voi muuttaa.'),

  // 10
  h1('10. Siirtymien tuki'),
  h2('Pian vaihtuu -ennakkovaroitus'),
  para('Kun aikaa on jäljellä valittu määrä, asiakas näkee rauhallisen kortin: Pian vaihtuu – Seuraavaksi: Lounas.'),
  ...steps(['Avaa **Asetukset** → **Aika** ja valitse ennakkovaroituksen aika.', 'Avaa **Viestit** → **Pian vaihtuu -ennakkovaroitus** ja valitse ääni: ei ääntä (oletus), puheääni tai oma ääni.', 'Äänitä **yleinen äänite** kaikille asiakkaille ja tarvittaessa **tämän asiakkaan oma äänite**, joka korvaa yleisen.']),
  ...shot('o22-asetukset-aika'),
  ...shot('a03-ennakkovaroitus'),
  box('miksi', 'Ennakoimattomuus tekee siirtymistä vaikeita. Ennakkoilmoitus äänellä ja kuvalla on tutkimuksissa vähentänyt siirtymiin liittyvää kuormitusta. Ääni soi kerran tehtävää kohden eikä soi, kun puhelin on äänettömällä.'),
  h2('Tarvitsen tauon ja Tarvitsen apua'),
  para('Painikkeet ovat aina asiakkaan käytössä ilman PIN-koodia. Tauko pysäyttää ajan, ja Jatka käynnistää sen uudelleen. Viestin voi näyttää aikuiselle, ja sille voi valita puheäänen tai oman äänitteen (**Asetukset** → **Viestit**).'),
  ...shot('a04-tauko'),
  h2('Tarvitsen vielä hetken'),
  para('Kun aika on loppumassa, asiakas voi pyytää lisäaikaa ohjaajan asettamissa rajoissa (esimerkiksi 3 minuuttia kerran). Kun lisäaika on käytetty, pyyntö näyttää viestin aikuiselle eikä aika pysähdy.'),

  // 11
  h1('11. Muutokset'),
  para('Muutos on päivän poikkeus: toiminta perutaan, tilalle tulee muuta tai aika muuttuu. Asiakas näkee muutoskortin, joka kertoo, mikä muuttuu ja mikä pysyy samana.'),
  ...steps(['Valitse ohjaajan etusivulta **Tee muutos**.', 'Valitse päivä (tänään, huomenna tai muu päivä) ja **Mikä muuttuu?**', 'Valitse **Peruttu**, **Tilalle muuta** tai **Uusi aika**.', 'Valitse **Lisävalinnat**: syy, äänite ja milloin asiakas näkee muutoksen (heti, aamulla, edellisenä iltana tai vasta tapahtuman aikaan).', 'Valitse **Tallenna muutos**.']),
  ...shot('o18-tee-muutos'),
  ...shot('a08-muutoskortti'),
  para('Muutoksen voi perua: **Palauta alkuperäinen**. Jos asiakas on jo nähnyt muutoksen, sovellus muistuttaa kertomaan, että alkuperäinen palaa.'),
  h2('Koko ryhmän tai toiminnan muutos'),
  para('Ryhmä tänään -kortissa muutos koskee kaikkia paikalla olevia. Päivän toiminnan voi perua tulevalle päivälle valitsemalla päivän ja **Peru kaikki toiminnat tältä päivältä**. Peruttu toiminta tarkoittaa, että asukas on kotona.'),

  // 12
  h1('12. Poissaolot ja lomat'),
  ...steps(['Avaa **Tänään yksikössä** ja valitse **Merkitse loma tai poissaolo**.', 'Valitse asukas, syy (loma, kotona, sairaalassa tai muu) ja aikaväli.', 'Tallenna.']),
  ...shot('o11-loma'),
  para('Lomalla olevalle asukkaalle ei näytetä yksikön ja toimintojen kohtia. Hän näkee omat kohtansa ja kortin, joka kertoo, milloin hän palaa.'),

  // 13
  h1('13. Odotetaan'),
  para('Odotetaan on tilanteisiin, joissa odotuksen kestoa ei aina tiedä: taksi on myöhässä, ruoka ei ole valmis tai pitää odottaa vuoroa.'),
  ...steps(['Valitse ohjaajan etusivulta **Valmistele asiakkaalle** → **Odotetaan**.', 'Valitse, mitä odotetaan (esim. Lähtö).', 'Valitse kesto tai **Ei tiedossa**.', 'Valitse enintään kolme **Odottaessa voit** -korttia, esimerkiksi musiikki, lukeminen ja lepo.', 'Valitse **Aloita odotus**. Kun lähdetään, valitse **Nyt mennään**.']),
  ...shot('o19-odotetaan'),
  ...shot('a07-odotetaan'),
  box('miksi', 'Väärä tarkkuus lisää ahdistusta. Siksi odotuksessa ei näytetä sekunteja: kesto tiedossa näkyy hitaasti pienenevänä kaarena, muuten rauhallisena kuviona.'),

  // 14
  h1('14. Oma tila'),
  para('Asiakas voi kertoa, miltä tuntuu (kasvot: hyvä, ihan ok, huono) tai paljonko jaksaa (akku: jaksan hyvin, puolillaan, akku vähissä).'),
  ...steps(['Avaa **Asetukset** → **Viestit** → **Oma tila** ja kytke se käyttöön.', 'Valitse asteikko: kasvot tai akku.', 'Valitse halutessa **Kysy päiväohjelmassa tehtävän jälkeen**. Kysymyksen voi aina ohittaa.']),
  ...shot('o21-asetukset-viestit'),
  ...shot('a06-oma-tila'),
  box('huom', 'Oma tila on asiakkaan omaa tietoa. Se jää vain asiakkaan laitteelle eikä näy ohjaajan näkymissä. Asiakas voi itse näyttää sen aikuiselle tai lähettää sen ohjaajalle. Jos asiakas valitsee huonon tai vähäisen jaksamisen, sovellus tarjoaa tauon ja avun.'),

  // 15
  h1('15. Tänään yksikössä'),
  para('Ohjaajan etusivun **Tänään yksikössä** kertoo yhdellä silmäyksellä, kuka on missä: kotona, päivätoiminnassa, työpajassa tai lomalla, ja kuinka monta on kotona seuraavassa yhteisessä kohdassa (esim. lounaalla).'),
  ...shot('o10-tanaan-yksikossa'),

  // 16
  h1('16. Yksikön yhteys (kokeiluvaihe)'),
  para('Asiakkaan laite voi lähettää viestin ohjaajan puhelimeen samassa wifi-verkossa. Asiakas päättää joka kerta itse: viestien yhteydessä on **Kerro ohjaajalle** -painike. Asiakkaan valinta lähtee ohjaajalle tiedoksi.'),
  h2('Ohjaajan puhelin'),
  ...steps(['Avaa **Asetukset** → **Puhelimen asetukset** → **Yksikön yhteys**.', 'Kytke päälle **Vastaanota asiakkaiden viestit tällä puhelimella** ja salli ilmoitukset.']),
  h2('Asiakkaan laite'),
  ...steps(['Avaa samassa verkossa **Yksikön yhteys** asiakkaan laitteessa ohjaajan roolissa.', 'Valitse **Liitä ohjaajan puhelimeen** ja ohjaajan puhelin luettelosta.', 'Vertaa koodia: molemmissa laitteissa pitää näkyä sama kuusinumeroinen koodi.', 'Hyväksy liitos ohjaajan puhelimessa.']),
  ...shot('o24-yhteys'),
  box('huom', 'Viestit salataan ja ne menevät vain liitettyihin laitteisiin. Vierasverkoissa laitteet eivät aina näe toisiaan; silloin asiakas näyttää viestin aikuiselle. Ominaisuus on kokeiluvaiheessa.'),

  // 17
  h1('17. Tietojen vienti ja tuonti'),
  para('Päiväohjelmat, kortit ja viikko-ohjelman voi siirtää toiseen laitteeseen tai antaa kollegalle pohjaksi.'),
  ...steps(['Avaa **Asetukset** → **Vienti ja tuonti** → **Vie tiedostoksi**.', 'Valitse laajuus: kaikki asiakkaat, tämä asiakas tai valitut päiväohjelmat.', 'Valitse tarvittaessa **Ilman henkilötietoja** ja **Suojaa salasanalla**.', 'Jaa tai tallenna tiedosto. Tuo se toisessa laitteessa: **Tuo tiedostosta**.']),
  ...shot('o25-vienti'),
  box('huom', 'Vientitiedosto voi sisältää asiakkaan nimen, kuvia ja äänitteitä. Mukaan eivät tule kuittaukset, päivän muutokset, oma tila eikä PIN-koodi.'),

  // 18
  h1('18. Tietosuoja'),
  table(['Tieto', 'Missä se on', 'Kuka sen näkee'], [
    ['Asiakkaan päivä, ohjelmat, kortit, kuvat ja äänitteet', 'Asiakkaan laitteessa sovelluksen omassa tallennuksessa', 'Asiakas ja laitteen ohjaaja'],
    ['Oma tila', 'Vain asiakkaan laitteessa (eilinen ja tämä päivä)', 'Asiakas; ohjaaja vain, jos asiakas näyttää tai lähettää sen'],
    ['Kameralla otetut kuvat', 'Vain sovelluksessa, ei puhelimen kuvagalleriassa', 'Kuten muutkin asiakkaan tiedot'],
    ['Asiakkaan viestit (Kerro ohjaajalle)', 'Liitetyssä ohjaajan puhelimessa (eilinen ja tämä päivä)', 'Ohjaajat, joiden puhelimeen laite on liitetty'],
    ['Vientitiedosto', 'Siellä, minne se tallennetaan tai jaetaan', 'Vastaanottaja; voi suojata salasanalla'],
  ], [3000, 3100, 2926]),
  spacer(),
  bullet('Sovellus ei käytä pilvipalvelua eikä lähetä tietoja internetiin.'),
  bullet('Kuvaa esineitä ja paikkoja, ei muita asukkaita. Ohjaajan kuvan voi käyttää vain hänen suostumuksellaan.'),
  bullet('Kun asiakas poistetaan, hänen tietonsa ja vain hänen käyttämänsä kuvat ja äänitteet poistetaan laitteesta.'),
  h2('Yksikön vastuut'),
  para('Se, että tiedot ovat laitteella, ei yksin ratkaise tietosuojaa. Yksikkö on rekisterinpitäjä, ja seuraavat asiat sovitaan ennen käyttöönottoa:'),
  bullet('**Laitteen suojaus:** näytön lukitus ja laitteen salaus päällä, ohjaajan PIN-koodi käytössä.'),
  bullet('**Kadonnut laite:** kenelle ilmoitetaan ja miten laite lukitaan tai tyhjennetään etänä.'),
  bullet('**Säilytys ja poisto:** oma tila ja asiakkaan viestit säilyvät vain eilisen ja tämän päivän ajan. Kun asiakkuus päättyy, asiakas poistetaan sovelluksesta.'),
  bullet('**Vientitiedostot:** mihin niitä saa tallentaa, ja että ne suojataan salasanalla.'),
  bullet('**Kuvat ja äänitteet:** asiakkaan suostumus, ja muita asukkaita ei kuvata.'),

  // 19
  h1('19. Vianetsintä'),
  table(['Ongelma', 'Ratkaisu'], [
    ['Muistutus ei tule', 'Tarkista ilmoitusten lupa ja että puhelin ei ole Älä häiritse -tilassa. Katso tila: Asetukset → muistutusten tila.'],
    ['Pian vaihtuu -ääni ei kuulu', 'Ääni ei soi äänettömällä tai Älä häiritse -tilassa. Tarkista, että ennakkovaroitus on päällä (Aika) ja ääni valittu (Viestit).'],
    ['Asukas näkee kodin tapahtumia päivätoiminnan aikana', 'Tarkista, että asukas on valittu toiminnan osallistujaksi ja toiminnan ajat ovat oikein (myös oma aika).'],
    ['Kaikki ei mahdu asiakkaan ruudulle', 'Valitse asiakkaan näkymäksi Nyt ja kaksi seuraavaa tai Ei aikajanaa.'],
    ['Ohjaajan puhelinta ei löydy liitettäessä', 'Laitteiden on oltava samassa wifissä ja vastaanoton päällä. Vierasverkko voi estää laitteiden näkymisen.'],
    ['Viesti ei mennyt perille', 'Asiakas näyttää viestin aikuiselle. Tarkista ohjaajan puhelimen vastaanotto ja verkko.'],
    ['Kuva näkyy väärin päin', 'Ota kuva uudelleen sovelluksen Ota kuva -painikkeella; sovellus kääntää kuvan oikein päin.'],
  ], [3200, 5826]),
];

L.build({ file: 'build/AikaPolku_Kayttoopas_Ohjaaja.docx', title: 'AikaPolku · Ohjaajan käyttöopas', sections: [kansi, sisalto] })
  .then(() => console.log('ok'));
