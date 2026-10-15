'use strict';

const VILKÅR = {
    HOVEDREGEL: {
        vilkårskode: 'OPPTJENING_ARBEID_MINST_4_UKER',
        navn: 'Arbeid i minst 4 uker',
        spørsmål: 'Vurder om søkeren har hatt arbeid i minst 4 uker',
        lovreferanse: 'L1P1',
    },
    LIKESTILT: {
        vilkårskode: 'OPPTJENING_LIKESTILT_YTELSE',
        navn: 'Likestilt ytelse',
        spørsmål:
            'Vurder om søkeren har hatt arbeid og/eller likestilt ytelse i minst 4 uker umiddelbart før arbeidsuførheten',
        lovreferanse: 'L2',
    },
    L2P1: {
        vilkårskode: 'OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER',
        navn: 'Likestilt ytelse i minst 4 uker',
        spørsmål:
            'Vurder om søkeren har hatt likestilt ytelse, eventuelt sammen med arbeid, i minst 4 uker umiddelbart før arbeidsuførheten',
        lovreferanse: 'L2P1',
    },
    L2P2: {
        vilkårskode: 'OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP',
        navn: 'Foreldrepenger ikke opptjent på AAP',
        spørsmål: 'Vurder om foreldrepengene er opptjent på annet grunnlag enn arbeidsavklaringspenger',
        lovreferanse: 'L2P2',
    },
    L2P3: {
        vilkårskode: 'OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER',
        navn: 'Yrkesaktiv i minst 4 uker før uttak av foreldrepenger',
        spørsmål:
            'Vurder om søkeren var sammenhengende yrkesaktiv, eller mottok ytelse etter kapittel 8, 9 eller 14, i minst 4 uker umiddelbart før uttaket av foreldrepenger startet',
        lovreferanse: 'L2P3',
    },
    LETT2: {
        vilkårskode: 'OPPTJENING_ARBEID_OG_LIKESTILTE_YTELSER_MINST_4_UKER',
        navn: 'Arbeid og likestilte ytelser i minst 4 uker',
        lovreferanse: 'L2P1',
    },
    LETT3: {
        vilkårskode: 'OPPTJENING_FORELDREPENGER_MED_YRKESAKTIVITET_FØR_UTTAK',
        navn: 'Foreldrepenger med yrkesaktivitet før uttak',
        lovreferanse: 'L2P3',
    },
};

const LETT_HJEMLER = {
    B: '§ 8-2 andre ledd første punktum',
    C: '§ 8-2 andre ledd tredje punktum',
    D: '§ 8-2 første ledd og andre ledd første punktum',
    E: '§ 8-2 andre ledd andre punktum, jf. tredje punktum',
};

const DAG_MS = 24 * 60 * 60 * 1000;

function tilDato(iso) {
    return new Date(`${iso}T00:00:00Z`);
}

function tilIso(dato) {
    return dato.toISOString().slice(0, 10);
}

function plussDager(iso, antall) {
    return tilIso(new Date(tilDato(iso).getTime() + antall * DAG_MS));
}

function erGyldigDato(iso) {
    return typeof iso === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(iso) && !Number.isNaN(tilDato(iso).getTime());
}

function arbeidsdager(scenario) {
    if (!scenario.arbeidFom) return 0;
    return Math.round((tilDato(SKJÆRINGSTIDSPUNKT) - tilDato(scenario.arbeidFom)) / DAG_MS);
}

function periodeTeller(periode) {
    if (periode.type !== 'FORELDREPENGER') return true;
    return periode.opptjentPåAap === 'NEI' || periode.yrkesaktivFørUttak === 'JA';
}

function dagstype(scenario, perioder, iso, medIkkeTellende) {
    if (scenario.arbeidFom && iso >= scenario.arbeidFom && iso < SKJÆRINGSTIDSPUNKT) return 'arbeid';
    const treff = perioder.filter((p) => erGyldigDato(p.fom) && erGyldigDato(p.tom) && p.fom <= iso && iso <= p.tom);
    if (treff.some((p) => periodeTeller(p))) return 'ytelse';
    if (treff.length > 0) return medIkkeTellende ? 'ytelse' : 'ikke-tellende';
    return 'ingen';
}

function sammenhengendeDager(scenario, perioder, medIkkeTellende = false) {
    let dager = 0;
    let dag = plussDager(SKJÆRINGSTIDSPUNKT, -1);
    while (dager < 730) {
        const type = dagstype(scenario, perioder, dag, medIkkeTellende);
        if (type !== 'arbeid' && type !== 'ytelse') break;
        dager++;
        dag = plussDager(dag, -1);
    }
    return { dager, fom: dager > 0 ? plussDager(SKJÆRINGSTIDSPUNKT, -dager) : null };
}

function tidslinje(scenario, perioder) {
    return Array.from({ length: 56 }, (_, i) => {
        const iso = plussDager(SKJÆRINGSTIDSPUNKT, i - 56);
        return { iso, type: dagstype(scenario, perioder, iso, false), iOpptjeningsvindu: i >= 28 };
    });
}

function utledNivå2(svar) {
    const anvendt = ['L2P1'];
    if (!svar.sammenlagt) return null;
    if (svar.sammenlagt === 'NEI') return { utfall: 'IKKE_OPPFYLT', punktum: 'L2P1', anvendt };
    if (!(svar.ytelser ?? []).length) return null;
    if (!(svar.ytelser ?? []).includes('FORELDREPENGER')) return { utfall: 'OPPFYLT', punktum: 'L2P1', anvendt };
    anvendt.push('L2P2');
    if (!svar.fpAap) return null;
    if (svar.fpAap === 'NEI') return { utfall: 'OPPFYLT', punktum: 'L2P1', anvendt };
    anvendt.push('L2P3');
    if (!svar.yrkesaktiv) return null;
    return svar.yrkesaktiv === 'JA'
        ? { utfall: 'OPPFYLT', punktum: 'L2P3', anvendt }
        : { utfall: 'IKKE_OPPFYLT', punktum: 'L2P2', anvendt };
}

function lettResultat(kombinasjon, utfall, punktum, kode) {
    return { utfall, punktum, kode, kombinasjon, hjemmeltekst: LETT_HJEMLER[kombinasjon], anvendt: [] };
}

function utledLett(svar) {
    if (!svar.arbeidOgYtelser) return null;
    if (svar.arbeidOgYtelser === 'JA') return lettResultat('B', 'OPPFYLT', 'L2P1', 'LETT2');
    if (!svar.fpAapGirFireUker) return null;
    if (svar.fpAapGirFireUker === 'NEI') return lettResultat('D', 'IKKE_OPPFYLT', 'L2P1', null);
    if (!svar.yrkesaktivFørUttak) return null;
    return svar.yrkesaktivFørUttak === 'JA'
        ? lettResultat('C', 'OPPFYLT', 'L2P3', 'LETT3')
        : lettResultat('E', 'IKKE_OPPFYLT', 'L2P2', null);
}

function utledNivå4(scenario, svar) {
    const perioder = svar.perioder ?? [];
    if (perioder.length === 0) return null;
    const { dager, fom } = sammenhengendeDager(scenario, perioder);
    const harFp = perioder.some((p) => p.type === 'FORELDREPENGER');
    const fpPåAap = perioder.some((p) => p.type === 'FORELDREPENGER' && p.opptjentPåAap === 'JA');
    const anvendt = ['L2P1', ...(harFp ? ['L2P2'] : []), ...(fpPåAap ? ['L2P3'] : [])];
    if (dager >= 28) {
        const reddetAvTredje = fpPåAap && sammenhengendeDager(scenario, perioder.filter((p) => !(p.type === 'FORELDREPENGER' && p.opptjentPåAap === 'JA'))).dager < 28;
        return { utfall: 'OPPFYLT', punktum: reddetAvTredje ? 'L2P3' : 'L2P1', anvendt, dager, fom };
    }
    const hindretAvAndre = sammenhengendeDager(scenario, perioder, true).dager >= 28;
    return { utfall: 'IKKE_OPPFYLT', punktum: hindretAvAndre ? 'L2P2' : 'L2P1', anvendt, dager, fom };
}

function likestiltResultat(nivå, scenario, vurderinger) {
    if (nivå === '1') {
        const v = vurderinger.LIKESTILT;
        return v ? { utfall: v.utfall, punktum: 'L2', anvendt: ['L2'] } : null;
    }
    if (nivå === '2') return vurderinger.LIKESTILT ? utledNivå2(vurderinger.LIKESTILT.svar) : null;
    if (nivå === '2L') return vurderinger.LIKESTILT ? utledLett(vurderinger.LIKESTILT.svar) : null;
    if (nivå === '4') return vurderinger.LIKESTILT ? utledNivå4(scenario, vurderinger.LIKESTILT.svar) : null;

    const a = vurderinger.L2P1;
    const b = vurderinger.L2P2;
    const c = vurderinger.L2P3;
    if (!a) return null;
    if (a.utfall === 'IKKE_OPPFYLT') return { utfall: 'IKKE_OPPFYLT', punktum: 'L2P1', anvendt: ['L2P1'] };
    if (!nivå3HarForeldrepenger(vurderinger)) return { utfall: 'OPPFYLT', punktum: 'L2P1', anvendt: ['L2P1'] };
    if (!b) return null;
    if (b.utfall === 'OPPFYLT') return { utfall: 'OPPFYLT', punktum: 'L2P1', anvendt: ['L2P1', 'L2P2'] };
    if (!c) return null;
    return c.utfall === 'OPPFYLT'
        ? { utfall: 'OPPFYLT', punktum: 'L2P3', anvendt: ['L2P1', 'L2P2', 'L2P3'] }
        : { utfall: 'IKKE_OPPFYLT', punktum: 'L2P2', anvendt: ['L2P1', 'L2P2', 'L2P3'] };
}

function nivå3HarForeldrepenger(vurderinger) {
    return (vurderinger.L2P1?.svar?.ytelser ?? []).includes('FORELDREPENGER');
}

function nivå3SynligeRader(vurderinger) {
    const rader = ['L2P1'];
    if (vurderinger.L2P1?.utfall === 'OPPFYLT' && nivå3HarForeldrepenger(vurderinger)) {
        rader.push('L2P2');
        if (vurderinger.L2P2?.utfall === 'IKKE_OPPFYLT') rader.push('L2P3');
    }
    return rader;
}

function totalResultat(nivå, scenario, vurderinger) {
    const hovedregelUtfall = vurderinger.HOVEDREGEL?.utfall ?? (arbeidsdager(scenario) >= 28 ? 'OPPFYLT' : 'IKKE_OPPFYLT');
    const likestilt = likestiltResultat(nivå, scenario, vurderinger);
    if (hovedregelUtfall === 'OPPFYLT') {
        return { utfall: 'OPPFYLT', avgjørende: 'HOVEDREGEL', hovedregelUtfall, likestilt };
    }
    if (likestilt?.utfall === 'OPPFYLT') {
        const avgjørende = nivå === '3' ? likestilt.punktum : 'LIKESTILT';
        return { utfall: 'OPPFYLT', avgjørende, hovedregelUtfall, likestilt };
    }
    return { utfall: 'IKKE_OPPFYLT', avgjørende: null, hovedregelUtfall, likestilt };
}

function validerSkjema(nivå, nøkkel, svar, scenario) {
    const feil = {};
    const enkel = nøkkel === 'HOVEDREGEL' || nivå === '1' || nivå === '3';
    if (enkel && !svar.utfall) feil.utfall = 'Velg utfall';
    if (nivå === '3' && nøkkel === 'L2P1' && svar.utfall === 'OPPFYLT' && !(svar.ytelser ?? []).length) {
        feil.ytelser = 'Velg minst én ytelse';
    }
    if (nivå === '2' && nøkkel === 'LIKESTILT') {
        if (!svar.sammenlagt) feil.sammenlagt = 'Svar ja eller nei';
        if (svar.sammenlagt === 'JA' && !(svar.ytelser ?? []).length) feil.ytelser = 'Velg minst én ytelse';
        if (svar.sammenlagt === 'JA' && (svar.ytelser ?? []).includes('FORELDREPENGER')) {
            if (!svar.fpAap) feil.fpAap = 'Svar ja eller nei';
            if (svar.fpAap === 'JA' && !svar.yrkesaktiv) feil.yrkesaktiv = 'Svar ja eller nei';
        }
    }
    if (nivå === '2L' && nøkkel === 'LIKESTILT') {
        if (!svar.arbeidOgYtelser) feil.arbeidOgYtelser = 'Svar ja eller nei';
        if (svar.arbeidOgYtelser === 'NEI' && !svar.fpAapGirFireUker) feil.fpAapGirFireUker = 'Svar ja eller nei';
        if (svar.fpAapGirFireUker === 'JA' && !svar.yrkesaktivFørUttak) feil.yrkesaktivFørUttak = 'Svar ja eller nei';
    }
    if (nivå === '4' && nøkkel === 'LIKESTILT') {
        const perioder = svar.perioder ?? [];
        if (perioder.length === 0) feil.perioder = 'Legg til minst én periode';
        perioder.forEach((p, i) => {
            if (!erGyldigDato(p.fom)) feil[`perioder.${i}.fom`] = 'Fyll inn dato';
            if (!erGyldigDato(p.tom)) feil[`perioder.${i}.tom`] = 'Fyll inn dato';
            if (erGyldigDato(p.fom) && erGyldigDato(p.tom) && p.fom > p.tom) feil[`perioder.${i}.tom`] = 'Til og med må være etter fra og med';
            if (p.type === 'FORELDREPENGER' && !p.opptjentPåAap) feil[`perioder.${i}.opptjentPåAap`] = 'Svar ja eller nei';
            if (p.type === 'FORELDREPENGER' && p.opptjentPåAap === 'JA' && !p.yrkesaktivFørUttak) feil[`perioder.${i}.yrkesaktivFørUttak`] = 'Svar ja eller nei';
        });
    }
    if (!svar.begrunnelse?.trim()) feil.begrunnelse = 'Fyll inn begrunnelse';
    if (Object.keys(feil).length === 0 && nøkkel === 'LIKESTILT' && nivå === '4' && !utledNivå4(scenario, svar)) {
        feil.perioder = 'Kan ikke utlede utfall';
    }
    return feil;
}

function utfallForLagring(nivå, nøkkel, svar, scenario) {
    if (nøkkel === 'LIKESTILT' && nivå === '2') return utledNivå2(svar)?.utfall;
    if (nøkkel === 'LIKESTILT' && nivå === '2L') return utledLett(svar)?.utfall;
    if (nøkkel === 'LIKESTILT' && nivå === '4') return utledNivå4(scenario, svar)?.utfall;
    return svar.utfall;
}
