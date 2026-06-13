package kfinance.client

import kfinance.error.KFinanceException
import kfinance.model.Quote
import kfinance.net.KFinanceHttpClient
import kfinance.session.YahooSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpRequest

internal class QuoteClient(
    private val http: KFinanceHttpClient,
    private val session: YahooSession
) {
    companion object {
        private const val BASE_URL = "https://query1.finance.yahoo.com/v7/finance/quote"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36..."
        private val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun fetch(vararg symbols: String): List<Quote> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext emptyList()

        session.ensureValid()

        val joinedSymbols = symbols.joinToString(",")
        val url = "$BASE_URL?symbols=$joinedSymbols&crumb=${session.crumb()}"

        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Cookie", session.cookie())
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()

        val response = http.send(request)

        if (response.statusCode() !in 200..299) {
            throw KFinanceException.NetworkException(
                statusCode = response.statusCode(),
                message = "Failed to fetch quotes for $joinedSymbols, status: ${response.statusCode()}"
            )
        }

        response.body().toQuotes(json)
    }

    private fun String.toQuotes(json: Json): List<Quote> {
        try {
            val jsonTree = json.parseToJsonElement(this).jsonObject
            val quoteResponse = jsonTree["quoteResponse"]?.jsonObject
                ?: throw KFinanceException.ParseException("Missing 'quoteResponse' root element")

            val error = quoteResponse["error"]?.jsonPrimitive?.contentOrNull
            if (!error.isNullOrBlank()) {
                throw KFinanceException.ParseException("Yahoo returned an error: $error")
            }

            val resultArray = quoteResponse["result"]?.jsonArray ?: return emptyList()
            return json.decodeFromJsonElement(resultArray)
        } catch (e: SerializationException) {
            throw KFinanceException.ParseException("Failed to parse Quote JSON", e)
        } catch (e: IllegalArgumentException) {
            throw KFinanceException.ParseException("JSON structural mismatch while parsing", e)
        }
    }
}
