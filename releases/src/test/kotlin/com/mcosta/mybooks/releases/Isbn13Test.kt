package com.mcosta.mybooks.releases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Isbn13Test {
    @Test
    fun hyphenatedIsbn13() = assertEquals("9789895682799", isbn13("978-989-568-279-9"))

    @Test
    fun isbn10ConvertedWithNewCheckDigit() = assertEquals("9789727723973", isbn13("972-772-397-7"))

    @Test
    fun badCheckDigitIsNull() = assertNull(isbn13("978-989-568-279-0"))

    @Test
    fun nonBooklandEanIsNull() = assertNull(isbn13("4006381333931"))

    @Test
    fun countryCodeIsNull() = assertNull(isbn13("PT"))
}
