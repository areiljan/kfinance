package kfinance.session

import kfinance.error.KFinanceException
import kfinance.net.KFinanceHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration
import java.time.Instant

public interface YahooSession {
    public suspend fun ensureValid()
    public fun cookie(): String
    public fun crumb(): String
}

internal class DefaultYahooSession(private val http: KFinanceHttpClient) : YahooSession {

    private var cookie: String = ""
    private var crumb: String = ""
    private var expiresAt: Instant = Instant.EPOCH
    private val mutex = Mutex()

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val COOKIE_URL = "https://fc.yahoo.com"
        private const val CRUMB_URL = "https://query1.finance.yahoo.com/v1/test/getcrumb"
    }

    override suspend fun ensureValid() {
        mutex.withLock {
            if (isSessionValid()) return
            var attempts = 0
            while (!isSessionValid() && attempts < 3) {
                attempts++
                refresh()
            }
        }
    }

    private fun isSessionValid(): Boolean {
        return cookie.isNotEmpty() && crumb.isNotEmpty() && Instant.now().isBefore(expiresAt)
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val cookieReq = HttpRequest.newBuilder(URI.create(COOKIE_URL))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()

            val cookieRes = http.send(cookieReq)

            val setCookieHeaders = cookieRes.headers().allValues("Set-Cookie")
            val validCookie = setCookieHeaders.firstOrNull { it.startsWith("A3=") || it.startsWith("B=") }
                ?: throw KFinanceException.SessionException("Could not find A3/B cookie in Set-Cookie headers from fc.yahoo.com")

            cookie = validCookie.substringBefore(";")

            val crumbReq = HttpRequest.newBuilder(URI.create(CRUMB_URL))
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .GET()
                .build()

            val crumbRes = http.send(crumbReq)
            if (crumbRes.statusCode() !in 200..299) {
                throw KFinanceException.SessionException("Failed to fetch crumb, status: ${crumbRes.statusCode()}")
            }

            crumb = crumbRes.body().trim()
            if (crumb.isEmpty()) {
                throw KFinanceException.SessionException("Crumb is empty")
            }

            expiresAt = Instant.now().plus(Duration.ofHours(1))
        } catch (e: Exception) {
            if (e is KFinanceException) throw e
            throw KFinanceException.SessionException("Error refreshing session", e)
        }
    }

    override fun cookie(): String = cookie
    override fun crumb(): String = crumb
}
