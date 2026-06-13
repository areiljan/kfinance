package kfinance

import io.kotest.core.spec.style.FunSpec
import kfinance.net.KFinanceHttpClient
import kfinance.session.DefaultYahooSession
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DownloadFixturesTest : FunSpec({
    test("download fixtures") {
        val rawHttp = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        val http = KFinanceHttpClient { request ->
            rawHttp.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val session = DefaultYahooSession(http)
        session.ensureValid()

        val crumb = session.crumb()
        val cookie = session.cookie()
        println("Crumb: $crumb")
        println("Cookie: $cookie")

        val fixturesDir = File("src/test/resources/fixtures")
        fixturesDir.mkdirs()

        val quoteUrl = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=LHV1T.TL&crumb=$crumb"
        val quoteReq = HttpRequest.newBuilder(URI.create(quoteUrl))
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .GET().build()
        
        val quoteRes = http.send(quoteReq)
        File(fixturesDir, "quote_response.json").writeText(quoteRes.body())
        println("Wrote quote_response.json")

        val fundiesUrl = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/LHV1T.TL?modules=incomeStatementHistory,cashflowStatementHistory,balanceSheetHistory&crumb=$crumb"
        val fundiesReq = HttpRequest.newBuilder(URI.create(fundiesUrl))
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .GET().build()
        
        val fundiesRes = http.send(fundiesReq)
        File(fixturesDir, "fundamentals_response.json").writeText(fundiesRes.body())
        println("Wrote fundamentals_response.json")
        
        rawHttp.executor().ifPresent { (it as? java.util.concurrent.ExecutorService)?.shutdown() }
    }
})
