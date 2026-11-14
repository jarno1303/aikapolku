// Tekee AikaPolun tutkielman Word-version markdown-päälähteestä.
// Käyttö: node md_docxiksi.js TUTKIELMA_OPTIMAALINEN_SOVELLUS.md AikaPolku_tutkielma_optimaalinen_sovellus.docx
// Tukee: # ## ### otsikot, kappaleet, **lihavointi**, *kursiivi*, `koodi`, > lainaus,
// - luettelot, 1. numeroidut luettelot, | taulukot |, --- erotin.
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType,
  ShadingType, BorderStyle, LevelFormat, AlignmentType, Footer, PageNumber, HeadingLevel,
} = require("docx");

const [, , src, out] = process.argv;
const lines = fs.readFileSync(src, "utf8").replace(/\r/g, "").split("\n");
const FONT = "Calibri";
const CONTENT_W = 9026; // A4, 1" reunukset
const ACCENT = "2F5D62";

function runs(text, base = {}) {
  const out = [];
  const re = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)/g;
  let last = 0, m;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(new TextRun({ text: text.slice(last, m.index), font: FONT, ...base }));
    const t = m[0];
    if (t.startsWith("**")) out.push(new TextRun({ text: t.slice(2, -2), bold: true, font: FONT, ...base }));
    else if (t.startsWith("`")) out.push(new TextRun({ text: t.slice(1, -1), font: "Consolas", ...base }));
    else out.push(new TextRun({ text: t.slice(1, -1), italics: true, font: FONT, ...base }));
    last = m.index + t.length;
  }
  if (last < text.length) out.push(new TextRun({ text: text.slice(last), font: FONT, ...base }));
  return out;
}

function table(rows) {
  const cells = rows.map((r) => r.replace(/^\||\|$/g, "").split("|").map((c) => c.trim()));
  const head = cells[0], body = cells.slice(2);
  const n = head.length;
  const data = cells.filter((_, k) => k !== 1);
  const plain = (s) => (s || "").replace(/[*`]/g, "");
  // Vähimmäisleveys: pisin sana mahtuu katkeamatta. Loput jaetaan tekstin määrän mukaan.
  const minW = head.map((_, i) => Math.max(...data.map((r) => Math.max(...plain(r[i]).split(/\s+/).map((w) => w.length)))) * 105 + 260);
  const weight = head.map((_, i) => Math.max(...data.map((r) => Math.min(plain(r[i]).length, 60)), 4));
  const spare = Math.max(CONTENT_W - minW.reduce((a, b) => a + b, 0), 0);
  const wsum = weight.reduce((a, b) => a + b, 0);
  let widths = head.map((_, i) => minW[i] + Math.floor((weight[i] / wsum) * spare));
  const scale = CONTENT_W / widths.reduce((a, b) => a + b, 0);
  if (scale < 1) widths = widths.map((w) => Math.floor(w * scale));
  widths[n - 1] += CONTENT_W - widths.reduce((a, b) => a + b, 0);
  const border = { style: BorderStyle.SINGLE, size: 4, color: "BFC9CA" };
  const borders = { top: border, bottom: border, left: border, right: border };
  const mk = (txt, i, isHead) => new TableCell({
    width: { size: widths[i], type: WidthType.DXA },
    borders,
    shading: isHead ? { fill: "E3ECEC", type: ShadingType.CLEAR, color: "auto" } : undefined,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({ children: runs(txt || "", { size: 19, bold: isHead || undefined }) })],
  });
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: widths,
    rows: [
      new TableRow({ tableHeader: true, children: head.map((t, i) => mk(t, i, true)) }),
      ...body.map((r) => new TableRow({ children: head.map((_, i) => mk(r[i], i, false)) })),
    ],
  });
}

const children = [];
let numberedList = 0;
for (let i = 0; i < lines.length; i++) {
  const l = lines[i];
  if (!l.trim()) continue;
  if (l.startsWith("|")) {
    const rows = [];
    while (i < lines.length && lines[i].startsWith("|")) rows.push(lines[i++]);
    i--;
    children.push(table(rows));
    children.push(new Paragraph({ children: [], spacing: { after: 80 } }));
    continue;
  }
  let m;
  if ((m = l.match(/^(#{1,3}) (.*)$/))) {
    const lvl = m[1].length;
    const heading = [HeadingLevel.TITLE, HeadingLevel.HEADING_1, HeadingLevel.HEADING_2][lvl - 1];
    children.push(new Paragraph({ heading, children: runs(m[2]) }));
    numberedList++;
    continue;
  }
  if (l.trim() === "---") {
    children.push(new Paragraph({ border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: "BFC9CA", space: 1 } }, children: [] }));
    continue;
  }
  if ((m = l.match(/^> ?(.*)$/))) {
    children.push(new Paragraph({
      indent: { left: 400 },
      border: { left: { style: BorderStyle.SINGLE, size: 12, color: ACCENT, space: 8 } },
      children: runs(m[1], { color: "44546A" }),
    }));
    continue;
  }
  if ((m = l.match(/^- (.*)$/))) {
    children.push(new Paragraph({ numbering: { reference: "bullets", level: 0 }, children: runs(m[1]) }));
    continue;
  }
  if ((m = l.match(/^\d+\. (.*)$/))) {
    children.push(new Paragraph({ numbering: { reference: "numbers", instance: numberedList, level: 0 }, children: runs(m[1]) }));
    continue;
  }
  children.push(new Paragraph({ children: runs(l) }));
}

const H = (size, before, after, color) => ({
  run: { font: FONT, size, bold: true, color },
  paragraph: { spacing: { before, after }, keepNext: true },
});
const doc = new Document({
  creator: "Bon Vivo",
  title: "AikaPolku – tutkielma",
  styles: {
    default: { document: { run: { font: FONT, size: 21 }, paragraph: { spacing: { after: 100, line: 276 } } } },
    paragraphStyles: [
      { id: "Title", name: "Title", basedOn: "Normal", ...H(36, 0, 160, ACCENT) },
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true, ...H(28, 320, 120, ACCENT) },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true, ...H(23, 220, 80, "333333") },
    ],
  },
  numbering: {
    config: [
      { reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 400, hanging: 260 } } } }] },
      { reference: "numbers", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 400, hanging: 300 } } } }] },
    ],
  },
  sections: [{
    properties: { page: { margin: { top: 1440, bottom: 1440, left: 1440, right: 1440 } } },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [
      new TextRun({ text: "AikaPolku – tutkielma · sivu ", font: FONT, size: 16, color: "808080" }),
      new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 16, color: "808080" }),
    ] })] }) },
    children,
  }],
});
Packer.toBuffer(doc).then((b) => { fs.writeFileSync(out, b); console.log("Kirjoitettu:", out); });
