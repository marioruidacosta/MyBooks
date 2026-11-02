package com.mcosta.mybooks.domain

/** Removes accents: decomposes characters (NFD) and drops the combining marks ("Ética" → "Etica"). */
expect fun foldAccents(text: String): String

/** Folds text for searching: lowercase and without accents ("Ética" → "etica", "José" → "jose"). */
fun searchKey(text: String): String = foldAccents(text).lowercase()

/** Unicode combining marks (category Mn), removed after decomposition. */
internal val combiningMarks = Regex("\\p{Mn}+")
