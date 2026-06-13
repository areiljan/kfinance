package kfinance.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class Quote(
    public val symbol: String,
    @SerialName("regularMarketPrice") public val currentPrice: Double? = null,
    @SerialName("regularMarketPreviousClose") public val previousClose: Double? = null,
    @SerialName("regularMarketOpen") public val open: Double? = null,
    @SerialName("regularMarketDayHigh") public val dayHigh: Double? = null,
    @SerialName("regularMarketDayLow") public val dayLow: Double? = null,
    @SerialName("regularMarketVolume") public val volume: Long? = null,
    public val marketCap: Long? = null,
    public val currency: String? = null,
    @SerialName("exchange") public val exchangeName: String? = null
)
