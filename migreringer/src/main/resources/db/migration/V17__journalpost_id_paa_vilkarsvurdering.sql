ALTER TABLE vilkarsvurdering
    ADD COLUMN journalpost_id JSONB NOT NULL DEFAULT '[]'::jsonb;
