package com.mcosta.mybooks.data.lookup

import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal actual fun platformNetworkReason(cause: Throwable): String? = when (cause) {
    is UnknownHostException -> "no internet connection"
    is SocketTimeoutException, is InterruptedIOException -> "request timed out"
    else -> null
}
