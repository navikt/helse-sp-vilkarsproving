INSERT INTO opptjeningsvurdering
    (id, fødselsnummer, skjæringstidspunkt, vurderingskilde, opptjening_ok, avgjørende_vilkårskode, vurdert_tidspunkt, kategori)
VALUES
    ('00000000-0000-0000-0000-000000000001', '12345678910', '2026-09-01', 'VURDERT_I_SPEIL', true,
     'OPPTJENING_ARBEID_MINST_4_UKER', '2026-09-01 12:00:00+02', 'ARBEIDSTAKER'),
    ('00000000-0000-0000-0000-000000000002', '12345678910', '2026-09-01', 'OVERFOERT_FRA_INFOTRYGD', false,
     NULL, '2026-09-02 12:00:00+02', 'SELVSTENDIG_NÆRINGSDRIVENDE');

INSERT INTO vilkarsvurdering (id, vilkårskode, utfall, vurdert_tidspunkt, kilde, lovreferanse)
VALUES
    ('00000000-0000-0000-0000-000000000003', 'OPPTJENING_ARBEID_MINST_4_UKER', 'OPPFYLT',
     '2026-09-01 12:00:00+02', '{"type":"SAKSBEHANDLER","journalpostId":["123456"]}', '{"paragraf":"8-2"}');

INSERT INTO opptjeningsvurdering_vilkarsvurdering (opptjeningsvurdering_id, vilkarsvurdering_id)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003');

INSERT INTO opptjeningsproving
    (id, fødselsnummer, skjæringstidspunkt, startet, tilstand, opptjeningsvurdering_id, kategori)
VALUES
    ('00000000-0000-0000-0000-000000000004', '12345678910', '2026-09-01', now(), 'FULLFØRT',
     '00000000-0000-0000-0000-000000000001', 'ARBEIDSTAKER'),
    ('00000000-0000-0000-0000-000000000005', '12345678910', '2026-09-01', now(), 'STARTET', NULL, 'SELVSTENDIG_NÆRINGSDRIVENDE');

INSERT INTO outbox (id, melding, fodselsnummer, publisert_tidspunkt)
VALUES
    ('00000000-0000-0000-0000-000000000006', '{"type":"OPPTJENINGSVURDERING_ENDRET"}', '12345678910', NULL),
    ('00000000-0000-0000-0000-000000000007', '{"type":"OPPTJENINGSVURDERING_ENDRET"}', '12345678910', now());
