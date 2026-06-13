package kfinance.client

import kfinance.error.KFinanceException
import kfinance.model.*
import kfinance.net.KFinanceHttpClient
import kfinance.session.YahooSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpRequest

internal class FundamentalsClient(
    private val http: KFinanceHttpClient,
    private val session: YahooSession
) {
    companion object {
        private const val BASE_URL = "https://query1.finance.yahoo.com/v10/finance/quoteSummary"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    suspend fun fetch(symbol: String, period: Period): Financials = withContext(Dispatchers.IO) {
        session.ensureValid()

        val modules = if (period == Period.ANNUAL) {
            "incomeStatementHistory,cashflowStatementHistory,balanceSheetHistory"
        } else {
            "incomeStatementHistoryQuarterly,cashflowStatementHistoryQuarterly,balanceSheetHistoryQuarterly"
        }

        val url = "$BASE_URL/$symbol?modules=$modules&crumb=${session.crumb()}"

        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Cookie", session.cookie())
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()

        val response = http.send(request)

        if (response.statusCode() !in 200..299) {
            throw KFinanceException.NetworkException(
                statusCode = response.statusCode(),
                message = "Failed to fetch fundamentals for $symbol, status: ${response.statusCode()}"
            )
        }

        response.body().toFinancials(symbol, period)
    }

    @Serializable
    private data class QuoteSummaryResponseWrapper(val quoteSummary: QuoteSummaryResponse)

    @Serializable
    private data class QuoteSummaryResponse(val result: List<QuoteSummaryResult>? = null, val error: String? = null)

    @Serializable
    private data class QuoteSummaryResult(
        val incomeStatementHistory: ModuleWrapper? = null,
        val cashflowStatementHistory: ModuleWrapper? = null,
        val balanceSheetHistory: ModuleWrapper? = null,
        val incomeStatementHistoryQuarterly: ModuleWrapper? = null,
        val cashflowStatementHistoryQuarterly: ModuleWrapper? = null,
        val balanceSheetHistoryQuarterly: ModuleWrapper? = null
    )

    @Serializable
    private data class ModuleWrapper(
        val incomeStatementHistory: List<JsonObject>? = null,
        val cashflowStatements: List<JsonObject>? = null,
        val balanceSheetStatements: List<JsonObject>? = null
    )

    private fun String.toFinancials(symbol: String, period: Period): Financials {
        try {
            val wrapper = json.decodeFromString<QuoteSummaryResponseWrapper>(this)
            if (wrapper.quoteSummary.error != null) {
                throw KFinanceException.ParseException("Yahoo returned an error: ${wrapper.quoteSummary.error}")
            }
            val result =
                wrapper.quoteSummary.result?.firstOrNull() ?: return Financials(symbol, period, null, null, null)

            val incModule =
                if (period == Period.ANNUAL) result.incomeStatementHistory else result.incomeStatementHistoryQuarterly
            val cfModule =
                if (period == Period.ANNUAL) result.cashflowStatementHistory else result.cashflowStatementHistoryQuarterly
            val bsModule =
                if (period == Period.ANNUAL) result.balanceSheetHistory else result.balanceSheetHistoryQuarterly

            val incomeStatement = incModule?.incomeStatementHistory?.let { list ->
                IncomeStatement(list.mapNotNull { parseIncomeEntry(it) })
            }
            val cashFlow = cfModule?.cashflowStatements?.let { list ->
                CashFlow(list.mapNotNull { parseCashFlowEntry(it) })
            }
            val balanceSheet = bsModule?.balanceSheetStatements?.let { list ->
                BalanceSheet(list.mapNotNull { parseBalanceSheetEntry(it) })
            }

            return Financials(
                symbol = symbol,
                period = period,
                incomeStatement = incomeStatement,
                cashFlow = cashFlow,
                balanceSheet = balanceSheet
            )
        } catch (e: SerializationException) {
            throw KFinanceException.ParseException("Failed to parse Fundamentals JSON", e)
        }
    }

    private fun parseIncomeEntry(obj: JsonObject): IncomeEntry? {
        val endDate = extractRawLong(obj["endDate"]) ?: return null
        return IncomeEntry(
            endDate = endDate,
            totalRevenue = extractRawDouble(obj["totalRevenue"]),
            grossProfit = extractRawDouble(obj["grossProfit"]),
            operatingIncome = extractRawDouble(obj["operatingIncome"]),
            netIncome = extractRawDouble(obj["netIncome"])
        )
    }

    private fun parseCashFlowEntry(obj: JsonObject): CashFlowEntry? {
        val endDate = extractRawLong(obj["endDate"]) ?: return null
        return CashFlowEntry(
            endDate = endDate,
            operatingCashFlow = extractRawDouble(obj["totalCashFromOperatingActivities"])
                ?: extractRawDouble(obj["operatingCashflow"]),
            capitalExpenditures = extractRawDouble(obj["capitalExpenditures"]),
            netChangeInCash = extractRawDouble(obj["changeInCash"])
        )
    }

    private fun parseBalanceSheetEntry(obj: JsonObject): BalanceSheetEntry? {
        val endDate = extractRawLong(obj["endDate"]) ?: return null
        return BalanceSheetEntry(
            endDate = endDate,
            totalAssets = extractRawDouble(obj["totalAssets"]),
            totalLiabilities = extractRawDouble(obj["totalLiab"]),
            totalEquity = extractRawDouble(obj["totalStockholderEquity"]),
            bookValuePerShare = null
        )
    }

    private fun extractRawLong(element: JsonElement?): Long? {
        if (element == null || element !is JsonObject) return null
        return element["raw"]?.jsonPrimitive?.longOrNull
    }

    private fun extractRawDouble(element: JsonElement?): Double? {
        if (element == null || element !is JsonObject) return null
        return element["raw"]?.jsonPrimitive?.doubleOrNull
    }
}
