# AikaPolku – tietosuojan ja tietoturvan puutelista ennen organisaatiopilottia

Versio 1.0 · 15.11.2026 · Laadittu sovelluksen koodin perusteella. Tämä ei ole juridinen arvio; vaikutustenarviointi (DPIA) tehdään yhdessä pilottiorganisaation tietosuojavastaavan kanssa.

## 1. Mitä tietoa sovellus käsittelee

| Tieto | Missä | Säilytys | Varmuuskopio / vienti |
| --- | --- | --- | --- |
| Asiakkaan nimi ja kuva (profiili) | Laitteen sovellustallennus | Kunnes asiakas poistetaan | Vienti (voi tehdä ilman henkilötietoja) |
| Päiväohjelmat, kortit, viikko-ohjelma, asetukset | Sovellustallennus, asiakaskohtaisesti | Kunnes poistetaan | Vienti |
| Omat kuvat ja äänitteet | Sovelluksen tiedostot (ei galleriaa) | Kunnes poistetaan | Vienti |
| Yksiköt, ryhmät, toiminnat, lomat | Sovellustallennus, laitekohtainen | Kunnes poistetaan | Vienti |
| Kuittaukset ja päivän muutokset | Sovellustallennus | Vanhat poistuvat automaattisesti | Ei vientiä |
| Oma tila | Asiakkaan laite | Eilinen ja tämä päivä | Ei vientiä, ei näy ohjaajalle |
| Asiakkaan viestit ohjaajalle | Ohjaajan puhelin | Eilinen ja tämä päivä | Ei vientiä |
| Lähtevien viestien jono | Asiakkaan laite | Enintään 20 min | Ei vientiä |
| Käytön seuranta (lukumäärät) | Laite, asiakaskohtaisesti | 180 päivää, oletuksena pois | CSV tunnuksin (A1, A2 …) |
| Laiteliitosten avaimet | Sovellustallennus | Kunnes liitos poistetaan | Ei vientiä |
| Ohjaajan PIN-koodi | Suolattu tiiviste | Kunnes vaihdetaan | Ei vientiä |

## 2. Kunnossa

- Ei pilvipalvelua eikä internetyhteyttä: laitteiden yhteys vain samassa lähiverkossa, salattuna.
- Androidin automaattinen varmuuskopio ja laitesiirto on poistettu käytöstä (15.11.2026).
- Pariliitos kerran koodia vertaamalla; viestit salataan; toistetut ja vanhat viestit hylätään.
- Oma tila on asiakkaan omaa tietoa, eikä se siirry mihinkään ilman asiakkaan painallusta.
- Käytön seuranta on oletuksena pois, kirjaa vain lukumääriä eikä omaa tilaa, ja vienti on ilman nimiä.
- Asiakkaan poisto poistaa hänen tietonsa ja vain hänen käyttämänsä kuvat ja äänitteet.
- Vienti voidaan tehdä ilman henkilötietoja ja suojata salasanalla.

## 3. Puutteet ja korjausehdotukset

| # | Puute | Riski | Ehdotus | Kiireellisyys |
| --- | --- | --- | --- | --- |
| T1 | Laiteliitosten avaimet tavallisessa sovellustallennuksessa | Pääsy laitteen tiedostoihin paljastaisi avaimet | Avaimet Android Keystoreen | Ennen pilottia |
| T2 | Ohjaajan puhelimen ilmoitukset voivat näyttää asiakkaan nimen ja viestin lukitusnäytöllä | Sivullinen näkee | Lukitusnäytölle yleinen teksti ("Uusi viesti"), sisältö vasta avattuna | Ennen pilottia |
| T3 | Vientitiedoston salasana on valinnainen | Henkilötiedot suojaamattomassa tiedostossa | Salasana pakolliseksi, kun vienti sisältää henkilötietoja | Ennen pilottia |
| T4 | Sovellus ei vaadi laitteen näytön lukitusta (näyttää vain varoituksen) | Kadonnut laite avautuu | Yksikön ohje: näytön lukitus pakollinen; harkitaan sovelluksen estoa ilman lukitusta | Ennen pilottia (ohje) |
| T5 | Viimeisimmät sovellukset -näkymä voi näyttää asiakkaan tietoja | Sivullinen näkee | Harkitaan FLAG_SECURE ohjaajan näkymiin | Pilotin aikana |
| T6 | Ei toimintoa "poista kaikki tiedot tästä laitteesta" | Laitteen luovutus eteenpäin | Oma toiminto tai ohje: sovelluksen tietojen tyhjennys Androidin asetuksista | Ennen pilottia (ohje) |
| T7 | Android 16:n lähiverkkolupa | Yhteys ei toimi uusissa laitteissa | Lupapyyntö ja selkeä virheilmoitus | Ennen laajempaa käyttöä |
| T8 | Kehitysvaiheen testitila (vapaa roolinvaihto) | Asiakas pääsee ohjaajan toimintoihin | Testitila pois pilottilaitteista, PIN käyttöön | Käyttöönotossa |

## 3b. Korjattu 16.11.2026
- T1: laiteliitosten avaimet Android Keystoren avaimella salattuina.
- T2: lukitusnäytöllä vain "Uusi viesti asukkaalta"; nimi ja viesti vasta avattuna.
- T3: viennin salasana pakollinen aina, kun vienti sisältää asukkaiden tietoja.
- T4: paikan laitteella ohjaajan tila vaatii laitteen näytön lukituksen (Android salaa silloin tiedot); asukkaan laitteessa muistutus.
- T5: ohjaajan näkymät piilossa kuvakaappauksilta ja sovellusvaihtajasta (asetus, oletuksena päällä).
- T6: "Poista kaikki tiedot tästä laitteesta" (kirjoitettu vahvistus).
- T8: testitila oletuksena pois; muistutus ohjaajan etusivulla, jos PIN-lukitus puuttuu.
- Uusi: automaattinen salattu koko laitteen varmuuskopio kerran vuorokaudessa valittuun kansioon (7 uusinta) ja palautus.
- Jäljellä: T7 (Android 16:n lähiverkkolupa) ennen laajempaa käyttöä.

## 4. Dokumentaatio, joka tarvitaan ennen pilottia

- **Roolit:** yksikkö / palveluntuottaja on rekisterinpitäjä. Bon Vivo ei käsittele asiakastietoja (ei palvelinta), mikä kirjataan sopimukseen.
- **Tietosuojaseloste** tai lisäys yksikön olemassa olevaan selosteeseen.
- **Vaikutustenarviointi (DPIA)** yhdessä tietosuojavastaavan kanssa: haavoittuvassa asemassa olevat henkilöt ja terveyteen liittyvä tieto (oma tila).
- **Suostumukset:** kuvat ja äänitteet, käytön seuranta, Kerro ohjaajalle -viestit (asukas tai edustaja).
- **Laiteohje:** näytön lukitus, PIN, kadonneen laitteen toimintamalli, vientitiedostojen säilytyspaikka.
- **Rajaus käyttöehdoissa:** avustava työväline, ei asiakastietojärjestelmä, ei lääkityksen hallintaa.
- **Säilytys ja poisto:** asiakkuuden päättyessä asiakas poistetaan sovelluksesta; seurannan koosteet säilytetään pilotin arvioinnin ajan.
