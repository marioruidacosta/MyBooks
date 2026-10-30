package com.mcosta.mybooks.data.lookup

import okhttp3.mockwebserver.MockResponse

fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/lookup/$name")) { "missing fixture $name" }.readText()

fun json(name: String): MockResponse =
    MockResponse().setHeader("Content-Type", "application/json").setBody(fixture(name))
