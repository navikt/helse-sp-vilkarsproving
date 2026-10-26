# 📜 Opptjening etter ftrl. § 8-2: forslag til kodeverk

> 💡 **Prinsipp:** Hver vilkårskode er en **positiv vei til innvilgelse**. Vi har ingen negative vilkår.
> Opptjening er oppfylt når **minst én** vei er `Oppfylt`. Avslag betyr at **ingen** vei er oppfylt.

## 🏷️ Vilkårskodene

| # | Vilkårskode | Hjemmel | Innhold |
|---|---|---|---|
| 🔵 **1** | `OPPTJENING_ARBEID_MINST_4_UKER` | § 8-2 **første ledd**, første setning | Minst 4 uker **arbeid** umiddelbart før arbeidsuførheten |
| 🟣 **2** | `OPPTJENING_ARBEID_OG_LIKESTILTE_YTELSER_MINST_4_UKER` | § 8-2 **andre ledd**, første og andre setning | Minst 4 uker arbeid og/eller **ytelser etter kapittel 4, 8, 9 eller 14**. Foreldrepenger opptjent på AAP regnes **ikke** med |
| 🟠 **3** | `OPPTJENING_FORELDREPENGER_MED_YRKESAKTIVITET_FØR_UTTAK` | § 8-2 **andre ledd**, tredje setning | Foreldrepenger opptjent på AAP gir likevel rett hvis medlemmet var yrkesaktiv, eller mottok ytelse etter kapittel 8, 9 eller 14, i minst 4 uker **før FP-uttaket startet**. Vurderes bare når det er foreldrepengene opptjent på AAP som avgjør |

## 🌳 Beslutningstre i GUI (helmanuell saksbehandling)

```
❓ 1. Arbeid i minst 4 uker umiddelbart før arbeidsuførheten?
 ├── ✅ Ja  ➜ 🔵 ARBEID = Oppfylt
 │            🟢 INNVILGET  · § 8-2 første ledd
 └── ❌ Nei ➜ 🔵 ARBEID = IkkeOppfylt
      │
❓ 2. Arbeid og/eller ytelser (kapittel 4, 8, 9, 14) i minst 4 uker umiddelbart før?
      (⚠️ foreldrepenger opptjent på AAP regnes IKKE med)
 ├── ✅ Ja  ➜ 🟣 LIKESTILTE_YTELSER = Oppfylt
 │            🟢 INNVILGET  · § 8-2 andre ledd, første setning
 └── ❌ Nei ➜ 🟣 LIKESTILTE_YTELSER = IkkeOppfylt
      │
❓ 3. Ville det blitt 4 uker hvis FP opptjent på AAP var regnet med?
 ├── ❌ Nei ➜ ⚪ vilkår 3 vurderes ikke
 │            🔴 AVSLAG     · § 8-2 første ledd og andre ledd, første setning
 └── ✅ Ja
      │
❓ 4. Yrkesaktiv, eller ytelse etter kapittel 8, 9 eller 14, i minst 4 uker før FP-uttaket startet?
 ├── ✅ Ja  ➜ 🟠 FP_MED_YRKESAKTIVITET = Oppfylt
 │            🟢 INNVILGET  · § 8-2 andre ledd, tredje setning
 └── ❌ Nei ➜ 🟠 FP_MED_YRKESAKTIVITET = IkkeOppfylt
              🔴 AVSLAG     · § 8-2 andre ledd, andre setning, jf. tredje setning
```

## 🧾 Samlet skjema i Speil (slik det er implementert)

Saksbehandleren vurderer hele § 8-2 i ett skjema, som sendes til
`POST /api/personer/{personId}/vilkarsvurderinger/manuell/opptjening`.
Hver lagring erstatter alle tidligere vilkårsvurderinger for opptjeningen.

```
❓ 1. Har søkeren hatt arbeid, likestilt ytelse, eller en kombinasjon av arbeid og likestilt ytelse,
      i minst 4 uker umiddelbart før arbeidsuførheten?
 ├── ❌ Nei ➜ OPPTJENING_ARBEID_MINST_4_UKER = IkkeOppfylt
 │            OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER = IkkeOppfylt
 │            (avvises foreløpig med 400, Speil viser «støttes ikke ennå»)
 └── ✅ Ja
      │
❓ 2. Hvilken likestilt ytelse/arbeid har søkeren hatt? (Arbeid, kap. 4, 8, 9, 14)
 ├── Bare arbeid ➜ OPPTJENING_ARBEID_MINST_4_UKER = Oppfylt
 │                 🟢 INNVILGET · § 8-2 første ledd
 └── Minst én ytelse (med eller uten arbeid)
                 ➜ OPPTJENING_ARBEID_MINST_4_UKER = IkkeOppfylt
                   OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER = Oppfylt
      │
❓ 3. Bare når foreldrepenger er valgt: Er foreldrepengene opptjent på grunnlag av AAP?
 ├── ❌ Nei ➜ OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP = Oppfylt
 │            🟢 INNVILGET · § 8-2 andre ledd, første punktum
 └── ✅ Ja  ➜ OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP = IkkeOppfylt
      │
❓ 4. Var søkeren yrkesaktiv, eller mottok ytelse etter kapittel 8, 9 eller 14, i minst 4 uker før uttaket startet?
 ├── ✅ Ja  ➜ OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER = Oppfylt
 │            🟢 INNVILGET · § 8-2 andre ledd, tredje punktum
 └── ❌ Nei ➜ OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER = IkkeOppfylt
              🔴 AVSLAG    · § 8-2 andre ledd, andre punktum, jf. tredje punktum
```

Valgene i spørsmål 2 lagres som `opptjeningsaktiviteter` på saksbehandlerkilden til alle vurderingene.
I prod-gcp er likestilte ytelser foreløpig skrudd av, så der kan bare arbeid velges.

## 🎯 Mulige sluttresultater

| | 🔵 Vilkår 1 | 🟣 Vilkår 2 | 🟠 Vilkår 3 | Resultat | Hjemmel |
|---|---|---|---|---|---|
| **A** | ✅ Oppfylt | ⚪ – | ⚪ – | 🟢 Innvilget | § 8-2 første ledd |
| **B** | ❌ IkkeOppfylt | ✅ Oppfylt | ⚪ – | 🟢 Innvilget | § 8-2 andre ledd, første setning |
| **C** | ❌ IkkeOppfylt | ❌ IkkeOppfylt | ✅ Oppfylt | 🟢 Innvilget | § 8-2 andre ledd, tredje setning |
| **D** | ❌ IkkeOppfylt | ❌ IkkeOppfylt | ⚪ – | 🔴 Avslag | § 8-2 første ledd og andre ledd, første setning |
| **E** | ❌ IkkeOppfylt | ❌ IkkeOppfylt | ❌ IkkeOppfylt | 🔴 Avslag | § 8-2 andre ledd, andre setning, jf. tredje setning |

⚪ – = ikke vurdert (ingen vurdering lagret)

## ✨ Hvorfor denne modellen?

- ➕ **Alle vilkår er positive.** Det blir ingen dobbel negasjon av typen «ikke unntak fra unntak».
- 📌 **Hver kode viser til én bestemt hjemmel** for innvilgelse.
- 🔍 **D og E kan skilles fra hverandre**, fordi vilkår 3 bare er vurdert i E.
- ⚖️ **Avslagshjemmelen utledes av hele kombinasjonen** av vurderte vilkår. I E er hjemmelen 2. setning, mens vilkår 3 har 3. setning som lovreferanse.
- 🧭 **Saksbehandler svarer på faktaspørsmål og velger ikke koder selv.** Det gir lik praksis. Backend avviser vilkår 3 hvis det ikke finnes FP opptjent på AAP.

## ❓ Åpent spørsmål til fag

> Skal vilkår 3 **bare** vurderes når FP opptjent på AAP faktisk ville gitt 4 uker (spørsmål 3)?
> Eller skal det vurderes **hver gang** slike foreldrepenger ligger umiddelbart før arbeidsuførheten?
