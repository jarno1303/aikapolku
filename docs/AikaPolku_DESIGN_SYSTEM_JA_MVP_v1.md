# AikaPolku – kehitys- ja UI/UX-määrittely

Versio: 1.0
Päivämäärä: 31.10.2026
Tila: Suunnittelu / MVP
Teknologia: Kotlin, Jetpack Compose, Material 3
Ensisijainen kehitysdokumentti.

## 1. Sovelluksen tarkoitus

AikaPolku on visuaalinen ajastin- ja toiminnanohjaussovellus.

Tärkeimmät kysymykset:
- Mitä teen nyt?
- Mitä teen seuraavaksi?
- Kuinka paljon aikaa on jäljellä?

Kohderyhmiä ovat:
- Lapsiperheet
- Varhaiskasvatus
- Koulut ja erityisopetus
- Vammaispalvelut ja päivätoiminta
- Toimintaterapia ja kuntoutus
- Mielenterveystyö
- Ikääntyneiden palvelut
- Muut toiminnanohjauksesta hyötyvät käyttäjät

## 2. Tärkein kehitysperiaate

UI/UX ja helppokäyttöisyys ovat ensisijaisia.

Sovelluksen tulee olla intuitiivinen myös ensimmäisellä käyttökerralla.

Säännöt:
- Yksi päätehtävä kerrallaan.
- Ei monimutkaisia valikoita.
- Suuret painikkeet ja selkeät tekstit.
- Kuvakkeiden yhteydessä ymmärrettävät nimet.
- Ei pakollista rekisteröitymistä.
- Ei pakollisia ääniä.
- Ei mainoksia.
- Toimii ilman internetyhteyttä.
- Käyttäjän oma toimijuus ja itsemääräämisoikeus säilyvät.
- Kaikki kriittiset muutokset voidaan peruuttaa.
- Oletusasetuksilla voi aloittaa heti.

Tavoitteet:
- Ajastimen käynnistys alle 10 sekunnissa.
- Ensin–Sitten-pari alle 30 sekunnissa.
- Kolmen toiminnon rutiini alle 60 sekunnissa.
- Tallennetun ohjelman avaus 1–2 painalluksella.

Nämä tavoitteet vahvistetaan käyttäjätesteillä.

## 3. Visuaalinen ilme

Tyyli:
- Rauhallinen
- Lämmin
- Moderni
- Selkeä
- Ikäneutraali
- Saavutettava

Värit:
- Pääväri: #0F766E
- Vaalea päävärin tausta: #E6F4F1
- Ajastin: #C2413A
- Valmis: #166534
- Varoitustausta: #FDE68A
- Varoitusteksti: #713F12
- Tausta: #F8FAFC
- Kortti: #FFFFFF
- Pääteksti: #1F2937
- Aputeksti: #475569
- Reunukset: #CBD5E1
- Virhe: #B42318

Väri ei koskaan ole ainoa tilan ilmaisin.

Typografia:
- Roboto / Android-järjestelmäfontti
- Pääotsikko 28 sp
- Väliotsikko 22 sp
- Kortin nimi 20 sp
- Perusteksti 16–18 sp
- Ajastin 48–64 sp
- Teksti skaalautuu saavutettavasti.

Mitat:
- Painikkeen korkeus vähintään 56 dp
- Kosketusalue vähintään 48 x 48 dp
- Kortin kulmasäde 20 dp
- Näytön reunaväli 16 dp
- Riittävät välit painikkeiden välillä

## 4. Sovelluksen kolme päätoimintoa

Aloitusnäkymässä kolme suurta painiketta:

1. Ajastin
2. Ensin – Sitten
3. Päiväohjelmat

Muut asetukset sijoitetaan erilliseen näkymään.

## 5. Ajastin

Ominaisuudet:
- Visuaalinen vähenevä ympyrä
- Suuri jäljellä olevan ajan näyttö
- Pikavalinnat: 1, 2, 5, 10, 15, 20, 30, 60 min
- Oma kesto
- Aloita
- Tauko
- Jatka
- Nollaa
- Lisää 1 minuutti
- Valinnainen 10 sekunnin loppulaskenta
- Valinnainen ennakkovaroitus
- Valinnainen loppuääni
- Valinnainen oma tallennettu puhe

Ajastin käyttää luotettavaa monotonic-aikalähdettä,
esimerkiksi SystemClock.elapsedRealtime().

Ajastimen tilat:
IDLE, RUNNING, PAUSED, FINISHED.

Taustatoiminta ja Androidin ilmoitukset testataan erikseen.

## 6. Ensin – Sitten

Kaksi suurta kuvallista toimintokorttia:

ENSIN:
- Nykyinen toiminta
- Kuva
- Nimi
- Valinnainen ajastin

SITTEN:
- Seuraava toiminta
- Kuva
- Nimi
- Mahdollinen aloitusaika

Toiminnon päättyminen ei saa pakottaa käyttäjää siirtymään.
Käyttäjä voi vahvistaa valmiin toiminnon.

## 7. Päiväohjelmat

Käyttäjä voi:
- Luoda ohjelman
- Valita valmiita toimintokortteja
- Ottaa omia valokuvia
- Kirjoittaa omia kuvauksia
- Muuttaa korttien järjestystä
- Tallentaa ohjelman
- Käynnistää tallennetun ohjelman
- Kopioida ohjelman
- Määrittää toistumisen viikonpäivittäin

Toiminnon kolme ajoitustapaa:
1. Kiinteä kellonaika
2. Kestoajastin
3. Manuaalinen eteneminen

Kiinteät kellonajat eivät muutu automaattisesti
edellisen toiminnon viivästyessä.

## 8. Toimintokortti

Kortin tiedot:
- Yksilöllinen tunniste
- Nimi
- Kuva tai kuvake
- Lyhyt kuvaus
- Ajoitustapa
- Kesto tai kellonaika
- Valinnainen ääniohje
- Kortin järjestys ohjelmassa

Kuvat voidaan valita kirjastosta tai ottaa kameralla.

Omat kuvat tallennetaan ensisijaisesti paikallisesti.

## 9. Ensimmäinen toimintokirjasto

- Aamupala
- Ruokailu
- Välipala
- Pukeutuminen
- Käsienpesu
- WC
- Lepo
- Nukkumaanmeno
- Ulkoilu
- Askartelu
- Pelaaminen
- Lukeminen
- Musiikki
- Liikunta
- Koulutyö
- Kotitehtävät
- Lähtö
- Odota
- Valmis
- Tauko

Valmiita rutiinipohjia:
- Aamutoimet
- Iltatoimet
- Päivätoiminta
- Päiväkotipäivä
- Koulupäivä

## 10. Äänen tallentaminen

Ohjaaja tai vanhempi voi tallentaa oman äänensä.

Mahdollisuudet:
- Ei ääntä
- Tavallinen loppuääni
- Oma puheviesti
- Puhe ja äänimerkki

Ääni voidaan liittää toimintokorttiin.

Äänitysnäkymän painikkeet:
- Tallenna ääni
- Lopeta tallennus
- Kuuntele
- Nauhoita uudelleen
- Poista

Ääni on oletuksena pois päältä.

Äänitiedostot säilytetään paikallisesti.
Mikrofonilupa pyydetään vasta tallennettaessa.

## 11. Ohjaaja- ja käyttäjänäkymät

Ohjaajan näkymä:
- Rutiinien rakentaminen
- Kuvien lisääminen
- Aikojen muuttaminen
- Äänien tallennus
- Ohjelmien jakaminen myöhemmin

Käyttäjän näkymä:
- Nyt
- Seuraavaksi
- Jäljellä oleva aika
- Valmis
- Tauko tai lisäajan pyytäminen

Käyttäjän näkymässä ei näytetä tarpeettomia asetuksia.

## 12. Tekninen arkkitehtuuri

Android:
- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel
- StateFlow
- Room-tietokanta
- DataStore-asetukset
- Android Photo Picker / Camera
- MediaRecorder
- MediaPlayer tai Media3 tarpeen mukaan

Arkkitehtuuri:
- UI
- Domain / ajastuslogiikka
- Data / paikallinen tallennus
- Assets / resurssit

Offline-first.
Ei analytiikkaa eikä pilvipakkoa ensimmäiseen versioon.

## 13. Jatkokehitys

P1:
- Rutiinien jakaminen QR-koodilla
- Ohjelmien turvallinen tuonti ja vienti
- TV-/Chromecast-tila
- Asiakkaan henkilökohtainen ohjelma
- Englanti, ruotsi, saksa, portugali ja espanja

P2:
- Turvallinen pilvisynkronointi
- Organisaatiohallinta
- Usean käyttäjän ohjelmat
- Premium-ostot
- Lisenssien hallinta

Suunniteltu hinnoittelu:
- Free: ilmainen
- Premium: 14,99 euroa kertamaksu
- Premium-kokeilu: 7 päivää
- Organisaatiolisenssi: erikseen määritettävä

Maksuja ei toteuteta ensimmäiseen MVP:hen.

## 14. Saavutettavuus ja turvallisuus

- WCAG 2.2 soveltuvin osin
- Android TalkBack
- Skaalautuva teksti
- Riittävä kontrasti
- Suuret kosketusalueet
- Ei pakollista ääntä
- Ei välkkyviä animaatioita
- Ei automaattista asiakkaan toiminnan ohittamista
- Ei arkaluonteisia tietoja ryhmän televisioon
- Tietojen turvallinen paikallinen tallennus

## 15. Kehityksen hyväksymiskriteerit

Jokaisen uuden ominaisuuden yhteydessä tarkistetaan:
- Ymmärtääkö ensikertalainen toiminnon?
- Onko seuraava vaihe selkeä?
- Voiko painallusten määrää vähentää?
- Toimiiko ilman internetiä?
- Toimiiko suurella fontilla?
- Toimiiko puhelimella ja tabletilla?
- Säilyvätkö tallennetut tiedot?
- Toimiiko Androidin takaisin-painike?
- Onko ääni valinnainen?

## 16. Toteutusjärjestys

P0-A: Sovellusrakenne, teema ja navigaatio
P0-B: Toimiva ajastin
P0-C: Korttikirjasto ja Ensin–Sitten
P0-D: Päiväohjelmat ja paikallinen tallennus
P0-E: Omat kuvat ja äänet
P0-F: Testaus ja saavutettavuus

## 17. Muutosten hallinta

Tämä dokumentti pidetään projektin docs-kansiossa.
Kaikki merkittävät suunnittelupäätökset kirjataan tänne.
Uudet versiot päivitetään Git-versionhallintaan.

Tärkein periaate:
AikaPolku on ominaisuuksiltaan monipuolinen,
mutta käyttäjälle poikkeuksellisen yksinkertainen.
