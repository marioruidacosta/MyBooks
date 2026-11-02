package com.mcosta.mybooks.domain

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class IsbnTest {
    @Test fun isbn13Valid() = assertEquals("9780306406157", Isbn.normalize("9780306406157"))

    @Test fun stripsHyphensAndSpaces() = assertEquals("9780306406157", Isbn.normalize(" 978-0-306-40615-7 "))

    @Test fun isbn10Converted() = assertEquals("9780306406157", Isbn.normalize("0306406152"))

    @Test fun isbn10WithX() = assertEquals("9780439420891", Isbn.normalize("043942089x"))

    @Test fun isbn979Accepted() = assertEquals("9791032305690", Isbn.normalize("9791032305690"))

    @Test fun badCheckDigitRejected() = assertNull(Isbn.normalize("9780306406158"))

    @Test fun badIsbn10CheckDigitRejected() = assertNull(Isbn.normalize("0306406153"))

    @Test fun nonBookEanRejected() = assertNull(Isbn.normalize("4006381333931"))

    @Test fun garbageRejected() = assertNull(Isbn.normalize("hello"))

    @Test fun emptyRejected() = assertNull(Isbn.normalize(""))
}
