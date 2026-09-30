package no.nav.helse.sykepenger.vilkarsproving.infra.spleis

import no.nav.helse.speil.backend.app.logging.loggWarn
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal fun <T> HttpClient.sendMedRetry(
    request: HttpRequest,
    bodyHandler: HttpResponse.BodyHandler<T>,
    maksAntallForsøk: Int = 3,
    ventetidMellomForsøk: Duration = Duration.ofMillis(500),
): HttpResponse<T> {
    var forsøk = 1
    while (true) {
        try {
            return send(request, bodyHandler)
        } catch (ex: IOException) {
            if (forsøk >= maksAntallForsøk) throw ex
            loggWarn("Kall mot ${request.uri()} feilet (forsøk $forsøk av $maksAntallForsøk), prøver igjen: $ex")
            Thread.sleep(ventetidMellomForsøk.multipliedBy(forsøk.toLong()))
            forsøk++
        }
    }
}
