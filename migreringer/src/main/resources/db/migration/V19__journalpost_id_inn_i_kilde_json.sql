-- journalpostId hører hjemme sammen med resten av den manuelle vurderingen, ikke som en egen kolonne.
-- Det finnes ingen data på journalpost_id i prod, så vi patcher inn et tomt array i kilde-jsonen for
-- saksbehandlervurderinger som mangler feltet, og fjerner deretter kolonnen.
UPDATE vilkarsvurdering
SET kilde = jsonb_set(kilde, '{journalpostId}', '[]'::jsonb, true)
WHERE kilde ->> 'type' = 'SAKSBEHANDLER'
  AND NOT (kilde ? 'journalpostId');

ALTER TABLE vilkarsvurdering
    DROP COLUMN journalpost_id;
