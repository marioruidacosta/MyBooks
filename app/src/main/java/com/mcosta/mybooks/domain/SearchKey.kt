package com.mcosta.mybooks.domain

import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{Mn}+")

/** Folds text for searching: lowercase and without accents ("Ética" → "etica", "José" → "jose"). */
fun searchKey(text: String): String =
    combiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
