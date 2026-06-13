package kfinance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kfinance.client.QuoteClient
import kfinance.net.KFinanceHttpClient
import kfinance.session.YahooSession
import java.io.File
import java.net.http.HttpResponse
import javax.net.ssl.SSLSession

class QuoteClientTest : FunSpec({
    test("fetch processes request and parses string fixture flawlessly") {
        val jsonFixture = File("src/test/resources/fixtures/quote_response.json").readText()

        val fakeResponse = object : HttpResponse<String> {
            override fun statusCode() = 200
            override fun body() = jsonFixture
            override fun headers() = TODO("Not needed for this test")
            override fun request() = TODO("Not needed for this test")
            override fun previousResponse() = java.util.Optional.empty<HttpResponse<String>>()
            override fun sslSession() = java.util.Optional.empty<SSLSession>()
            override fun uri() = TODO("Not needed for this test")
            override fun version() = java.net.http.HttpClient.Version.HTTP_2
        }

        val fakeHttpClient = KFinanceHttpClient { request -> fakeResponse }

        val fakeSession = object : YahooSession {
            override suspend fun ensureValid() {} // No-op
            override fun crumb() = "dummyCrumb"
            override fun cookie() = "B=dummyCookie"
        }

        val client = QuoteClient(fakeHttpClient, fakeSession)
        val results = client.fetch("LHV1T.TL")

        results.size shouldBe 1
        val quote = results.first()
        quote.symbol shouldBe "LHV1T.TL"
        quote.currency shouldBe "EUR"
        quote.exchangeName shouldBe "TAL"
        quote.currentPrice shouldNotBe null
    }
})