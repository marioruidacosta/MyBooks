package com.mcosta.mybooks.releases

import kotlin.test.Test
import kotlin.test.assertEquals

class GenreClassifierTest {
    private fun genre(vararg udc: String) = GenreClassifier.classify(udc.toList())

    @Test
    fun literatureForms() {
        assertEquals("crime", genre("821.111(417)-312.4\"20\""))
        assertEquals("fantasy", genre("821.111(73)-312.9\"20\""))
        assertEquals("scifi", genre("821.111(73)-311.9\"19/20\""))
        assertEquals("historical", genre("821.134.3-311.6\"20\""))
        assertEquals("adventure", genre("821.134.3-311.3\"20\""))
        assertEquals("adventure", genre("821.134.3-311.8\"20\""))
        assertEquals("novel", genre("821.133.1-31\"20\""))
        assertEquals("novel", genre("821.134.3-311.1\"20\""))
        assertEquals("stories", genre("821.112.2(437.3)-32\"19\""))
        assertEquals("stories", genre("821.134.3-34\"20\""))
        assertEquals("poetry", genre("821.134.3(81)-1\"19/20\""))
        assertEquals("theatre", genre("821.134.3-2\"20\""))
        assertEquals("essays", genre("821.134.3-4\"20\""))
        assertEquals("biography", genre("821.134.2(82)-94\"20\""))
        assertEquals("humour", genre("821.134.3-7\"20\""))
        assertEquals("literature", genre("821.134.3-92\"20\""))
        assertEquals("literature", genre("821.134.3Camões, Luís de.09"))
        assertEquals("language", genre("811.134.3'36(035)", "003.086(035)"))
    }

    @Test
    fun children() {
        assertEquals("children", genre("087.5", "159.942(0:053.4)"))
        assertEquals("children", genre("821.111(73)-93\"20\""))
    }

    @Test
    fun childrenWinsOverNovel() = assertEquals("children", genre("821.111(73)-93-31\"20\""))

    @Test
    fun nonFictionByFirstDigit() {
        assertEquals("general", genre("003.07(072)"))
        assertEquals("philosophy", genre("159.95(035)", "615.85(035)"))
        assertEquals("religion", genre("279-584"))
        assertEquals("social", genre("325.8(673)\"1971/1972\"(047.53)"))
        assertEquals("science", genre("53(075.3)"))
        assertEquals("applied", genre("614.8.084(035)"))
        assertEquals("arts", genre("730Romão, André(083.82)"))
        assertEquals("history", genre("94(52)\"-04/20\""))
    }

    @Test
    fun otherAndUnclassified() {
        assertEquals("other", genre("4"))
        assertEquals("unclassified", genre())
    }

    /** A book catalogued first under medicine but also as literature counts as literature (rule 2 before rule 3). */
    @Test
    fun usesFirst82Code() =
        assertEquals("literature", genre("61(469)(046)", "61Pereira, Luís Paulino(047.53)", "821.134.3-92\"20\""))
}
