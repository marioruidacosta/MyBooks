package com.mcosta.mybooks.ui.edit

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlin.test.assertEquals
import kotlin.test.Test

class IsbnFieldTest {
    /** ISBN-10s can end in "X", which a numeric keyboard can't type. */
    @Test
    fun isbnKeyboardAllowsLetterX() {
        assertEquals(KeyboardType.Ascii, isbnKeyboardOptions.keyboardType)
        assertEquals(KeyboardCapitalization.Characters, isbnKeyboardOptions.capitalization)
    }
}
