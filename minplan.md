# Plan: `speil-backend-app` — felles Ktor-app-lib

Status: **Fase 3 (review) ferdig — godkjent med endringer.** Klar for Fase 4 (leveranse) etter bekreftelse.

## Problem

`sp-vilkarsproving` og `sporhund` duplikerer boilerplate for oppstart av Ktor-app: logging,
Azure AD-autentisering, tilgangskontroll, datasource/Flyway og appmontering. Målet er en gjenbrukbar
modul (`speil-backend-app`) der appen kun leverer **rivere** og **API-endepunkter** inn i en
oppstartsfunksjon. Modulen utvikles som modul i `sp-vilkarsproving` og flyttes senere til `tbd-libs`.

## Avklart i Fase 1

| # | Spørsmål | Svar |
|---|---|---|
| 1 | Hvem kaller spv sitt API? | **Saksbehandler via Speil** — brukerkontekst, trenger person-pseudo-id (Valkey) |
| 2 | Bootstrap-modeller | **Kun rapid-variant** inntil videre (`RapidApplication` + ktor-modul) |
| 3 | Modulnavn | **`speil-backend-app`** |
| 4 | Jackson | **Jackson 3** (`tools.jackson`) |
| 5 | Domenetyper | **Generisk** over app-definerte typer |
| 6 | OpenAPI | **Ja** — autogenerert etter modell fra spesialist |
| 7 | Testfixtures | **Ja** — `java-test-fixtures` i libben |
| 8 | Tilgangsmodell | **Libben eier `Tilgang.Les/Skriv`**; brukerroller er app-definerte (generiske) |
| 9 | Dagens `POST /vilkarsvurderinger/{id}` | **Erstattes** — midlertidig kode, ingen bakoverkompatibilitet nødvendig |
| 10 | Omfang | **Kun sp-vilkarsproving nå.** Sporhund/spesialist migreres ikke i denne leveransen |

## Nåsituasjon (kartlagt)

### sp-vilkarsproving (målappen)
- Gradle multiprosjekt med `sas-gradle-plugins`, jvmToolchain 25, versjonskatalog.
- Bootstrap: `RapidApplication.create(env) { withKtorModule { vilkårsprøvingApi(...) } }` + 3 rivere.
  Hikari inline i `App.kt` (pool 10/1), Flyway på `ApplicationStarted`.
- Auth: bar JWT-verifikasjon → `JWTPrincipal`. Ingen grupper, roller, tilganger.
- Mangler: populasjonstilgangskontroll, person-pseudo-id/Valkey, auditlogg, OpenAPI.
- Ktor-plugins settes opp manuelt (CallId, CallLogging, ContentNegotiation, StatusPages).
- Bruker Jackson 3 i rivere, Jackson 2 via `ktor-serialization-jackson`.

### sporhund (duplikatkilde)
- Enkeltmodul, plain `kotlin("jvm")`, jvmToolchain 21, Jackson 2, `naisApp` + egne Kafka-jobber
  (ingen rapids). Har full JWT + `SaksbehandlerPrincipal` + tilgangsgrupper, populasjonstilgangs-
  kontroll via `TilgangsmaskinenClient`, `medPerson`/`krevTilgangOgRolle`, auditlogg, OpenAPI (smiley4),
  `DataSourceBuilder` (pool 20/2), `KotliqueryWrapper`.

### spesialist (referansemodell for API-rammeverket)
Har allerede tilnærmet ønsket arkitektur, og er nærmest sp-vilkarsproving teknisk (sas-plugins,
Jackson 3, `java-test-fixtures`):
- `RestBehandler`-interface: `tag`, `påkrevdTilgang`, `påkrevdeBrukerroller`, `openApi(config)`,
  `operationIdBasertPåKlassenavn()`.
- `RestAdapter`: autoriserer (tilgang + rolle), åpner transaksjon, bygger `KallKontekst`, mapper
  `RestResponse` → HTTP, feil → RFC 7807 problem+json, MDC-håndtering, outbox-publisering.
- `KallKontekst`: saksbehandler, tilganger, brukerroller, transaksjon, outbox, `PersonPseudoIdProvider`,
  `PopulasjonstilgangskontrollProvider`, accessToken + `medPerson`-hjelpere.
- `RESTAPI.kt`: typede `Route.get/post/put/patch/delete(behandler, adapter)` over ktor `Resources`,
  OpenAPI autogenereres (`autoDocumentResourcesRoutes = true`).
- `configureOpenApiPlugin`: pathFilter på `/api`, schema-overwrites, JWT-securityScheme.
- `ConfigureJwtAuthentication` + `TilgangsgrupperTilTilganger`/`TilgangsgrupperTilBrukerroller`.
- `testFixtures` med `MockOAuth2Server`-tokenutstedelse per tilgang/rolle.

### Duplisert i dag
| Område | Status |
|---|---|
| logg-API (`loggInfo/Warn/Error/Debug`, `teamLogs`) | ~identisk i alle tre apper |
| `SKIP_TEAM_LOGS_MARKER` + `SkipTeamLogsMarkerFilter` | identisk (klassenavn refereres i `logback.xml`) |
| `logback.xml` | ~identisk (sporhund har i tillegg auditLogger-appender) |
| JWT/Azure AD + tilgangsgrupper | spesialist ≈ sporhund; spv mangler |
| Datasource/Hikari/Flyway | samme mønster, ulike tall |
| Populasjonstilgang + auditlogg | spesialist ≈ sporhund; spv mangler |
| OpenAPI-plugin-config | spesialist ≈ sporhund |

## Foreslått løsning

Ny modul `speil-backend-app` i `sp-vilkarsproving`, bygget med `no.nav.helse.sas.sas-kotlin` +
`java-test-fixtures`. Nøytralt pakkenavn (f.eks. `no.nav.helse.speil.backend.app`) slik at flytting
til `tbd-libs` blir en koordinat-/pakkeendring. Bygger på tbd-libs der det finnes: `naisful-postgres`,
`access-token-provider-texas`, `populasjonstilgangskontroll-provider-*`, `person-pseudo-id`,
`rapids-and-rivers`.

### Pakkeinndeling
1. **`logging`** — `teamLogs`, `loggInfo/Warn/Error/Debug`, `SKIP_TEAM_LOGS_MARKER`,
   `SkipTeamLogsMarkerFilter`, MDC-hjelpere med app-definerte nøkler (`interface MdcKey { val value: String }`).
2. **`auditlogg`** — `Auditlogger` med appnavn/CEF-prefiks som konfig + micrometer-teller.
3. **`auth`** — `AzureAdConfig`, `configureAzureAdAuthentication`, `AccessToken`,
   `SaksbehandlerPrincipal<ROLLE>`, `Saksbehandler`/`NavIdent`/`SaksbehandlerOid`,
   **`Tilgang.Les/Skriv` eid av libben**, `TilgangsgrupperTilTilganger` + generisk
   `TilgangsgrupperTilBrukerroller<ROLLE>` (Entra-gruppe-UUID → tilgang/rolle).
4. **`person`** — `PersonPseudoIdProvider`-abstraksjon + Valkey-implementasjon (tbd-libs
   `person-pseudo-id`), `PopulasjonstilgangskontrollProvider`-oppsett (Texas + tilgangsmaskinen).
5. **`rest`** — generalisert utgave av spesialists rammeverk: `RestBehandler`,
   `Get/Post/Put/Patch/DeleteBehandler`, `RestAdapter`, `RestResponse`, `ApiErrorCode`,
   RFC 7807-problemsvar, `KallKontekst<TRANSAKSJON>` med `medPerson`-tilgangskontroll og auditlogg.
6. **`openapi`** — `configureOpenApiPlugin` + `/api/openapi.json` og `/api/swagger`,
   autogenerering fra ktor `Resources` (`autoDocumentResourcesRoutes = true`).
7. **`db`** — `DatabaseConfig` (bygger på `naisful-postgres` `ConnectionConfigFactory`/`defaultJdbcUrl`),
   Hikari-defaults (overstyrbare), Flyway-migrering, kotliquery-hjelpere (`asSQL`, `single`, `list`, `update`).
8. **`bootstrap`** — `startApp(...)`: setter opp `RapidApplication` med ktor-modul, alle plugins,
   auth, datasource, migrering, ruter og rivere.
9. **`testFixtures`** — se under.

### Skisse av ønsket app-kode
```kotlin
fun main() = startApp(
    appNavn = "sp-vilkarsproving",
    tilgangsmodell = Tilgangsmodell(Tilgang.entries, Brukerrolle.entries),
) {
    val transaksjoner = PostgresTransaksjonProvider(dataSource)
    rivere {
        OpptjeningsvurderingRiver(it, transaksjoner)
    }
    endepunkter {
        get(GetVilkårsvurderingBehandler(), restAdapter)
        post(PostVilkårsvurderingBehandler(), restAdapter)
    }
}
```

## Om testFixtures (svar på spørsmål)

`java-test-fixtures` er en Gradle-mekanisme som lar en modul publisere en **egen testhjelpe-artefakt**
(`src/testFixtures/kotlin`) som andre moduler bruker med `testImplementation(testFixtures(...))`.
Hjelperne havner ikke i produksjonsjaren. Spesialist bruker dette allerede
(`ApiModuleIntegrationTestFixture`), og `sas-kotlin`-pluginen har innebygd støtte.

Konkret for `speil-backend-app` ville det bety at appene slipper å skrive:
- `MockOAuth2Server`-oppsett og tokenutstedelse med riktige claims (`NAVident`, `oid`, `name`,
  `preferred_username`, `groups`) per tilgang/rolle — i dag duplisert i sporhund og spesialist.
- Oppsett av testapp (`ApplicationTestBuilder`) med samme plugins/auth som produksjon.
- Mock av Texas og tilgangsmaskinen, samt en in-memory `PersonPseudoIdProvider`.
- Testcontainers-database + Flyway-migrering.

## Todos

Se SQL-tabellen `todos`. Kort:
`modul-oppsett` → `logging` → `db` → `auth` → `populasjonstilgang`(+person-pseudo-id) →
`rest-rammeverk` → `openapi` → `bootstrap` → `test-fixtures` → `nais-endringer` → `verifisering` →
(senere) `tbd-libs-flytt` + sporhund-migrering.

## 🔴 Rød sone (skriv selv / forstå grundig)

- JWT-validering og gruppe→tilgang/rolle-mapping — sikkerhetskritisk.
- `RestAdapter` sin autorisasjonssti (tilgang + rolle + populasjonstilgang) — én feil her eksponerer persondata.
- `medPerson`-flyten og pseudo-id → identitetsnummer-oppslag i sp-vilkarsproving — ny mekanisme for appen.
- Auditlogg-formatet (CEF) — feil format gir manglende etterlevelse.

🟢 Grønn sone (genereres, leses gjennom): modul-/Gradle-oppsett, logg-flytting, datasource/Flyway,
kotliquery-hjelpere, OpenAPI-config, testfixtures, nais-manifestendringer.

## Risikoer

- **Toolchain**: spv/spesialist bygger med JVM 25, tbd-libs med 21. Modulen bør kompileres mot 21.
- **logback.xml**: filterklassens fulle navn endres → må oppdateres samtidig, ellers feiler oppstart.
- **Generisitet vs enkelhet**: `Tilgang.Les/Skriv` eies av libben (avklart), mens brukerroller og
  transaksjonskontekst er generiske typeparametere. Hold antall typeparametere lavt for lesbarhet.
- **Erstatning av dagens endepunkt**: `POST /vilkarsvurderinger/{id}` er midlertidig kode og fjernes.
  Ingen bakoverkompatibilitet kreves, men verifiser at ingen konsument i dev peker på det før sletting.
- **Sirkulær avhengighet i eierskap**: sporhund kan ikke bruke libben før den er i tbd-libs.
- **Ny nais-konfig for spv**: Valkey-instans, outbound til populasjonstilgangskontroll, Entra-grupper
  og `NAVident`-claim — krever koordinering med plattform/tilgangsstyring.

## Hikari-oppsett i dag

| Innstilling | sp-vilkarsproving | sporhund / spesialist | opprydding-dev | Forslag til default i libben |
|---|---|---|---|---|
| `maximumPoolSize` | 10 | 20 | 3 | **10** |
| `minimumIdle` | 1 | 2 | 1 | **1** |
| `idleTimeout` | 5 min | 1 min | 10 min | **5 min** |
| `maxLifetime` | 30 min | 5 min (`idleTimeout * 5`) | 30 min | **30 min** |
| `connectionTimeout` | 5 s | 5 s | 5 s | **5 s** |
| `initializationFailTimeout` | – | 1 min | 1 min | **1 min** |
| `leakDetectionThreshold` | – | 30 s | – | **30 s** |
| `metricRegistry` | – | Prometheus | – | **Prometheus** |
| Migrering | Flyway på `ApplicationStarted`, deler datasource | Egen kortlivet datasource i `migrate()` | – | **Egen kortlivet datasource** |
| jdbc-url | `DATABASE_JDBC_URL` | url + brukernavn/passord | Google socket factory | **`naisful-postgres` (`defaultJdbcUrl`), dekker alle tre** |

Begrunnelse for forslaget: pool på 20 per pod er høyt når Cloud SQL-instansen (`db-f1-micro` i dev)
deles med flere databasebrukere (app, purring, opprydding) og appen kan skalere til flere replikaer.
10 dekker rapid-konsumenten pluss API-trafikken med god margin. `maxLifetime` 30 min er nærmere
Hikaris egen anbefaling enn 5 min, og gir færre reconnects. `leakDetectionThreshold` og
`initializationFailTimeout` tas med fordi de har fanget reelle feil i sporhund/spesialist.
Alle verdier skal kunne overstyres per app.

Hikari-anbefalingen er **godkjent** og er nå libbens defaults.

---

# Fase 2: Plan

## 1. Arkitekturbeslutninger

| Beslutning | Valg | Begrunnelse |
|---|---|---|
| Autentisering | Azure AD (Entra) + JWT-validering i appen | Saksbehandler via Speil; Speil sender token videre |
| Autorisasjon | `Tilgang.Les/Skriv` fra Entra-grupper (libben) + app-definerte brukerroller | Deny-by-default per endepunkt via `RestBehandler.påkrevdTilgang` |
| Persontilgang | `PopulasjonstilgangskontrollProvider` (tilgangsmaskinen) med Texas-token | Samme som sporhund/spesialist |
| Person-id i API | `PersonPseudoId` (UUID) i URL, oppslag mot Valkey | Fnr aldri i URL eller vanlig logg |
| Kommunikasjon inn | REST (typede ktor `Resources`) + rapid/rivers | Kun rapid-variant i denne omgang |
| Serialisering | Jackson 3 via `io.ktor:ktor-serialization-jackson3` | Fjerner Jackson 2/3-konflikten helt |
| Datalagring | PostgreSQL + Flyway + kotliquery | Uendret fra i dag |
| Appoppstart | `RapidApplication.create(env) { withKtorModule { ... } }` inni libben | Uendret bootstrap-modell for spv |

**Merk:** `ktor-serialization-jackson3` finnes (brukes av spesialist) — den tidligere Jackson-risikoen
i risikolista bortfaller.

## 2. Modulstruktur

```
sp-vilkarsproving/
  speil-backend-app/
    build.gradle.kts                    # sas-kotlin + java-test-fixtures
    src/main/kotlin/no/nav/helse/speil/backend/app/
      bootstrap/    StartApp.kt, AppKonfigurasjon.kt
      plugins/      ConfigureCallId.kt, ConfigureCallLogging.kt,
                    ConfigureContentNegotiation.kt, ConfigureStatusPages.kt,
                    ConfigureResources.kt
      logging/      Loggers.kt, MdcKey.kt, SkipTeamLogsMarkerFilter.kt
      auditlogg/    Auditlogger.kt
      auth/         AzureAdConfig.kt, ConfigureJwtAuthentication.kt,
                    SaksbehandlerPrincipal.kt, AccessToken.kt, Saksbehandler.kt,
                    Tilgang.kt, TilgangsgrupperTilTilganger.kt,
                    TilgangsgrupperTilBrukerroller.kt
      person/       Identitetsnummer.kt, PersonPseudoId.kt,
                    PersonPseudoIdProvider.kt, ValkeyPersonPseudoIdProvider.kt,
                    Populasjonstilgang.kt
      rest/         RestBehandler.kt, RestAdapter.kt, RestResponse.kt,
                    ApiErrorCode.kt, ProblemDetails.kt, KallKontekst.kt, Ruting.kt
      openapi/      ConfigureOpenApiPlugin.kt, OpenApiRuter.kt
      db/           DatabaseConfig.kt, DataSourceBuilder.kt, Migrering.kt,
                    KotliqueryHjelpere.kt
    src/testFixtures/kotlin/.../testfixtures/
      TestApp.kt, TokenUtsteder.kt, MockTexasServer.kt,
      MockTilgangsmaskinenServer.kt, InMemoryPersonPseudoIdProvider.kt,
      TestDatabase.kt
    src/test/kotlin/...                 # libbens egne tester
```

Registreres i `settings.gradle.kts`. Pakkenavnet er nøytralt slik at flytting til `tbd-libs`
kun krever endring av koordinater (`com.github.navikt.tbd-libs:speil-backend-app`).

## 3. Offentlig API (utkast)

```kotlin
// bootstrap/AppKonfigurasjon.kt
data class AppKonfigurasjon(
    val appNavn: String,
    val azureAd: AzureAdConfig,
    val database: DatabaseConfig,
    val populasjonstilgang: PopulasjonstilgangConfig,
    val personPseudoId: PersonPseudoIdConfig,
    val tilganger: TilgangsgrupperTilTilganger,
) {
    companion object {
        fun fraEnv(appNavn: String, env: Map<String, String> = System.getenv()): AppKonfigurasjon
    }
}

// bootstrap/StartApp.kt
fun <ROLLE : Any, TRANSAKSJON : Any> startApp(
    konfigurasjon: AppKonfigurasjon,
    brukerroller: TilgangsgrupperTilBrukerroller<ROLLE>,
    transaksjonProvider: (DataSource) -> TransaksjonProvider<TRANSAKSJON>,
    rivere: RapidsConnection.(DataSource) -> Unit = {},
    endepunkter: RestRuting<ROLLE, TRANSAKSJON>.() -> Unit = {},
)

// rest/RestBehandler.kt
interface RestBehandler<ROLLE> {
    val påkrevdTilgang: Tilgang                       // deny-by-default
    val påkrevdeBrukerroller: Set<ROLLE> get() = emptySet()
    val tag: String
    fun openApi(config: RouteConfig) {}
    fun operationIdBasertPåKlassenavn(): String
}

interface GetBehandler<RESOURCE, RESPONSE, ERROR : ApiErrorCode, ROLLE, TRANSAKSJON> : RestBehandler<ROLLE> {
    fun behandle(resource: RESOURCE, kallKontekst: KallKontekst<TRANSAKSJON, ROLLE>): RestResponse<RESPONSE, ERROR>
}
// tilsvarende Post/Put/Patch/DeleteBehandler (med/uten request body)

// rest/KallKontekst.kt
class KallKontekst<TRANSAKSJON, ROLLE>(
    val saksbehandler: Saksbehandler,
    val tilganger: Set<Tilgang>,
    val brukerroller: Set<ROLLE>,
    val transaksjon: TRANSAKSJON,
    val accessToken: AccessToken,
) {
    fun <RESPONSE, ERROR : ApiErrorCode> medPerson(
        personPseudoId: PersonPseudoId,
        personIkkeFunnet: () -> ERROR,
        manglerTilgang: () -> ERROR,
        block: (Identitetsnummer) -> RestResponse<RESPONSE, ERROR>,
    ): RestResponse<RESPONSE, ERROR>   // slår opp pseudo-id, kaller tilgangsmaskinen, auditlogger
}
```

**Appkode etter migrering (mål):**

```kotlin
fun main() = startApp(
    konfigurasjon = AppKonfigurasjon.fraEnv("sp-vilkarsproving"),
    brukerroller = TilgangsgrupperTilBrukerroller(Brukerrolle.entries, System.getenv()),
    transaksjonProvider = ::PostgresTransaksjonProvider,
    rivere = { dataSource ->
        OpptjeningsvurderingRiver(this, PostgresTransaksjonProvider(dataSource))
        GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(this, ...)
        OpptjeningsvurderingResultatRiver(this, ...)
    },
    endepunkter = {
        get(GetVilkårsvurderingBehandler())
        post(PostVilkårsvurderingBehandler())
    },
)
```

### Bevisste avgrensninger i v1
- **Ingen outbox/meldingspublisering i `KallKontekst`.** Spesialist har det, men det er app-spesifikt.
  Appen legger dette i sin egen transaksjonskontekst. Vurderes til v2.
- **Ingen naisApp-variant uten rapid** (avklart: kun rapid nå).
- **Ingen GraphQL/SSE** — spesialist-spesifikt.

## 4. Databasestrategi

- `DatabaseConfig` bygger jdbc-url via tbd-libs `naisful-postgres` (`defaultJdbcUrl` /
  `jdbcUrlWithGoogleSocketFactory`) — dekker både `DATABASE_JDBC_URL`, bruker/passord og socket factory.
- Hikari-defaults som godkjent i tabellen over, alle overstyrbare via `DatabaseConfig`.
- Flyway kjøres i egen kortlivet datasource før appen starter (ikke delt pool),
  med `lockRetryCount(-1)`, `cleanDisabled(true)`, `validateMigrationNaming(true)`.
- Migrasjonsfiler blir liggende i `migreringer`-modulen i spv — libben eier kun kjøringen.
- Kotliquery-hjelpere (`asSQL`, `single`, `list`, `update`, `updateAndReturnGeneratedKey`) flyttes fra
  sporhund-mønsteret inn i libben.

## 5. Nais-endringer for sp-vilkarsproving

```yaml
  azure:
    application:
      enabled: true
      claims:
        extra:
          - NAVident
        groups:
          - id: {{ENTRAID_UUID_SPEIL_SAKSBEHANDLER}}
          - id: {{ENTRAID_UUID_SPEIL_LESETILGANG}}
  valkey:
    - instance: personpseudoid
      access: read              # ⚠️ verifiser: read vs readwrite
  accessPolicy:
    inbound:
      rules:
        - application: speil
    outbound:
      rules:
        - application: logging
          namespace: nais-system
        - application: populasjonstilgangskontroll
          namespace: tilgangsmaskin
  env:
    - name: TILGANGSMASKINEN_SCOPE
      value: api://<cluster>.tilgangsmaskin.populasjonstilgangskontroll/.default
    - name: TILGANGSMASKINEN_BASE_URL
      value: http://populasjonstilgangskontroll.tilgangsmaskin
    - name: TILGANG_SKRIV
      value: {{ENTRAID_UUID_SPEIL_SAKSBEHANDLER}}
    - name: TILGANG_LES
      value: {{ENTRAID_UUID_SPEIL_LESETILGANG}}
```

`NAIS_TOKEN_ENDPOINT` / `NAIS_TOKEN_EXCHANGE_ENDPOINT` injiseres automatisk av plattformen når
`azure.application.enabled: true`. Entra-gruppe-UUID-ene hentes fra samme vars-mønster som sporhund
(`.nais/entra-id-vars.yml`). Ressurser og CPU-oppsett endres ikke (ingen CPU-limits).

## 6. CI/CD

Ingen endringer i workflow-filene nødvendig: `bygg-og-test-med-gradle` bygger alle moduler, og
`bygg-modul-image-med-jib` peker på `sp-vilkarsproving`-modulen. Nye `.nais`-filer og vars må legges
inn i deploy-stegene hvis Entra-vars flyttes til egen fil.

## 7. Teststrategi

| Nivå | Hva | Hvor |
|---|---|---|
| Enhet | Gruppe→tilgang/rolle-mapping, jdbc-url-bygging, `SkipTeamLogsMarkerFilter`, problem+json-format, `operationId`-utledning | libbens `src/test` |
| Enhet | `RestAdapter`: 401 uten principal, 403 ved manglende tilgang, 403 ved manglende rolle, 500 → problem+json uten lekkasje | libbens `src/test` (ktor test host) |
| Integrasjon | `medPerson`: pseudo-id ikke funnet → 404, manglende tilgang → 403 + auditlogg, ok → blokk kjøres | libbens `src/test` med mock tilgangsmaskin |
| Integrasjon | spv sine endepunkter via testfixtures (MockOAuth2Server + testapp) | spv `src/test` |
| Karakterisering | Eksisterende rivertester (`TestRapid`) og DB-tester (testcontainers) skal passere uendret gjennom hele migreringen | spv `src/test` |

Karakteriseringstestene er den viktigste sikringen: logg- og db-flyttingen skal ikke endre oppførsel.

## 8. Sikkerhetssjekkliste

- [ ] Fnr aldri i vanlig logg — kun `teamLogs`; pseudo-id i URL-er og MDC
- [ ] Auditlogg ved personoppslag **og** ved avslag (`flexString1=Deny`)
- [ ] Deny-by-default: hvert endepunkt må deklarere `påkrevdTilgang`
- [ ] JWT: verifiser både `issuer` og `audience`; `groups`-claim må komme fra Azure-konfigurasjon
- [ ] Feilsvar (RFC 7807) lekker ikke stacktrace eller intern tilstand
- [ ] Ingen hemmeligheter i kode eller versjonskatalog
- [ ] `accessPolicy.inbound` eksplisitt satt (kun `speil`)
- [ ] Ingen CPU-limits i nais
- [ ] Swagger-UI eksponeres kun der det er ønsket (`eksponerOpenApi`-flagg som i spesialist)

## 9. Rekkefølge og migreringsstrategi

Hvert steg skal være grønt (bygg + test) før neste starter.

1. `modul-oppsett` — modul, settings, versjonskatalog
2. `logging` — flytt logg-API; oppdater `logback.xml` **i samme commit** (filterklassens navn endres)
3. `db` — `DatabaseConfig` + Hikari-defaults + Flyway + kotliquery; ta i bruk i `App.kt`
4. `auth` — `Tilgang`, principal, JWT-oppsett, tilgangsgrupper 🔴
5. `populasjonstilgang` — person-pseudo-id (Valkey), tilgangsmaskinen, auditlogg 🔴
6. `rest-rammeverk` — `RestBehandler`/`RestAdapter`/`KallKontekst`/`RestResponse` 🔴
7. `openapi` — plugin-config + `/api/openapi.json` + `/api/swagger`
8. `bootstrap` — `startApp`; `App.kt` slankes til rivere + endepunkter
9. `test-fixtures` — delte testhjelpere; spv-tester legges om
10. `midlertidig-endepunkt` — nytt saksbehandler-endepunkt erstatter `POST /vilkarsvurderinger/{id}`
11. `nais-endringer` — Valkey, accessPolicy, Entra-grupper, env
12. `verifisering` — full bygg/test + manuell verifisering i dev

**Rollback:** alt er additivt fram til steg 8. Feiler noe i dev, rulles image tilbake til forrige
versjon; nais-endringene (steg 11) er bakoverkompatible siden dagens app ikke leser de nye
env-variablene. Det midlertidige endepunktet slettes først når det nye er verifisert i dev.

## 10. 🔴 Rød-sone-deklarasjon

**Skriv selv (nav-pilot genererer kun teststubber og TODO-signaturer):**
- [ ] `ConfigureJwtAuthentication` + `TilgangsgrupperTilTilganger`/`TilgangsgrupperTilBrukerroller` —
      sikkerhetskritisk mapping fra Entra-grupper til tilgang
- [ ] `RestAdapter` sin autorisasjonssti (tilgang → rolle → populasjonstilgang → transaksjon) —
      én feil her eksponerer persondata
- [ ] `KallKontekst.medPerson` — pseudo-id-oppslag, tilgangskontroll og auditlogging
- [ ] `Auditlogger` sitt CEF-format — etterlevelseskrav

**Genereres av nav-pilot (les gjennom før merge):**
- [ ] Modul- og Gradle-oppsett, versjonskatalog
- [ ] Flytting av logg-API + `logback.xml`-endring
- [ ] `DatabaseConfig`, Hikari-oppsett, Flyway-kjøring, kotliquery-hjelpere
- [ ] `RestResponse`, `ApiErrorCode`, problem+json-serialisering, `Ruting.kt`
- [ ] OpenAPI-plugin-config og ruter
- [ ] Testfixtures og teststubber
- [ ] Nais-manifestendringer

## 11. Observerbarhet

- Metrikker: `auditlog_total` (finnes), Hikari-pool-metrikker via `metricRegistry`,
  ktor-request-metrikker fra `naisApp`/rapids, samt teller for avviste kall per årsak
  (401/403/manglende populasjonstilgang).
- Logging: `callId` i MDC på alle kall; `teamLogs` for detaljer med persondata; `SKIP_TEAM_LOGS_MARKER`
  hindrer dobbeltlogging.
- Tracing: autoinstrumentering er allerede på i nais for spv.
- Etter deploy: verifiser at `/api/openapi.json` svarer, at et Speil-token gir 200, at token uten
  riktig gruppe gir 403, og at auditlogg-innslag dukker opp.

## Åpne punkter å verifisere under arbeidet

- Valkey-tilgangsnivå for `personpseudoid` (`read` vs `readwrite`) — hvem oppretter pseudo-id-ene?
- Hvilke Entra-grupper spv skal bruke (samme som Speil/sporhund, eller egne?)
- Om `migreringer`-modulen skal beholdes som egen modul (anbefaling: ja, uendret)

---

# Fase 3: Review

| Perspektiv | Vurdering | Funn |
|---|---|---|
| Sikkerhet | ⚠️ | Persontilgang er opt-in; swagger-eksponering; rå request-body i feillogg; gruppevalg |
| Plattform | ⚠️ | `db-f1-micro` i dev vs pool 10; migreringsrekkefølge og startup-probe; Valkey-TTL |
| Arkitektur | ⚠️ | For mange typeparametere; libben verifiseres kun mot én app |
| Endringssikkerhet | ✅ | Additivt til steg 8, karakteriseringstester finnes, rollback via image |

### Sikkerhet

1. **🔴 Persontilgangskontroll er opt-in.** `KallKontekst.medPerson` må kalles eksplisitt av hver
   behandler. Glemmer man det, gjøres personoppslag uten kontroll mot tilgangsmaskinen — samme
   svakhet som sporhund og spesialist har i dag.
   **Endring:** innfør markørinterface `PersonResource { val pseudoId: UUID }`. Når `RESOURCE`
   implementerer det, kjører `RestAdapter` pseudo-id-oppslag, populasjonstilgangskontroll og
   auditlogg **automatisk** før behandleren kalles. `medPerson` beholdes kun for behandlere som
   trenger flere personer. Dette er en reell forbedring over kildene.
2. **Swagger/OpenAPI-eksponering.** Sporhunds JWT-config har `skipWhen` på `/api/openapi.json` og
   `/api/swagger`, altså uautentisert.
   **Endring:** `eksponerOpenApi`-flagg (som spesialist), default `false` i prod, styrt av env.
3. **Rå request-body i feillogg.** Spesialists `RestAdapter` logger `call.receive<String>()` ved feil.
   **Endring:** body går kun til `teamLogs`, aldri til vanlig logg — verifiseres med test.
4. **Entra-gruppevalg.** Gjenbruk av Speils saksbehandlergruppe gir alle Speil-saksbehandlere
   skrivetilgang til vilkårsprøving. **Må avklares med fagansvarlig** før nais-endringen.
5. **Least privilege på Valkey:** `access: read` hvis spv ikke oppretter pseudo-id-er.
6. ✅ Auditlogg ved både oppslag og avslag, fnr kun i `teamLogs`, eksplisitt `accessPolicy.inbound`.

### Plattform

1. **🔴 Connection pool vs db-tier.** Dev bruker `db-f1-micro` (`sp-vilkarsproving-vars-dev.yaml`),
   som har lav `max_connections`. Pool 10 × opptil 2 replikaer + migreringspool + `opprydding-dev` (3)
   kan tømme instansen.
   **Endring:** gjør poolstørrelsen miljøstyrt (default 10, men `DB_POOL_SIZE`-override), og sett
   dev til 5. Alternativt løft dev-tier. Verifiser `max_connections` før produksjonssetting.
2. **Migreringsrekkefølge.** I dag kjøres Flyway på `ApplicationStarted`, altså etter at rapid er i
   gang. Planen flytter den før oppstart — bra, men **må skje før rivere begynner å konsumere**.
   **Endring:** kjør migrering synkront i `startApp` før `RapidApplication.start()`, og sjekk at
   startup-probe-budsjettet (initialDelay 20 s + 9 × 5 s = 65 s) dekker migreringstiden.
3. **Valkey-TTL.** Pseudo-id-er har 7 dagers TTL i tbd-libs-klienten. Appen må svare 404 (ikke 500)
   på utløpt pseudo-id — dekkes av `PersonResource`-flyten, men trenger egen test.
4. ✅ Ingen CPU-limits, observability og preStopHook allerede på plass.

### Arkitektur

1. **⚠️ Typeparameter-støy.** `startApp<ROLLE, TRANSAKSJON>` + behandlere med
   `RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE, TRANSAKSJON` blir tungt å lese og skrive.
   **Endring:** la brukerroller være et **markørinterface** (`interface Brukerrolle { val navn: String }`)
   som appen implementerer med sin egen enum, i stedet for typeparameter. Da faller `ROLLE` bort som
   generisk parameter, og `KallKontekst<TRANSAKSJON>` har kun én.
2. **⚠️ Libben verifiseres kun mot sp-vilkarsproving.** Den generaliserer fra tre apper, men bare én
   tar den i bruk nå. Risiko for at API-et ikke passer sporhund/spesialist ved senere migrering.
   **Endring:** dokumentér i libbens README hvilke behov fra sporhund og spesialist API-et skal dekke,
   som akseptansekriterium — uten å implementere migreringen nå.
3. **⚠️ Lekkasje av app-spesifikke behov.** Libben ligger i spv-repoet.
   **Endring:** libben skal ikke ha prosjektavhengighet til `:sp-vilkarsproving`, og libbens egne
   tester skal kjøre uten den. Håndheves i `build.gradle.kts`.
4. ✅ Gjenbruk av tbd-libs (`naisful-postgres`, Texas, tilgangsmaskinen, person-pseudo-id) er riktig nivå.

### Endringssikkerhet

1. ✅ Additivt til og med steg 7; rollback = forrige image.
2. **⚠️ Logback-endringen (steg 2)** er den eneste ikke-additive. Logback feiler ikke hardt på ukjent
   filterklasse — den logger en feil og fortsetter, slik at `SKIP_TEAM_LOGS_MARKER`-filteret stille
   slutter å virke og team-logs får dobbeltoppføringer.
   **Endring:** legg til en test som verifiserer at filteret faktisk er aktivt etter flyttingen.
3. **⚠️ Ingen test dekker `vilkårsprøvingApi` i dag.** Det finnes ingen karakteriseringstest for
   API-laget. Akseptabelt siden endepunktet er midlertidig og skal erstattes, men det nye endepunktet
   må ha testdekning fra dag én.
4. ✅ Rivertester og DB-tester (testcontainers) fungerer som karakteriseringstester for resten.

## Konklusjon Fase 3

**Godkjent med endringer.** Følgende tas inn i planen før Fase 4:

- [ ] `PersonResource`-markør gjør populasjonstilgangskontroll obligatorisk, ikke opt-in
- [ ] `eksponerOpenApi`-flagg, av som default i prod
- [ ] Request-body kun til `teamLogs` ved feil
- [ ] Poolstørrelse miljøstyrt; dev = 5 pga. `db-f1-micro`
- [ ] Flyway kjøres synkront før rapid starter; verifiser startup-probe-budsjett
- [ ] Brukerroller som markørinterface i stedet for typeparameter
- [ ] Test som verifiserer at logback-filteret er aktivt etter flytting
- [ ] Test for utløpt pseudo-id → 404
- [ ] Libben uten prosjektavhengighet til spv; README dokumenterer sporhund/spesialist-behov
- [ ] Avklar Entra-gruppevalg med fagansvarlig før nais-endring
