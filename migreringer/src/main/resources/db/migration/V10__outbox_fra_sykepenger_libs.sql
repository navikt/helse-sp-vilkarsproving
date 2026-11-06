-- Outboxen fra sykepenger-libs publiserer melding-kolonnen uendret. Upubliserte rader i det gamle formatet ville gått
-- ut på rapiden som de er, så vi stopper migreringen i stedet.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1 FROM outbox WHERE publisert_tidspunkt IS NULL) THEN
            RAISE EXCEPTION 'Outboxen har upubliserte meldinger i det gamle formatet';
        END IF;
    END
$$;

-- Eksisterende verdier er skrevet i UTC, både av databasen (now()) og av appen.
ALTER TABLE outbox
    ALTER COLUMN opprettet TYPE timestamptz USING opprettet AT TIME ZONE 'UTC',
    ALTER COLUMN publisert_tidspunkt TYPE timestamptz USING publisert_tidspunkt AT TIME ZONE 'UTC';

ALTER TABLE outbox ADD CONSTRAINT outbox_sekvensnummer_key UNIQUE (sekvensnummer);

ALTER INDEX outbox_upublisert RENAME TO outbox_upublisert_idx;
