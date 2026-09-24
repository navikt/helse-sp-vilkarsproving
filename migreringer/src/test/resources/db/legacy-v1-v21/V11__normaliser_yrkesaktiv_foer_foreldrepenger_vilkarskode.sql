-- Domenets Vilkårskode-enum har rename av OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER til
-- OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER. Verdien lagres som ren TEXT i vilkarsvurdering.vilkårskode,
-- så eksisterende rader med den gamle stavemåten må normaliseres — ellers feiler
-- Vilkårskode.valueOf(...) ved lesing, og V12 sin backfill av avgjørende_vilkårskode vil ikke
-- kjenne igjen disse radene.

UPDATE vilkarsvurdering
SET vilkårskode = 'OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER'
WHERE vilkårskode = 'OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER';
