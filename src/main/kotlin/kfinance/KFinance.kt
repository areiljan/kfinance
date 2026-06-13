package kfinance

import kfinance.client.FundamentalsClient
import kfinance.client.QuoteClient
import kfinance.error.KFinanceException
import kfinance.model.Financials
import kfinance.model.Period
import kfinance.model.Quote
import kfinance.net.KFinanceHttpClient
import kfinance.session.DefaultYahooSession
import java.io.Closeable
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.ExecutorService

public class KFinance : Closeable {

    private val javaHttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val http = KFinanceHttpClient { javaHttpClient.send(it, java.net.http.HttpResponse.BodyHandlers.ofString()) }
    private val session = DefaultYahooSession(http)
    private val quotes = QuoteClient(http, session)
    private val fundies = FundamentalsClient(http, session)

    public suspend fun quote(symbol: String): Quote =
        quotes.fetch(symbol).firstOrNull()
            ?: throw KFinanceException.TickerNotFoundException(symbol)

    public suspend fun quotes(vararg symbols: String): List<Quote> =
        quotes.fetch(*symbols)

    public suspend fun financials(symbol: String, period: Period = Period.ANNUAL): Financials =
        fundies.fetch(symbol, period)

    override fun close() {
        javaHttpClient.executor().ifPresent {
            (it as? ExecutorService)?.shutdown()
        }
    }
}
