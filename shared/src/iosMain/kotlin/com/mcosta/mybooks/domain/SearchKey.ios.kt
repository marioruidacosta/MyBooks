package com.mcosta.mybooks.domain

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun foldAccents(text: String): String =
    combiningMarks.replace((text as NSString).decomposedStringWithCanonicalMapping, "")
