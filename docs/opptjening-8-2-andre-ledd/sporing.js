'use strict';

function lovreferanse(nøkkel) {
    const { paragraf, ledd, punktum } = LOVREFERANSER[nøkkel];
    return punktum ? { paragraf, ledd, punktum } : { paragraf, ledd };
}

function saksbehandlerkilde(lagret, ekstra = {}) {
    return {
        type: 'Saksbehandler',
        ident: SAKSBEHANDLER,
        fritekstbegrunnelse: lagret.svar.begrunnelse,
        journalpostId: [],
        ...ekstra,
    };
}

function vurdering(nøkkel, utfall, kilde, lovNøkkel = VILKÅR[nøkkel].lovreferanse) {
    return {
        vilkårskode: VILKÅR[nøkkel].vilkårskode,
        utfall,
        lovreferanse: lovreferanse(lovNøkkel),
        kilde,
    };
}

function hovedregelVurdering(scenario, vurderinger) {
    if (vurderinger.HOVEDREGEL) {
        return vurdering('HOVEDREGEL', vurderinger.HOVEDREGEL.utfall, saksbehandlerkilde(vurderinger.HOVEDREGEL));
    }
    return vurdering('HOVEDREGEL', arbeidsdager(scenario) >= 28 ? 'OPPFYLT' : 'IKKE_OPPFYLT', {
        type: 'Automatisk',
        grunnlag: { opptjeningsdager: arbeidsdager(scenario), arbeidsforhold: '…' },
    });
}

function jaNeiTilBoolean(verdi) {
    return verdi === undefined ? null : verdi === 'JA';
}

function nyeVurderinger(nivå, scenario, vurderinger, lagringsmodell) {
    if (nivå === '1') {
        const v = vurderinger.LIKESTILT;
        return v ? [vurdering('LIKESTILT', v.utfall, saksbehandlerkilde(v))] : [];
    }

    if (nivå === '2') {
        const v = vurderinger.LIKESTILT;
        if (!v) return [];
        const { svar } = v;
        const utledet = utledNivå2(svar);
        if (lagringsmodell === 'svar') {
            return [
                vurdering(
                    'LIKESTILT',
                    utledet.utfall,
                    saksbehandlerkilde(v, {
                        svar: {
                            ytelser: svar.ytelser,
                            sammenlagtMinst4Uker: jaNeiTilBoolean(svar.sammenlagt),
                            foreldrepengerOpptjentPåAap: jaNeiTilBoolean(svar.fpAap),
                            yrkesaktivMinst4UkerFørUttak: jaNeiTilBoolean(svar.yrkesaktiv),
                        },
                        anvendteLovreferanser: utledet.anvendt.map(lovreferanse),
                    }),
                    utledet.punktum,
                ),
            ];
        }
        const felles = saksbehandlerkilde(v, { ytelser: svar.ytelser });
        const liste = [vurdering('L2P1', svar.sammenlagt === 'JA' ? 'OPPFYLT' : 'IKKE_OPPFYLT', felles)];
        if (utledet.anvendt.includes('L2P2')) {
            liste.push(vurdering('L2P2', svar.fpAap === 'NEI' ? 'OPPFYLT' : 'IKKE_OPPFYLT', felles));
        }
        if (utledet.anvendt.includes('L2P3')) {
            liste.push(vurdering('L2P3', svar.yrkesaktiv === 'JA' ? 'OPPFYLT' : 'IKKE_OPPFYLT', felles));
        }
        return liste;
    }

    if (nivå === '2L') {
        const v = vurderinger.LIKESTILT;
        if (!v) return [];
        const { svar } = v;
        const kilde = saksbehandlerkilde(v);
        const liste = [vurdering('LETT2', svar.arbeidOgYtelser === 'JA' ? 'OPPFYLT' : 'IKKE_OPPFYLT', kilde)];
        if (svar.fpAapGirFireUker === 'JA') {
            liste.push(vurdering('LETT3', svar.yrkesaktivFørUttak === 'JA' ? 'OPPFYLT' : 'IKKE_OPPFYLT', kilde));
        }
        return liste;
    }

    if (nivå === '3') {
        return nivå3SynligeRader(vurderinger)
            .filter((nøkkel) => vurderinger[nøkkel])
            .map((nøkkel) => {
                const v = vurderinger[nøkkel];
                const ekstra = nøkkel === 'L2P1' ? { ytelser: v.svar.ytelser ?? [] } : {};
                return vurdering(nøkkel, v.utfall, saksbehandlerkilde(v, ekstra));
            });
    }

    const v = vurderinger.LIKESTILT;
    if (!v) return [];
    const utledet = utledNivå4(scenario, v.svar);
    return [
        vurdering(
            'LIKESTILT',
            utledet.utfall,
            saksbehandlerkilde(v, {
                grunnlag: {
                    opptjeningsperiode: utledet.fom ? { fom: utledet.fom, tom: plussDager(SKJÆRINGSTIDSPUNKT, -1) } : null,
                    opptjeningsdager: utledet.dager,
                    likestilteYtelser: v.svar.perioder.map((p) => ({
                        ytelse: p.type,
                        fom: p.fom,
                        tom: p.tom,
                        ...(p.type === 'FORELDREPENGER'
                            ? {
                                  opptjentPåAap: jaNeiTilBoolean(p.opptjentPåAap),
                                  yrkesaktivMinst4UkerFørUttak: jaNeiTilBoolean(p.yrkesaktivFørUttak),
                              }
                            : {}),
                    })),
                },
                anvendteLovreferanser: utledet.anvendt.map(lovreferanse),
            }),
            utledet.punktum,
        ),
    ];
}

function sporingsobjekt(nivå, scenario, vurderinger, lagringsmodell) {
    const resultat = totalResultat(nivå, scenario, vurderinger);
    const nye = nyeVurderinger(nivå, scenario, vurderinger, lagringsmodell);
    let avgjørendeVilkårskode = null;
    if (resultat.avgjørende === 'HOVEDREGEL') {
        avgjørendeVilkårskode = VILKÅR.HOVEDREGEL.vilkårskode;
    } else if (resultat.utfall === 'OPPFYLT') {
        if (nivå === '2L') avgjørendeVilkårskode = VILKÅR[resultat.likestilt.kode].vilkårskode;
        else
            avgjørendeVilkårskode =
                nivå === '2' && lagringsmodell === 'punktum'
                    ? VILKÅR[resultat.likestilt.punktum].vilkårskode
                    : VILKÅR[resultat.avgjørende].vilkårskode;
    }
    return {
        skjæringstidspunkt: SKJÆRINGSTIDSPUNKT,
        erOk: resultat.utfall === 'OPPFYLT',
        avgjørendeVilkårskode,
        vilkårsvurderinger: [hovedregelVurdering(scenario, vurderinger), ...nye],
    };
}

function subsumerteLovreferanser(nivå, scenario, vurderinger, lagringsmodell) {
    const objekt = sporingsobjekt(nivå, scenario, vurderinger, lagringsmodell);
    const referanser = new Set();
    objekt.vilkårsvurderinger.forEach((v) => {
        referanser.add(JSON.stringify(v.lovreferanse));
        (v.kilde.anvendteLovreferanser ?? []).forEach((l) => referanser.add(JSON.stringify(l)));
    });
    return [...referanser].map((r) => {
        const { ledd, punktum } = JSON.parse(r);
        return Object.values(LOVREFERANSER).find((l) => l.ledd === ledd && (l.punktum ?? undefined) === punktum)?.tekst;
    });
}
