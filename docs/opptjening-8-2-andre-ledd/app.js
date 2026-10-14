'use strict';

function svarmerke(svar) {
    const varianter = { ja: ['Ja', 'success'], delvis: ['Delvis', 'warning'], fritekst: ['Bare fritekst', 'warning'], nei: ['Nei', 'danger'] };
    const [tekst, farge] = varianter[svar];
    return tag(tekst, farge);
}

function fasitsammenligning(resultat) {
    const s = scenario();
    const likestilt = resultat.likestilt;
    let merke;
    if (resultat.avgjørende === 'HOVEDREGEL') {
        merke = tag('Hovedregelen er overstyrt', 'warning');
    } else if (!likestilt) {
        merke = tag('Ikke ferdig vurdert', 'neutral');
    } else if (likestilt.utfall !== s.fasit.utfall) {
        merke = tag('Feil utfall', 'danger');
    } else if (likestilt.punktum === 'L2') {
        merke = tag('Riktig utfall, punktum ikke sporet', 'warning');
    } else if (likestilt.punktum !== s.fasit.punktum) {
        merke = tag('Riktig utfall, annet punktum', 'warning');
    } else {
        merke = tag('Stemmer med fasit', 'success');
    }
    const lover = subsumerteLovreferanser(state.nivå, s, vurderingerForNivå(), state.lagringsmodell);
    return `<div class="kort vstack gap-12">
        <h3 class="aksel-heading aksel-heading--xsmall">Resultat mot fasit</h3>
        <dl class="opplysningsliste aksel-body-short aksel-body-short--small">
            <dt>Fasit</dt><dd>${utfallstekst(s.fasit.utfall)} – ${esc(LOVREFERANSER[s.fasit.punktum].tekst)}</dd>
            <dt>Skissen gir</dt><dd>${utfallstekst(resultat.utfall)}${likestilt ? ` – ${esc(likestilt.hjemmeltekst ?? LOVREFERANSER[likestilt.punktum].tekst)}` : ''}</dd>
        </dl>
        <div>${merke}</div>
        <div class="vstack gap-4">
            <p class="aksel-label aksel-label--small">Lovreferanser som lagres</p>
            <ul class="aksel-body-short aksel-body-short--small punktliste">${lover.map((l) => `<li>${esc(l)}</li>`).join('')}</ul>
        </div>
    </div>`;
}

function etterkantkort() {
    const indeks = NIVÅER.findIndex((n) => n.id === state.nivå);
    const rader = SPORINGSSPØRSMÅL.map((r) => `<tr><td>${esc(r.spørsmål)}</td><td>${svarmerke(r.svar[indeks])}</td></tr>`).join('');
    return `<div class="kort vstack gap-12">
        <h3 class="aksel-heading aksel-heading--xsmall">Hva kan vi svare på i etterkant?</h3>
        <table class="tabell aksel-body-short aksel-body-short--small">
            <thead><tr><th scope="col">Spørsmål</th><th scope="col">${esc(SAMMENLIGNING.kolonner[indeks])}</th></tr></thead>
            <tbody>${rader}</tbody>
        </table>
    </div>`;
}

function sporingskort() {
    const objekt = sporingsobjekt(state.nivå, scenario(), vurderingerForNivå(), state.lagringsmodell);
    const modellvalg =
        state.nivå === '2'
            ? radiogruppe({
                  id: 'lagringsmodell',
                  felt: 'lagringsmodell',
                  legend: 'Lagringsmodell',
                  valg: [
                      { verdi: 'svar', tekst: 'Én vurdering med svar', beskrivelse: 'OPPTJENING_LIKESTILT_YTELSE med svarene og anvendte lovreferanser i kilden.' },
                      { verdi: 'punktum', tekst: 'Én vurdering per punktum', beskrivelse: 'Samme skjema, men lagres som nivå 3. Det er dette jeg foreslår.' },
                  ],
                  verdi: state.lagringsmodell,
              })
            : '';
    return `<div class="kort vstack gap-12">
        <h3 class="aksel-heading aksel-heading--xsmall">Sporing – dette lagres i sp-vilkarsproving</h3>
        ${modellvalg}
        <pre class="json" tabindex="0" aria-label="Opptjeningsvurdering som JSON">${esc(JSON.stringify(objekt, null, 2))}</pre>
        <p class="aksel-body-short aksel-body-short--small aksel-typo--color-subtle">Forenklet. Feltene følger Vilkårsvurdering, Vurderingskilde og Lovreferanse i domenet. Nye felter er forslag.</p>
    </div>`;
}

function renderMockup() {
    const resultat = totalResultat(state.nivå, scenario(), vurderingerForNivå());
    const panel = state.aktiv
        ? `<span class="vurderingskant"></span>
           <section class="vurderingspanel vstack gap-20" aria-label="Vurder ${esc(state.aktiv === 'LIKESTILT' ? VILKÅR.LIKESTILT.navn : VILKÅR[state.aktiv].navn)}">${skjema()}</section>`
        : '';
    document.getElementById('mockup').innerHTML = `<div class="vstack gap-24">
        <div class="speil-flate vstack gap-16" id="niva-panel" role="tabpanel" aria-labelledby="niva-tab-${state.nivå}">
            <div class="vstack gap-4">
                <p class="aksel-body-short">Inngangsvilkår ved skjæringstidspunktet ${datoNorsk(SKJÆRINGSTIDSPUNKT)}</p>
                <h3 class="aksel-heading aksel-heading--medium">Inngangsvilkår</h3>
            </div>
            <div class="hstack nowrap align-start gap-0">
                <div class="vilkarskolonne vstack">
                    <section class="vilkarsgruppe vstack gap-4" aria-labelledby="opptjening-tittel">
                        <div class="hstack gap-12">
                            <span class="ikonplass">${utfallsikonForOverskrift(resultat.utfall)}</span>
                            <h4 id="opptjening-tittel" class="aksel-heading aksel-heading--xsmall">Opptjeningstid</h4>
                        </div>
                        <ul class="vilkarsliste">
                            ${hovedregelrad(resultat)}
                            ${likestiltRader(resultat)}
                        </ul>
                    </section>
                </div>
                ${panel}
            </div>
        </div>
        <div class="sporing">
            ${sporingskort()}
            <div class="vstack gap-24">
                ${fasitsammenligning(resultat)}
                ${etterkantkort()}
            </div>
        </div>
    </div>`;
}

function renderSammenligning() {
    const hode = `<tr><th scope="col">Egenskap</th>${SAMMENLIGNING.kolonner.map((k) => `<th scope="col">${esc(k)}</th>`).join('')}</tr>`;
    const faste = SAMMENLIGNING.rader.map(([navn, ...celler]) => `<tr><th scope="row">${esc(navn)}</th>${celler.map((c) => `<td>${esc(c)}</td>`).join('')}</tr>`);
    const sporing = SPORINGSSPØRSMÅL.map((r) => `<tr><th scope="row">${esc(r.spørsmål)}</th>${r.svar.map((s) => `<td>${svarmerke(s)}</td>`).join('')}</tr>`);
    document.getElementById('sammenligning').innerHTML = `<div class="kort tabellramme">
        <table class="tabell aksel-body-short aksel-body-short--small">
            <caption class="visuelt-skjult">Sammenligning av nivåene</caption>
            <thead>${hode}</thead>
            <tbody>${[...faste, ...sporing].join('')}</tbody>
        </table>
    </div>`;
}

function render() {
    const fokusId = document.activeElement?.id;
    renderLovtekst();
    renderScenariovelger();
    renderFaner();
    renderMockup();
    if (fokusId) document.getElementById(fokusId)?.focus();
}

/* ---------- Hendelser ---------- */

function settVerdi(objekt, sti, verdi) {
    const deler = sti.split('.');
    let mål = objekt;
    deler.slice(0, -1).forEach((del) => {
        mål = mål[del];
    });
    mål[deler.at(-1)] = verdi;
}

function åpneSkjema(nøkkel) {
    const lagret = vurderingerForNivå()[nøkkel];
    state.aktiv = nøkkel;
    state.feil = {};
    state.utkast = lagret ? structuredClone(lagret.svar) : nøkkel === 'LIKESTILT' && state.nivå === '4' ? { perioder: [] } : {};
    render();
    document.querySelector('.vurderingspanel input, .vurderingspanel select')?.focus();
}

function lukkSkjema() {
    const nøkkel = state.aktiv;
    state.aktiv = null;
    state.utkast = {};
    state.feil = {};
    render();
    document.getElementById(`vurder-${nøkkel}`)?.focus();
}

function ryddUtkast(nivå, nøkkel, utkast) {
    if (nivå === '2') {
        const harFp = (utkast.ytelser ?? []).includes('FORELDREPENGER');
        if (utkast.sammenlagt !== 'JA' || !harFp) {
            delete utkast.fpAap;
            delete utkast.yrkesaktiv;
        }
        if (utkast.fpAap !== 'JA') delete utkast.yrkesaktiv;
    }
    if (nivå === '2L') {
        if (utkast.arbeidOgYtelser !== 'NEI') delete utkast.fpAapGirFireUker;
        if (utkast.fpAapGirFireUker !== 'JA') delete utkast.yrkesaktivFørUttak;
    }
    if (nivå === '3' && nøkkel === 'L2P1' && utkast.utfall !== 'OPPFYLT') delete utkast.ytelser;
    if (nivå === '4') {
        (utkast.perioder ?? []).forEach((p) => {
            if (p.type !== 'FORELDREPENGER') {
                delete p.opptjentPåAap;
                delete p.yrkesaktivFørUttak;
            }
            if (p.opptjentPåAap !== 'JA') delete p.yrkesaktivFørUttak;
        });
    }
}

function lagre() {
    const nøkkel = state.aktiv;
    const svar = structuredClone(state.utkast);
    ryddUtkast(state.nivå, nøkkel, svar);
    const feil = validerSkjema(state.nivå, nøkkel, svar, scenario());
    if (Object.keys(feil).length > 0) {
        state.feil = feil;
        render();
        const førsteFeil = document.querySelector('.vurderingspanel [aria-invalid="true"], .vurderingspanel .aksel-fieldset--error input');
        førsteFeil?.focus();
        return;
    }
    const vurderinger = vurderingerForNivå();
    vurderinger[nøkkel] = { svar, utfall: utfallForLagring(state.nivå, nøkkel, svar, scenario()), tidspunkt: new Date() };
    if (state.nivå === '3') {
        const synlige = nivå3SynligeRader(vurderinger);
        ['L2P2', 'L2P3'].forEach((k) => {
            if (!synlige.includes(k)) delete vurderinger[k];
        });
    }
    lukkSkjema();
}

function hentPerioderFraScenario() {
    state.utkast.perioder = scenario().ytelser.map((y) => ({
        type: y.type,
        fom: y.fom,
        tom: y.tom,
        ...(y.type === 'FORELDREPENGER'
            ? {
                  opptjentPåAap: y.opptjentPåAap ? 'JA' : 'NEI',
                  ...(y.opptjentPåAap ? { yrkesaktivFørUttak: y.yrkesaktivFørUttak ? 'JA' : 'NEI' } : {}),
              }
            : {}),
    }));
    state.feil = {};
    render();
}

function håndterKlikk(event) {
    const mål = event.target.closest('[data-handling]');
    if (!mål) return;
    const handling = mål.dataset.handling;
    if (handling === 'vurder') {
        if (state.aktiv === mål.dataset.nokkel) lukkSkjema();
        else åpneSkjema(mål.dataset.nokkel);
    } else if (handling === 'lagre') {
        lagre();
    } else if (handling === 'avbryt') {
        lukkSkjema();
    } else if (handling === 'fane') {
        state.nivå = mål.dataset.fane;
        state.aktiv = null;
        state.utkast = {};
        state.feil = {};
        render();
        document.getElementById(`niva-tab-${state.nivå}`)?.focus();
    } else if (handling === 'lesmer') {
        state.lesmer[mål.dataset.lesmer] = !state.lesmer[mål.dataset.lesmer];
        render();
    } else if (handling === 'legg-til-periode') {
        state.utkast.perioder = [...(state.utkast.perioder ?? []), { type: 'SYKEPENGER', fom: '', tom: '' }];
        render();
        document.getElementById(`p${state.utkast.perioder.length - 1}-type`)?.focus();
    } else if (handling === 'fjern-periode') {
        state.utkast.perioder.splice(Number(mål.dataset.indeks), 1);
        state.feil = {};
        render();
    } else if (handling === 'hent-perioder') {
        hentPerioderFraScenario();
    }
}

function håndterEndring(event) {
    const felt = event.target.dataset?.felt;
    if (!felt) return;
    const verdi = event.target.value;
    if (felt === 'scenario') {
        state.scenarioId = verdi;
        state.vurderinger = { 1: {}, 2: {}, '2L': {}, 3: {}, 4: {} };
        state.aktiv = null;
        state.utkast = {};
        state.feil = {};
    } else if (felt === 'lagringsmodell') {
        state.lagringsmodell = verdi;
    } else if (event.target.dataset.flervalg) {
        const liste = new Set(state.utkast[felt] ?? []);
        if (event.target.checked) liste.add(verdi);
        else liste.delete(verdi);
        state.utkast[felt] = Object.keys(YTELSER).filter((y) => liste.has(y));
    } else {
        settVerdi(state.utkast, felt, verdi);
    }
    if (event.type === 'input') return;
    render();
}

function håndterTastatur(event) {
    const fane = event.target.closest('[role="tab"]');
    if (!fane || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const ider = NIVÅER.map((n) => n.id);
    const nå = ider.indexOf(fane.dataset.fane);
    const neste = {
        ArrowLeft: (nå - 1 + ider.length) % ider.length,
        ArrowRight: (nå + 1) % ider.length,
        Home: 0,
        End: ider.length - 1,
    }[event.key];
    document.getElementById(`niva-tab-${ider[neste]}`)?.click();
}

document.addEventListener('click', håndterKlikk);
document.addEventListener('change', håndterEndring);
document.addEventListener('input', håndterEndring);
document.addEventListener('keydown', håndterTastatur);

renderSammenligning();
render();
