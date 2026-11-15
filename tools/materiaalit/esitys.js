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
    const file = path.join(__dirname, fs.existsSync(path.join(__dirname, 'kuvat-rajattu', id + '.png')) ? 'kuvat-rajattu' : 'kuvat', id + '.png');
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
      { text: title, options: { bold: true, fontSize: 21, color: C.text1, breakLine: true } },
      { text, options: { fontSize: 17, color: C.accent4 } }
    ], { x: x + 0.8, y: y - 0.06, w: wText, h: 1.2, valign: 'top', margin: 0, isTextBox: true });
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

  // --- Apuvälineet uudelle rakenteelle: isot puhelinkuvat ja numeroidut nostot ---
  const RATIO = 1080 / 2261; // rajattu kuvakaappaus (leveys / korkeus)
  // Puhelin, jonka koko seuraa kuvaa: palauttaa kuvan alueen numeromerkkejä varten.
  function bigPhone(slide, id, x, y, h) {
    const w = h * RATIO;
    const file = path.join(__dirname, fs.existsSync(path.join(__dirname, 'kuvat-rajattu', id + '.png')) ? 'kuvat-rajattu' : 'kuvat', id + '.png');
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: x - 0.09, y: y - 0.09, w: w + 0.18, h: h + 0.18, rectRadius: 0.24, fill: { color: C.text1 }, line: { color: C.text1 }, objectName: 'puhelin ' + id });
    if (fs.existsSync(file)) {
      slide.addImage({ path: file, x, y, w, h, altText: KUVAT[id] || id, objectName: 'kuva ' + id });
    } else {
      slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.16, fill: { color: C.background2 }, line: { color: C.background2 } });
      slide.addText('Kuvakaappaus:\n' + id, { x, y: y + h / 2 - 0.5, w, h: 1.0, fontSize: 12, color: C.text2, align: 'center', isTextBox: true });
    }
    return { x, y, w, h };
  }
  // Numeromerkki kuvan kohtaan (fx, fy = osuus kuvan leveydestä ja korkeudesta).
  function marker(slide, box, n, fx, fy) {
    const d = 0.5;
    slide.addShape(pres.shapes.OVAL, { x: box.x + box.w * fx - d / 2, y: box.y + box.h * fy - d / 2, w: d, h: d, fill: { color: C.accent2 }, line: { color: C.background1, width: 2.5 }, objectName: 'merkki ' + n });
    slide.addText(String(n), { x: box.x + box.w * fx - d / 2, y: box.y + box.h * fy - d / 2, w: d, h: d, fontSize: 18, bold: true, color: C.text1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
  }
  // Numeroitu nosto tekstipalstaan.
  function point(slide, n, x, y, title, text, w = 5.6) {
    slide.addShape(pres.shapes.OVAL, { x, y: y + 0.02, w: 0.5, h: 0.5, fill: { color: C.accent2 }, line: { color: C.accent2 } });
    slide.addText(String(n), { x, y: y + 0.02, w: 0.5, h: 0.5, fontSize: 18, bold: true, color: C.text1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
    slide.addText([
      { text: title, options: { bold: true, fontSize: 22, color: C.text1, breakLine: true } },
      { text, options: { fontSize: 17, color: C.accent4 } }
    ], { x: x + 0.7, y: y - 0.05, w, h: 1.05, valign: 'top', margin: 0, isTextBox: true });
  }
  // Kolmen puhelimen dia: puhelin keskelle omaa palstaansa ja numeroitu kuvateksti sen yläpuolelle.
  function phoneColumn(slide, i, id, n, text, h) {
    const slotW = 4.05, slotX = 0.6 + i * slotW;
    const w = h * RATIO;
    const box = bigPhone(slide, id, slotX + (slotW - w) / 2, 2.25, h);
    slide.addShape(pres.shapes.OVAL, { x: slotX + 0.15, y: 1.42, w: 0.5, h: 0.5, fill: { color: C.accent2 }, line: { color: C.accent2 } });
    slide.addText(String(n), { x: slotX + 0.15, y: 1.42, w: 0.5, h: 0.5, fontSize: 18, bold: true, color: C.text1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
    slide.addText(text, { x: slotX + 0.78, y: 1.3, w: slotW - 0.95, h: 0.75, fontSize: 18, bold: true, color: C.text1, valign: 'middle', margin: 0, isTextBox: true });
    return box;
  }
  function bigCard(slide, x, y, w, h, title, text, source) {
    slide.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.14, fill: { color: C.background2 }, line: { color: C.background2 } });
    const runs = [
      { text: title, options: { bold: true, fontSize: 24, color: C.text2, breakLine: true } },
      { text, options: { fontSize: 18, color: C.text1, breakLine: !!source } },
    ];
    if (source) runs.push({ text: source, options: { fontSize: 12, color: C.accent4, italic: true } });
    slide.addText(runs, { x: x + 0.3, y: y + 0.25, w: w - 0.6, h: h - 0.5, valign: 'top', margin: 0, isTextBox: true, paraSpaceAfter: 8 });
  }

  // 1 Kansi
  pres.addSection({ title: 'Johdanto' });
  let s = pres.addSlide({ masterName: 'Otsikkodia (tumma)', sectionTitle: 'Johdanto' });
  s.addImage({ path: logo, x: 0.8, y: 0.9, w: 1.3, h: 1.3 });
  s.addText('AikaPolku – arjen ennakointi kuvin ja äänin', { placeholder: 'title' });
  s.addText('Ehdotamme Autismisäätiölle kumppanuutta ja pilottia yhdessä yksikössä.', { placeholder: 'body' });
  s.addText('Bon Vivo · [Nimi] · [päivämäärä]', { x: 0.8, y: 6.5, w: 8, h: 0.4, fontSize: 16, color: C.accent5, isTextBox: true });

  // 2 Miksi
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Johdanto' });
  s.addText('Vaikeimmat hetket ovat siirtymät ja muutokset', { placeholder: 'title' });
  s.addText('55 000', { x: 0.6, y: 1.7, w: 5.2, h: 1.4, fontSize: 88, bold: true, color: C.text2, fontFace: THEME.headFontFace, margin: 0, isTextBox: true });
  s.addText('autismikirjon ihmistä Suomessa (arvio, noin 1 % väestöstä)', { x: 0.6, y: 3.1, w: 5.0, h: 0.9, fontSize: 20, color: C.accent4, margin: 0, isTextBox: true });
  s.addText([
    { text: 'Ennakoimattomuus kuormittaa.', options: { bold: true, fontSize: 30, color: C.accent6, breakLine: true } },
    { text: 'AikaPolku kertoo asukkaan omalla laitteella joka hetki: mitä nyt, mitä seuraavaksi, kauanko – ja mitä tapahtuu, jos jokin muuttuu.', options: { fontSize: 22, color: C.text1 } }
  ], { x: 6.3, y: 1.8, w: 6.4, h: 2.6, valign: 'top', margin: 0, isTextBox: true, paraSpaceAfter: 10 });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.6, y: 4.9, w: 12.1, h: 1.4, rectRadius: 0.14, fill: { color: C.text2 }, line: { color: C.text2 } });
  s.addText([
    { text: 'Pyyntömme tänään: ', options: { bold: true, fontSize: 24, color: C.background1 } },
    { text: 'kumppanuus ja 8–12 viikon pilotti yhdessä Autismisäätiön yksikön kanssa.', options: { fontSize: 24, color: C.background1 } }
  ], { x: 0.9, y: 4.9, w: 11.5, h: 1.4, valign: 'middle', margin: 0, isTextBox: true });
  s.addText('Lähde: Autismiliitto, Autismikirjon lapsi kouluiässä (2023)', { x: 0.6, y: 6.85, w: 8, h: 0.3, fontSize: 11, color: C.accent4, isTextBox: true });

  // 3 Ainon iltapäivä
  pres.addSection({ title: 'Sovellus' });
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Ainon iltapäivä kolmena hetkenä', { placeholder: 'title' });
  [['a02-paivaohjelma', 'Ohjelma etenee kuva kerrallaan'], ['a08-muutoskortti', 'Suunnitelma muuttuu – Aino näkee miksi'], ['a04-tauko', 'Aino pyytää tauon itse']]
    .forEach(([id, text], i) => phoneColumn(s, i, id, i + 1, text, 4.95));

  // 4 Mitä Aino näkee
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Aino näkee vain sen, mikä koskee häntä nyt', { placeholder: 'title' });
  const b4 = bigPhone(s, 'a02-paivaohjelma', 1.2, 1.35, 5.85);
  marker(s, b4, 1, 1.12, 0.3);
  marker(s, b4, 2, 1.12, 0.62);
  marker(s, b4, 3, 1.12, 0.735);
  marker(s, b4, 4, 1.12, 0.835);
  point(s, 1, 5.6, 1.5, 'Iso kuva ja yksi sana', 'Mitä tehdään nyt. Omat kuvat ja tutun ihmisen ääni käyvät.', 6.4);
  point(s, 2, 5.6, 2.85, 'Apu ja tauko aina saatavilla', 'Ilman PIN-koodia – myös En ymmärrä.', 6.4);
  point(s, 3, 5.6, 4.2, 'Yksi selvä painallus', 'Valmis vie seuraavaan. Enintään viisi painiketta ruudulla.', 6.4);
  point(s, 4, 5.6, 5.55, 'Seuraava näkyy etukäteen', 'Kuvana, tekstinä tai piilossa – ohjaaja valitsee asukkaan mukaan.', 6.4);

  // 5 Siirtymät ja muutokset
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Siirtymät tuetaan ennen kuin ne tulevat', { placeholder: 'title' });
  const b5a = bigPhone(s, 'a03-ennakkovaroitus', 0.9, 1.35, 5.85);
  marker(s, b5a, 1, 1.08, 0.81);
  marker(s, b5a, 2, 1.08, 0.9);
  const b5b = bigPhone(s, 'a07-odotetaan', 4.3, 1.35, 5.85);
  marker(s, b5b, 3, 1.08, 0.3);
  point(s, 1, 7.85, 1.6, 'Pian vaihtuu', 'Rauhallinen ennakkovaroitus kuvalla – halutessa vanhemman tai ohjaajan omalla äänellä.', 4.4);
  point(s, 2, 7.85, 3.2, 'Tarvitsen vielä hetken', 'Lisäaikaa ohjaajan rajoissa, sitten viesti aikuiselle.', 4.4);
  point(s, 3, 7.85, 4.8, 'Odotetaan', 'Kun kestoa ei tiedä: ei sekunteja, vaan rauhallinen kuvio ja tekemistä odottaessa.', 4.4);

  // 6 Asukkaan oma ääni
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Asukkaalla on oma ääni', { placeholder: 'title' });
  [['a05-valinta', 'Valitsee itse kahdesta'], ['a06-oma-tila', 'Kertoo voinnistaan – jos haluaa'], ['a11-en-ymmarra', 'Sanoo: en ymmärrä']]
    .forEach(([id, text], i) => phoneColumn(s, i, id, i + 1, text, 4.6));
  s.addText('Oma tila on asukkaan omaa tietoa: se ei näy ohjaajalle, ellei asukas itse halua.', { x: 0.6, y: 7.0, w: 11.4, h: 0.4, fontSize: 15, italic: true, color: C.accent4, margin: 0, isTextBox: true });

  // 7 Ohjaajan työ
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Sovellus' });
  s.addText('Ohjaajalle kevyt: yksikkö noin 15 minuutissa', { placeholder: 'title' });
  [['o03-ohjattu-1-yksikko', 'Yksikkö ja asukkaat ohjatusti'], ['o12-asiakkaan-viikko', 'Viikko: yhteiset ja omat'], ['o18-tee-muutos', 'Muutos muutamalla napautuksella']]
    .forEach(([id, text], i) => phoneColumn(s, i, id, i + 1, text, 4.95));

  // 8 Tutkimus
  pres.addSection({ title: 'Näyttö ja turvallisuus' });
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Näyttö ja turvallisuus' });
  s.addText('Ratkaisut nojaavat tutkimukseen', { placeholder: 'title' });
  const rw = 3.9;
  bigCard(s, 0.6, 1.6, rw, 3.6, 'Ennakointi ja jäsentäminen', 'Toiminnanohjauksen vaikeuksiin auttavat riittävä ennakointi ja toiminnan jäsentäminen: mitä, milloin ja missä.', 'Autismiliitto 2023');
  bigCard(s, 0.6 + rw + 0.2, 1.6, rw, 3.6, 'Ennakkovaroitus kuvalla ja äänellä', 'Ennakkoilmoitus vähensi siirtymiin liittyvää kuormitusta; äänivihjeiden hyöty näkyi erityisesti autistisilla lapsilla.', 'Leon ym. 2023 · Sheppard ym. 2021');
  bigCard(s, 0.6 + 2 * (rw + 0.2), 1.6, rw, 3.6, 'Aika näkyväksi', 'Satunnaistetussa tutkimuksessa aika-apuvälineillä oli suuri vaikutus ajan hahmottamiseen ja ajanhallintaan.', 'Janeslätt ym.');
  s.addText('Tutkimus perustelee ratkaisut. AikaPolun oma vaikuttavuus osoitetaan pilotissa.', { x: 0.6, y: 5.6, w: 11.4, h: 0.4, fontSize: 18, italic: true, color: C.accent4, margin: 0, isTextBox: true });

  // 9 Turvallisuus ja rajaus
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Näyttö ja turvallisuus' });
  s.addText('Turvallinen ja selkeästi rajattu', { placeholder: 'title' });
  s.addText('Tieto pysyy laitteella', { x: 0.6, y: 1.5, w: 5.8, h: 0.6, fontSize: 26, bold: true, color: C.text2, margin: 0, isTextBox: true });
  await iconRow(s, md.MdCloudOff, 0.6, 2.35, 'Ei pilveä eikä palvelinta', 'Ei Googlen varmuuskopiota; laitteet keskustelevat vain samassa verkossa salattuna.', 5.2);
  await iconRow(s, md.MdFavorite, 0.6, 3.65, 'Oma tila on asukkaan', 'Ei näy ohjaajalle eikä siirry mihinkään ilman asukkaan painallusta.', 5.2);
  await iconRow(s, md.MdLock, 0.6, 4.95, 'Ohjaajan toiminnot PIN-koodin takana', 'Asukkaan apu ja tauko eivät koskaan ole lukossa.', 5.2);
  s.addText('AikaPolku ei ole', { x: 7.0, y: 1.5, w: 5.8, h: 0.6, fontSize: 26, bold: true, color: C.accent6, margin: 0, isTextBox: true });
  await iconRow(s, md.MdDescription, 7.0, 2.35, 'Asiakastietojärjestelmä', 'Kirjaukset tehdään yksikön omaan järjestelmään.', 5.2);
  await iconRow(s, md.MdMedication, 7.0, 3.65, 'Lääkityksen hallinta', 'Muistutus käy, mutta lääkkeenantoa ei kuitata eikä kirjata.', 5.2);
  await iconRow(s, md.MdVisibilityOff, 7.0, 4.95, 'Valvontaväline', 'Asukkaasta ei raportoida ohjaajalle automaattisesti.', 5.2);
  s.addText('Laitteiden suojaus, säilytysajat ja vastuut sovitaan yhdessä ennen pilottia.', { x: 0.6, y: 6.45, w: 11.6, h: 0.4, fontSize: 16, italic: true, color: C.accent4, margin: 0, isTextBox: true });

  // 10 Ehdotus
  pres.addSection({ title: 'Ehdotus' });
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Ehdotus' });
  s.addText('Ehdotamme kumppanuutta ja pilottia', { placeholder: 'title' });
  const ew = 3.9;
  bigCard(s, 0.6, 1.5, ew, 2.7, 'Kumppanuus', 'Säätiön asiantuntijat ja kokemusasiantuntijat mukaan suunnitteluun.');
  bigCard(s, 0.6 + ew + 0.2, 1.5, ew, 2.7, 'Pilotti', 'Yksi yksikkö, 8–12 viikkoa. Ohjaajat koulutetaan, ja tuki on koko ajan saatavilla.');
  bigCard(s, 0.6 + 2 * (ew + 0.2), 1.5, ew, 2.7, 'Mitä säätiö saa', 'Sovelluksen käyttöön pilotin ajaksi ja vaikutusvallan sen kehitykseen.');
  const phases = [['Valmistelu', '2 vk'], ['Käyttöönotto', '1 vk'], ['Käyttö', '8 vk'], ['Arviointi', '2 vk']];
  const pw = 2.9;
  phases.forEach(([t, d], i) => {
    const x = 0.6 + i * (pw + 0.17);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 4.6, w: pw, h: 1.2, rectRadius: 0.12, fill: { color: i === 2 ? C.accent2 : C.text2 }, line: { color: i === 2 ? C.accent2 : C.text2 } });
    s.addText([
      { text: t, options: { bold: true, fontSize: 22, color: i === 2 ? C.text1 : C.background1, breakLine: true } },
      { text: d, options: { fontSize: 17, color: i === 2 ? C.text1 : C.background1 } }
    ], { x, y: 4.6, w: pw, h: 1.2, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
  });
  s.addText('Palaute joka toinen viikko. Lähtötilanne kirjataan ennen käyttöönottoa.', { x: 0.6, y: 6.15, w: 11.6, h: 0.4, fontSize: 17, color: C.accent4, margin: 0, isTextBox: true });

  // 11 Mittarit
  s = pres.addSlide({ masterName: 'Sisältö', sectionTitle: 'Ehdotus' });
  s.addText('Miten onnistumista mitataan', { placeholder: 'title' });
  const mw = 5.95, mh = 2.15;
  [['Käyttö arjessa', 'Käytetäänkö sovellusta päivittäin pilotin loppuun asti?'],
   ['Siirtymät', 'Sujuvatko siirtymät ja muutokset paremmin ohjaajien arvioimana?'],
   ['Ohjaajan työ', 'Väheneekö manuaalinen ohjaus ja muistuttaminen?'],
   ['Asukkaan kokemus', 'Asukkaan oma arvio – jos hän haluaa kertoa.']]
    .forEach(([t, x2], i) => bigCard(s, 0.6 + (i % 2) * (mw + 0.2), 1.5 + Math.floor(i / 2) * (mh + 0.2), mw, mh, t, x2));
  s.addText('Sovelluksessa on valmiina nimetön käytön seuranta (oletuksena pois). Oma tila ei ole mittari.', { x: 0.6, y: 6.45, w: 11.6, h: 0.4, fontSize: 16, italic: true, color: C.accent4, margin: 0, isTextBox: true });

  // 12 Seuraavat askeleet
  s = pres.addSlide({ masterName: 'Otsikkodia (tumma)', sectionTitle: 'Ehdotus' });
  s.addImage({ path: logo, x: 0.8, y: 0.9, w: 1.1, h: 1.1 });
  s.addText('Seuraavat askeleet', { placeholder: 'title' });
  s.addText([
    { text: '1. Sovitaan pilottiyksikkö ja yhteyshenkilöt', options: { breakLine: true } },
    { text: '2. Tavataan kokemusasiantuntijat', options: { breakLine: true } },
    { text: '3. Aloitetaan pilotti', options: { breakLine: true } },
    { text: ' ', options: { breakLine: true } },
    { text: 'Mitä tarvitsette päättääksenne pilotista?', options: { bold: true, color: C.accent2 } }
  ], { x: 0.8, y: 4.15, w: 11.7, h: 2.3, fontSize: 24, color: C.background2, valign: 'top', margin: 0, isTextBox: true });
  s.addText('[Nimi] · [sähköposti] · [puhelin] · Bon Vivo', { x: 0.8, y: 6.6, w: 10, h: 0.4, fontSize: 16, color: C.accent5, isTextBox: true });

  const out = path.join(__dirname, 'build', 'AikaPolku_Esitys_Autismisaatio.pptx');
  await pres.writeFile({ fileName: out });
  await applyTheme(out, THEME);
  console.log('ok');
})();
