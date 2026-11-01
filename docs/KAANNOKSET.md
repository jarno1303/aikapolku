# AikaPolku – kieliversiot

Kaikki käyttäjälle näkyvät tekstit ovat tiedostossa
`android/app/src/main/res/values/strings.xml` (suomi, oletuskieli). Koodissa ei ole näkyviä tekstejä.

## Uuden kielen lisääminen

1. Kopioi `values/strings.xml` uuteen kansioon kielikoodin mukaan, esim.
   `values-en/strings.xml` (englanti), `values-sv/strings.xml` (ruotsi), `values-de/strings.xml` (saksa).
2. Käännä jokaisen `<string>`-rivin **arvo**. Älä muuta `name`-tunnuksia.
3. Säilytä paikkamerkit sellaisinaan: `%1$s`, `%2$s`, `%1$d` (sovellus täyttää niihin nimen, luvun tai kellonajan).
   Paikkamerkkien järjestystä saa vaihtaa lauseessa, numeroa ei.
4. Monikkomuodot (`<plurals>`): käännä jokainen `quantity`-muoto, jota kieli tarvitsee
   (englanniksi `one` ja `other`; esim. puolaksi myös `few` ja `many`).
5. Heittomerkki kirjoitetaan `\'` ja lainausmerkki `\"`.
6. `tts_locale`: puheäänen kieli, esim. `en-GB`, `sv-SE`, `de-DE`.
7. Sovellus vaihtaa kielen automaattisesti puhelimen kieliasetuksen mukaan. Jos käännöstä ei ole,
   näytetään suomi.

## Valmiit kortit, osavaiheet ja pohjat

- Korttien (`card_…`), luokkien (`card_category_…`), osavaiheiden (`substep_…`) ja
  valmiiden päiväohjelmien (`template_…`) nimet käännetään kuten muutkin tekstit.
- Uusi ohjelma tai kortti saa nimen sillä kielellä, joka puhelimessa on käytössä luontihetkellä.
  Jo tallennetut ohjelmat säilyttävät nimensä.
- Kuvat löytyvät aina: tunnuksen (`builtin:…`), suomenkielisen nimen tai nykyisen kielen nimen perusteella.

## Tarkistus

- Aja `gradlew testDebugUnitTest`: testit hakevat tekstit resursseista, joten ne toimivat kaikilla kielillä.
- Käy puhelimella läpi ainakin aloitussivu, Ensin–Sitten, päiväohjelman luonti ja suoritus sekä ohjaajan
  asetukset suurella fonttikoolla: pidemmät käännökset eivät saa katketa.
