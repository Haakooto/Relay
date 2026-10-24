# Relay: brukarrettleiing

Relay erstattar den vanlege Klokke-appen: alarmar, tidtakarar, stoppeklokker, verdsklokke og
moduler på heimeskjermen. Det som skil Relay ut: tidtakarar kan lenkjast saman til **relé**,
alarmar kan **starte** ein tidtakar, eit relé eller ei stoppeklokke i staden for berre å ringje,
og stoppeklokker har **rundar med merkelappar som kan rettast**.

Dei fem rutene nedst er Alarmar, Tidtakarar, Stoppeklokke, Klokke og Innstillingar. Relay opnar
på ruta du brukte sist.

---

## Alarmar

**Lag ein.** Trykk **+**. Tidsveljaren kjem først, deretter resten av alarmen:

- **Etikett**: blir vist når han ringjer.
- **Gjenta**: kryss av vekedagar. Utan kryss er det ein eingongsalarm: vel dato (standard: neste
  gong klokka er der), og om han skal **slettast** eller **behaldast, slått av** etterpå.
- **Sluttar** (berre ved gjentaking): aldri, på ein dato, eller etter eit tal gonger.
- **Lyd, vibrering, gradvis auke i volum**, **slumretid** (kor mange minutt du vil) og
  **maks tal slumringar** (ingen, eit tal, eller uavgrensa).

**Lagre** står ved sida av klokkeslettet. Ei melding fortel kor lenge det er til han ringjer, og
lista er sortert etter kva som ringjer først.

**Når han ringjer**, tek han over skjermen, også når telefonen er låst, og held fram til du gjer
noko: **Slumre** eller **Avvis**. Fleire alarmar samstundes blir stabla på same skjerm. Ein
alarm ingen svarar på, stoppar etter 10 minutt (Innstillingar → Slutt å ringje etter) og legg
igjen eit varsel.

**Unntak** (på ein lagra alarm): hopp over ein dag eller ein periode, eller ring på eit anna
tidspunkt dei dagane. «Gjeld òg» legg det same unntaket til andre alarmar, kjekt for ei
ferieveke.

**Før han ringjer**, kjem eit varsel om komande alarm (2 timar før som standard, kan endrast i
Innstillingar). **Avvis no** hoppar over berre den eine gongen.

**Var telefonen av** då alarmen skulle gå, ringjer han når telefonen er på att, så sant det er
innan ein time, og seier «Forseinka frå …». Eldre tap gjev eit varsel i staden.

**Det raude feltet** øvst i Alarmar kjem berre når noko ville hindra alarmar i å verke (varsel
av, fullskjerm blokkert, alarmvolum på null, batterioptimalisering). Kvar linje har ein knapp for
å fikse det.

### Alarmar som startar noko

Under **Når han går av** kan ein alarm gjere meir enn å ringje:

| Val | Ved alarmtida |
|---|---|
| **Ring** | Ein vanleg alarm. |
| **Start ein tidtakar** | Startar ein lagra tidtakar, eller ein **Ny** du lagar der og då. |
| **Start eit relé** | Startar eit lagra relé, eller eit **Nytt** du skriv der og då. |
| **Start ei stoppeklokke** | Lagar ei stoppeklokke med namnet, startpunktet og merkelappane du gjev, og startar ho. |

Desse startar stille, berre med eit kort **signal** (vel det under «Signal når han startar»), og
oppfører seg deretter som om du hadde starta dei sjølv. Om noko ikkje kan starte, til dømes
fordi det lagra reléet er sletta, **ringjer alarmen og fortel kvifor**. **Lagre i biblioteket**
gjer ein ny tidtakar eller eit nytt relé om til ein lagra.

Døme: ein alarm kvar vekedag kl. 06:30 som startar *Vekk*-reléet ditt (sjå nedanfor), gjev deg
ein morgon utan slumreknapp.

---

## Tidtakarar

**Raske tidtakarar.** Dei små knappane til venstre for **+** startar ein tidtakar på 1, 5, 10
eller 15 minutt med eitt trykk. Dei fire tidene kan du endre i Innstillingar.

**Ny tidtakar.** Han har **varigheit**, **etikett**, eit valfritt **varsel** og **lyd**.
Varigheita kjem først, på eit taltastatur: siffer, `h`, `m`, `s`, og **+1m**, **+5m**, **+10m**.
Hald inne ⌫ for å tømme. Tastatur-ikonet byter til heile tastaturet for alt anna relé-språket
kan uttrykkje.
- **Start** køyrer han éin gong utan å lagre. Etiketten blir likevel vist på nedteljinga og når
  han ringjer.
- **Lagre** legg han i biblioteket. Lagra etikettar kan berre ha bokstavar, tal og `_` (til
  dømes `Te`, `Arbeid_25`), fordi relé viser til tidtakarar med etiketten.

Varigheiter skriv du som `25m`, `1h30m`, `45s` eller `90`. Eit tal åleine er minutt.

**Varsel.** «Varsle før slutten» spelar eit kort signal så lenge før tidtakaren ringjer: ein
tidtakar på 20 minutt med 1 minutts varsel gir signal etter 19 minutt. Nedteljinga går likevel
heilt til slutten. Inne i eit relé blir varselet til ein tidtakar ikkje brukt.

**Når han går.** Kvar start får sitt eige kort øvst i ruta og sitt eige varsel med nedteljing.
Same tidtakar kan gå fleire gonger samstundes. Kontrollane er **Pause** / **Hald fram**,
**+1 min**, **Nullstill** (tilbake til start, på pause) og **Stopp**. Når han er ferdig,
ringjer han som ein alarm, med **Stopp**, **Start på nytt** og **+1 min**. Tidtakarar går
vidare når appen er lukka, og etter omstart av telefonen.

**Biblioteket.** Lagra tidtakarar og relé står under dei som går. **▶** startar ein, og trykk på
han for å redigere. Endringar gjeld først når du trykkjer Lagre. **+ → Ny overskrift** samlar
ting under ei overskrift: hald inne på ein og dra han under overskrifta, og bruk pilene for å
flytte overskrifter.

---

## Relé

Eit relé er ei rekkje steg som går etter kvarandre. Eit steg er ei varigheit, eller ein lagra
tidtakar eller eit lagra relé. Du skriv relé som eit kort skript.

### Skriptet

```
(
  Arbeid, Pause,          # lagra tidtakarar, ved etikett
  Arbeid, Pause,
  15m "lang pause",       # eit steg utan namn, med etikett
  repeat(4, (Arbeid, Pause))
)
```

| Du skriv | Tyder |
|---|---|
| `25m`, `1h30m`, `45s`, `20` | Eit steg med den lengda. Eit tal åleine er minutt. |
| `Arbeid` | Ein lagra tidtakar eller eit lagra relé, køyrt som eitt eller fleire steg, med eigen lyd. Endrar du *Arbeid* seinare, blir alle relé som brukar han, oppdaterte. |
| `Arbeid - 2` | Eitt steg 2 minutt kortare enn *Arbeid*, med lyden til Arbeid. Eit namn i reknestykke står for lengda. |
| `5m "strekk"` | Ein etikett på eit steg utan namn, vist medan det går og når det ringjer. |
| `repeat(4, (Arbeid, Pause))` | Gjentek kva som helst, også namn. |
| `range(1, 3)` | Steg på 1, 2 og 3 minutt. `range(1, 4, 2)` gjev 1 og 3. `range(1, 3, 0.5)` tel i halve minutt. Slutten er med når eit steg treffer han. |
| `MINUTE(NOW)`, `RANDOM(5, 10)` | Blir rekna ut éin gong, når reléet startar. |
| `# …` | Ein kommentar: resten av linja blir ignorert. |

Steg skil du med komma eller linjeskift. **?** i redigeringa viser denne tabellen.

**Redigeringa hjelper til.** Ho lukkar parentesar og hermeteikn, rykkjer inn ved linjeskift, og
føreslår lagra namn medan du skriv (aldri namn som ville fått eit relé til å innehalde seg
sjølv). Under skriptet viser ei førehandsvising stega og totalen, og peikar på feil med linje og
kolonne. Start og Lagre er grå til skriptet er gyldig. Lagre treng òg ein gyldig etikett.

### Mellom stega

- **Gå vidare sjølv**: kvart steg sluttar med eit kort signal (Pip, Klokke, …, eller Stille), og
  neste steg startar med ein gong. Berre slutten på reléet ringjer.
- **Vent på meg**: slutten på kvart steg ringjer til du trykkjer **Vidare** (eller **Stopp
  reléet**).

**Per steg:** trykk på eit steg i førehandsvisinga for å gje det eigen lyd, eller eit anna val
for «etter dette steget». Reléet kan til dømes gå vidare sjølv inn i *Pause*, men vente på deg
før *Arbeid* startar att.

Eit nyttig vekkjarrelé er fem korte tidtakarar med stadig høgare lyd, sett til «Vent på meg».
Kvart trykk på Vidare gjev fem minutt til, og **Stopp reléet** avsluttar når du er oppe.

---

## Stoppeklokke

Trykk **+** for å starte ei. Du kan gje henne:
- eit **namn**,
- eit **startpunkt**: start på 1:00, eller kryss av **−** for å starte under null. −2 m tel opp
  frå −2:00, gjennom null og vidare,
- **merkelappar** (til dømes `løp` og `pause`). Kvar stoppeklokke har sine eigne.

Du kan ha så mange stoppeklokker du vil. Dei tel vidare når appen er lukka eller telefonen blir
starta på nytt. Første rad med kontrollar er **Runde**, **Pause** og **Start på nytt** (tilbake
til startpunktet, rundar tømde). Har stoppeklokka merkelappar, har andre rad ein **Runde: løp**-
knapp per merkelapp, som tek ein runde og merkjer han med eitt trykk.

**Rundar** viser eiga tid og totalen så langt. Første runde blir målt frå startpunktet. Trykk på
ein runde for å leggje til eit **notat** eller endre **merkelappane**: merkelappar blir viste som
små boksar, notat i kursiv.

**Rette feil.** Blyanten på ein runde har tre rettingar. Ingen av dei endrar totaltida:
- **Fjern runde**: slår han saman med runden før (runde 1 blir slått saman med runde 2).
- **Set inn runde**: deler runden ved tida du gjev. Ein runde på 20 s delt ved `5` blir 5 s,
  deretter 15 s.
- **Juster runde**: flyttar slutten av runden med til dømes `-5s`, `2m`, `1:30` eller `5`. Eit
  tal åleine er sekund her. Neste runde får eller gjev skilnaden.

Ein runde kan aldri bli kortare enn null. Kvar retting kan ha ei grunngjeving. Retta rundar får
ein **★** (trykk på han for å sjå historikken). Pilene i kortet **angrar** og **gjer om**, så
langt tilbake du vil, og **Rettingar** listar alle endringane.

**Diagram.** Diagramikonet viser ein stolpe per runde, med tal, snitt, raskast og seinast. Trykk
på ein stolpe for nøyaktig tid, eller kryss av merkelappar for å sjå til dømes berre
`løp`-rundane.

---

## Klokke

Klokke-ruta viser ei stor klokke med byane dine under, kvar med tida si og kor mykje føre eller
etter dei er («I morgon, +9 t»). **+** søkjer etter ein by. Sveip ein by bort for å fjerne han, og
hald inne for å dra han.

**Stil.** Trykk på klokka (eller palett-ikonet) for å endre stilen, med førehandsvising:
- **Digital**: skrift (System, Serif, Mono, 7-segment, Display), 12/24 timar, sekund, dato.
- **Analog**: visarar (tjukke, tynne, prikkar), merke (minutt, timar, kvarter, ingen), skive
  (ring, fylt, tal, inga), sekundvisar.
- **Fargar** for kvar del: Auto (følgjer temaet), paletten, eller ein eigen farge.

Skrifta gjeld sifra, tala og datoen.

**Heimeklokke.** Vel Innstillingar → Klokke → Heimetidssone. Når du reiser, kjem ei «Heime»-rad
øvst blant byane.

## Moduler

Hald inne på heimeskjermen → Moduler → Relay. Kvar modul får sitt eige oppsett:
- **stilen** (alle Klokke-vala ovanfor),
- ein **by** (eller tida på telefonen),
- **bakgrunn** (tema, gjennomsiktig, farge eller overgang),
- **hjørne** og **kant**.

Kvar modul kan sjå ulik ut. Vil du endre ein seinare, hald inne på han og vel innstillingar for
modulen. Trykk på ein modul opnar Relay på ruta du brukte sist.

---

## Innstillingar

- **Vising**: tema (System, Lyst, Mørkt, AMOLED), 24-timarsklokke, første vekedag, språk (English
  eller Norsk, som er nynorsk).
- **Alarmar**: standard slumretid, standard lyd, standard volumauke, kor lenge han ringjer, og
  varselet om komande alarm.
- **Klokke**: heimetidssone.
- **Tidtakarar**: dei fire hurtigstart-tidene, standard lyd for tidtakarar, maks tal steg i eit
  relé, og når du vil åtvarast om svært lange relé.
- **Generelt**: haptikk, appversjon.
