package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat
import no.nav.helse.sykepenger.vilkarsproving.domain.Hovedkategori
import no.nav.helse.sykepenger.vilkarsproving.domain.Kategori
import no.nav.helse.sykepenger.vilkarsproving.domain.RettighetskategoriId
import no.nav.helse.sykepenger.vilkarsproving.infra.rettighetskategori.IRettighetskategoriClient
import no.nav.helse.sykepenger.vilkarsproving.infra.rettighetskategori.RettighetskategoriClientException
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.TransaksjonProvider
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.loggWarn
import no.nav.sykepenger.libs.logging.medMdc
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate

internal class OpptjeningsvurderingRiver(
    rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    private val rettighetskategoriClient: IRettighetskategoriClient,
    private val versjonAvKode: String = "test",
) : River.PacketListener {
    private val behovKey = "Opptjeningsvurdering"

    init {

        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov")
                    it.requireAllOrAny("@behov", listOf(behovKey))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey("fødselsnummer")
                    it.require("Opptjeningsvurdering.skjæringstidspunkt", JsonNode::asLocalDate)
                    it.requireKey("Opptjeningsvurdering.rettighetskategoriId")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fødselsnummer = packet["fødselsnummer"].asString()
        val skjæringstidspunkt = packet["Opptjeningsvurdering.skjæringstidspunkt"].asLocalDate()
        val rettighetskategoriId = RettighetskategoriId(packet["Opptjeningsvurdering.rettighetskategoriId"].asUUID())

        medMdc(
            MdcKey.IDENTITETSNUMMER to fødselsnummer,
        ) {
            loggInfo(
                "Mottatt behov for $behovKey",
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                "rettighetskategoriId" to rettighetskategoriId.toString(),
            )

            val vurderOpptjeningResultat =
                transaksjonProvider.transaksjon { kontekst ->
                    OpptjeningService(kontekst, versjonAvKode).kobletVurdering(fødselsnummer, skjæringstidspunkt, rettighetskategoriId)
                } ?: run {
                    // Oppslaget gjøres utenfor transaksjonen, så vi ikke holder på en databaseforbindelse mens vi venter på svar
                    val kategori = hentKategori(rettighetskategoriId, fødselsnummer, skjæringstidspunkt) ?: return@medMdc
                    transaksjonProvider.transaksjon { kontekst ->
                        OpptjeningService(kontekst, versjonAvKode).vurderOpptjening(
                            fødselsnummer = fødselsnummer,
                            skjæringstidspunkt = skjæringstidspunkt,
                            kategori = kategori,
                            rettighetskategoriId = rettighetskategoriId,
                        )
                    }
                }

            // Publisering skjer først etter at transaksjonen er commitet — vi forteller aldri
            // omverdenen om en vurdering vi ikke har lagret.
            when (vurderOpptjeningResultat) {
                is VurderOpptjeningResultat.HarVurdering -> {
                    packet["@løsning"] =
                        mapOf(
                            "Opptjeningsvurdering" to
                                mapOf(
                                    "id" to vurderOpptjeningResultat.opptjeningsvurderingId.toString(),
                                ),
                        )
                    loggInfo(
                        "Har vurdering. Publiserer løsning",
                        "opptjeningsvurderingId" to vurderOpptjeningResultat.opptjeningsvurderingId.toString(),
                        "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                        "løsning" to packet.toJson(),
                    )
                    context.publish(packet.toJson())
                }

                is VurderOpptjeningResultat.TrengerArbeidsforhold -> {
                    val utgåendeBehov =
                        JsonMessage.newNeed(
                            behov = listOf("ArbeidsforholdV2"),
                            map =
                                mapOf(
                                    "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                                    "fødselsnummer" to fødselsnummer,
                                    "opprinneligBehov" to jacksonObjectMapper().readTree(packet.toJson()),
                                ),
                        )
                    loggInfo(
                        "Trenger arbeidsforhold. Publiserer nytt behov",
                        "behov" to utgåendeBehov.toJson(),
                        "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                    )
                    context.publish(utgåendeBehov.toJson())
                }
            }
        }
    }

    /** Kategorien til opptjening ut fra hovedkategorien. Null betyr at behovet skal ignoreres. */
    private fun hentKategori(
        rettighetskategoriId: RettighetskategoriId,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Kategori? {
        val detaljer =
            arrayOf(
                "rettighetskategoriId" to rettighetskategoriId.toString(),
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
            )
        val hovedkategori =
            try {
                rettighetskategoriClient.hentHovedkategori(rettighetskategoriId, fødselsnummer)
            } catch (ex: RettighetskategoriClientException) {
                loggWarn("Fikk ikke slått opp rettighetskategorien. Sender ingen løsning", ex, *detaljer)
                return null
            }
        return when (hovedkategori) {
            Hovedkategori.Arbeidstaker -> {
                Kategori.Arbeidstaker
            }

            Hovedkategori.SelvstendigNæringsdrivende -> {
                Kategori.SelvstendigNæringsdrivende
            }

            Hovedkategori.ArbeidstakerOgSelvstendigNæringsdrivende -> {
                loggError("Opptjening kan ikke vurdere hovedkategorien Arbeidstaker og selvstendig næringsdrivende. Ignorerer behovet", *detaljer)
                null
            }

            is Hovedkategori.Ukjent -> {
                loggError("Hovedkategorien mangler eller er ukjent. Ignorerer behovet", "hovedkategori" to hovedkategori.verdi.toString(), *detaljer)
                null
            }
        }
    }
}
