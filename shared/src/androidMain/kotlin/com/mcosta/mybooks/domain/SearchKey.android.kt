package com.mcosta.mybooks.domain

import java.text.Normalizer

actual fun foldAccents(text: String): String =
    combiningMarks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
