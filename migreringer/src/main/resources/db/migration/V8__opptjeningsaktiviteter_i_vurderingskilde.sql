UPDATE vilkarsvurdering
SET kilde = (kilde - 'ytelser') || jsonb_build_object('opptjeningsaktiviteter', kilde -> 'ytelser')
WHERE kilde ? 'ytelser';
