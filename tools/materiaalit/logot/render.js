const sharp = require('sharp'); const fs = require('fs');
(async () => {
  for (const n of ['logo1','logo2','logo3']) {
    const svg = fs.readFileSync(n + '.svg');
    await sharp(svg).resize(1024, 1024).png().toFile(n + '.png');
    // Vaakalogo: merkki + teksti
    const word = `<svg xmlns="http://www.w3.org/2000/svg" width="2000" height="520" viewBox="0 0 2000 520">
      <rect width="2000" height="520" fill="white"/>
      <image href="data:image/svg+xml;base64,${svg.toString('base64')}" x="20" y="20" width="480" height="480"/>
      <text x="560" y="330" font-family="Arial, Helvetica, sans-serif" font-size="230" font-weight="700" fill="#1F2937">Aika<tspan fill="#0F766E">Polku</tspan></text>
      <text x="568" y="440" font-family="Arial, Helvetica, sans-serif" font-size="66" fill="#475569">Toiminnanohjauksen tuki arkeen</text>
    </svg>`;
    await sharp(Buffer.from(word)).png().toFile(n + '-vaaka.png');
  }
})();
