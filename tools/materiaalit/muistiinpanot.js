// Puhujan muistiinpanot erillisenä Word-tiedostona.
const L = require('./lib');
const { h1, h2, para, bullet, cover } = L;
const notes = [
  ['1. AikaPolku – toiminnanohjauksen tuki arkeen', ['Kiitos ajasta. Kerron lyhyesti, mikä AikaPolku on, miksi se on tehty ja mitä ehdotamme Autismisäätiölle.', 'Olen sosionomiopiskelija ja kehittäjä; taustani on sote-alalla ja teknologiassa.']],
  ['2. Vaikeimmat hetket ovat siirtymät ja muutokset', ['Suomessa on arviolta 55 000 autismikirjon ihmistä. Arjessa kuormittavat erityisesti siirtymät ja yllättävät muutokset.', 'AikaPolku tuo ennakoinnin ja jäsentämisen asiakkaan omaan laitteeseen – asumisyksikköön, päivätoimintaan ja kotiin.']],
  ['3. Asiakas näkee vain sen, mikä koskee häntä nyt', ['NYT-kortti kertoo yhdellä kuvalla, mitä tehdään nyt. Ohjaaja valitsee kerran, näkeekö asiakas koko päivän vai vain lähimmät.', 'Asiakkaan ruudulla on enintään viisi painiketta, ja kaikki mahtuu ruudulle. Tämä testataan automaattisesti.']],
  ['4. Siirtymät tuetaan ennen kuin ne tulevat', ['Pian vaihtuu -kortti ennen siirtymää, halutessa tutun ihmisen äänellä.', 'Tarvitsen tauon toimii aina, Tarvitsen vielä hetken antaa lisäaikaa ohjaajan rajoissa.', 'Odotetaan: kun kestoa ei tiedä, ei näytetä sekunteja vaan rauhallinen kuvio.']],
  ['5. Muutos kerrotaan selkeästi ja ajoissa', ['Muutoskortti kertoo, mikä muuttuu ja mikä pysyy samana. Ohjaaja valitsee, milloin asiakas näkee muutoksen.']],
  ['6. Itsemääräämisoikeus on rakennettu sisään', ['Asiakas valitsee kahdesta ja aloittaa itse.', 'Oma tila on asiakkaan omaa tietoa – se ei näy ohjaajalle, ellei asiakas itse halua.', 'Kysymys säätiölle: miten kokemusasiantuntijat näkevät oman tilan ilmaisun?']],
  ['7. Ohjaaja rakentaa yksikön noin 15 minuutissa', ['Ohjattu aloitus: yksikkö, alayksiköt, asukkaat, ruokailut, päivän toiminnat ja näkymä. Näytä tarvittaessa demona puhelimesta.']],
  ['8. Sijainti ratkaisee, mitä asukas näkee', ['Esimerkki Eero: työpajassa kodin kohdat eivät näy; lopussa paluukortti. Asukas ei koskaan näe muiden tietoja.']],
  ['9. Ratkaisut nojaavat tutkimukseen', ['Strukturointi ja ennakointi ovat suomalaisen ohjeistuksen ydin. Ennakkoilmoitus ja äänivihjeet tukevat siirtymiä; aika-apuvälineistä on satunnaistettua näyttöä.', 'Arvion pohjalta lisäsimme valinnan, oman tilan, odotetaan-tilan ja ennakkovaroituksen äänen. Seuraava kehityskohde on tuen asteittainen vähentäminen.']],
  ['10. Tieto pysyy siellä, missä sitä tarvitaan', ['Ei pilveä, kuvat vain sovelluksessa, oma tila yksityinen. Laitteiden yhteys on kokeiluvaiheessa ja salattu.', 'Ehdotamme tietosuojan vaikutustenarviointia yhdessä ennen pilottia.']],
  ['11. Laatu varmistetaan joka muutoksessa', ['Yli 400 automaattista testiä ja puhelintestit jokaisessa versiossa. Laatuportit varmistavat asiakkaan näkymän rauhallisuuden.']],
  ['12. Ehdotamme kumppanuutta ja pilottia', ['Pyyntö: kumppanuus ja yhden yksikön pilotti 8–12 viikkoa. Säätiö saa sovelluksen käyttöön ja vaikuttaa kehitykseen.']],
  ['13. Pilotti neljässä vaiheessa', ['Valmistelu 2 vk, käyttöönotto 1 vk, käyttö 8 vk, arviointi 2 vk. Palaute joka toinen viikko.']],
  ['14. Seuraavat askeleet', ['Sovitaan pilottiyksikkö ja yhteyshenkilöt, tavataan kokemusasiantuntijat ja sovitaan aloitus.', 'Kysy lopuksi: mikä teidän mielestänne on tärkeintä onnistumiselle?']],
];
const children = [h1('Puhujan muistiinpanot')];
notes.forEach(([title, lines]) => { children.push(L.h2(title)); lines.forEach(l => children.push(bullet(l))); });
children[0] = new L.Paragraph({ heading: 'Heading1', children: [L.t('Puhujan muistiinpanot')] });
L.build({ file: 'build/AikaPolku_Esitys_Puhujan_muistiinpanot.docx', title: 'AikaPolku · Puhujan muistiinpanot', sections: [cover({ title: 'Puhujan muistiinpanot', subtitle: 'AikaPolku – esitys Autismisäätiölle', audience: 'Esittäjälle', version: 'Versio 1.0 · marraskuu 2026' }), children] }).then(() => console.log('ok'));
