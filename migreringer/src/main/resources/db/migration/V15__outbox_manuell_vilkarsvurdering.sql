UPDATE outbox
SET melding = jsonb_set(melding, '{type}', '"OPPTJENINGSVURDERING_ENDRET"')
WHERE melding ->> 'type' = 'OPPTJENINGSVURDERING_OVERSTYRT';
