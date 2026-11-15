// Yhteiset apuvälineet AikaPolun Word-oppaille (docx-js).
// Kuvat: kuvat/<id>.png. Jos kuvaa ei vielä ole, tulee näkyvä paikkamerkki.
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, HeadingLevel, AlignmentType, LevelFormat,
  Table, TableRow, TableCell, WidthType, BorderStyle, ShadingType, Header, Footer, PageNumber,
  TableOfContents, PageBreak
} = require('docx');

const TEAL = '0F766E', INK = '1F2937', MUTED = '475569', SOFT = 'E6F4F1', AMBER = 'F2A65A';
const FONT = 'Arial';
const KUVAT = JSON.parse(fs.readFileSync(path.join(__dirname, 'kuvat.json'), 'utf8'));

function pngSize(file) {
  const b = fs.readFileSync(file);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}

const t = (text, opts = {}) => new TextRun({ text, font: FONT, ...opts });

function p(text, opts = {}) {
  const runs = Array.isArray(text) ? text : [t(text)];
  return new Paragraph({ children: runs, spacing: { after: 120, line: 300 }, ...opts });
}

// **lihavointi** tekstin sisällä
function rich(text) {
  return text.split(/(\*\*[^*]+\*\*)/).filter(Boolean).map(s =>
    s.startsWith('**') ? t(s.slice(2, -2), { bold: true }) : t(s));
}

const h1 = (text) => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [t(text)], pageBreakBefore: true });
const h2 = (text) => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [t(text)] });
const h3 = (text) => new Paragraph({ heading: HeadingLevel.HEADING_3, children: [t(text)] });
const para = (text) => p(rich(text));
const bullet = (text, level = 0) => new Paragraph({ numbering: { reference: 'bullets', level }, children: rich(text), spacing: { after: 80, line: 290 } });
let stepListCounter = 0;
function steps(items) {
  const ref = 'steps' + (++stepListCounter);
  usedStepRefs.push(ref);
  return items.map(s => new Paragraph({ numbering: { reference: ref, level: 0 }, children: rich(s), spacing: { after: 90, line: 290 } }));
}
const usedStepRefs = [];

function box(kind, text) {
  const fill = kind === 'vinkki' ? SOFT : kind === 'huom' ? 'FDF0E1' : 'F1F5F9';
  const label = kind === 'vinkki' ? 'Vinkki' : kind === 'huom' ? 'Huomaa' : 'Miksi';
  return new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [9026],
    rows: [new TableRow({ children: [new TableCell({
      width: { size: 9026, type: WidthType.DXA },
      shading: { type: ShadingType.CLEAR, color: 'auto', fill },
      borders: noBorders(),
      margins: { top: 120, bottom: 120, left: 180, right: 180 },
      children: [new Paragraph({ children: [t(label + ': ', { bold: true, color: kind === 'huom' ? '9A5B13' : TEAL }), ...rich(text)], spacing: { after: 0, line: 290 } })]
    })] })]
  });
}

function noBorders() {
  const n = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' };
  return { top: n, bottom: n, left: n, right: n };
}

// Puhelimen kuvakaappaus (pysty) tai paikkamerkki.
function shot(id, widthPx = 210) {
  const caption = KUVAT[id] || id;
  const file = path.join(__dirname, 'kuvat', id + '.png');
  const out = [];
  if (fs.existsSync(file)) {
    const { w, h } = pngSize(file);
    out.push(new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { before: 120, after: 60 }, keepNext: true,
      children: [new ImageRun({ type: 'png', data: fs.readFileSync(file), transformation: { width: widthPx, height: Math.round(widthPx * h / w) },
        altText: { title: caption, description: caption, name: id } })]
    }));
  } else {
    out.push(new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { before: 120, after: 60 }, keepNext: true,
      border: { top: { style: BorderStyle.DASHED, size: 6, color: TEAL, space: 8 }, bottom: { style: BorderStyle.DASHED, size: 6, color: TEAL, space: 8 },
        left: { style: BorderStyle.DASHED, size: 6, color: TEAL, space: 8 }, right: { style: BorderStyle.DASHED, size: 6, color: TEAL, space: 8 } },
      shading: { type: ShadingType.CLEAR, color: 'auto', fill: 'F8FAFC' },
      children: [t('Kuvakaappaus tulee tähän: ' + id, { color: MUTED, italics: true, size: 18 })]
    }));
  }
  out.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [t(caption, { italics: true, color: MUTED, size: 18 })] }));
  return out;
}

function table(headers, rows, widths) {
  const total = widths.reduce((a, b) => a + b, 0);
  const border = { style: BorderStyle.SINGLE, size: 4, color: 'CBD5E1' };
  const borders = { top: border, bottom: border, left: border, right: border };
  const cell = (text, head, w) => new TableCell({
    width: { size: w, type: WidthType.DXA }, borders,
    shading: head ? { type: ShadingType.CLEAR, color: 'auto', fill: SOFT } : undefined,
    margins: { top: 80, bottom: 80, left: 120, right: 120 },
    children: [new Paragraph({ children: head ? [t(text, { bold: true })] : rich(text), spacing: { after: 0, line: 276 } })]
  });
  return new Table({
    width: { size: total, type: WidthType.DXA }, columnWidths: widths,
    rows: [new TableRow({ tableHeader: true, children: headers.map((h, i) => cell(h, true, widths[i])) }),
      ...rows.map(r => new TableRow({ children: r.map((c, i) => cell(c, false, widths[i])) }))]
  });
}

const spacer = () => new Paragraph({ children: [], spacing: { after: 120 } });

function cover({ title, subtitle, audience, version }) {
  const logo = path.join(__dirname, 'logot', 'logo1-vaaka.png');
  return [
    new Paragraph({ spacing: { before: 1800 }, children: [] }),
    new Paragraph({ alignment: AlignmentType.CENTER, children: [new ImageRun({ type: 'png', data: fs.readFileSync(logo), transformation: { width: 420, height: 109 }, altText: { title: 'AikaPolku', description: 'AikaPolku-logo', name: 'logo' } })] }),
    new Paragraph({ spacing: { before: 900, after: 200 }, alignment: AlignmentType.CENTER, children: [t(title, { size: 52, bold: true, color: INK })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [t(subtitle, { size: 28, color: TEAL })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, children: [t(audience, { size: 22, color: MUTED })] }),
    new Paragraph({ spacing: { before: 2200 }, alignment: AlignmentType.CENTER, children: [t(version, { size: 18, color: MUTED })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, children: [t('Bon Vivo · [Nimi] · [sähköposti] · [puhelin] · [verkkosivu]', { size: 18, color: MUTED })] }),
  ];
}

async function build({ file, title, sections }) {
  const doc = new Document({
    creator: 'Bon Vivo', title, description: title, features: { updateFields: true },
    styles: {
      default: { document: { run: { font: FONT, size: 22, color: INK } } },
      paragraphStyles: [
        { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
          run: { font: FONT, size: 36, bold: true, color: TEAL }, paragraph: { spacing: { before: 240, after: 200 }, keepNext: true, outlineLevel: 0 } },
        { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
          run: { font: FONT, size: 28, bold: true, color: INK }, paragraph: { spacing: { before: 320, after: 140 }, keepNext: true, outlineLevel: 1 } },
        { id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal', quickFormat: true,
          run: { font: FONT, size: 24, bold: true, color: TEAL }, paragraph: { spacing: { before: 220, after: 100 }, keepNext: true, outlineLevel: 2 } },
      ]
    },
    numbering: {
      config: [
        { reference: 'bullets', levels: [
          { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 300 } } } },
          { level: 1, format: LevelFormat.BULLET, text: '–', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 1000, hanging: 300 } } } }] },
        ...usedStepRefs.map(ref => ({ reference: ref, levels: [
          { level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 360 } }, run: { bold: true, color: TEAL } } }] }))
      ]
    },
    sections: sections.map((s, i) => ({
      properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1300, bottom: 1300, left: 1440, right: 1440 } } },
      headers: i === 0 ? undefined : { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [t(title, { size: 16, color: MUTED })] })] }) },
      footers: i === 0 ? undefined : { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [t('', { size: 16 }), new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 16, color: MUTED })] })] }) },
      children: s
    }))
  });
  fs.writeFileSync(file, await Packer.toBuffer(doc));
}

const toc = () => [
  new Paragraph({ heading: HeadingLevel.HEADING_1, children: [t('Sisällys')] }),
  new TableOfContents('Sisällys', { hyperlink: true, headingStyleRange: '1-2' }),
  new Paragraph({ children: [t('Päivitä sisällysluettelo Wordissa: napsauta sitä hiiren oikealla ja valitse Päivitä kenttä.', { size: 16, color: MUTED, italics: true })] })
];

module.exports = { t, p, rich, h1, h2, h3, para, bullet, steps, box, shot, table, spacer, cover, build, toc, PageBreak, Paragraph, TEAL, INK, MUTED };
