'use strict';

// Aksel-markup speilet fra @navikt/ds-react 8.17.2 (renderToStaticMarkup), slik at CSS fra CDN treffer.

function esc(verdi) {
    return String(verdi ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;');
}

function attrs(attributter = {}) {
    return Object.entries(attributter)
        .filter(([, v]) => v !== undefined && v !== null && v !== false)
        .map(([k, v]) => (v === true ? k : `${k}="${esc(v)}"`))
        .join(' ');
}

const IKONSTIER = {
    oppfylt:
        'M12 21.75c5.385 0 9.75-4.365 9.75-9.75S17.385 2.25 12 2.25 2.25 6.615 2.25 12s4.365 9.75 9.75 9.75m4.954-12.475a.813.813 0 0 0-1.24-1.05l-5.389 6.368L7.7 11.967a.812.812 0 0 0-1.15 1.15l3.25 3.25a.81.81 0 0 0 1.195-.05z',
    oppfyltOmriss:
        'M3.75 12a8.25 8.25 0 1 1 16.5 0 8.25 8.25 0 0 1-16.5 0M12 2.25c-5.385 0-9.75 4.365-9.75 9.75s4.365 9.75 9.75 9.75 9.75-4.365 9.75-9.75S17.385 2.25 12 2.25m4.573 7.234a.75.75 0 1 0-1.146-.968l-4.973 5.877L8.03 11.97a.75.75 0 0 0-1.06 1.06l3 3a.75.75 0 0 0 1.102-.046z',
    ikkeOppfylt:
        'M8.272 2.25a.75.75 0 0 0-.53.22L2.47 7.742a.75.75 0 0 0-.22.53v7.456c0 .199.079.39.22.53l5.272 5.272c.14.141.331.22.53.22h7.456a.75.75 0 0 0 .53-.22l5.272-5.272a.75.75 0 0 0 .22-.53V8.272a.75.75 0 0 0-.22-.53L16.258 2.47a.75.75 0 0 0-.53-.22zM3.75 8.583 8.583 3.75h6.834l4.833 4.833v6.834l-4.833 4.833H8.583L3.75 15.417zm5.28-.613a.75.75 0 0 0-1.06 1.06L10.94 12l-2.97 2.97a.75.75 0 1 0 1.06 1.06L12 13.06l2.97 2.97a.75.75 0 1 0 1.06-1.06L13.06 12l2.97-2.97a.75.75 0 0 0-1.06-1.06L12 10.94z',
    ikkeVurdert:
        'M12 2.25a.75.75 0 0 1 .656.387l9.527 17.25A.75.75 0 0 1 21.526 21H2.474a.75.75 0 0 1-.657-1.113l9.527-17.25A.75.75 0 0 1 12 2.25m8.255 17.25L12 4.551 3.745 19.5zM12 8.75a.75.75 0 0 1 .75.75v4a.75.75 0 0 1-1.5 0v-4a.75.75 0 0 1 .75-.75m0 6.75a1 1 0 1 0 0 2 1 1 0 0 0 0-2',
    ikkeVurdertFylt:
        'M12 2.25a.75.75 0 0 1 .656.387l9.527 17.25A.75.75 0 0 1 21.526 21H2.474a.75.75 0 0 1-.657-1.113l9.526-17.25A.75.75 0 0 1 12 2.25M12 8.75a.75.75 0 0 1 .75.75v4a.75.75 0 0 1-1.5 0v-4a.75.75 0 0 1 .75-.75m-1 7.75a1 1 0 1 1 2 0 1 1 0 0 1-2 0',
    chevronNed:
        'M5.97 9.47a.75.75 0 0 1 1.06 0L12 14.44l4.97-4.97a.75.75 0 1 1 1.06 1.06l-5.5 5.5a.75.75 0 0 1-1.06 0l-5.5-5.5a.75.75 0 0 1 0-1.06',
    pluss: 'M12.75 5.5a.75.75 0 0 0-1.5 0v5.75H5.5a.75.75 0 0 0 0 1.5h5.75v5.75a.75.75 0 0 0 1.5 0v-5.75h5.75a.75.75 0 0 0 0-1.5h-5.75z',
};

const FEILIKON =
    '<svg viewBox="0 0 17 16" fill="none" xmlns="http://www.w3.org/2000/svg" focusable="false" aria-hidden="true"><path fill-rule="evenodd" clip-rule="evenodd" d="M3.49209 11.534L8.11398 2.7594C8.48895 2.04752 9.50833 2.04743 9.88343 2.75924L14.5073 11.5339C14.8582 12.1998 14.3753 13 13.6226 13H4.37685C3.6242 13 3.14132 12.1999 3.49209 11.534ZM9.74855 10.495C9.74855 10.9092 9.41276 11.245 8.99855 11.245C8.58433 11.245 8.24855 10.9092 8.24855 10.495C8.24855 10.0808 8.58433 9.74497 8.99855 9.74497C9.41276 9.74497 9.74855 10.0808 9.74855 10.495ZM9.49988 5.49997C9.49988 5.22383 9.27602 4.99997 8.99988 4.99997C8.72373 4.99997 8.49988 5.22383 8.49988 5.49997V7.99997C8.49988 8.27611 8.72373 8.49997 8.99988 8.49997C9.27602 8.49997 9.49988 8.27611 9.49988 7.99997V5.49997Z" fill="currentColor"></path></svg>';

function ikon(navn, tittel, klasse = '') {
    const tilgjengelighet = tittel ? `role="img" aria-label="${esc(tittel)}"` : 'aria-hidden="true"';
    return `<svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" fill="none" viewBox="0 0 24 24" focusable="false" ${tilgjengelighet} class="${klasse}"><path fill="currentColor" fill-rule="evenodd" clip-rule="evenodd" d="${IKONSTIER[navn]}"></path></svg>`;
}

function knapp(tekst, { variant = 'secondary', size = 'small', ikonNavn, ...rest } = {}) {
    const ikonHtml = ikonNavn ? `<span class="aksel-button__icon">${ikon(ikonNavn)}</span>` : '';
    return `<button type="button" data-variant="${variant}" class="aksel-button aksel-button--${size}" ${attrs(rest)}>${ikonHtml}<span class="aksel-label aksel-label--small">${esc(tekst)}</span></button>`;
}

function tag(tekst, farge = 'neutral') {
    return `<span data-color="${farge}" data-variant="outline" class="aksel-tag aksel-tag--xsmall aksel-body-short aksel-body-short--small">${esc(tekst)}</span>`;
}

function feilmelding(id, melding) {
    return melding
        ? `<p class="aksel-error-message aksel-label aksel-label--small aksel-error-message--show-icon">${FEILIKON}${esc(melding)}</p>`
        : '';
}

function radiogruppe({ id, felt, legend, beskrivelse, valg, verdi, feil }) {
    const beskrivelseId = `${id}-beskrivelse`;
    const feilId = `${id}-feil`;
    const labelledBy = [`${id}-legend`, beskrivelse ? beskrivelseId : null].filter(Boolean).join(' ');
    const knapper = valg
        .map((v, i) => {
            const inputId = `${id}-${i}`;
            const subId = `${inputId}-beskrivelse`;
            const describedBy = [v.beskrivelse ? subId : null, feil ? feilId : null].filter(Boolean).join(' ') || undefined;
            return `<div class="aksel-radio aksel-radio--small${feil ? ' aksel-radio--error' : ''}"${feil ? ' data-color="danger"' : ''}>
                <input ${attrs({ id: inputId, type: 'radio', class: 'aksel-radio__input', 'data-standalone': 'false', name: id, value: v.verdi, checked: verdi === v.verdi, 'data-felt': felt, 'aria-describedby': describedBy })}/>
                <label for="${inputId}" class="aksel-radio__label aksel-body-short aksel-body-short--small">${esc(v.tekst)}</label>
                ${v.beskrivelse ? `<p id="${subId}" class="aksel-form-field__subdescription aksel-radio__description aksel-body-short aksel-body-short--small">${esc(v.beskrivelse)}</p>` : ''}
            </div>`;
        })
        .join('');
    return `<fieldset role="radiogroup" ${attrs({ 'aria-invalid': feil ? 'true' : undefined })} aria-labelledby="${labelledBy}" class="aksel-radio-group aksel-radio-group--small aksel-fieldset aksel-fieldset--small${feil ? ' aksel-fieldset--error' : ''}">
        <legend id="${id}-legend" class="aksel-fieldset__legend aksel-label aksel-label--small">${esc(legend)}</legend>
        ${beskrivelse ? `<div id="${beskrivelseId}" class="aksel-fieldset__description aksel-body-short aksel-body-short--small">${esc(beskrivelse)}</div>` : ''}
        <div class="aksel-radio-buttons">${knapper}</div>
        <div id="${feilId}" aria-relevant="additions removals" aria-live="polite" class="aksel-fieldset__error">${feilmelding(feilId, feil)}</div>
    </fieldset>`;
}

const JA_NEI = [
    { verdi: 'JA', tekst: 'Ja' },
    { verdi: 'NEI', tekst: 'Nei' },
];

const UTFALLSVALG = [
    { verdi: 'OPPFYLT', tekst: 'Oppfylt' },
    { verdi: 'IKKE_OPPFYLT', tekst: 'Ikke oppfylt' },
];

const CHECKMARK =
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 13 10" fill="none" focusable="false" aria-hidden="true" class="aksel-checkbox__icon"><path d="M4.03524 6.41478L10.4752 0.404669C11.0792 -0.160351 12.029 -0.130672 12.5955 0.47478C13.162 1.08027 13.1296 2.03007 12.5245 2.59621L5.02111 9.59934C4.74099 9.85904 4.37559 10 4.00025 10C3.60651 10 3.22717 9.84621 2.93914 9.56111L0.439143 7.06111C-0.146381 6.47558 -0.146381 5.52542 0.439143 4.93989C1.02467 4.35437 1.97483 4.35437 2.56036 4.93989L4.03524 6.41478Z" fill="currentColor"></path></svg>';

function avkrysningsgruppe({ id, felt, legend, beskrivelse, valg, verdier = [], feil }) {
    const feilId = `${id}-feil`;
    const bokser = valg
        .map((v, i) => {
            const inputId = `${id}-${i}`;
            return `<div class="aksel-checkbox aksel-checkbox--small${feil ? ' aksel-checkbox--error' : ''}">
                <div class="aksel-checkbox__input-wrapper" data-standalone="false">
                    <input ${attrs({ id: inputId, class: 'aksel-checkbox__input', type: 'checkbox', value: v.verdi, checked: verdier.includes(v.verdi), 'data-felt': felt, 'data-flervalg': 'true', 'aria-describedby': feil ? feilId : undefined })}/>${CHECKMARK}
                </div>
                <label for="${inputId}" class="aksel-checkbox__label aksel-body-short aksel-body-short--small">${esc(v.tekst)}</label>
            </div>`;
        })
        .join('');
    return `<fieldset aria-labelledby="${id}-legend" class="aksel-checkbox-group aksel-checkbox-group--small aksel-fieldset aksel-fieldset--small${feil ? ' aksel-fieldset--error' : ''}">
        <legend id="${id}-legend" class="aksel-fieldset__legend aksel-label aksel-label--small">${esc(legend)}</legend>
        ${beskrivelse ? `<div class="aksel-fieldset__description aksel-body-short aksel-body-short--small">${esc(beskrivelse)}</div>` : ''}
        <div class="aksel-checkboxes">${bokser}</div>
        <div id="${feilId}" aria-relevant="additions removals" aria-live="polite" class="aksel-fieldset__error">${feilmelding(feilId, feil)}</div>
    </fieldset>`;
}

function tekstområde({ id, felt, verdi, feil }) {
    const beskrivelseId = `${id}-beskrivelse`;
    const feilId = `${id}-feil`;
    return `<div class="aksel-form-field aksel-form-field--small${feil ? ' aksel-textarea--error' : ''}">
        <div class="vstack gap-2">
            <label for="${id}" class="aksel-form-field__label aksel-label aksel-label--small">Begrunnelse</label>
            <span>${tag('Vises ikke i vedtaket', 'meta-purple')}</span>
        </div>
        <div id="${beskrivelseId}" class="aksel-form-field__description aksel-body-short aksel-body-short--small">Begrunnelsen blir ikke vist til den sykmeldte, med mindre hen ber om innsyn.</div>
        <textarea ${attrs({ id, rows: 3, class: 'aksel-textarea__input aksel-body-short aksel-body-short--small', 'data-felt': felt, 'aria-invalid': feil ? 'true' : undefined, 'aria-describedby': feil ? feilId : beskrivelseId })}>${esc(verdi)}</textarea>
        <div class="aksel-form-field__error" id="${feilId}" aria-relevant="additions removals" aria-live="polite">${feilmelding(feilId, feil)}</div>
    </div>`;
}

function tekstfelt({ id, felt, label, verdi, type = 'text', feil }) {
    const feilId = `${id}-feil`;
    return `<div class="aksel-form-field aksel-form-field--small${feil ? ' aksel-text-field--error' : ''}">
        <label for="${id}" class="aksel-form-field__label aksel-label aksel-label--small">${esc(label)}</label>
        <input ${attrs({ id, type, value: verdi, class: 'aksel-text-field__input aksel-body-short aksel-body-short--small', 'data-felt': felt, 'aria-invalid': feil ? 'true' : undefined, 'aria-describedby': feil ? feilId : undefined })}/>
        <div class="aksel-form-field__error" id="${feilId}" aria-relevant="additions removals" aria-live="polite">${feilmelding(feilId, feil)}</div>
    </div>`;
}

function nedtrekk({ id, felt, label, verdi, valg, beskrivelse }) {
    const options = valg
        .map((v) => `<option ${attrs({ value: v.verdi, selected: v.verdi === verdi })}>${esc(v.tekst)}</option>`)
        .join('');
    return `<div class="aksel-form-field aksel-form-field--small">
        <label for="${id}" class="aksel-form-field__label aksel-label aksel-label--small">${esc(label)}</label>
        ${beskrivelse ? `<div class="aksel-form-field__description aksel-body-short aksel-body-short--small">${esc(beskrivelse)}</div>` : ''}
        <div class="aksel-select__container">
            <select ${attrs({ id, class: 'aksel-select__input aksel-body-short aksel-body-short--small', 'data-felt': felt })}>${options}</select>
            ${ikon('chevronNed', null, 'aksel-select__chevron')}
        </div>
    </div>`;
}

function lesMer({ id, tittel, åpen, innhold }) {
    return `<div class="aksel-read-more aksel-read-more--small" data-variant="ghost" data-state="${åpen ? 'open' : 'closed'}">
        <button type="button" id="${id}" class="aksel-read-more__button aksel-body-short aksel-body-short--small" aria-expanded="${åpen}" data-handling="lesmer" data-lesmer="${id}">
            ${ikon('chevronNed', null, 'aksel-read-more__expand-icon')}<span>${esc(tittel)}</span>
        </button>
        <div class="aksel-read-more__content aksel-body-long aksel-body-long--small">${innhold}</div>
    </div>`;
}

function varsel(innhold, farge = 'info') {
    return `<div data-color="${farge}" data-variant="${farge}" class="aksel-alert aksel-alert--${farge} aksel-alert--small aksel-alert--inline">
        <div class="aksel-alert__wrapper aksel-alert__wrapper--maxwidth aksel-body-long aksel-body-long--small">${innhold}</div>
    </div>`;
}

function faner({ id, valgt, faner: liste }) {
    const knapper = liste
        .map((f) => {
            const erValgt = f.id === valgt;
            return `<button class="aksel-tabs__tab aksel-tabs__tab--small" type="button" id="${id}-tab-${f.id}" aria-controls="${id}-panel" tabindex="${erValgt ? 0 : -1}" aria-selected="${erValgt}" data-state="${erValgt ? 'active' : 'inactive'}" role="tab" data-handling="fane" data-fane="${f.id}">
                <span class="aksel-tabs__tab-inner aksel-body-short aksel-body-short--small"><span>${esc(f.tekst)}</span></span>
            </button>`;
        })
        .join('');
    return `<div class="aksel-tabs aksel-tabs--small"><div class="aksel-tabs__tablist-wrapper"><div class="aksel-tabs__tablist" role="tablist" aria-orientation="horizontal" aria-label="Nivå">${knapper}</div></div></div>`;
}

function datoNorsk(iso) {
    if (!iso) return '–';
    const [å, m, d] = iso.split('-');
    return `${d}.${m}.${å}`;
}

function tidspunktNorsk(dato) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${pad(dato.getDate())}.${pad(dato.getMonth() + 1)}.${dato.getFullYear()} kl. ${pad(dato.getHours())}.${pad(dato.getMinutes())}`;
}
