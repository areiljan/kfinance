package kfinance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kfinance.client.FundamentalsClient
import kfinance.model.Period
import kfinance.net.KFinanceHttpClient
import kfinance.session.YahooSession
import java.io.File
import java.net.http.HttpResponse
import javax.net.ssl.SSLSession

class FundamentalsClientTest : FunSpec({
    test("fetch processes request and parses string fixture flawlessly") {
        val jsonFixture = File("src/test/resources/fixtures/fundamentals_response.json").readText()

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

        val client = FundamentalsClient(fakeHttpClient, fakeSession)
        val fin = client.fetch("LHV1T.TL", Period.ANNUAL)

        fin.symbol shouldBe "LHV1T.TL"
        fin.period shouldBe Period.ANNUAL

        fin.incomeStatement shouldNotBe null
        fin.incomeStatement?.entries?.isNotEmpty() shouldBe true
        val incomeEntry = fin.incomeStatement?.entries?.first()
        incomeEntry?.endDate shouldNotBe null
        incomeEntry?.totalRevenue shouldNotBe null

        fin.balanceSheet shouldNotBe null
        fin.balanceSheet?.entries?.isNotEmpty() shouldBe true
        val balanceEntry = fin.balanceSheet?.entries?.first()
        balanceEntry?.endDate shouldNotBe null

        fin.cashFlow shouldNotBe null
        fin.cashFlow?.entries?.isNotEmpty() shouldBe true
        val cashFlowEntry = fin.cashFlow?.entries?.first()
        cashFlowEntry?.endDate shouldNotBe null
    }
})
