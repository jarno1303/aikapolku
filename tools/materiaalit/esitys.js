// AikaPolku – esitys Autismisäätiölle (pptxgenjs, rakenteinen pakka)
const fs = require('fs');
const path = require('path');
const pptxgen = require('pptxgenjs');
const React = require('react');
const ReactDOMServer = require('react-dom/server');
const sharp = require('sharp');
const md = require('react-icons/md');
const { applyTheme } = require('/root/.claude/skills/synced/c695a6da-d147-4570-8464-cec5bb415e1b_c96116a9-c7c3-4cc8-aca1-0d7ed1e227f3/pptx/scripts/apply_theme.js');

const THEME = {
  name: 'AikaPolku',
  headFontFace: 'Cambria',
  bodyFontFace: 'Calibri',
  colors: {
    dk1: '1F2937', lt1: 'FFFFFF', dk2: '0F766E', lt2: 'E6F4F1',
    accent1: '0F766E', accent2: 'F2A65A', accent3: '2A9D8F', accent4: '475569', accent5: 'CBD5E1', accent6: 'B45309',
    hlink: '0F766E', folHlink: '475569'
  }
};
const HEX = { teal: '0F766E', soft: 'E6F4F1', ink: '1F2937', muted: '475569', amber: 'F2A65A', white: 'FFFFFF' };
const KUVAT = JSON.parse(fs.readFileSync(path.join(__dirname, 'kuvat.json'), 'utf8'));

async function icon(Comp, color = '#FFFFFF') {
  const svg = ReactDOMServer.renderToStaticMarkup(React.createElement(Comp, { color, size: 256 }));
  const buf = await sharp(Buffer.from(svg)).png().toBuffer();
  return 'image/png;base64,' + buf.toString('base64');
}

(async () => {
  const pres = new pptxgen();
  pres.layout = 'LAYOUT_WIDE'; // 13.33 x 7.5
  pres.author = 'Bon Vivo';
  pres.company = 'Bon Vivo';
  pres.title = 'AikaPolku – kumppanuus- ja pilottiehdotus';
  pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
  const C = pres.SchemeColor;

  const logo = path.join(__dirname, 'logot', 'logo1.png');
  const logoWide = path.join(__dirname, 'logot', 'logo1-vaaka.png');

  pres.defineSlideMaster({
    title: 'Otsikkodia (tumma)',
    background: { color: C.text1 },
    objects: [
      { placeholder: { options: { name: 'title', type: 'title', x: 0.8, y: 2.3, w: 11.7, h: 1.7, fontSize: 44, bold: true, color: C.background1, fontFace: THEME.headFontFace, align: 'left', valign: 'bottom' }, text: '' } },
      { placeholder: { options: { name: 'body', type: 'body', x: 0.8, y: 4.2, w: 11.7, h: 1.8, fontSize: 22, color: C.background2, fontFace: THEME.bodyFontFace, align: 'left', valign: 'top' }, text: '' } },
    ]
  });
  pres.defineSlideMaster({
    title: 'Sisältö',
    background: { color: C.background1 },
    margin: [0.5, 0.6, 0.6, 0.6],
    objects: [
      { placeholder: { options: { name: 'title', type: 'title', x: 0.6, y: 0.35, w: 11.4, h: 0.9, fontSize: 30, bold: true, color: C.text1, fontFace: THEME.headFontFace, valign: 'middle', align: 'left' }, text: '' } },
      { image: { x: 12.2, y: 0.45, w: 0.6, h: 0.6, path: logo } },
    ],
    slideNumber: { x: 12.3, y: 7.0, w: 0.6, h: 0.3, fontSize: 11, color: '475569' }
  });

  // Puhelimen kuvakaappaus tai paikkamerkki.
  function phone(slide, id, x, y, h) {
    const w = h * 0.45;
    const file = path.join(__dirname, 'kuvat', id + '.png');
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: x - 0.08, y: y - 0.08, w: w + 0.16, h: h + 0.16, rectRadius: 0.22, fill: { color: C.text1 }, line: { color: C.text1 }, objectName: 'puhelin ' + id });
    if (fs.existsSync(file)) {
      slide.addImage({ path: file, x, y, w, h, sizing: { type: 'contain', w, h }, altText: KUVAT[id] || id, objectName: 'kuva ' + id });
    } else {
      slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.16, fill: { color: C.background2 }, line: { color: C.background2 } });
      slide.addText('Kuvakaappaus:\n' + id, { x, y: y + h / 2 - 0.5, w, h: 1.0, fontSize: 11, color: C.text2, align: 'center', isTextBox: true });
    }
  }

  async function iconRow(slide, Comp, x, y, title, text, wText = 4.6) {
    slide.addShape(pres.shapes.OVAL, { x, y, w: 0.62, h: 0.62, fill: { color: C.text2 }, line: { color: C.text2 } });
    slide.addImage({ data: await icon(Comp), x: x + 0.13, y: y + 0.13, w: 0.36, h: 0.36 });
    slide.addText([
      { text: title, options: { bold: true, fontSize: 18, color: C.text1, breakLine: true } },
      { text, options: { fontSize: 15, color: C.accent4 } }
    ], { x: x + 0.8, y: y - 0.06, w: wText, h: 0.95, valign: 'top', margin: 0, isTextBox: true });
  }

  function card(slide, x, y, w, h, title, text, source) {
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.12, fill: { color: C.background2 }, line: { color: C.background2 } });
    const runs = [
      { text: title, options: { bold: true, fontSize: 19, color: C.text2, breakLine: true } },
      { text, options: { fontSize: 16, color: C.text1, breakLine: !!source } },
    ];
    if (source) runs.push({ text: source, options: { fontSize: 11, color: C.accent4, italic: true } });
    slide.addText(runs, { x: x + 0.2, y: y + 0.15, w: w - 0.4, h: h - 0.3, valign: 'top', margin: 0, isTextBox: true, paraSpaceAfter: 4 });
  }

  // 1 Otsikko
  pres.addSection({ title: 'Johdanto' });
  let s = pres.addSlide({ masterName: 'Otsikkodia (tumma)', sectionTitle: 'Johdanto' });
  s.addImage({ path: logo, x: 0.8, y: 1.0, w: 1.3, h: 1.3 });
  s.addText('AikaPolku – toiminnanohjauksen tuki arkeen', { placeholder: 'title' });
  s.addText('Ehdotus kumppanuudesta ja pilotista Autismisäätiölle', { placeholder: 'body' });
  s.addText('Bon Vivo · [Nimi] · [päivämäärä]', { x: 0.8, y: 6.5, w: 8, h: 0.4, fontSize: 14, color: C.accent5, isTextBox: true });

  // 2 Miksi
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Johdanto' });
  s.addText('Vaikeimmat hetket ovat siirtymät ja muutokset', { placeholder: 'title' });
  s.addText('55 000', { x: 0.6, y: 1.7, w: 4.2, h: 1.1, fontSize: 66, bold: true, color: C.text2, fontFace: THEME.headFontFace, margin: 0, isTextBox: true });
  s.addText('autismikirjon ihmistä Suomessa (arvio, noin 1 % väestöstä)', { x: 0.6, y: 2.8, w: 4.2, h: 0.8, fontSize: 15, color: C.accent4, margin: 0, isTextBox: true });
  s.addText('Ennakoimattomuus', { x: 0.6, y: 3.9, w: 4.2, h: 0.6, fontSize: 28, bold: true, color: C.accent6, margin: 0, isTextBox: true });
  s.addText('tekee siirtymistä kuormittavia. Ennakointi ja toiminnan jäsentäminen auttavat.', { x: 0.6, y: 4.5, w: 4.2, h: 0.9, fontSize: 15, color: C.accent4, margin: 0, isTextBox: true });
  await iconRow(s, md.MdPhoneAndroid, 5.6, 1.8, 'Asiakkaan oma laite', 'Mitä nyt, mitä seuraavaksi, kauanko – kuvin, lyhyin sanoin ja äänellä.', 6.6);
  await iconRow(s, md.MdSwapHoriz, 5.6, 3.0, 'Siirtymät ja muutokset keskiössä', 'Ennakkovaroitus, muutoskortti, tauko ja lisäaika.', 6.6);
  await iconRow(s, md.MdApartment, 5.6, 4.2, 'Tehty yksikön arkeen', 'Yksiköt, alayksiköt, yhteiset ruokailut ja päivän toiminnat.', 6.6);
  await iconRow(s, md.MdHome, 5.6, 5.4, 'Toimii myös kotona', 'Sama sovellus perheille ja itsenäisesti asuville.', 6.6);
  s.addText('Lähde: Autismiliitto, Autismikirjon lapsi kouluiässä (2023)', { x: 0.6, y: 6.85, w: 8, h: 0.3, fontSize: 10, color: C.accent4, isTextBox: true });

  // 3 Asiakas
  pres.addSection({ title: 'Sovellus' });
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Asiakas näkee vain sen, mikä koskee häntä nyt', { placeholder: 'title' });
  phone(s, 'a01-nyt-kortti', 0.9, 1.5, 5.4);
  phone(s, 'a02-paivaohjelma', 3.75, 1.5, 5.4);
  await iconRow(s, md.MdCropSquare, 7.2, 1.7, 'NYT-kortti', 'Iso kuva ja sana: mitä tehdään nyt.', 5.2);
  await iconRow(s, md.MdViewAgenda, 7.2, 2.9, 'Näkymä asiakkaan mukaan', 'Koko päivä, nyt ja kaksi seuraavaa tai vain NYT – ohjaaja valitsee kerran.', 5.2);
  await iconRow(s, md.MdPhotoCamera, 7.2, 4.1, 'Omat kuvat ja äänet', 'Oma hammasharja, oma bussi, tutun ihmisen ääni.', 5.2);
  await iconRow(s, md.MdTouchApp, 7.2, 5.3, 'Enintään viisi painiketta', 'Kaikki näkyy ruudulla; laatuportit testaavat tämän.', 5.2);

  // 4 Siirtymät
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Siirtymät tuetaan ennen kuin ne tulevat', { placeholder: 'title' });
  await iconRow(s, md.MdNotificationsActive, 0.7, 1.7, 'Pian vaihtuu', 'Rauhallinen ennakkovaroitus kuvalla ja halutessa äänellä – myös ohjaajan tai vanhemman omalla.', 5.6);
  await iconRow(s, md.MdPauseCircle, 0.7, 3.0, 'Tarvitsen tauon', 'Aina asiakkaan käytössä ilman PIN-koodia.', 5.6);
  await iconRow(s, md.MdMoreTime, 0.7, 4.3, 'Tarvitsen vielä hetken', 'Lisäaikaa ohjaajan rajoissa, sen jälkeen viesti aikuiselle.', 5.6);
  await iconRow(s, md.MdHourglassEmpty, 0.7, 5.6, 'Odotetaan', 'Kun kestoa ei tiedä: ei sekuntilaskentaa, vaan rauhallinen kuvio ja tekemistä odottaessa.', 5.6);
  phone(s, 'a03-ennakkovaroitus', 7.5, 1.5, 5.4);
  phone(s, 'a07-odotetaan', 10.3, 1.5, 5.4);

  // 5 Muutokset
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Muutos kerrotaan selkeästi ja ajoissa', { placeholder: 'title' });
  phone(s, 'a08-muutoskortti', 0.9, 1.5, 5.4);
  card(s, 4.4, 1.6, 8.2, 1.4, 'Mikä muuttuu – ja mikä pysyy samana', 'Muutoskortti näyttää perutun, tilalle tulevan tai uuden ajan. Samana pysyvät kohdat rauhoittavat.');
  card(s, 4.4, 3.2, 8.2, 1.4, 'Ohjaaja valitsee ajankohdan', 'Heti, edellisenä iltana, aamulla tai vasta tapahtuman aikaan – asiakkaan mukaan.');
  card(s, 4.4, 4.8, 8.2, 1.4, 'Koko ryhmä yhdellä kertaa', 'Ryhmän tai päivätoiminnan muutos koskee kaikkia paikalla olevia; peruttu toiminta = kotona.');

  // 6 Itsemääräämisoikeus
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Itsemääräämisoikeus on rakennettu sisään', { placeholder: 'title' });
  phone(s, 'a05-valinta', 0.9, 1.5, 5.4);
  phone(s, 'a06-oma-tila', 3.75, 1.5, 5.4);
  await iconRow(s, md.MdCallSplit, 7.2, 1.7, 'Valinta', 'Asiakas valitsee kahdesta ja aloittaa itse. Ohjaaja voi sallia: En halua kumpaakaan.', 5.2);
  await iconRow(s, md.MdFavorite, 7.2, 3.0, 'Oma tila on asiakkaan omaa tietoa', 'Kasvot tai akku. Jää laitteelle – asiakas päättää, näyttääkö tai lähettääkö sen.', 5.2);
  await iconRow(s, md.MdSend, 7.2, 4.3, 'Kerro ohjaajalle', 'Asiakas lähettää avun pyynnön tai tilansa ohjaajan puhelimeen, kun haluaa.', 5.2);
  await iconRow(s, md.MdLockOpen, 7.2, 5.6, 'Ei lukittuja oikeuksia', 'Tauko ja apu toimivat aina.', 5.2);

  // 7 Ohjaajalle
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Ohjaaja rakentaa yksikön noin 15 minuutissa', { placeholder: 'title' });
  phone(s, 'o03-ohjattu-1-yksikko', 0.9, 1.5, 5.4);
  phone(s, 'o10-tanaan-yksikossa', 3.75, 1.5, 5.4);
  const vaiheet = ['Yksikkö ja alayksiköt', 'Asukkaat', 'Yhteiset ruokailut', 'Päivän toiminnat', 'Asukkaiden näkymä', 'Esikatselu ja tallennus'];
  vaiheet.forEach((v, i) => {
    const y = 1.6 + i * 0.78;
    s.addShape(pres.shapes.OVAL, { x: 7.2, y, w: 0.55, h: 0.55, fill: { color: C.text2 }, line: { color: C.text2 } });
    s.addText(String(i + 1), { x: 7.2, y, w: 0.55, h: 0.55, fontSize: 16, bold: true, color: C.background1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
    s.addText(v, { x: 7.95, y, w: 4.6, h: 0.55, fontSize: 17, color: C.text1, valign: 'middle', margin: 0, isTextBox: true });
  });

  // 8 Sijaintisääntö
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Sijainti ratkaisee, mitä asukas näkee', { placeholder: 'title' });
  const box = (x, y, w, h, text, fill, color) => {
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.14, fill: { color: fill }, line: { color: fill } });
    s.addText(text, { x, y, w, h, fontSize: 16, bold: true, color, align: 'center', valign: 'middle', isTextBox: true });
  };
  box(0.8, 1.8, 3.6, 0.9, 'Villa Metsämaja\nruokailut kaikille', C.text2, C.background1);
  box(0.8, 3.2, 1.7, 0.8, 'Omena', C.background2, C.text1);
  box(2.7, 3.2, 1.7, 0.8, 'Päärynä', C.background2, C.text1);
  box(5.2, 1.8, 3.2, 0.9, 'Päivätoiminta Tammi\nma–pe 9–14', C.accent2, C.text1);
  box(5.2, 3.2, 3.2, 0.8, 'Työpaja Verstas\nti ja to 9–15', C.accent2, C.text1);
  s.addShape(pres.shapes.LINE, { x: 1.65, y: 2.7, w: 0, h: 0.5, line: { color: C.accent4, width: 2 } });
  s.addShape(pres.shapes.LINE, { x: 3.55, y: 2.7, w: 0, h: 0.5, line: { color: C.accent4, width: 2 } });
  card(s, 0.8, 4.5, 7.6, 1.9, 'Asukas Eero, Omena, tiistaina', 'Klo 8 aamupala (yksikkö) → 9–15 Työpaja Verstas: kodin kohdat piilossa, näkyy yksi kortti tai työpajan runko → klo 15 Paluu kotiin → klo 17 päivällinen.');
  await iconRow(s, md.MdVisibilityOff, 9.0, 1.8, 'Ei turhaa kuormaa', 'Asukas ei näe sitä, mikä ei koske häntä.', 3.6);
  await iconRow(s, md.MdPerson, 9.0, 3.2, 'Vain omat tiedot', 'Asukas ei koskaan näe toisten tietoja.', 3.6);
  await iconRow(s, md.MdBeachAccess, 9.0, 4.6, 'Lomat ja poissaolot', 'Asukas näkee, milloin hän palaa.', 3.6);

  // 9 Tutkimusnäyttö
  pres.addSection({ title: 'Näyttö ja turvallisuus' });
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Näyttö ja turvallisuus' });
  s.addText('Ratkaisut nojaavat tutkimukseen', { placeholder: 'title' });
  const cw = 3.85, ch = 2.45;
  card(s, 0.6, 1.5, cw, ch, 'Strukturointi ja ennakointi', 'Toiminnanohjauksen vaikeuksiin auttavat riittävä ennakointi ja toiminnan jäsentäminen.', 'Autismiliitto 2023');
  card(s, 0.6 + cw + 0.25, 1.5, cw, ch, 'Ennakkoilmoitus siirtymiin', 'Ääni- ja kuvavihje ennen siirtymää vähensi siirtymiin liittyvää haastavaa käyttäytymistä.', 'Leon ym. 2023, Behavioral Interventions');
  card(s, 0.6 + 2 * (cw + 0.25), 1.5, cw, ch, 'Äänivihjeet', 'Äänivihjeiden hyöty muistamisessa näkyi erityisesti autistisilla lapsilla.', 'Sheppard ym. 2021, Res. Dev. Disabil.');
  card(s, 0.6, 4.15, cw, ch, 'Aika-apuvälineet', 'Satunnaistettu tutkimus: suuri vaikutus ajan hahmottamiseen (d = 0,81).', 'Janeslätt ym., Scand. J. Occup. Ther.');
  card(s, 0.6 + cw + 0.25, 4.15, cw, ch, 'Asumisyksikössä', 'Kognitiivinen apuväline toi ennakoitavuutta, kommunikoinnin tukea ja itsemääräämistä.', 'Søderstrøm ym., J. Intellect. Disabil.');
  card(s, 0.6 + 2 * (cw + 0.25), 4.15, cw, ch, 'Päivitys 15.11.2026', 'Arvion pohjalta lisätty: valinta, oma tila, odotetaan ja ennakkovaroituksen ääni.', 'Seuraavaksi: tuen asteittainen vähentäminen');

  // 10 Tietosuoja
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Näyttö ja turvallisuus' });
  s.addText('Tieto pysyy siellä, missä sitä tarvitaan', { placeholder: 'title' });
  await iconRow(s, md.MdCloudOff, 0.7, 1.7, 'Ei pilvipalvelua', 'Tiedot ovat laitteella. Mitään ei lähetetä internetiin.', 5.4);
  await iconRow(s, md.MdPhotoLibrary, 0.7, 3.0, 'Kuvat vain sovelluksessa', 'Kameralla otetut kuvat eivät mene puhelimen galleriaan.', 5.4);
  await iconRow(s, md.MdFavoriteBorder, 0.7, 4.3, 'Oma tila on yksityinen', 'Ei näy ohjaajalle, ellei asiakas itse näytä tai lähetä.', 5.4);
  await iconRow(s, md.MdEnhancedEncryption, 6.9, 1.7, 'Salattu yhteys (kokeiluvaihe)', 'Laitteet liitetään kerran koodia vertaamalla; viestit salataan.', 5.4);
  await iconRow(s, md.MdPin, 6.9, 3.0, 'PIN ja roolit', 'Ohjaajan toiminnot PIN-koodin takana, asiakkaan oikeudet vapaina.', 5.4);
  await iconRow(s, md.MdIosShare, 6.9, 4.3, 'Hallittu siirto', 'Vienti voidaan tehdä ilman henkilötietoja ja suojata salasanalla.', 5.4);
  card(s, 0.7, 5.6, 11.9, 0.95, 'Huomio', 'AikaPolku on toiminnanohjauksen tuki. Tietosuojan vaikutustenarviointi tehdään yhdessä ennen pilottia.');

  // 11 Laatu
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Näyttö ja turvallisuus' });
  s.addText('Laatu varmistetaan joka muutoksessa', { placeholder: 'title' });
  const stat = (x, num, label) => {
    s.addText(num, { x, y: 1.9, w: 3.8, h: 1.2, fontSize: 60, bold: true, color: C.text2, fontFace: THEME.headFontFace, margin: 0, isTextBox: true });
    s.addText(label, { x, y: 3.1, w: 3.8, h: 0.9, fontSize: 16, color: C.accent4, margin: 0, isTextBox: true });
  };
  stat(0.7, 'yli 400', 'automaattista testiä jokaisessa käännöksessä');
  stat(4.75, '8', 'puhelintestiä oikealla laitteella');
  stat(8.8, '≤ 5', 'painiketta asiakkaan ruudulla kerrallaan');
  card(s, 0.7, 4.5, 11.9, 1.7, 'Laatuportit asiakkaan näkymille', 'Kaikki näkyy ruudulla ilman vieritystä, kuva ja teksti yhdessä, tauko aina saatavilla, ei punaisia hälytyksiä. Erillinen tarkastaja käy läpi isot muutokset.');

  // 12 Ehdotus
  pres.addSection({ title: 'Ehdotus' });
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Ehdotus' });
  s.addText('Ehdotamme kumppanuutta ja pilottia', { placeholder: 'title' });
  card(s, 0.6, 1.6, 3.9, 3.3, 'Kumppanuus', 'Autismisäätiön asiantuntemus ja kokemusasiantuntijat mukaan suunnitteluun. Säännöllinen palaute kehityksen suunnasta. Yhteinen näkemys siitä, mitä asukkaat todella tarvitsevat.');
  card(s, 4.72, 1.6, 3.9, 3.3, 'Pilotti', 'Yksi yksikkö, 8–12 viikkoa. Ohjaajien koulutus, ohjattu käyttöönotto ja tuki koko pilotin ajan. Käyttöopas ohjaajille ja selkokielinen kuvaopas asukkaille.');
  card(s, 8.84, 1.6, 3.9, 3.3, 'Mitä säätiö saa', 'Sovellus käyttöön pilotin ajaksi, suora vaikutus kehitykseen ja yhteinen arvio tuloksista. Asukkaiden ääni kuuluu tuotteessa.');

  s.addText('Tavoite: sovellus, joka on tehty autististen ihmisten kanssa – ei vain heille.', { x: 0.6, y: 5.3, w: 12.1, h: 0.8, fontSize: 22, italic: true, color: C.text2, fontFace: THEME.headFontFace, margin: 0, isTextBox: true });

  // 13 Aikataulu
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Ehdotus' });
  s.addText('Pilotti neljässä vaiheessa', { placeholder: 'title' });
  const phases = [
    ['Valmistelu', '2 vk', 'Tavoitteet, tietosuoja, laitteet, yksikön valinta'],
    ['Käyttöönotto', '1 vk', 'Ohjaajien koulutus, ohjattu aloitus, asukkaiden näkymät'],
    ['Käyttö', '8 vk', 'Arkikäyttö, palaute joka toinen viikko, nopeat korjaukset'],
    ['Arviointi', '2 vk', 'Asukkaiden ja ohjaajien kokemukset, jatkosta sopiminen'],
  ];
  phases.forEach(([n, d, t], i) => {
    const x = 0.6 + i * 3.1;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 1.9, w: 2.9, h: 1.0, rectRadius: 0.14, fill: { color: i === 2 ? C.accent2 : C.text2 }, line: { color: i === 2 ? C.accent2 : C.text2 } });
    s.addText([{ text: n, options: { bold: true, fontSize: 19, breakLine: true } }, { text: d, options: { fontSize: 14 } }],
      { x, y: 1.9, w: 2.9, h: 1.0, color: i === 2 ? C.text1 : C.background1, align: 'center', valign: 'middle', isTextBox: true });
    s.addText(t, { x, y: 3.1, w: 2.9, h: 1.3, fontSize: 16, color: C.text1, valign: 'top', margin: 0, isTextBox: true });
  });
  card(s, 0.6, 4.6, 12.1, 1.6, 'Mitä seurataan', 'Käyttö arjessa, siirtymien sujuminen ohjaajien arvioimana, asukkaiden oma kokemus (myös oman tilan kautta, jos asukas haluaa jakaa) ja ohjaajien työmäärä.');

  // 14 Seuraavat askeleet
  s = pres.addSlide({ masterName: 'Otsikkodia (tumma)', sectionTitle: 'Ehdotus' });
  s.addImage({ path: logo, x: 0.8, y: 0.8, w: 1.1, h: 1.1 });
  s.addText('Seuraavat askeleet', { placeholder: 'title' });
  s.addText([
    { text: '1. Sovitaan pilottiyksikkö ja yhteyshenkilöt', options: { breakLine: true } },
    { text: '2. Kokemusasiantuntijoiden tapaaminen', options: { breakLine: true } },
    { text: '3. Pilotin aloitus', options: {} },
  ], { placeholder: 'body' });
  s.addText('[Nimi] · [sähköposti] · [puhelin] · Bon Vivo', { x: 0.8, y: 6.5, w: 10, h: 0.4, fontSize: 14, color: C.accent5, isTextBox: true });

  const out = path.join(__dirname, 'build', 'AikaPolku_Esitys_Autismisaatio.pptx');
  await pres.writeFile({ fileName: out });
  await applyTheme(out, THEME);
  console.log('ok');
})();
