package kfinance.client

import kfinance.error.KFinanceException
import kfinance.model.*
import kfinance.net.KFinanceHttpClient
import kfinance.session.YahooSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal class FundamentalsClient(
    private val http: KFinanceHttpClient,
    private val session: YahooSession
) {
    companion object {
        private const val BASE_URL = "https://query2.finance.yahoo.com/ws/fundamentals-timeseries/v1/finance/timeseries"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private val FIELD_KEYS = listOf(
            "TotalRevenue", "GrossProfit", "OperatingIncome", "NetIncome",
            "OperatingCashFlow", "CapitalExpenditure", "ChangesInCash",
            "TotalAssets", "TotalLiabilitiesNetMinorityInterest", "TotalEquityGrossMinorityInterest", "TangibleBookValue",
        )

        private val json = Json { ignoreUnknownKeys = true }
        private const val PERIOD1 = 1483228800L
    }

    suspend fun fetch(symbol: String, period: Period): Financials = withContext(Dispatchers.IO) {
        session.ensureValid()

        val prefix = if (period == Period.ANNUAL) "annual" else "quarterly"
        val typeParam = FIELD_KEYS.joinToString(",") { "$prefix$it" }
        val period2 = Instant.now().epochSecond
        val url = "$BASE_URL/$symbol?symbol=$symbol&type=$typeParam&period1=$PERIOD1&period2=$period2"

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

        response.body().toFinancials(symbol, period, prefix)
    }

    private fun String.toFinancials(symbol: String, period: Period, prefix: String): Financials {
        try {
            val root = json.parseToJsonElement(this).jsonObject
            val result = root["timeseries"]?.jsonObject
                ?.get("result")?.jsonArray ?: return Financials(symbol, period, null, null, null)

            val fieldData = mutableMapOf<String, Map<String, Double>>()
            for (item in result) {
                val obj = item.jsonObject
                val dataKey = obj.keys.firstOrNull { it != "meta" && it != "timestamp" } ?: continue
                val dataArray = obj[dataKey]?.jsonArray ?: continue

                val byDate = mutableMapOf<String, Double>()
                for (elem in dataArray) {
                    val entry = elem.jsonObject
                    val asOfDate = entry["asOfDate"]?.jsonPrimitive?.contentOrNull ?: continue
                    val raw = entry["reportedValue"]?.jsonObject
                        ?.get("raw")?.jsonPrimitive?.doubleOrNull ?: continue
                    byDate[asOfDate] = raw
                }
                if (byDate.isNotEmpty()) fieldData[dataKey] = byDate
            }

            val allDates = fieldData.values.flatMap { it.keys }.distinct().sorted().reversed()

            fun field(key: String, date: String) = fieldData["$prefix$key"]?.get(date)

            val incomeEntries = allDates.map { date ->
                val endDate = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                IncomeEntry(
                    endDate = endDate,
                    totalRevenue = field("TotalRevenue", date),
                    grossProfit = field("GrossProfit", date),
                    operatingIncome = field("OperatingIncome", date),
                    netIncome = field("NetIncome", date),
                )
            }
            val cashFlowEntries = allDates.map { date ->
                val endDate = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                CashFlowEntry(
                    endDate = endDate,
                    operatingCashFlow = field("OperatingCashFlow", date),
                    capitalExpenditures = field("CapitalExpenditure", date),
                    netChangeInCash = field("ChangesInCash", date),
                )
            }
            val balanceSheetEntries = allDates.map { date ->
                val endDate = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                BalanceSheetEntry(
                    endDate = endDate,
                    totalAssets = field("TotalAssets", date),
                    totalLiabilities = field("TotalLiabilitiesNetMinorityInterest", date),
                    totalEquity = field("TotalEquityGrossMinorityInterest", date),
                    bookValuePerShare = field("TangibleBookValue", date),
                )
            }

            return Financials(
                symbol = symbol,
                period = period,
                incomeStatement = if (incomeEntries.isNotEmpty()) IncomeStatement(incomeEntries) else null,
                cashFlow = if (cashFlowEntries.isNotEmpty()) CashFlow(cashFlowEntries) else null,
                balanceSheet = if (balanceSheetEntries.isNotEmpty()) BalanceSheet(balanceSheetEntries) else null,
            )
        } catch (e: SerializationException) {
            throw KFinanceException.ParseException("Failed to parse Fundamentals JSON", e)
        }
    }
}
