package com.mcosta.mybooks.releases

/** Genre key for a record's CDU/UDC codes (`675$a`, in order). */
object GenreClassifier {
    fun classify(udc: List<String>): String = "unclassified"
}
