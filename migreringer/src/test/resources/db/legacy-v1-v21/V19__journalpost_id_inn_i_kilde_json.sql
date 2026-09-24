UPDATE vilkarsvurdering
SET kilde = jsonb_set(kilde, '{journalpostId}', '[]'::jsonb, true)
WHERE kilde ->> 'type' = 'SAKSBEHANDLER'
  AND NOT (kilde ? 'journalpostId');

ALTER TABLE vilkarsvurdering
    DROP COLUMN journalpost_id;
