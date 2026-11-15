// Puhujan muistiinpanot erillisenä Word-tiedostona.
const L = require('./lib');
const { h1, h2, para, bullet, cover } = L;
const notes = [
  ['1. AikaPolku – arjen ennakointi kuvin ja äänin', ['Kiitos ajasta. Kerron, mikä AikaPolku on, näytän sen asukkaan ja ohjaajan silmin ja päätän ehdotukseen.', 'Olen sosionomiopiskelija ja kehittäjä; taustani on sote-alalla ja teknologiassa.']],
  ['2. Vaikeimmat hetket ovat siirtymät ja muutokset', ['Suomessa on arviolta 55 000 autismikirjon ihmistä. Arjessa kuormittavat erityisesti siirtymät ja yllättävät muutokset.', 'Kerro pyyntö heti alussa: kumppanuus ja 8–12 viikon pilotti yhdessä yksikössä. Loppuesitys perustelee sen.']],
  ['3. Ainon iltapäivä kolmena hetkenä', ['Aino on kuvitteellinen asukas. Ohjelma etenee kuva kerrallaan.', 'Kun suunnitelma muuttuu, Aino näkee mikä muuttui ja miksi.', 'Kun tarvitsee tauon, Aino pyytää sen itse – ilman sanoja.']],
  ['4. Aino näkee vain sen, mikä koskee häntä nyt', ['Käy numerot läpi kuvasta: iso kuva, apu ja tauko, Valmis, seuraava.', 'Ruudulla on enintään viisi painiketta ja kaikki mahtuu ruudulle. Tämä testataan automaattisesti jokaisessa versiossa.']],
  ['5. Siirtymät tuetaan ennen kuin ne tulevat', ['Pian vaihtuu -ennakkovaroitus tulee ennen siirtymää, halutessa tutun ihmisen äänellä.', 'Tarvitsen vielä hetken antaa lisäaikaa ohjaajan asettamissa rajoissa; sen jälkeen ohjaaja saa viestin.', 'Odotetaan: kun kestoa ei tiedä, ei näytetä sekunteja vaan rauhallinen kuvio ja tekemistä.']],
  ['6. Asukkaalla on oma ääni', ['Asukas valitsee kahdesta ja aloittaa itse.', 'Oma tila on asukkaan omaa tietoa – se ei näy ohjaajalle, ellei asukas itse halua.', 'En ymmärrä -viestillä asukas voi pyytää näyttämään asian.', 'Kysymys säätiölle: miten kokemusasiantuntijat näkevät oman tilan ilmaisun?']],
  ['7. Ohjaajalle kevyt: yksikkö noin 15 minuutissa', ['Ohjattu aloitus: yksikkö, alayksiköt, asukkaat, ruokailut ja päivän toiminnat.', 'Viikkonäkymässä näkyvät yhteiset ja asukkaan omat tapahtumat. Muutos tehdään muutamalla napautuksella.', 'Näytä tarvittaessa demona puhelimesta.']],
  ['8. Ratkaisut nojaavat tutkimukseen', ['Ennakointi ja jäsentäminen ovat suomalaisen ohjeistuksen ydin.', 'Ennakkoilmoitus ja äänivihjeet tukevat siirtymiä; aika-apuvälineistä on satunnaistettua näyttöä.', 'Tutkimus perustelee ratkaisut; AikaPolun oma vaikuttavuus osoitetaan pilotissa.']],
  ['9. Turvallinen ja selkeästi rajattu', ['Ei pilveä, ei Googlen varmuuskopiota, oma tila yksityinen. Laitteiden yhteys toimii vain samassa verkossa salattuna.', 'AikaPolku ei ole asiakastietojärjestelmä, lääkityksen hallinta eikä valvontaväline.', 'Ehdotamme tietosuojan vaikutustenarviointia yhdessä ennen pilottia.']],
  ['10. Ehdotamme kumppanuutta ja pilottia', ['Kumppanuus, yksi pilottiyksikkö 8–12 viikkoa, säätiö vaikuttaa kehitykseen.', 'Vaiheet: valmistelu 2 vk, käyttöönotto 1 vk, käyttö 8 vk, arviointi 2 vk. Palaute joka toinen viikko.']],
  ['11. Miten onnistumista mitataan', ['Neljä pääkysymystä: käyttö arjessa, siirtymät, ohjaajan työ ja asukkaan kokemus.', 'Sovelluksessa on valmiina nimetön käytön seuranta: lukumäärät päivittäin, ei sisältöä eikä omaa tilaa.', 'Emme väitä vaikuttavuutta ennen pilotin tuloksia. Kysy: mikä hyöty oikeuttaisi jatkuvan käytön?']],
  ['12. Seuraavat askeleet', ['Sovitaan pilottiyksikkö ja yhteyshenkilöt, tavataan kokemusasiantuntijat ja aloitetaan.', 'Kysy lopuksi: mitä tarvitsette päättääksenne pilotista? Kuuntele ja kirjaa vastaus.']],
];
const children = [h1('Puhujan muistiinpanot')];
notes.forEach(([title, lines]) => { children.push(L.h2(title)); lines.forEach(l => children.push(bullet(l))); });
children[0] = new L.Paragraph({ heading: 'Heading1', children: [L.t('Puhujan muistiinpanot')] });
L.build({ file: 'build/AikaPolku_Esitys_Puhujan_muistiinpanot.docx', title: 'AikaPolku · Puhujan muistiinpanot', sections: [cover({ title: 'Puhujan muistiinpanot', subtitle: 'AikaPolku – esitys Autismisäätiölle', audience: 'Esittäjälle', version: 'Versio 1.0 · marraskuu 2026' }), children] }).then(() => console.log('ok'));
