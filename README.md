# kfinance

Kotlin Yahoo Finance wrapper for quotes and fundamentals, designed as a lightweight subset of yfinance for JVM projects.

## JitPack

```kotlin
// settings.gradle.kts or build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

// build.gradle.kts
dependencies {
    implementation("com.github.areiljan:kfinance:0.1.0")
}
```

## Usage

```kotlin
import kfinance.KFinance
import kfinance.model.Period
import kfinance.error.KFinanceException

val kf = KFinance()

// Single quote
val quote = kf.quote("LHV1T.TL")
println("${quote.symbol}: ${quote.currentPrice} ${quote.currency}")

// Batch quotes
val quotes = kf.quotes("AAPL", "GOOGL", "MSFT")

// Annual fundamentals (income statement, balance sheet, cash flow)
val fin = kf.financials("AAPL", Period.ANNUAL)
fin.incomeStatement?.entries?.firstOrNull()?.let {
    println("Revenue: ${it.totalRevenue}")
}

// Quarterly fundamentals
val quarterly = kf.financials("AAPL", Period.QUARTERLY)

// Error handling
try {
    kf.quote("INVALID")
} catch (e: KFinanceException.TickerNotFoundException) {
    println("Ticker not found: ${e.symbol}")
} catch (e: KFinanceException.NetworkException) {
    println("HTTP ${e.statusCode}: ${e.message}")
}

kf.close()
```

## API

| Method | Returns | Description |
|---|---|---|
| `quote(symbol)` | `Quote` | Single quote, throws `TickerNotFoundException` if missing |
| `quotes(vararg symbols)` | `List<Quote>` | Batch quotes, returns empty list for missing tickers |
| `financials(symbol, period)` | `Financials` | Annual or quarterly fundamentals |
