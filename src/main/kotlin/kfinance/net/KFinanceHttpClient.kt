package kfinance.net

import java.net.http.HttpRequest
import java.net.http.HttpResponse

public fun interface KFinanceHttpClient {
    public fun send(request: HttpRequest): HttpResponse<String>
}
