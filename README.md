# sp-vilkarsproving

## Flyway: overgang fra V1-V21 til V1-V3

Migreringene er samlet i `V1__initielt_skjema.sql`, `V2__outbox.sql` og
`V3__opprydding_dev_tilganger.sql`. Nye databaser kjører alle tre.
Eksisterende databaser får én `BASELINE`-rad på versjon 3. Skjema, data og rettigheter beholdes.

Overgangen krever to deployer:

1. Deploy med `beforeValidate__squash.sql`. Callbacken kontrollerer at V1-V21 er kjørt
   med forventede sjekksummer og at skjemaet stemmer, før den erstatter Flyway-historikken
   i én transaksjon. Prøv først i dev via en `dev-*`-branch. På `main` deployer workflowen
   til dev og prod uavhengig av hverandre.
2. Når **begge miljøer** har startet med baseline V3, også etter en omstart, slett
   `migreringer/src/main/resources/db/migration/beforeValidate__squash.sql` og deploy igjen.
   Fjern samtidig `FlywaySquashTest`, testressursene `db/legacy-v1-v21` og
   `db/eksempeldata.sql`, og overgangstilfellene i `FlywaySkjemaTest`.
   Behold testene for nyoppretting og dev-tilganger. Neste ordinære migrering blir V4.

Ikke deploy et gammelt image med V1-V21 etter konverteringen. Det kan ikke validere den
nye historikken. Behold imaget fra første deploy som rollback-mål. Allerede kjørende gamle
podder kan bruke det uendrede skjemaet, men kan feile ved omstart. Unngå andre
skjemaendringer mellom de to deployene. Ved avvik stopper callbacken uten å endre historikken;
undersøk feilen fremfor å kjøre `repair` eller slå av validering.

