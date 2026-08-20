package no.nav.helse.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf

/** Bygger en `kotliquery`-spørring med navngitte parametere. */
fun asSQL(
    query: String,
    params: Map<String, Any?> = emptyMap(),
) = queryOf(query, params)

/** Returnerer nøyaktig én rad mappet med [mapper], eller `null` hvis ingen treff. */
fun <T> Session.single(
    query: String,
    params: Map<String, Any?> = emptyMap(),
    mapper: (Row) -> T,
): T? = run(asSQL(query, params).map(mapper).asSingle)

/** Returnerer alle rader mappet med [mapper]. */
fun <T> Session.list(
    query: String,
    params: Map<String, Any?> = emptyMap(),
    mapper: (Row) -> T,
): List<T> = run(asSQL(query, params).map(mapper).asList)

/** Kjører en oppdaterende spørring (INSERT/UPDATE/DELETE) og returnerer antall berørte rader. */
fun Session.update(
    query: String,
    params: Map<String, Any?> = emptyMap(),
): Int = run(asSQL(query, params).asUpdate)

/** Kjører en INSERT og returnerer generert nøkkel (f.eks. serial/identity-kolonne). */
fun Session.updateAndReturnGeneratedKey(
    query: String,
    params: Map<String, Any?> = emptyMap(),
): Long? = run(asSQL(query, params).asUpdateAndReturnGeneratedKey)
