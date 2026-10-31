package com.mcosta.mybooks.data.lookup

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut

internal actual fun platformNetworkReason(cause: Throwable): String? {
    val code = (cause as? DarwinHttpRequestException)?.origin?.code ?: return null
    return when (code) {
        NSURLErrorNotConnectedToInternet, NSURLErrorCannotFindHost -> "no internet connection"
        NSURLErrorTimedOut -> "request timed out"
        else -> null
    }
}
