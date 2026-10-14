'use strict';

const SKJÆRINGSTIDSPUNKT = '2025-04-09';
const SAKSBEHANDLER = 'A123456';

const LOVREFERANSER = {
    L1P1: { paragraf: '8-2', ledd: 1, punktum: 1, tekst: '§ 8-2 første ledd første punktum' },
    L2: { paragraf: '8-2', ledd: 2, punktum: null, tekst: '§ 8-2 andre ledd' },
    L2P1: { paragraf: '8-2', ledd: 2, punktum: 1, tekst: '§ 8-2 andre ledd første punktum' },
    L2P2: { paragraf: '8-2', ledd: 2, punktum: 2, tekst: '§ 8-2 andre ledd andre punktum' },
    L2P3: { paragraf: '8-2', ledd: 2, punktum: 3, tekst: '§ 8-2 andre ledd tredje punktum' },
};

const YTELSER = {
    DAGPENGER: { navn: 'Dagpenger', kapittel: 4 },
    SYKEPENGER: { navn: 'Sykepenger', kapittel: 8 },
    KAPITTEL_9: { navn: 'Pleie-, omsorgs- eller opplæringspenger', kapittel: 9 },
    FORELDREPENGER: { navn: 'Foreldrepenger', kapittel: 14 },
    SVANGERSKAPSPENGER: { navn: 'Svangerskapspenger', kapittel: 14 },
};

const SCENARIOER = [
    {
        id: 'sykepenger',
        navn: 'Sykepenger rett før, kort tid i ny jobb',
        beskrivelse:
            'Fikk sykepenger fra et avsluttet arbeidsforhold fram til 30.03. Begynte i ny jobb 31.03 og ble syk igjen 09.04.',
        arbeidFom: '2025-03-31',
        ytelser: [{ type: 'SYKEPENGER', fom: '2025-01-06', tom: '2025-03-30' }],
        fasit: {
            utfall: 'OPPFYLT',
            punktum: 'L2P1',
            forklaring: 'Sykepenger (kap. 8) og arbeid til sammen gir mer enn 28 dager uten opphold.',
        },
    },
    {
        id: 'dagpenger',
        navn: 'Dagpenger fram til sykmeldingen',
        beskrivelse: 'Arbeidsledig med dagpenger fra 03.02 og fram til skjæringstidspunktet. Ingen arbeidsforhold.',
        arbeidFom: null,
        ytelser: [{ type: 'DAGPENGER', fom: '2025-02-03', tom: '2025-04-08' }],
        fasit: {
            utfall: 'OPPFYLT',
            punktum: 'L2P1',
            forklaring: 'Dagpenger (kap. 4) er likestilt med arbeid etter første punktum.',
        },
    },
    {
        id: 'fp-arbeid',
        navn: 'Foreldrepenger opptjent ved arbeid',
        beskrivelse: 'Foreldrepenger fra 04.11.2024 fram til skjæringstidspunktet. Foreldrepengene er opptjent ved arbeid.',
        arbeidFom: null,
        ytelser: [
            {
                type: 'FORELDREPENGER',
                fom: '2024-11-04',
                tom: '2025-04-08',
                opptjentPåAap: false,
                førUttak: 'Arbeid 100 % i Bakeriet AS fram til uttaket',
            },
        ],
        fasit: {
            utfall: 'OPPFYLT',
            punktum: 'L2P1',
            forklaring: 'Foreldrepenger opptjent ved arbeid teller fullt ut. Andre punktum er kontrollert og slår ikke inn.',
        },
    },
    {
        id: 'fp-aap',
        navn: 'Foreldrepenger opptjent på AAP',
        beskrivelse:
            'Mottok AAP fram til uttak av foreldrepenger 04.11.2024. Foreldrepengene er opptjent på grunnlag av AAP.',
        arbeidFom: null,
        ytelser: [
            {
                type: 'FORELDREPENGER',
                fom: '2024-11-04',
                tom: '2025-04-08',
                opptjentPåAap: true,
                yrkesaktivFørUttak: false,
                førUttak: 'AAP fra 01.06.2023 fram til uttaket',
            },
        ],
        fasit: {
            utfall: 'IKKE_OPPFYLT',
            punktum: 'L2P2',
            forklaring: 'Foreldrepenger opptjent på AAP likestilles ikke med arbeid, og unntaket i tredje punktum slår ikke inn.',
        },
    },
    {
        id: 'fp-aap-arbeid',
        navn: 'Foreldrepenger opptjent på AAP, men jobbet før uttaket',
        beskrivelse:
            'Foreldrepengene er opptjent på AAP, men medlemmet begynte å jobbe igjen 01.09.2024 og jobbet 9 uker før uttaket startet 04.11.2024.',
        arbeidFom: null,
        ytelser: [
            {
                type: 'FORELDREPENGER',
                fom: '2024-11-04',
                tom: '2025-04-08',
                opptjentPåAap: true,
                yrkesaktivFørUttak: true,
                førUttak: 'Arbeid 100 % i Snekker Hansen AS 01.09.2024–03.11.2024 (9 uker)',
            },
        ],
        fasit: {
            utfall: 'OPPFYLT',
            punktum: 'L2P3',
            forklaring: 'Opptjent på AAP, men yrkesaktiv i minst 4 uker rett før uttaket. Tredje punktum gir rett.',
        },
    },
    {
        id: 'kombinasjon',
        navn: 'To uker arbeid og to uker pleiepenger',
        beskrivelse: 'Pleiepenger (kap. 9) 17.02–24.03, deretter i arbeid fra 25.03. Syk 09.04.',
        arbeidFom: '2025-03-25',
        ytelser: [{ type: 'KAPITTEL_9', fom: '2025-02-17', tom: '2025-03-24' }],
        fasit: {
            utfall: 'OPPFYLT',
            punktum: 'L2P1',
            forklaring: 'Arbeid og pleiepenger legges sammen og gir mer enn 28 dager.',
        },
    },
];

const NIVÅER = [
    {
        id: '1',
        navn: 'Nivå 1: Én rad, ja/nei',
        kort: 'Én rad',
        beskrivelse:
            'Raden «Likestilt ytelse» vurderes akkurat som «Arbeid i minst 4 uker» i dag: Oppfylt / Ikke oppfylt og en begrunnelse. Det er minst å bygge, men all kunnskap om hvilken ytelse det var, og om andre og tredje punktum ble vurdert, ligger bare i fritekst.',
    },
    {
        id: '2',
        navn: 'Nivå 2: Én rad, styrte spørsmål',
        kort: 'Styrte spørsmål',
        beskrivelse:
            'Fortsatt én rad, men skjemaet stiller spørsmålene som loven stiller: hvilken ytelse, om det er 28 dager til sammen, og bare ved foreldrepenger om de er opptjent på AAP og om medlemmet var yrkesaktiv før uttaket. Utfallet utledes av svarene. Under «Sporing» kan du velge om svarene lagres som én vurdering eller som én vurdering per punktum.',
    },
    {
        id: '2L',
        navn: 'Nivå 2 lett: Positive vilkår',
        kort: 'Positive vilkår',
        beskrivelse:
            'Lettvektsvariant av nivå 2, bygget på forslaget til kodeverk. Hver vilkårskode er en positiv vei til innvilgelse, og opptjening er oppfylt når minst én vei er oppfylt. Saksbehandleren svarer på opptil tre faktaspørsmål i ett panel og velger aldri koder selv. Vi registrerer ikke hvilken ytelse det var. Vilkår 3 (foreldrepenger med yrkesaktivitet før uttak) vurderes bare når foreldrepenger opptjent på AAP er det som avgjør. Avslagshjemmelen utledes av hvilke vilkår som er vurdert.',
    },
    {
        id: '3',
        navn: 'Nivå 3: Én rad per punktum',
        kort: 'Én rad per punktum',
        beskrivelse:
            'Hvert punktum i andre ledd er et eget delvilkår med egen vilkårskode, lovreferanse og vurdering. Rader for andre og tredje punktum vises bare når de er aktuelle. Det gir best sporing, men flest klikk, og saksbehandleren må forstå regel–unntak–unntak fra unntaket.',
    },
    {
        id: '4',
        navn: 'Nivå 4: Perioder og tidslinje',
        kort: 'Perioder',
        beskrivelse:
            'Saksbehandleren registrerer periodene med likestilt ytelse. Systemet legger dem sammen med arbeidsforholdene, teller opptjeningsdager og utleder utfall og punktum. Mest å registrere, men grunnlaget kan etterprøves maskinelt, og det er samme form som en framtidig automatisk vurdering vil ha.',
    },
];

const SPORINGSSPØRSMÅL = [
    { spørsmål: 'Hvilket alternativ i § 8-2 ga opptjening?', svar: ['ja', 'ja', 'ja', 'ja', 'ja'] },
    { spørsmål: 'Hvilken ytelse ga opptjening?', svar: ['fritekst', 'ja', 'fritekst', 'delvis', 'ja'] },
    {
        spørsmål: 'Ble andre punktum (foreldrepenger opptjent på AAP) kontrollert?',
        svar: ['fritekst', 'ja', 'delvis', 'ja', 'ja'],
    },
    {
        spørsmål: 'Ga tredje punktum (4 uker yrkesaktiv før uttak) retten?',
        svar: ['fritekst', 'ja', 'ja', 'ja', 'ja'],
    },
    { spørsmål: 'Subsumsjon på punktumnivå', svar: ['nei', 'delvis', 'delvis', 'ja', 'ja'] },
    { spørsmål: 'Hvilke perioder og hvor mange opptjeningsdager?', svar: ['nei', 'nei', 'nei', 'nei', 'ja'] },
    { spørsmål: 'Kan utfallet etterprøves maskinelt?', svar: ['nei', 'delvis', 'delvis', 'delvis', 'ja'] },
];

const SAMMENLIGNING = {
    kolonner: ['Nivå 1', 'Nivå 2', 'Nivå 2 lett', 'Nivå 3', 'Nivå 4'],
    rader: [
        ['Nye vilkårskoder', '1', '1 (eller 3)', '2', '3', '1 + periodegrunnlag'],
        ['Endring i Speil', 'Ny rad, samme skjema', 'Ny rad, nytt skjema', 'Ny rad, 1–3 faktaspørsmål', '3 rader med avhengigheter', 'Ny rad, periodeskjema og tidslinje'],
        ['Endring i sp-vilkarsproving', 'Ny Vilkårskode', 'Svar på Saksbehandler-kilden', '2 nye Vilkårskoder, avslagshjemmel utledes', 'Ny avgjørende-logikk på tvers av rader', 'Nytt grunnlag og regelmotor for sammenlegging'],
        ['Klikk for en vanlig sak', '2–3', '3–4', '2–4', '3–6', '6+'],
        ['Risiko for feil utfall', 'Høy', 'Lav', 'Lav', 'Middels', 'Lav'],
        ['Sporing', 'Fritekst', 'God', 'God', 'Svært god', 'Full'],
    ],
};
