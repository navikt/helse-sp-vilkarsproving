'use strict';

const state = {
    scenarioId: SCENARIOER[0].id,
    nivå: '2',
    aktiv: null,
    utkast: {},
    feil: {},
    lagringsmodell: 'punktum',
    vurderinger: { 1: {}, 2: {}, '2L': {}, 3: {}, 4: {} },
    lesmer: {},
};

function scenario() {
    return SCENARIOER.find((s) => s.id === state.scenarioId);
}

function vurderingerForNivå() {
    return state.vurderinger[state.nivå];
}

function ytelsesvalg() {
    return Object.entries(YTELSER).map(([verdi, y]) => ({ verdi, tekst: `${y.navn} (kap. ${y.kapittel})` }));
}

function ytelsesnavn(liste = []) {
    return liste.map((y) => YTELSER[y]?.navn ?? y).join(', ') || '–';
}

function jaNeiTekst(verdi) {
    return verdi === 'JA' ? 'Ja' : verdi === 'NEI' ? 'Nei' : '–';
}

function utfallstekst(utfall) {
    return { OPPFYLT: 'Oppfylt', IKKE_OPPFYLT: 'Ikke oppfylt' }[utfall] ?? 'Ikke vurdert';
}

/* ---------- Lovtekst og saksvalg ---------- */

function renderLovtekst() {
    const innhold = `<div class="vstack gap-8">
        <p><strong>Første ledd:</strong> For å få rett til sykepenger må medlemmet ha vært i arbeid i minst fire uker umiddelbart før han eller hun ble arbeidsufør (opptjeningstid). Fravær i et arbeidsforhold uten gyldig grunn regnes ikke med i opptjeningstiden.</p>
        <p><strong>Andre ledd, første punktum:</strong> Likestilt med forutgående arbeid er tidsrom da medlemmet har mottatt en ytelse til livsopphold etter kapittel 4, 8, 9 eller 14.</p>
        <p><strong>Andre ledd, andre punktum:</strong> Foreldrepenger etter kapittel 14 opptjent på grunnlag av arbeidsavklaringspenger etter kapittel 11 likestilles likevel ikke med arbeid.</p>
        <p><strong>Andre ledd, tredje punktum:</strong> Dersom medlemmet har vært sammenhengende yrkesaktiv (eller mottatt ytelse etter kapittel 8, 9 eller 14) i minst fire uker umiddelbart før uttaket av foreldrepenger starter, kan det likevel gis rett til sykepenger.</p>
        <p><a class="aksel-link" href="https://lovdata.no/nav/folketrygdloven/kap8/%C2%A78-2">Folketrygdloven § 8-2 med rundskriv på Lovdata</a></p>
    </div>`;
    document.getElementById('lovtekst').innerHTML = lesMer({
        id: 'lesmer-lovtekst',
        tittel: 'Lovteksten, delt opp i punktum',
        åpen: !!state.lesmer['lesmer-lovtekst'],
        innhold,
    });
}

function renderScenariovelger() {
    const s = scenario();
    const ytelsesrader = s.ytelser
        .map(
            (y) => `<dt>${esc(YTELSER[y.type].navn)}</dt><dd>${datoNorsk(y.fom)} – ${datoNorsk(y.tom)}${
                y.type === 'FORELDREPENGER'
                    ? `<br><span class="aksel-typo--color-subtle">Opptjent på ${y.opptjentPåAap ? 'AAP' : 'arbeid'} · Før uttak: ${esc(y.førUttak)}</span>`
                    : ''
            }</dd>`,
        )
        .join('');
    const fasit = `<p><strong>${utfallstekst(s.fasit.utfall)}</strong> – ${esc(LOVREFERANSER[s.fasit.punktum].tekst)}.</p><p>${esc(s.fasit.forklaring)}</p>`;
    document.getElementById('scenariovelger').innerHTML = `<div class="kort saksopplysninger">
        <div class="vstack gap-12">
            ${nedtrekk({
                id: 'scenario',
                felt: 'scenario',
                label: 'Sak',
                beskrivelse: 'Bytter du sak, nullstilles alle vurderinger.',
                verdi: state.scenarioId,
                valg: SCENARIOER.map((sc) => ({ verdi: sc.id, tekst: sc.navn })),
            })}
        </div>
        <div class="vstack gap-8">
            <p class="aksel-body-short aksel-body-short--small">${esc(s.beskrivelse)}</p>
            <dl class="opplysningsliste aksel-body-short aksel-body-short--small">
                <dt>Skjæringstidspunkt</dt><dd>${datoNorsk(SKJÆRINGSTIDSPUNKT)}</dd>
                <dt>Arbeid</dt><dd>${s.arbeidFom ? `Fra ${datoNorsk(s.arbeidFom)} (${arbeidsdager(s)} dager)` : 'Ingen arbeidsforhold'}</dd>
                ${ytelsesrader}
            </dl>
            ${lesMer({ id: 'lesmer-fasit', tittel: 'Vis fasit for saken', åpen: !!state.lesmer['lesmer-fasit'], innhold: fasit })}
        </div>
    </div>`;
}

function renderFaner() {
    const nivå = NIVÅER.find((n) => n.id === state.nivå);
    document.getElementById('nivafaner').innerHTML = `${faner({
        id: 'niva',
        valgt: state.nivå,
        faner: NIVÅER.map((n) => ({ id: n.id, tekst: n.navn })),
    })}
    <p class="aksel-body-long nivabeskrivelse">${esc(nivå.beskrivelse)}</p>`;
}

/* ---------- Vilkårsrader ---------- */

function utfallsikonForRad(utfall) {
    if (utfall === 'OPPFYLT') return ikon('oppfyltOmriss', 'Oppfylt');
    if (utfall === 'IKKE_OPPFYLT') return ikon('ikkeOppfylt', 'Ikke oppfylt');
    return ikon('ikkeVurdert', 'Ikke vurdert');
}

function utfallsikonForOverskrift(utfall) {
    if (utfall === 'OPPFYLT') return ikon('oppfylt', 'Oppfylt', 'ikon-oppfylt');
    if (utfall === 'IKKE_OPPFYLT') return ikon('ikkeOppfylt', 'Ikke oppfylt');
    return ikon('ikkeVurdert', 'Ikke vurdert', 'ikon-ikke-vurdert');
}

function detaljliste(par) {
    const rader = par
        .filter(([, verdi]) => verdi !== undefined && verdi !== null && verdi !== '')
        .map(
            ([label, verdi]) =>
                `<dt class="aksel-body-short aksel-body-short--small">${esc(label)}</dt><dd class="aksel-body-short aksel-body-short--small">${esc(verdi)}</dd>`,
        )
        .join('');
    return rader ? `<dl class="detaljer">${rader}</dl>` : '';
}

function vurdertTekst(lagret) {
    return `Vurdert ${tidspunktNorsk(lagret.tidspunkt)} – ${SAKSBEHANDLER}`;
}

function vilkårsrad({ nøkkel, utfall, tags, detaljer = '', under = false, vurderbar = true, navn }) {
    const erAktiv = state.aktiv === nøkkel;
    const lov = VILKÅR[nøkkel] ? LOVREFERANSER[VILKÅR[nøkkel].lovreferanse].tekst : '';
    return `<li class="vilkarsrad vstack gap-8${erAktiv ? ' vilkarsrad--aktiv' : ''}${under ? ' vilkarsrad--under' : ''}" aria-labelledby="navn-${nøkkel}">
        <div class="hstack nowrap gap-8">
            <span class="ikonplass">${utfallsikonForRad(utfall)}</span>
            <div class="vstack">
                <p id="navn-${nøkkel}" class="aksel-body-short aksel-typo--semibold">${esc(navn ?? VILKÅR[nøkkel].navn)}</p>
                ${lov ? `<p class="aksel-body-short aksel-body-short--small aksel-typo--color-subtle">${esc(lov)}</p>` : ''}
            </div>
            <span class="spacer"></span>
            ${vurderbar ? knapp('Vurder vilkår', { id: `vurder-${nøkkel}`, 'data-handling': 'vurder', 'data-nokkel': nøkkel, 'aria-expanded': erAktiv ? 'true' : 'false' }) : ''}
        </div>
        <div class="hstack gap-8">${tags.join('')}</div>
        ${detaljer}
    </li>`;
}

function hovedregelrad(resultat) {
    const s = scenario();
    const lagret = vurderingerForNivå().HOVEDREGEL;
    const tags = [tag(lagret ? vurdertTekst(lagret) : 'Vurdert automatisk 09.04.2025 kl. 12.03')];
    if (resultat.avgjørende === 'HOVEDREGEL') tags.push(tag('Avgjørende vilkår'));
    const detaljer = lagret
        ? detaljliste([['Begrunnelse', lagret.svar.begrunnelse]])
        : detaljliste([['Opptjening fra', s.arbeidFom ? `${datoNorsk(s.arbeidFom)} (${arbeidsdager(s)} dager)` : 'Ingen arbeidsforhold']]);
    return vilkårsrad({ nøkkel: 'HOVEDREGEL', utfall: resultat.hovedregelUtfall, tags, detaljer });
}

function likestiltDetaljer(nivå, lagret) {
    const { svar } = lagret;
    if (nivå === '1') return detaljliste([['Begrunnelse', svar.begrunnelse]]);
    if (nivå === '2') {
        const utledet = utledNivå2(svar);
        return detaljliste([
            ['Ytelse', svar.ytelser ? ytelsesnavn(svar.ytelser) : null],
            ['Minst 4 uker sammenlagt', jaNeiTekst(svar.sammenlagt)],
            ['Foreldrepenger opptjent på AAP', svar.fpAap ? jaNeiTekst(svar.fpAap) : null],
            ['Yrkesaktiv 4 uker før uttak', svar.yrkesaktiv ? jaNeiTekst(svar.yrkesaktiv) : null],
            ['Hjemmel', LOVREFERANSER[utledet.punktum].tekst],
            ['Begrunnelse', svar.begrunnelse],
        ]);
    }
    if (nivå === '2L') {
        const utledet = utledLett(svar);
        return detaljliste([
            ['Arbeid og/eller ytelser i 4 uker', jaNeiTekst(svar.arbeidOgYtelser)],
            ['FP opptjent på AAP ville gitt 4 uker', svar.fpAapGirFireUker ? jaNeiTekst(svar.fpAapGirFireUker) : null],
            ['Yrkesaktiv 4 uker før FP-uttaket', svar.yrkesaktivFørUttak ? jaNeiTekst(svar.yrkesaktivFørUttak) : null],
            ['Vilkår 2', utfallstekst(svar.arbeidOgYtelser === 'JA' ? 'OPPFYLT' : 'IKKE_OPPFYLT')],
            ['Vilkår 3', svar.fpAapGirFireUker === 'JA' ? utfallstekst(svar.yrkesaktivFørUttak === 'JA' ? 'OPPFYLT' : 'IKKE_OPPFYLT') : 'Ikke vurdert'],
            ['Hjemmel', `${utledet.hjemmeltekst} (kombinasjon ${utledet.kombinasjon})`],
            ['Begrunnelse', svar.begrunnelse],
        ]);
    }
    const utledet = utledNivå4(scenario(), svar);
    const perioder = svar.perioder
        .map((p) => `${YTELSER[p.type].navn} ${datoNorsk(p.fom)}–${datoNorsk(p.tom)}${p.type === 'FORELDREPENGER' && p.opptjentPåAap === 'JA' ? ' (opptjent på AAP)' : ''}`)
        .join('; ');
    return detaljliste([
        ['Opptjening fra', utledet.fom ? `${datoNorsk(utledet.fom)} (${utledet.dager} dager)` : 'Ingen'],
        ['Likestilte perioder', perioder],
        ['Hjemmel', LOVREFERANSER[utledet.punktum].tekst],
        ['Begrunnelse', svar.begrunnelse],
    ]);
}

function likestiltRader(resultat) {
    const nivå = state.nivå;
    const vurderinger = vurderingerForNivå();

    if (nivå !== '3') {
        const lagret = vurderinger.LIKESTILT;
        const tags = [tag(lagret ? vurdertTekst(lagret) : 'Ikke vurdert')];
        if (resultat.avgjørende === 'LIKESTILT') tags.push(tag('Avgjørende vilkår'));
        return vilkårsrad({
            nøkkel: 'LIKESTILT',
            navn: nivå === '2L' ? 'Arbeid og likestilte ytelser' : undefined,
            utfall: lagret?.utfall,
            tags,
            detaljer: lagret ? likestiltDetaljer(nivå, lagret) : '',
        });
    }

    const gruppeUtfall = resultat.likestilt?.utfall;
    const gruppe = vilkårsrad({
        nøkkel: 'LIKESTILT',
        navn: 'Likestilt ytelse',
        utfall: gruppeUtfall,
        vurderbar: false,
        tags: [tag(gruppeUtfall ? `Utledet: ${utfallstekst(gruppeUtfall)}` : 'Ikke ferdig vurdert')],
        detaljer: `<p class="aksel-body-short aksel-body-short--small aksel-typo--color-subtle">Utledes fra delvilkårene under. Andre og tredje punktum vises bare når de er aktuelle.</p>`,
    });
    const underrader = nivå3SynligeRader(vurderinger)
        .map((nøkkel) => {
            const lagret = vurderinger[nøkkel];
            const tags = [tag(lagret ? vurdertTekst(lagret) : 'Ikke vurdert')];
            if (resultat.avgjørende === nøkkel) tags.push(tag('Avgjørende vilkår'));
            const detaljer = lagret
                ? detaljliste([
                      ['Ytelse', nøkkel === 'L2P1' && lagret.utfall === 'OPPFYLT' ? ytelsesnavn(lagret.svar.ytelser) : null],
                      ['Begrunnelse', lagret.svar.begrunnelse],
                  ])
                : '';
            return vilkårsrad({ nøkkel, utfall: lagret?.utfall, tags, detaljer, under: true });
        })
        .join('');
    return gruppe + underrader;
}

/* ---------- Skjema ---------- */

function begrunnelseOgKnapper() {
    return `${tekstområde({ id: 'begrunnelse', felt: 'begrunnelse', verdi: state.utkast.begrunnelse, feil: state.feil.begrunnelse })}
    <div class="hstack gap-8">
        ${knapp('Lagre', { variant: 'primary', 'data-handling': 'lagre' })}
        ${knapp('Avbryt', { variant: 'tertiary', 'data-handling': 'avbryt' })}
    </div>`;
}

function utledetBoks(utledet, tomTekst) {
    if (!utledet) return `<div class="utledet aksel-body-short aksel-body-short--small">${esc(tomTekst)}</div>`;
    return `<div class="utledet vstack gap-4" aria-live="polite">
        <p class="aksel-label aksel-label--small">Utfall: ${esc(utfallstekst(utledet.utfall))}</p>
        <p class="aksel-body-short aksel-body-short--small">Hjemmel: ${esc(utledet.hjemmeltekst ?? LOVREFERANSER[utledet.punktum].tekst)}${
            utledet.dager !== undefined ? ` · ${utledet.dager} opptjeningsdager` : ''
        }${utledet.kombinasjon ? ` · kombinasjon ${utledet.kombinasjon}` : ''}</p>
    </div>`;
}

function lettSkjema() {
    const u = state.utkast;
    const spørsmål3 =
        u.arbeidOgYtelser === 'NEI'
            ? radiogruppe({
                  id: 'fpAapGirFireUker',
                  felt: 'fpAapGirFireUker',
                  legend: '3. Ville det blitt 4 uker hvis foreldrepenger opptjent på AAP var regnet med?',
                  beskrivelse: 'Svarer du nei, blir vilkår 3 ikke vurdert, og saken får avslag.',
                  valg: JA_NEI,
                  verdi: u.fpAapGirFireUker,
                  feil: state.feil.fpAapGirFireUker,
              })
            : '';
    const spørsmål4 =
        u.fpAapGirFireUker === 'JA'
            ? radiogruppe({
                  id: 'yrkesaktivFørUttak',
                  felt: 'yrkesaktivFørUttak',
                  legend: '4. Var søkeren yrkesaktiv, eller mottok ytelse etter kapittel 8, 9 eller 14, i minst 4 uker før uttaket av foreldrepenger startet?',
                  beskrivelse: 'Vilkår 3, andre ledd tredje punktum. Dagpenger teller ikke.',
                  valg: JA_NEI,
                  verdi: u.yrkesaktivFørUttak,
                  feil: state.feil.yrkesaktivFørUttak,
              })
            : '';
    const oppfølging = spørsmål3 || spørsmål4 ? `<div class="underspørsmål vstack gap-20">${spørsmål3}${spørsmål4}</div>` : '';
    return `<p class="aksel-body-short aksel-body-short--small">Spørsmål 1 (vilkår 1) er raden «Arbeid i minst 4 uker».</p>
    ${radiogruppe({
        id: 'arbeidOgYtelser',
        felt: 'arbeidOgYtelser',
        legend: '2. Har søkeren hatt arbeid og/eller ytelser etter kapittel 4, 8, 9 eller 14 i minst 4 uker umiddelbart før arbeidsuførheten?',
        beskrivelse: 'Vilkår 2. Foreldrepenger opptjent på AAP skal ikke regnes med her.',
        valg: JA_NEI,
        verdi: u.arbeidOgYtelser,
        feil: state.feil.arbeidOgYtelser,
    })}
    ${oppfølging}
    ${utledetBoks(utledLett(u), 'Svar på spørsmålene for å se utfallet.')}
    ${begrunnelseOgKnapper()}`;
}

function enkeltSkjema(nøkkel) {
    const u = state.utkast;
    const beskrivelser = {
        L2P2: 'Andre punktum: foreldrepenger opptjent på grunnlag av AAP likestilles ikke med arbeid. Oppfylt betyr at foreldrepengene er opptjent på annet grunnlag.',
        L2P3: 'Tredje punktum er et unntak fra andre punktum. Likestilt med yrkesaktivitet er sykepenger, ytelser etter kapittel 9 og svangerskapspenger opptjent ved yrkesaktivitet. Dagpenger teller ikke.',
    };
    let ekstra = '';
    if (nøkkel === 'L2P1' && u.utfall === 'OPPFYLT') {
        ekstra = `<div class="underspørsmål">${avkrysningsgruppe({
            id: 'ytelser',
            felt: 'ytelser',
            legend: 'Hvilke ytelser inngår i opptjeningen?',
            beskrivelse: 'Velger du foreldrepenger, må andre punktum også vurderes.',
            valg: ytelsesvalg(),
            verdier: u.ytelser ?? [],
            feil: state.feil.ytelser,
        })}</div>`;
    }
    return `${radiogruppe({
        id: 'utfall',
        felt: 'utfall',
        legend: VILKÅR[nøkkel].spørsmål,
        beskrivelse: beskrivelser[nøkkel],
        valg: UTFALLSVALG,
        verdi: u.utfall,
        feil: state.feil.utfall,
    })}${ekstra}${begrunnelseOgKnapper()}`;
}

function nivå2Skjema() {
    const u = state.utkast;
    const harFp = (u.ytelser ?? []).includes('FORELDREPENGER');
    let underspørsmål = '';
    if (u.sammenlagt === 'JA') {
        underspørsmål = `<div class="underspørsmål vstack gap-20">
            ${avkrysningsgruppe({
                id: 'ytelser',
                felt: 'ytelser',
                legend: 'Hvilke likestilte ytelser har søkeren mottatt?',
                valg: ytelsesvalg(),
                verdier: u.ytelser ?? [],
                feil: state.feil.ytelser,
            })}
            ${
                harFp
                    ? radiogruppe({
                          id: 'fpAap',
                          felt: 'fpAap',
                          legend: 'Er foreldrepengene opptjent på grunnlag av arbeidsavklaringspenger?',
                          beskrivelse: 'Andre punktum. Se opptjeningen i fpsak.',
                          valg: JA_NEI,
                          verdi: u.fpAap,
                          feil: state.feil.fpAap,
                      })
                    : ''
            }
            ${
                harFp && u.fpAap === 'JA'
                    ? radiogruppe({
                          id: 'yrkesaktiv',
                          felt: 'yrkesaktiv',
                          legend: 'Var søkeren sammenhengende yrkesaktiv, eller mottok ytelse etter kapittel 8, 9 eller 14, i minst 4 uker umiddelbart før uttaket av foreldrepenger startet?',
                          beskrivelse: 'Tredje punktum. Dagpenger teller ikke. Svangerskapspenger må være opptjent ved yrkesaktivitet.',
                          valg: JA_NEI,
                          verdi: u.yrkesaktiv,
                          feil: state.feil.yrkesaktiv,
                      })
                    : ''
            }
        </div>`;
    }
    return `${radiogruppe({
        id: 'sammenlagt',
        felt: 'sammenlagt',
        legend: 'Har søkeren hatt arbeid og/eller likestilt ytelse i minst 4 uker umiddelbart før arbeidsuførheten?',
        beskrivelse: 'Arbeid og ytelser legges sammen. Det må ikke være opphold.',
        valg: JA_NEI,
        verdi: u.sammenlagt,
        feil: state.feil.sammenlagt,
    })}
    ${underspørsmål}
    ${utledetBoks(utledNivå2(u), 'Svar på spørsmålene for å se utfallet.')}
    ${begrunnelseOgKnapper()}`;
}

function periodeskjema(periode, i) {
    const f = (felt) => state.feil[`perioder.${i}.${felt}`];
    const fp =
        periode.type === 'FORELDREPENGER'
            ? `<div class="hel vstack gap-12">
                ${radiogruppe({ id: `p${i}-aap`, felt: `perioder.${i}.opptjentPåAap`, legend: 'Opptjent på grunnlag av AAP?', valg: JA_NEI, verdi: periode.opptjentPåAap, feil: f('opptjentPåAap') })}
                ${
                    periode.opptjentPåAap === 'JA'
                        ? radiogruppe({ id: `p${i}-yrkesaktiv`, felt: `perioder.${i}.yrkesaktivFørUttak`, legend: 'Yrkesaktiv eller ytelse etter kap. 8, 9 eller 14 i minst 4 uker før uttaket?', valg: JA_NEI, verdi: periode.yrkesaktivFørUttak, feil: f('yrkesaktivFørUttak') })
                        : ''
                }
            </div>`
            : '';
    return `<div class="periodekort vstack gap-8">
        <div class="periodefelter">
            <div class="hel">${nedtrekk({ id: `p${i}-type`, felt: `perioder.${i}.type`, label: 'Ytelse', verdi: periode.type, valg: ytelsesvalg() })}</div>
            ${tekstfelt({ id: `p${i}-fom`, felt: `perioder.${i}.fom`, label: 'Fra og med', type: 'date', verdi: periode.fom, feil: f('fom') })}
            ${tekstfelt({ id: `p${i}-tom`, felt: `perioder.${i}.tom`, label: 'Til og med', type: 'date', verdi: periode.tom, feil: f('tom') })}
            ${fp}
        </div>
        <div>${knapp('Fjern periode', { variant: 'tertiary', size: 'xsmall', 'data-handling': 'fjern-periode', 'data-indeks': i })}</div>
    </div>`;
}

function tidslinjeHtml(perioder) {
    const dager = tidslinje(scenario(), perioder);
    const celler = dager
        .map((d) => `<span class="dag-${d.type}${d.iOpptjeningsvindu ? '' : ' dag-utenfor'}" title="${datoNorsk(d.iso)}: ${d.type}"></span>`)
        .join('');
    return `<div class="vstack gap-4">
        <p class="aksel-label aksel-label--small">Siste 8 uker før skjæringstidspunktet</p>
        <div class="tidslinje" aria-hidden="true">${celler}</div>
        <div class="hstack aksel-body-short aksel-body-short--small" style="justify-content: space-between"><span>${datoNorsk(dager[0].iso)}</span><span>${datoNorsk(dager.at(-1).iso)}</span></div>
        <div class="tegnforklaring aksel-body-short aksel-body-short--small">
            <span class="dag-arbeid">Arbeid</span><span class="dag-ytelse">Likestilt ytelse</span><span class="dag-ikke-tellende">Teller ikke (2. pkt.)</span><span class="dag-ingen">Ingenting</span>
        </div>
    </div>`;
}

function nivå4Skjema() {
    const perioder = state.utkast.perioder ?? [];
    return `<div class="vstack gap-8">
        <p class="aksel-label aksel-label--small">Perioder med likestilt ytelse</p>
        <p class="aksel-body-short aksel-body-short--small">Arbeidsforholdene hentes fra Aa-registeret som i dag. Registrer periodene med ytelse, så teller vi opptjeningsdagene.</p>
        ${perioder.map(periodeskjema).join('')}
        ${feilmelding('perioder-feil', state.feil.perioder)}
        <div class="hstack gap-8">
            ${knapp('Legg til periode', { variant: 'tertiary', size: 'xsmall', ikonNavn: 'pluss', 'data-handling': 'legg-til-periode' })}
            ${knapp('Hent fra kildesystemer (simulert)', { variant: 'tertiary', size: 'xsmall', 'data-handling': 'hent-perioder' })}
        </div>
    </div>
    ${tidslinjeHtml(perioder)}
    ${utledetBoks(perioder.length ? utledNivå4(scenario(), { perioder }) : null, 'Legg til perioder for å se utfallet.')}
    ${begrunnelseOgKnapper()}`;
}

function skjema() {
    const nøkkel = state.aktiv;
    if (nøkkel === 'LIKESTILT' && state.nivå === '2') return nivå2Skjema();
    if (nøkkel === 'LIKESTILT' && state.nivå === '2L') return lettSkjema();
    if (nøkkel === 'LIKESTILT' && state.nivå === '4') return nivå4Skjema();
    return enkeltSkjema(nøkkel);
}
