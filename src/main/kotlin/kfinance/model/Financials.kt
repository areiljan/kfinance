package kfinance.model

public data class Financials(
    public val symbol: String,
    public val period: Period,
    public val incomeStatement: IncomeStatement?,
    public val cashFlow: CashFlow?,
    public val balanceSheet: BalanceSheet?,
)

public data class IncomeStatement(public val entries: List<IncomeEntry>)
public data class CashFlow(public val entries: List<CashFlowEntry>)
public data class BalanceSheet(public val entries: List<BalanceSheetEntry>)

public data class IncomeEntry(
    public val endDate: Long,
    public val totalRevenue: Double?,
    public val grossProfit: Double?,
    public val operatingIncome: Double?,
    public val netIncome: Double?,
)

public data class CashFlowEntry(
    public val endDate: Long,
    public val operatingCashFlow: Double?,
    public val capitalExpenditures: Double?,
    public val netChangeInCash: Double?,
)

public data class BalanceSheetEntry(
    public val endDate: Long,
    public val totalAssets: Double?,
    public val totalLiabilities: Double?,
    public val totalEquity: Double?,
    public val bookValuePerShare: Double?,
)
