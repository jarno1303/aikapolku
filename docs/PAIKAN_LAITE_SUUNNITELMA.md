# Paikan laite ja tahdistus asukkaiden puhelimiin (A5)

Sovittu 16.11.2026 (JarnoL). Tutkielman kohta A5.

## Sovittu malli
- **Paikan laite** (esim. yksikön tabletti) pitää paikan tiedot: asukkaat, viikot, ryhmät ja toiminnat, muutokset, päiväohjelmat, kortit, kuvat ja äänitteet.
- **Asetus paikkakohtaisesti:** "Paikalla on oma AikaPolku-laite" päällä / pois. Kun pois, paikka näkyy kotiyksikön tiedoista (esim. toimintakortti "Päivätoiminta Tammi 9–14"), ja kotiyksikön ohjaaja merkitsee muutokset.
- **Ilman paikan laitetta** (pieni yksikkö, koti) ohjaajan puhelin toimii itse paikan laitteena: sama mekanismi.
- **Asukkaan puhelin** saa vain omat tietonsa ja toimii ilman yhteyttä viimeksi saaduilla tiedoilla.
- **Sijainti:** ei GPS:ää. Puhelin löytää liitetyn paikan laitteen samasta verkosta (mDNS, `_aikapolku._tcp`). Sijaintia ei tallenneta.
- **Kaksi paikkaa samassa verkossa:** asukkaan viikko ratkaisee, kumman ohjelma näkyy.

## Päätös 16.11.2026: pilotissa paikallinen malli
- Ohjaajat suunnittelevat ja muokkaavat **paikan laitteella** (esim. toimiston tabletti). Ohjaajan oma puhelin vastaanottaa asukkaiden viestit kuten nyt.
- Ei pilvipalvelua pilotissa: tieto ei lähde talosta. Vertailu: kuluttajasovellukset (Tiimo, Goally) käyttävät pilveä käyttäjän hyväksynnällä; organisaatioille myyty MEMOplanner käyttää EU-pilveä, käsittelijäsopimusta, ISO-sertifiointia ja MDR-luokitusta.
- Pilotin jälkeen harkitaan valinnaista EU-pilveä (PC-suunnittelu, etämuokkaus) ja ohjaajien puhelimia etämuokkaajina, jos organisaatiot sitä pyytävät.

## Mitä siirtyy asukkaan puhelimeen
- Siirtyy: oma viikko, päiväohjelmat, kortit, muutokset, odotus, asukkaan asetukset (RUN_PREFS, TIMER_PREFS), asukkaan ryhmät ja yksiköt (vain hänen jäsenyytensä, poikkeuksensa ja omat aikansa), ryhmien muutokset, hänen poissaolonsa ja lomansa, viitatut kuvat ja äänitteet.
- Ei siirry: muiden asukkaiden tiedot, PIN-koodi, ohjaajan laiteasetukset.
- Pysyy vain asukkaan puhelimessa: oma tila, käytön seuranta, kuittaukset, suorituksen edistyminen.
- Asukkaan puhelimesta paikan laitteelle: vain asukkaan omat viestit (Kerro ohjaajalle) ja kuittaus "muutos nähty".

## Milloin
- Automaattisesti: ohjaajan tallennus kasvattaa asukkaan paketin versiota. Asukkaan puhelin kysyy versiota (pieni pyyntö) sovelluksen ollessa auki noin minuutin välein ja verkkoon tultaessa; jos versio muuttui, se hakee paketin.
- "Lähetä nyt": ohjaaja näkee asukkaittain, onko viimeisin versio perillä ("Aino ✓ 11.20", "Eero: odottaa, ei verkossa").
- Käynnissä oleva päiväohjelma tai ajastin ei hyppää: muutos tulee voimaan seuraavassa vaiheessa.

## Liitos paikkaan
Asukkaan puhelimessa ohjaajan tilassa "Liitä paikkaan" → paikan laite listasta → sama koodi molemmilla ruuduilla → paikan laitteella valitaan, kenen asukkaan puhelin tämä on. Kerran per paikka. Asukas ei tee mitään.

## Tekninen rakenne
- **Asukaspaketti** (`link/ResidentPackage.kt`): zip (data.json + media/). Ryhmien jäsentunnus korvataan merkillä `@self`, joka puretaan asukkaan puhelimen omaksi profiiliksi. Kuvat ja äänet nimetään sisällön tiivisteellä (SHA-256), ja pyynnössä kerrotaan jo olemassa olevat, joten ne siirtyvät vain uusina tai muuttuneina.
- **Purku** asukkaan puhelimeen korvaa paketin tiedostot kokonaan (paikka on totuus), mutta ei koske asukkaan omiin tietoihin. Paikan ryhmät korvataan paikkakohtaisesti (lähde muistetaan).
- **Siirto** (`LinkProtocol`): uudet salatut pyynnöt `ver` (versio) ja `pull` (paketti); paikan laite vastaa vain liitetylle asukkaan puhelimelle ja vain sen asukkaan tiedoilla.

## Vaiheet
1. Asukaspaketti: kokoaminen ja purku, testit. ← aloitettu 16.11.2026
2. Paikan laite: asetus, ilmoittautuminen verkossa, Liitä paikkaan (asukkaan valinta).
3. Siirto: ver/pull, automaattinen haku, Lähetä nyt, perillä-tila, muutos voimaan seuraavassa vaiheessa.
4. Myöhemmin: ohjaajien omat puhelimet paikan tietojen muokkaajina etänä; päivätoiminnan oma laite (useampi paikka samalle asukkaalle); paikan yhteinen TV-näkymä.
