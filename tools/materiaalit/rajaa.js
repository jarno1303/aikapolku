// Rajaa puhelimen tilapalkin pois kuvakaappauksista: kuvat/<id>.png -> kuvat-rajattu/<id>.png.
// Tilapalkin korkeus luetaan tiedostosta kuvat/tilapalkki-<px>.txt (kuvatesti kirjoittaa sen).
const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const src = path.join(__dirname, 'kuvat');
const out = path.join(__dirname, 'kuvat-rajattu');
fs.mkdirSync(out, { recursive: true });
const marker = fs.existsSync(src) ? fs.readdirSync(src).find(f => /^tilapalkki-\d+\.txt$/.test(f)) : null;
const top = marker ? parseInt(marker.match(/\d+/)[0], 10) : 0;
(async () => {
  let n = 0;
  for (const f of (fs.existsSync(src) ? fs.readdirSync(src) : [])) {
    if (!f.endsWith('.png') || f.includes('-VIRHE')) continue;
    const img = sharp(path.join(src, f));
    const { width, height } = await img.metadata();
    const cut = height > width ? top : 0; // vain pystykuvista
    await img.extract({ left: 0, top: cut, width, height: height - cut }).png().toFile(path.join(out, f));
    n++;
  }
  console.log(`rajattu ${n} kuvaa, tilapalkki ${top} px`);
})();
