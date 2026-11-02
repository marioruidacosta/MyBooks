package com.mcosta.mybooks.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs on Android here and on iOS in CI: both platforms must fold accents identically. */
class SearchKeyTest {
    @Test fun foldsPortugueseAccents() = assertEquals("Etica a Nicomaco", foldAccents("Ética a Nicómaco"))

    @Test fun foldsTildeAndCedilla() = assertEquals("Acao", foldAccents("Ação"))

    @Test fun keepsPlainText() = assertEquals("Dune 1965", foldAccents("Dune 1965"))

    @Test fun searchKeyIsFoldedAndLowercase() = assertEquals("jose saramago", searchKey("José Saramago"))
}
