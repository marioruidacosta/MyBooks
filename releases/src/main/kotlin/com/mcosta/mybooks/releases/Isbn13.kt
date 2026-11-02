package com.mcosta.mybooks.releases

/**
 * The 13-digit form of [raw] (hyphens and spaces removed; ISBN-10 converted, with a new check digit), or null
 * when it isn't a valid ISBN (13 digits must start 978 or 979). Agrees with the app's `Isbn.normalize`.
 */
fun isbn13(raw: String): String? {
    val s = raw.filter { !it.isWhitespace() && it != '-' }.uppercase()
    return when {
        s.length == 13 && s.all(Char::isDigit) && (s.startsWith("978") || s.startsWith("979")) ->
            s.takeIf { checkDigit13(it.take(12)) == it[12] }
        s.length == 10 && s.take(9).all(Char::isDigit) && (s[9].isDigit() || s[9] == 'X') ->
            if (checkDigit10(s.take(9)) == s[9]) "978${s.take(9)}".let { it + checkDigit13(it) } else null
        else -> null
    }
}

private fun checkDigit13(first12: String): Char {
    val sum = first12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
    return '0' + (10 - sum % 10) % 10
}

private fun checkDigit10(first9: String): Char {
    val sum = first9.mapIndexed { i, c -> (c - '0') * (10 - i) }.sum()
    val d = (11 - sum % 11) % 11
    return if (d == 10) 'X' else '0' + d
}
