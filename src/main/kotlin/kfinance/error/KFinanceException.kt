package kfinance.error

public sealed class KFinanceException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    public class SessionException(message: String, cause: Throwable? = null) : KFinanceException(message, cause)

    public class NetworkException(public val statusCode: Int?, message: String, cause: Throwable? = null) :
        KFinanceException(message, cause)

    public class TickerNotFoundException(public val symbol: String) : KFinanceException("Ticker not found: $symbol")

    public class ParseException(message: String, cause: Throwable? = null) : KFinanceException(message, cause)
}
