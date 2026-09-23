# Migrering av historiske opptjeningsvurderinger fra spleis

## Mål

Alle opptjeningsvurderinger som i dag bare finnes i person-JSON-en i spleis skal ligge som rader i
databasen til sp-vilkarsproving. Når migreringen er ferdig, kan sp-vilkarsproving svare på
`OpptjeningsvurderingResultat` og på API-oppslag fra speil uten å slå opp i spleis-api.

## Kort om valgt løsning

Vi lager en naisjob i spleis som leser alle fødselsnummer fra `person`-tabellen og sender én
Kafka-melding per person på `tbd.rapid.v1`. sp-vilkarsproving får en ny river som plukker opp
meldingen, kaller `spleis-api` med fødselsnummeret gjennom eksisterende `SpleisClient`, mapper med
eksisterende `tilOpptjeningsvurdering` og lagrer vurderingene.

Vi bruker denne løsningen fordi:

- Spleis eier person-tabellen og har allerede et mønster for å iterere over alle personer
  (`jobs/src/main/kotlin/no/nav/helse/spleis/jobs/App.kt`, `migrateTask` og `opprettOgUtførArbeid`).
- sp-vilkarsproving har allerede hele lesestien mot spleis: `SpleisClient.hentOpptjeningsvurderinger`,
  `SpleisOpptjeningsvurdering` og `tilOpptjeningsvurdering`. Vi trenger ikke duplisere mapping.
- Mapping og lagring skjer i appen som eier dataene etterpå. Spleis slipper å kjenne til
  datamodellen i sp-vilkarsproving.
- Vi kan kjøre jobben på nytt så mange ganger vi vil, fordi `opptjeningsvurderingId` fra spleis blir
  primærnøkkel i sp-vilkarsproving.

Alternativet vi ikke velger: å la spleis-jobben gjenopprette `Person` selv og publisere ferdige
opptjeningsvurderinger i Kafka-meldingen. Da slipper vi hundretusener av HTTP-kall mot spleis-api,
men vi må vedlikeholde JSON-formatet to steder. Vi holder dette i bakhånd hvis lasttesten i fase 3
viser at spleis-api ikke tåler volumet.

## Dagens situasjon

### sp-vilkarsproving

- `SpleisClient` kaller `POST http://spleis-api/api/opptjeningsvurderinger` med `{"fødselsnummer": "..."}`
  og får tilbake alle opptjeningsvurderinger for personen.
- `SpleisOpptjeningsvurderingService` brukes i dag bare som fallback ved lesing. Den lagrer ingenting.
- `OpptjeningsvurderingResultatRiver` slår først opp lokalt, og faller tilbake til spleis hvis
  vurderingen mangler.
- `tilOpptjeningsvurdering` mapper alle tre variantene: `SpleisArbeidstaker`, `SpleisSelvstendig` og
  `InfotrygdArbeidstaker`.
- `PostgresOpptjeningsvurderingRepository.lagre` skriver til `opptjeningsvurdering`,
  `vilkarsvurdering` og koblingstabellen `opptjeningsvurdering_vilkarsvurdering`, og kaster
  `IllegalStateException` ved unikhetsbrudd på id.

Alt som mangler er en måte å få tak i alle personene på, pluss en skrivesti som lagrer det vi
allerede klarer å lese.

### spleis

- `person.data` inneholder hele personaggregatet som JSON. Det finnes ingen normalisert tabell over
  vedtaksperioder eller opptjeningsvurderinger.
- `OpptjeningApi` gjenoppretter `Person`, flater ut `vilkårsgrunnlagHistorikk` og dedupliserer på
  `opptjeningsvurderingId`. Én person gir dermed alle sine opptjeningsvurderinger i ett kall.
- `jobs`-modulen har allerede både enkel iterering (`SELECT fnr FROM person` i `migrateTask`) og
  parallellisert iterering med `arbeidstabell` og `FOR UPDATE SKIP LOCKED` (`migrateV2Task`).
- Naisjob-en deployes med `deploy/job.yml` og styres av `RUNTIME_OPTS` og workflowen
  `.github/workflows/spleis-jobs.yml`.
- Toggelen `OPPTJENINGSVURDERINGBEHOV` er satt i `deploy/dev.yml`, men ikke i `deploy/prod.yml`. I
  prod lager spleis derfor fortsatt opptjeningsvurderingene selv, og sp-vilkarsproving svarer på
  `OpptjeningsvurderingResultat` ved å slå opp i `spleis-api`.

En opptjeningsvurdering identifiseres av `opptjeningsvurderingId`, ikke av `vedtaksperiodeId`. Samme
vurdering kan ligge i flere vedtaksperioder. Vi migrerer derfor per person og per
`opptjeningsvurderingId`, ikke per vedtaksperiode.

## Samtidighet: vurderinger som skjer mens importen kjører

Dette er den vanskeligste delen av migreringen. Rekkefølgen løser det meste: skrur vi på ny
vurderingsflyt før vi importerer, blir historikken en lukket mengde, og batchen kappløper ikke med
noe.

### 1. Skru på `OPPTJENINGSVURDERINGBEHOV` i prod før vi importerer

Toggelen `Toggle.OpptjeningsvurderingBehov` (`sykepenger-model/.../Toggle.kt:46`) styrer om spleis
sender behovet `Opptjeningsvurdering` til sp-vilkarsproving. Den er satt i `deploy/dev.yml:77`, men
ikke i `deploy/prod.yml`. I prod lager spleis derfor fortsatt sin egen
`opptjeningsvurderingId = UUID.randomUUID()` (`hendelser/Vilkårsgrunnlag.kt:111`).

Vi skrur på toggelen i prod før backfillen starter. Grunnene:

- Historikken blir en lukket mengde. Etter cutover lager ikke spleis nye vurderinger på egen hånd.
  Alt som skjer mens batchen kjører, også revurderinger av personer batchen allerede har vært innom,
  går gjennom `OpptjeningsvurderingRiver` og havner direkte i databasen. Batchen trenger bare å
  hente det som ble laget før cutover, og den mengden vokser ikke.
- Round-trip blir gratis. Etter cutover bruker spleis id-en fra løsningen til sp-vilkarsproving, så
  en vurdering som er laget av sp-vilkarsproving og senere leses tilbake fra `spleis-api` har samme
  `opptjeningsvurderingId`. Importen ser at raden finnes og gjør ingenting.
- Motsatt rekkefølge gir et hull vi ikke kan lukke billig. Migrerer vi først og skrur på toggelen
  etterpå, blir hver vurdering i mellomtiden laget av spleis med en ny tilfeldig UUID som aldri når
  sp-vilkarsproving. Vi må da kjøre hele backfillen på nytt, eller etterkjøre for endrede personer
  slik plan B under beskriver.

Forutsetningen for cutover er at sp-vilkarsproving allerede svarer riktig på `Opptjeningsvurdering`
og `OpptjeningsvurderingResultat` i prod. Det gjør den i dag, siden
`OpptjeningsvurderingResultat`-behovet sendes uavhengig av toggelen
(`EventBusOversetter.kt:697`) og besvares med fallback mot `spleis-api`.

### 2. Gjør fallbacken skrivende

`OpptjeningsvurderingResultatRiver` henter allerede vurderingen fra `spleis-api` når den ikke finnes
lokalt, men kaster resultatet etter bruk. Vi lar den lagre det den henter, gjennom samme
`ImporterOpptjeningsvurderingerService` som batchen bruker.

Da blir importen selvhelende for vurderinger fra før cutover: brukes en slik vurdering i en
behandling før batchen rekker fram til personen, blir den persistert der og da. Den gir oss også
målingen som forteller når importen er komplett, siden treffraten går mot null etter hvert som
batchen dekker resten.

### 3. Kafka-nøkkel serialiserer per person

Produser importmeldingen med fødselsnummer som nøkkel, slik `migrateTask` allerede gjør:
`ProducerRecord("tbd.rapid.v1", fnr, ...)`. Importmeldingen og `Opptjeningsvurdering`-behovet for
samme person havner da i samme partisjon og behandles etter hverandre, ikke samtidig.

Dette dekker ikke vurderinger som kommer inn via HTTP fra speil
(`PostManuellVilkårsvurderingBehandler`). Der stoler vi på rangeringen under.

### Plan B hvis cutover må vente

Kan vi ikke skru på toggelen før batchen, lager spleis fortsatt nye vurderinger underveis, og vi må
etterkjøre importen for personer som er endret etter at batchen startet. Noter tidspunktet `T0` når
batchen starter, og kjør deretter:

```sql
SELECT DISTINCT fnr FROM melding WHERE lest_dato > :forrigeKjøring;
```

Bruk `melding`-tabellen, ikke `person`-tabellen. `PersonDao.kt:195` oppdaterer bare `skjema_versjon`
og `data`, så `person.oppdatert` er ikke pålitelig som endringsmarkør.

Første etterkjøring dekker hele perioden batchen kjørte. Neste dekker bare perioden etter den igjen,
og blir mindre. Gjenta til utvalget er tilnærmet tomt. Dette er merarbeid vi slipper hvis fase 2 går
i orden først.

## Ting vi må løse før vi importerer

### Rekkefølge slår ut feil for personer som allerede er vurdert

`gjeldende(fødselsnummer, skjæringstidspunkt)` velger raden med høyeste `løpenummer`, og `løpenummer`
tildeles ved innsetting. En historisk vurdering som importeres i dag får dermed høyere `løpenummer`
enn en vurdering sp-vilkarsproving gjorde i går, og vil feilaktig bli gjeldende.

Tiltak, i prioritert rekkefølge:

1. Endre `gjeldende` til å rangere importerte rader lavest, uavhengig av `løpenummer`:

   ```sql
   order by (vurderingskilde = 'OVERFOERT_FRA_SPLEIS') asc, løpenummer desc
   ```

   Da kan en importert vurdering aldri overstyre en vurdering sp-vilkarsproving har gjort selv,
   uansett hvilken rekkefølge radene ble skrevet i. Dette er sikkerhetsnettet som gjør at vi ikke
   trenger låsing.
2. Importen hopper i tillegg over `(fødselsnummer, skjæringstidspunkt)` som allerede har en rad, slik
   at vi slipper å skrive rader vi uansett ikke skal bruke. Sjekk og innsetting skjer i samme
   transaksjon.

### Flere importerte vurderinger for samme skjæringstidspunkt

En revurdering i spleis gir en ny `opptjeningsvurderingId` for samme skjæringstidspunkt
(`VilkårsgrunnlagHistorikk.kt:250`). `OpptjeningApi` returnerer alle, og lista er sortert med nyeste
først fordi nye innslag legges inn med `historikk.add(0, nytt)` (`VilkårsgrunnlagHistorikk.kt:45`).

Tiltak: importer lista i omvendt rekkefølge, slik at den nyeste vurderingen får høyest `løpenummer`
og blir gjeldende. Skriv en test som fanger dette, siden feilen ellers er usynlig i produksjon.

### Vi kan ikke skille importerte rader fra egne vurderinger

`lagreVurdertISpeil` setter alltid `vurderingskilde = 'VURDERT_I_SPEIL'`, også for vurderinger som
kommer fra spleis. Da kan vi verken måle importen eller rulle den tilbake.

Tiltak: ny Flyway-migrering (`V16`) som innfører `vurderingskilde = 'OVERFOERT_FRA_SPLEIS'`. Legg til
en egen domenevariant eller et felt som gjør at repositoryet skriver og hydrerer denne kilden.
Behold `VURDERT_I_SPEIL` for alt annet.

### Loggstorm fra arbeidsforholdtype

`tilDomene` i `SpleisOpptjeningsvurderingTilOpptjeningsvurdering` logger en warning per
arbeidsforhold fordi spleis ikke oppgir type. Under en full backfill blir det titalls millioner
linjer.

Tiltak: gjør denne loggingen betinget, eller flytt den til en teller. Vurder samtidig om spleis-api
bør utvides til å oppgi reell arbeidsforholdtype, slik at importerte data blir like gode som nye.

### Utgående meldinger

Importen skal ikke publisere løsninger eller andre hendelser på rapid-en. Kontroller at skrivestien
ikke legger noe i outbox-tabellen, ellers vil `OutboxPubliseringsjobb` sende meldingene videre.

## Faser

### Fase 1: gjør sp-vilkarsproving klar til å ta imot (ingen trafikk ennå)

1. Flyway `V16`: nye verdier for `vurderingskilde`, og en indeks som gjør oppslaget
   `(fødselsnummer, skjæringstidspunkt)` billig ved import hvis den ikke allerede dekkes.
2. Utvid `PostgresOpptjeningsvurderingRepository` slik at den kan skrive og lese
   `OVERFOERT_FRA_SPLEIS`, og endre `gjeldende` til å rangere importerte rader lavest.
3. Ny `ImporterOpptjeningsvurderingerService` i `application`:
   - Kall `SpleisClient.hentOpptjeningsvurderinger(fødselsnummer)`.
   - Map med `tilOpptjeningsvurdering(fødselsnummer)`.
   - Snu rekkefølgen på lista, slik at nyeste vurdering skrives sist.
   - For hver vurdering, i én transaksjon: hopp over hvis `(fødselsnummer, skjæringstidspunkt)`
     allerede finnes, hopp over hvis `finn(opptjeningsvurderingId)` gir treff, ellers lagre.
   - Fang `IllegalStateException` fra unikhetsbrudd og tell den som «allerede importert».
   - Returner et resultat med antall lagret, hoppet over og feilet.
4. Ny `ImporterHistoriskOpptjeningRiver` i `infra/kafka` som matcher
   `@event_name = "importer_historisk_opptjening"` og `fødselsnummer`. Riveren publiserer ingenting
   tilbake på rapid-en.
5. La `OpptjeningsvurderingResultatRiver` lagre vurderingen den henter fra spleis, gjennom samme
   service. Fallbacken blir da skrivende, og importen selvhelende.
6. Metrikker i Prometheus: `lagret`, `hoppet_over_finnes`, `hoppet_over_nyere_vurdering`,
   `feilet`, pluss et histogram for tid brukt per person. Skill mellom batch og lazy import med en
   label.
7. Tester: enhetstester av servicen (nye, duplikate og konkurrerende vurderinger, og at nyeste
   vurdering for samme skjæringstidspunkt blir gjeldende), og en integrasjonstest som kjører riveren
   mot testdatabasen.

Etter fase 1 er appen deployet, men ingen sender meldingen ennå.

### Fase 2: skru på ny vurderingsflyt i prod

Sett `OPPTJENINGSVURDERINGBEHOV=true` i `deploy/prod.yml` i spleis. Følg med på at
`Opptjeningsvurdering`-behovet besvares, og at spleis legger id-en fra løsningen til grunn i
vilkårsgrunnlaget. Først når dette er stabilt går vi videre.

Fra dette tidspunktet lager ikke spleis flere opptjeningsvurderinger på egen hånd. Alt som kommer
etterpå skrives direkte i sp-vilkarsproving, og mengden vi skal migrere slutter å vokse.

### Fase 3: verifiser på et lite utvalg

1. Kjør i dev først. Send meldingen manuelt for et titalls fødselsnummer og sammenlign resultatet i
   databasen med svaret fra `spleis-api` for de samme personene.
2. I prod: send meldingen for et utvalg på 100 til 1000 personer, for eksempel via `mod(fnr, N)`.
3. Mål hva ett kall mot `spleis-api` koster. Spleis gjenoppretter hele `Person` per kall, så dette
   er den dyreste delen av migreringen. Regn ut hvor lenge en full kjøring tar med den raten vi
   tåler.
4. Sjekk at `OpptjeningsvurderingResultatRiver` fortsatt svarer riktig for de importerte personene,
   og at den nå svarer fra lokal database i stedet for fallback mot spleis.

Hvis kostnaden per kall er for høy, bytter vi til varianten der spleis-jobben gjenoppretter `Person`
selv og legger opptjeningsvurderingene i Kafka-meldingen. Resten av planen er lik.

### Fase 4: full kjøring

1. Ny task i `jobs`-modulen i spleis, etter mønster fra `migrateTask`:

   ```kotlin
   "importer_historisk_opptjening" -> importerHistoriskOpptjeningTask(factory)
   ```

   Den bruker `opprettOgUtførArbeid` med `arbeidstabell` slik `migrateV2Task` gjør, slik at flere
   pods kan dele arbeidet og en avbrutt kjøring kan fortsette der den slapp. Per fødselsnummer
   sender den:

   ```json
   {
     "@id": "...",
     "@event_name": "importer_historisk_opptjening",
     "@opprettet": "...",
     "fødselsnummer": "..."
   }
   ```

2. Legg jobben inn i `.github/workflows/spleis-jobs.yml` med `arbeid_id` som parameter.
3. Kjør med lav parallellitet først, og skru opp mens du følger med på responstid i `spleis-api`,
   consumer lag på `tbd.rapid.v1` og CPU i sp-vilkarsproving.
4. Kjør jobben på nytt med samme `arbeid_id` hvis den stopper. Importen er idempotent, og
   `arbeidstabell` husker hvem som er ferdig, så gjentatte kjøringer er trygge og billige.

### Fase 5: verifiser og rydd

1. Sammenlign antall rader med `vurderingskilde = 'OVERFOERT_FRA_SPLEIS'` mot antall personer i
   spleis som faktisk har vilkårsgrunnlag. Forventet avvik: personer uten vilkårsgrunnlag, og
   personer sp-vilkarsproving allerede hadde vurdert.
2. Ta stikkprøver: hent tilfeldige personer fra `spleis-api` og sammenlign felt for felt med det som
   ligger i databasen.
3. Mål hvor ofte den skrivende fallbacken i `OpptjeningsvurderingResultatRiver` faktisk lagrer noe
   nytt. Når raten er nær null, er importen komplett i praksis, og fallbacken kan fjernes i en egen
   oppgave.
4. Kjør jobben på nytt for personer batchen feilet på, med et nytt `arbeid_id`. Det er ikke behov for
   å etterkjøre for personer som er endret underveis, så lenge fase 2 ble gjort først.

## Idempotens og rollback

Importen er idempotent fordi `opptjeningsvurdering.id` er `opptjeningsvurderingId` fra spleis, og
fordi vi hopper over `(fødselsnummer, skjæringstidspunkt)` som allerede finnes.

Rollback er å slette radene vi importerte:

```sql
delete from opptjeningsvurdering_vilkarsvurdering
where opptjeningsvurdering_id in (
  select id from opptjeningsvurdering where vurderingskilde = 'OVERFOERT_FRA_SPLEIS'
);
delete from opptjeningsvurdering where vurderingskilde = 'OVERFOERT_FRA_SPLEIS';
```

Vilkårsvurderingene som ble opprettet for importerte vurderinger må slettes i samme slengen, men
bare de som ikke er koblet til andre opptjeningsvurderinger. Skriv og test dette skriptet i dev før
fase 4 starter, så det ligger klart.

## Personvern og logging

- Fødselsnummer skal ikke i meldingsteksten som går til nav-logs. Bruk Team Logs-detaljer, i tråd med
  skillene for `sykepenger-libs/logging` i dette repoet.
- Meldingen på rapid-en inneholder fødselsnummer, som er normalt på `tbd.rapid.v1`.
- Importen går utenom tilgangskontrollen i API-et. Den skal derfor ikke eksponeres som et
  HTTP-endepunkt.

## Ting å avklare

- Er sp-vilkarsproving klar til at `OPPTJENINGSVURDERINGBEHOV` skrus på i prod? Fase 2 er en
  forutsetning for resten av planen, og det er en større beslutning enn selve migreringen.
- Skal vi importere `InfotrygdArbeidstaker`-vurderinger? De lagres i dag som `erOk = true` uten
  vilkårsvurdering, og gir lite informasjon. Alternativet er å la fallbacken håndtere dem.
- Skal spleis-api utvides med arbeidsforholdtype, slik at importerte vurderinger blir like
  detaljerte som nye? Det bør i så fall gjøres før fase 4, ellers må vi importere på nytt.
- Hvor mange personer i spleis har vilkårsgrunnlag? Tallet avgjør om fase 4 tar timer eller dager.

## Filer som blir berørt

I sp-vilkarsproving:

- `migreringer/src/main/resources/db/migration/V16__overfoert_fra_spleis.sql` (ny)
- `sp-vilkarsproving/.../application/ImporterOpptjeningsvurderingerService.kt` (ny)
- `sp-vilkarsproving/.../infra/kafka/ImporterHistoriskOpptjeningRiver.kt` (ny)
- `sp-vilkarsproving/.../infra/kafka/OpptjeningsvurderingResultatRiver.kt` (skrivende fallback)
- `sp-vilkarsproving/.../infra/db/PostgresOpptjeningsvurderingRepository.kt`
- `sp-vilkarsproving/.../domain/Opptjeningsvurdering.kt`
- `sp-vilkarsproving/.../infra/spleis/SpleisOpptjeningsvurderingTilOpptjeningsvurdering.kt`
- `sp-vilkarsproving/.../bootstrap/App.kt`

I spleis:

- `deploy/prod.yml` (`OPPTJENINGSVURDERINGBEHOV`)
- `jobs/src/main/kotlin/no/nav/helse/spleis/jobs/App.kt`
- `.github/workflows/spleis-jobs.yml`
